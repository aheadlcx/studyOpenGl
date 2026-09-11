package com.example.studyopengl.demos;

import android.opengl.GLES30;
import android.opengl.Matrix;

import com.example.studyopengl.engine.BaseDemoEngine;
import com.example.studyopengl.gl.GeoGen;
import com.example.studyopengl.gl.Mesh;
import com.example.studyopengl.gl.ShaderProgram;
import com.example.studyopengl.param.ParamSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 29 · 帧缓冲与渲染到纹理（RTT）＋ 三种附件可视化
 *
 * Pass1 把场景画进 FBO：颜色 → 颜色纹理，深度 → 深度纹理（不再是 RBO，可采样！）
 * Pass2 切换查看：附件0（彩色画面）/ 深度附件（灰度=离相机的距离）
 * —— 让你亲眼看到"深度缓冲里到底存了什么"。
 */
public class D20FboRtt extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍帧缓冲的三种附件（每个像素三层记录）\n"
            + "① 颜色附件 COLOR_ATTACHMENT0 —— 片元着色器的输出，最终显示的画面；\n"
            + "② 深度附件 DEPTH_ATTACHMENT —— 每像素一个\"离相机距离\"，深度测试的判决依据；\n"
            + "③ 模板附件 STENCIL_ATTACHMENT —— 8位手工掩码，镂空判决（第 16 章）；\n"
            + "  ES3 还有 DEPTH24_STENCIL8 把②③打包在同一块存储。\n\n"
            + "▍本界面的核心变化：深度附件用的是【深度纹理】\n"
            + "普通写法深度挂在 renderbuffer（只测试不读取）；本例改挂纹理，"
            + "于是 Pass2 可以【采样它】——把深度值显示成灰度，亲眼看到：\n"
            + "· 深度非线性（1/z）：近处渐变丰富，远处骤然压缩；\n"
            + "· 这也是阴影贴图（Shadow Mapping）的实现基础。\n\n"
            + "▍创建 FBO 四步\n"
            + "① glGenFramebuffers + glBindFramebuffer；\n"
            + "② 颜色附件：纹理 + glFramebufferTexture2D(COLOR_ATTACHMENT0)；\n"
            + "③ 深度附件：GL_DEPTH_COMPONENT24 纹理 + glFramebufferTexture2D(DEPTH_ATTACHMENT)；\n"
            + "④ glCheckFramebufferStatus == COMPLETE 才能用。\n\n"
            + "▍切换到【深度】视图观察\n"
            + "· 越近越亮、越远越暗（做了 pow(d,8) 对比增强，原始深度挤在接近 1 的区间）；\n"
            + "· 立方体与地面在深度图里边界清晰——遮挡关系一目了然；\n"
            + "· 画面边缘（清屏值）= 最远 = 1.0。\n\n"
            + "▍完整性规则\n"
            + "所有启用的附件必须尺寸一致、格式可渲染；任何缺失 → FBO 不完整。\n"
            + "切回屏幕时记得 glBindFramebuffer(0) + glViewport(屏幕尺寸) ——两个都要！";

    private static final String SCENE_VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec3 a_color;\n"
            + "uniform mat4 u_mvp;\n"
            + "out vec3 v_color;\n"
            + "void main() {\n"
            + "    v_color = a_color;\n"
            + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String SCENE_FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec3 v_color;\n"
            + "out vec4 fragColor;\n"
            + "void main() { fragColor = vec4(v_color, 1.0); }\n";

    private static final String DISPLAY_VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=2) in vec2 a_uv;\n"
            + "uniform float u_mirror;\n"
            + "uniform float u_zoom;\n"
            + "out vec2 v_uv;\n"
            + "void main() {\n"
            + "    vec2 uv = (a_uv - 0.5) * u_zoom + 0.5;\n"
            + "    v_uv = vec2(mix(uv.x, 1.0 - uv.x, u_mirror), uv.y);\n"
            + "    gl_Position = vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String DISPLAY_FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec2 v_uv;\n"
            + "uniform sampler2D u_colorTex;\n"
            + "uniform sampler2D u_depthTex;\n"
            + "uniform int u_view;          // 0=颜色附件 1=深度附件\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    if (u_view == 1) {\n"
            + "        // 直接采样深度纹理：r 通道 = 深度值（非线性 1/z）\n"
            + "        float d = texture(u_depthTex, v_uv).r;\n"
            + "        fragColor = vec4(vec3(pow(d, 8.0)), 1.0);  // pow 增强近处对比\n"
            + "    } else {\n"
            + "        fragColor = texture(u_colorTex, v_uv);\n"
            + "    }\n"
            + "}\n";

    private static final String KEY_VIEW = "view";
    private static final String KEY_ZOOM = "zoom";
    private static final String KEY_MIRROR = "mirror";
    private static final String KEY_SPEED = "speed";

    private static final String[] VIEW_LABELS = {"附件0 · 颜色（画面）", "附件 · 深度（灰度=距离）"};

    private ShaderProgram mSceneProgram;
    private ShaderProgram mDisplayProgram;
    private Mesh mCubes;
    private Mesh mGround;
    private Mesh mFullScreenQuad;
    private int mFBO;
    private int mColorTex;
    private int mDepthTex;          // 深度附件改用纹理：可采样！
    private static final int RT_SIZE = 1024;
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mMvp = new float[16];
    private float mTime;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mSceneProgram = new ShaderProgram(SCENE_VS, SCENE_FS);
        mDisplayProgram = new ShaderProgram(DISPLAY_VS, DISPLAY_FS);

        // 立方体：pos+color
        GeoGen.GeoData cube = GeoGen.cube();
        int n = cube.positions.length / 3;
        float[] verts = new float[n * 6];
        for (int i = 0; i < n; i++) {
            int face = i / 4;
            float r = face == 0 || face == 1 ? 0.9f : 0.2f + face * 0.12f;
            float g = face == 2 || face == 3 ? 0.9f : 0.3f;
            float b = face == 4 || face == 5 ? 0.9f : 0.35f + (5 - face) * 0.1f;
            verts[i * 6] = cube.positions[i * 3];
            verts[i * 6 + 1] = cube.positions[i * 3 + 1];
            verts[i * 6 + 2] = cube.positions[i * 3 + 2];
            verts[i * 6 + 3] = r;
            verts[i * 6 + 4] = g;
            verts[i * 6 + 5] = b;
        }
        mCubes = new Mesh.Builder()
                .addBuffer(verts, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .setIndices(cube.indices)
                .build();

        // 地面：pos+color（灰色，让深度图有连续渐变）
        mGround = makeGroundMesh();

        mFullScreenQuad = new Mesh.Builder()
                .addBuffer(new float[]{
                        -1, -1, 0, 0, 0,
                        1, -1, 0, 1, 0,
                        -1, 1, 0, 0, 1,
                        1, -1, 0, 1, 0,
                        1, 1, 0, 1, 1,
                        -1, 1, 0, 0, 1
                }, new Mesh.Attrib(0, 3), new Mesh.Attrib(2, 2))
                .build();

        setupFramebuffer(RT_SIZE);
    }

    private Mesh makeGroundMesh() {
        float s = 6.0f;
        float[] g = {
            -s, -0.5f, -s, 0.35f, 0.4f, 0.45f,
             s, -0.5f, -s, 0.35f, 0.4f, 0.45f,
            -s, -0.5f,  s, 0.55f, 0.6f, 0.65f,
             s, -0.5f, -s, 0.35f, 0.4f, 0.45f,
             s, -0.5f,  s, 0.55f, 0.6f, 0.65f,
            -s, -0.5f,  s, 0.55f, 0.6f, 0.65f
        };
        return new Mesh.Builder()
                .addBuffer(g, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .build();
    }

    /** FBO 四步：fbo → 颜色纹理附件 → 深度【纹理】附件 → 完整性检查。 */
    private void setupFramebuffer(int size) {
        releaseFramebuffer();

        // 颜色附件：普通 RGBA 纹理
        int[] tex = new int[1];
        GLES30.glGenTextures(1, tex, 0);
        mColorTex = tex[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mColorTex);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
                size, size, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
        com.example.studyopengl.gl.TextureHelper.setParams(
                GLES30.GL_CLAMP_TO_EDGE, GLES30.GL_CLAMP_TO_EDGE,
                GLES30.GL_LINEAR, GLES30.GL_LINEAR);

        // 深度附件：深度纹理（ES3 核心能力，可被 FS 采样）
        int[] dtex = new int[1];
        GLES30.glGenTextures(1, dtex, 0);
        mDepthTex = dtex[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mDepthTex);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT24,
                size, size, 0, GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_INT, null);
        com.example.studyopengl.gl.TextureHelper.setParams(
                GLES30.GL_CLAMP_TO_EDGE, GLES30.GL_CLAMP_TO_EDGE,
                GLES30.GL_LINEAR, GLES30.GL_LINEAR);

        int[] fbo = new int[1];
        GLES30.glGenFramebuffers(1, fbo, 0);
        mFBO = fbo[0];
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mFBO);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, mColorTex, 0);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,
                GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, mDepthTex, 0);

        int status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("FBO 不完整: 0x" + Integer.toHexString(status));
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
    }

    private void releaseFramebuffer() {
        if (mFBO != 0) {
            GLES30.glDeleteFramebuffers(1, new int[]{mFBO}, 0);
            mFBO = 0;
        }
        if (mColorTex != 0) {
            GLES30.glDeleteTextures(1, new int[]{mColorTex}, 0);
            mColorTex = 0;
        }
        if (mDepthTex != 0) {
            GLES30.glDeleteTextures(1, new int[]{mDepthTex}, 0);
            mDepthTex = 0;
        }
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        mTime += deltaTime * getFloat(KEY_SPEED);

        // ============ Pass 1：渲染到 FBO（颜色纹理 + 深度纹理同时产出）============
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mFBO);
        GLES30.glViewport(0, 0, RT_SIZE, RT_SIZE);
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);   // 清屏深度=最远，颜色=黑
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 50f, 1f, 0.1f, 30f);
        Matrix.setLookAtM(mView, 0, 3.4f, 2.4f, 4.2f, 0, 0.4f, 0, 0, 1, 0);
        float[] pv = new float[16];
        Matrix.multiplyMM(pv, 0, mProj, 0, mView, 0);

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        mSceneProgram.use();
        
        float[] mvp = new float[16];

        // 地面（大面积灰色，让深度图有连续渐变）
        Matrix.setIdentityM(mModel, 0);
        Matrix.multiplyMM(mvp, 0, pv, 0, mModel, 0);
        mSceneProgram.setMat4("u_mvp", mvp);
        mGround.draw(GLES30.GL_TRIANGLES);

        // 四个彩色立方体
        for (int i = 0; i < 4; i++) {
            Matrix.setIdentityM(mModel, 0);
            Matrix.translateM(mModel, 0,
                    i % 2 == 0 ? -0.8f : 0.8f, 0.5f, i < 2 ? -0.8f : 0.8f);
            Matrix.rotateM(mModel, 0, mTime * 40f + i * 45f, 0.4f, 1, 0.2f);
            Matrix.multiplyMM(mvp, 0, pv, 0, mModel, 0);
            mSceneProgram.setMat4("u_mvp", mvp);
            mCubes.draw(GLES30.GL_TRIANGLES);
        }
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);

        // ============ Pass 2：查看某个附件 ============
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, mWidth, mHeight);
        GLES30.glClearColor(0.02f, 0.02f, 0.04f, 1.0f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

        int view = getOptionIndex(KEY_VIEW);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mColorTex);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mDepthTex);
        mDisplayProgram.use();
        mDisplayProgram.set("u_colorTex", 0);
        mDisplayProgram.set("u_depthTex", 1);
        GLES30.glUniform1i(mDisplayProgram.loc("u_view"), view);
        GLES30.glUniform1f(mDisplayProgram.loc("u_mirror"), getBool(KEY_MIRROR) ? 1f : 0f);
        GLES30.glUniform1f(mDisplayProgram.loc("u_zoom"), getFloat(KEY_ZOOM));
        mFullScreenQuad.draw(GLES30.GL_TRIANGLES);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<>();
        specs.add(ParamSpec.optionSpec(KEY_VIEW, "查看哪个附件", VIEW_LABELS, 0));
        specs.add(ParamSpec.floatSpec(KEY_ZOOM, "RTT 采样缩放", 0.3f, 1f, 1f));
        specs.add(ParamSpec.boolSpec(KEY_MIRROR, "镜像采样", false));
        specs.add(ParamSpec.floatSpec(KEY_SPEED, "旋转速度", 0f, 2f, 0.5f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        releaseFramebuffer();
        mCubes.dispose();
        mGround.dispose();
        mFullScreenQuad.dispose();
        mSceneProgram.release();
        mDisplayProgram.release();
    }
}
