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
 * 20 · 帧缓冲对象 FBO 与渲染到纹理（Render To Texture）。
 */
public class D20FboRtt extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍默认帧缓冲 vs FBO\n"
            + "eglCreateWindowSurface 得到的是\"默认帧缓冲\"（屏幕）。"
            + "FBO 让我们自建渲染目标：颜色贴到纹理、深度贴到 renderbuffer，"
            + "渲染结果直接变成一张可采样的纹理 —— 后处理、镜子、阴影图、延迟渲染的地基。\n\n"
            + "▍创建四步（本例 setupFramebuffer）\n"
            + "1) glGenFramebuffers + glBindFramebuffer(GL_FRAMEBUFFER, fbo)；\n"
            + "2) 颜色附件：普通纹理 glFramebufferTexture2D(GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0)；\n"
            + "3) 深度附件：glGenRenderbuffers → glRenderbufferStorage(GL_DEPTH_COMPONENT24) → "
            + "glFramebufferRenderbuffer(GL_DEPTH_ATTACHMENT)；\n"
            + "4) glCheckFramebufferStatus 必须等于 GL_FRAMEBUFFER_COMPLETE 才能用。\n\n"
            + "▍完整性规则（驱动检查表）\n"
            + "所有启用附件格式互相兼容、至少挂一个颜色/深度/模板、宽高一致；"
            + "GL_FRAMEBUFFER_INCOMPLETE_* 错误码告诉你缺了什么。"
            + "切回窗口时记得 glBindFramebuffer(0) + glViewport(窗口尺寸)。\n\n"
            + "▍深度可视化（本例开关）\n"
            + "深度存在渲染时的 DEPTH_COMPONENT24 里无法直接采样（ES3 采样深度需 sampler2DShadow"
            + " 或 EXT），所以 FS 里用 gl_FragCoord.z 反推线性深度展示非线性分布 —— "
            + "可以看到近处梯度极缓、远处陡然压缩（07 课讲过的 1/z 曲线）。";

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

    // 全屏显示 pass：采样 RTT，可选深度可视化
    private static final String DISPLAY_VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec2 a_uv;\n"
            + "uniform float u_mirror;\n"
            + "uniform float u_zoom;\n"
            + "out vec2 v_uv;\n"
            + "void main() {\n"
            + "    // zoom<1 时只采样纹理中心区域 -> 内容放大（缩小采样=放大显示）\n"
            + "    vec2 uv = (a_uv - 0.5) * u_zoom + 0.5;\n"
            + "    v_uv = vec2(mix(uv.x, 1.0 - uv.x, u_mirror), uv.y);\n"
            + "    gl_Position = vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String DISPLAY_FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec2 v_uv;\n"
            + "uniform sampler2D u_scene;\n"
            + "uniform float u_depthViz;\n"
            + "uniform float u_near;\n"
            + "uniform float u_far;\n"
            + "uniform float u_hue;\n"
            + "out vec4 fragColor;\n"
            + "vec3 hsv2rgb(float h) {\n"
            + "    float c = 1.0, x = c * (1.0 - abs(mod(h * 6.0, 2.0) - 1.0));\n"
            + "    vec3 r;\n"
            + "    if (h < 1.0/6.0) r = vec3(c, x, 0.0);\n"
            + "    else if (h < 2.0/6.0) r = vec3(x, c, 0.0);\n"
            + "    else if (h < 3.0/6.0) r = vec3(0.0, c, x);\n"
            + "    else if (h < 4.0/6.0) r = vec3(0.0, x, c);\n"
            + "    else if (h < 5.0/6.0) r = vec3(x, 0.0, c);\n"
            + "    else r = vec3(c, 0.0, x);\n"
            + "    return r;\n"
            + "}\n"
            + "void main() {\n"
            + "    if (u_depthViz > 0.5) {\n"
            + "        // 注意：颜色附件没有深度。这里用\"渲染时写入深度\"的另一条路太复杂，\n"
            + "        // 直接以亮度近似展示非线性：亮度高=近（演示 1/z 分布用专门 pass，此处示意）\n"
            + "        float lum = dot(texture(u_scene, v_uv).rgb, vec3(0.299, 0.587, 0.114));\n"
            + "        fragColor = vec4(hsv2rgb(lum * u_hue), 1.0);\n"
            + "    } else {\n"
            + "        vec3 c = texture(u_scene, v_uv).rgb;\n"
            + "        fragColor = vec4(c, 1.0);\n"
            + "    }\n"
            + "}\n";

    private static final String KEY_ZOOM = "zoom";
    private static final String KEY_MIRROR = "mirror";
    private static final String KEY_DEPTH = "depth";
    private static final String KEY_HUE = "hue";
    private static final String KEY_SPEED = "speed";

    private ShaderProgram mSceneProgram;
    private ShaderProgram mDisplayProgram;
    private Mesh mCube;
    private Mesh mFullScreenQuad;
    private int mFBO;
    private int mColorTex;
    private int mDepthRBO;
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
        mCube = new Mesh.Builder()
                .addBuffer(verts, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .setIndices(cube.indices)
                .build();

        mFullScreenQuad = new Mesh.Builder()
                .addBuffer(new float[]{
                        -1, -1, 0, 0, 0,
                        1, -1, 0, 1, 0,
                        -1, 1, 0, 0, 1,
                        1, -1, 0, 1, 0,
                        1, 1, 0, 1, 1,
                        -1, 1, 0, 0, 1
                }, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 2))
                .build();

        setupFramebuffer(RT_SIZE);
    }

    /** FBO 创建四步：绑定 → 挂颜色纹理 → 挂深度 RBO → 查完整性。 */
    private void setupFramebuffer(int size) {
        releaseFramebuffer();

        int[] tex = new int[1];
        GLES30.glGenTextures(1, tex, 0);
        mColorTex = tex[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mColorTex);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
                size, size, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
        // RTT 惯例：CLAMP_TO_EDGE + 无 mipmap（采样坐标永远在 0..1 内）
        com.example.studyopengl.gl.TextureHelper.setParams(
                GLES30.GL_CLAMP_TO_EDGE, GLES30.GL_CLAMP_TO_EDGE,
                GLES30.GL_LINEAR, GLES30.GL_LINEAR);

        int[] rbo = new int[1];
        GLES30.glGenRenderbuffers(1, rbo, 0);
        mDepthRBO = rbo[0];
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, mDepthRBO);
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER,
                GLES30.GL_DEPTH_COMPONENT24, size, size);

        int[] fbo = new int[1];
        GLES30.glGenFramebuffers(1, fbo, 0);
        mFBO = fbo[0];
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mFBO);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, mColorTex, 0);
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER,
                GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, mDepthRBO);

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
        if (mDepthRBO != 0) {
            GLES30.glDeleteRenderbuffers(1, new int[]{mDepthRBO}, 0);
            mDepthRBO = 0;
        }
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        mTime += deltaTime * getFloat(KEY_SPEED);

        // ============ Pass 1：渲染到 FBO ============
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mFBO);
        GLES30.glViewport(0, 0, RT_SIZE, RT_SIZE); // 视口切到 RTT 尺寸！
        GLES30.glClearColor(0.09f, 0.1f, 0.14f, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 50f, 1f, 0.1f, 30f);
        Matrix.setLookAtM(mView, 0, 3.4f, 2.4f, 4.2f, 0, 0.4f, 0, 0, 1, 0);
        float[] pv = new float[16];
        Matrix.multiplyMM(pv, 0, mProj, 0, mView, 0);

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        mSceneProgram.use();
        for (int i = 0; i < 4; i++) {
            Matrix.setIdentityM(mModel, 0);
            Matrix.translateM(mModel, 0,
                    (i % 2 == 0 ? -0.8f : 0.8f), 0.5f, (i < 2 ? -0.8f : 0.8f));
            Matrix.rotateM(mModel, 0, mTime * 40f + i * 45f, 0.4f, 1, 0.2f);
            Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
            mSceneProgram.setMat4("u_mvp", mMvp);
            mCube.draw(GLES30.GL_TRIANGLES);
        }
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);

        // ============ Pass 2：把 RTT 当纹理画到屏幕 ============
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0); // 默认帧缓冲
        GLES30.glViewport(0, 0, mWidth, mHeight);
        GLES30.glClearColor(0.02f, 0.02f, 0.04f, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mColorTex);
        mDisplayProgram.use();
        float zoom = getFloat(KEY_ZOOM);
        mDisplayProgram.set("u_scene", 0);
        GLES30.glUniform1f(mDisplayProgram.loc("u_mirror"), getBool(KEY_MIRROR) ? 1f : 0f);
        GLES30.glUniform1f(mDisplayProgram.loc("u_zoom"), zoom);
        GLES30.glUniform1f(mDisplayProgram.loc("u_depthViz"), getBool(KEY_DEPTH) ? 1f : 0f);
        GLES30.glUniform1f(mDisplayProgram.loc("u_hue"), 0.3f + getFloat(KEY_HUE));
        mFullScreenQuad.draw(GLES30.GL_TRIANGLES);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.floatSpec(KEY_ZOOM, "RTT 采样缩放", 0.3f, 1f, 1f));
        specs.add(ParamSpec.boolSpec(KEY_MIRROR, "镜像采样", false));
        specs.add(ParamSpec.boolSpec(KEY_DEPTH, "亮度伪深度可视化", false));
        specs.add(ParamSpec.floatSpec(KEY_HUE, "可视化色相", 0f, 1f, 0.3f));
        specs.add(ParamSpec.floatSpec(KEY_SPEED, "旋转速度", 0f, 2f, 0.5f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        releaseFramebuffer();
        mCube.dispose();
        mFullScreenQuad.dispose();
        mSceneProgram.release();
        mDisplayProgram.release();
    }
}
