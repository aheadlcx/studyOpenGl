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
 * 21 · 后处理：FBO 全屏 pass + 3x3 卷积核（模糊/锐化/边缘/浮雕）。
 */
public class D21PostProcess extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍后处理管线\n"
            + "Pass1 把场景画进 FBO 纹理 → Pass2 用一个全屏四边形把纹理\"贴\"回屏幕，"
            + "采样时逐像素做卷积。所有滤镜（景深、Bloom、FXAA、调色）都是这个骨架。\n\n"
            + "▍3×3 卷积核\n"
            + "对当前像素周围 9 个采样点加权求和，权重矩阵就是 kernel：\n"
            + "· 锐化 {{0,-1,0},{-1,5,-1},{0,-1,0}} —— 中心权重>1 突出自身与邻域差；\n"
            + "· 盒式模糊 全 1/9 —— 邻域平均；\n"
            + "· 高斯 {{1,2,1},{2,4,2},{1,2,1}}/16 —— 按距离加权，比盒式更自然；\n"
            + "· 边缘检测 {{1,1,1},{1,-8,1},{1,1,1}} —— 邻域和减 8 倍中心，平坦处≈0；\n"
            + "· 浮雕 {{-2,-1,0},{-1,1,1},{0,1,2}} —— 方向性明暗错位。\n\n"
            + "▍采样偏移\n"
            + "offset = 1.0/纹理宽高（一个纹素）。乘上\"步长\"滑条可以放大取样范围，"
            + "模糊立刻变强 —— 这也是廉价大半径模糊的做法。\n\n"
            + "▍性能注记\n"
            + "· 可分离核（高斯）拆成横竖两个 1D pass，O(9)→O(6)；\n"
            + "· RTT 纹理 wrap 必须 CLAMP_TO_EDGE，REPEAT 会把屏幕对边\"绕\"进来；\n"
            + "· 多个效果串成链时注意 FBO ping-pong（读写同一纹理是未定义行为）。";

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

    private static final String POST_VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec2 a_uv;\n"
            + "out vec2 v_uv;\n"
            + "void main() {\n"
            + "    v_uv = a_uv;\n"
            + "    gl_Position = vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String POST_FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec2 v_uv;\n"
            + "out vec4 fragColor;\n"
            + "uniform sampler2D u_scene;\n"
            + "uniform float u_offset;\n"          // 一个"纹素"大小
            + "uniform float u_kernel[9];\n"       // 3x3 卷积核
            + "uniform float u_mix;\n"             // 原图与效果混合
            + "void main() {\n"
            + "    vec2 off = vec2(u_offset, u_offset);\n"
            + "    vec3 sum =\n"
            + "        texture(u_scene, v_uv + off * vec2(-1.0,  1.0)).rgb * u_kernel[0] +\n"
            + "        texture(u_scene, v_uv + off * vec2( 0.0,  1.0)).rgb * u_kernel[1] +\n"
            + "        texture(u_scene, v_uv + off * vec2( 1.0,  1.0)).rgb * u_kernel[2] +\n"
            + "        texture(u_scene, v_uv + off * vec2(-1.0,  0.0)).rgb * u_kernel[3] +\n"
            + "        texture(u_scene, v_uv).rgb                          * u_kernel[4] +\n"
            + "        texture(u_scene, v_uv + off * vec2( 1.0,  0.0)).rgb * u_kernel[5] +\n"
            + "        texture(u_scene, v_uv + off * vec2(-1.0, -1.0)).rgb * u_kernel[6] +\n"
            + "        texture(u_scene, v_uv + off * vec2( 0.0, -1.0)).rgb * u_kernel[7] +\n"
            + "        texture(u_scene, v_uv + off * vec2( 1.0, -1.0)).rgb * u_kernel[8];\n"
            + "    vec3 orig = texture(u_scene, v_uv).rgb;\n"
            + "    fragColor = vec4(mix(orig, sum, u_mix), 1.0);\n"
            + "}\n";

    private static final String KEY_KERNEL = "kernel";
    private static final String KEY_STEP = "step";
    private static final String KEY_MIX = "mix";
    private static final String KEY_SPEED = "speed";

    private static final String[] KERNEL_LABELS = {
            "原图", "锐化 Sharpen", "盒式模糊 Box Blur", "高斯模糊 Gaussian",
            "边缘检测 Edge", "浮雕 Emboss"
    };
    private static final float[][] KERNELS = {
            {0, 0, 0, 0, 1, 0, 0, 0, 0},
            {0, -1, 0, -1, 5, -1, 0, -1, 0},
            {1f / 9f, 1f / 9f, 1f / 9f, 1f / 9f, 1f / 9f, 1f / 9f, 1f / 9f, 1f / 9f, 1f / 9f},
            {1f / 16f, 2f / 16f, 1f / 16f, 2f / 16f, 4f / 16f, 2f / 16f, 1f / 16f, 2f / 16f, 1f / 16f},
            {1, 1, 1, 1, -8, 1, 1, 1, 1},
            {-2, -1, 0, -1, 1, 1, 0, 1, 2}
    };

    private ShaderProgram mSceneProgram;
    private ShaderProgram mPostProgram;
    private Mesh mCube;
    private Mesh mQuad;
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
        mPostProgram = new ShaderProgram(POST_VS, POST_FS);

        GeoGen.GeoData cube = GeoGen.cube();
        int n = cube.positions.length / 3;
        float[] verts = new float[n * 6];
        for (int i = 0; i < n; i++) {
            float c = (i / 4) % 2 == 0 ? 0.95f : 0.45f;
            verts[i * 6] = cube.positions[i * 3];
            verts[i * 6 + 1] = cube.positions[i * 3 + 1];
            verts[i * 6 + 2] = cube.positions[i * 3 + 2];
            verts[i * 6 + 3] = c;
            verts[i * 6 + 4] = 0.5f + 0.4f * ((i / 4) % 3) / 2f;
            verts[i * 6 + 5] = 0.9f - c * 0.5f;
        }
        mCube = new Mesh.Builder()
                .addBuffer(verts, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .setIndices(cube.indices)
                .build();

        mQuad = new Mesh.Builder()
                .addBuffer(new float[]{
                        -1, -1, 0, 0, 0,
                        1, -1, 0, 1, 0,
                        -1, 1, 0, 0, 1,
                        1, -1, 0, 1, 0,
                        1, 1, 0, 1, 1,
                        -1, 1, 0, 0, 1
                }, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 2))
                .build();

        // FBO（与 D20 相同套路：颜色纹理 + 深度 RBO）
        int[] tex = new int[1];
        GLES30.glGenTextures(1, tex, 0);
        mColorTex = tex[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mColorTex);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
                RT_SIZE, RT_SIZE, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
        com.example.studyopengl.gl.TextureHelper.setParams(
                GLES30.GL_CLAMP_TO_EDGE, GLES30.GL_CLAMP_TO_EDGE,
                GLES30.GL_LINEAR, GLES30.GL_LINEAR);

        int[] rbo = new int[1];
        GLES30.glGenRenderbuffers(1, rbo, 0);
        mDepthRBO = rbo[0];
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, mDepthRBO);
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER,
                GLES30.GL_DEPTH_COMPONENT24, RT_SIZE, RT_SIZE);

        int[] fbo = new int[1];
        GLES30.glGenFramebuffers(1, fbo, 0);
        mFBO = fbo[0];
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mFBO);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, mColorTex, 0);
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER,
                GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, mDepthRBO);
        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
                != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("FBO 不完整");
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        mTime += deltaTime * getFloat(KEY_SPEED);

        // ---- Pass 1：场景 -> FBO ----
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mFBO);
        GLES30.glViewport(0, 0, RT_SIZE, RT_SIZE);
        GLES30.glClearColor(0.07f, 0.09f, 0.13f, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 50f, 1f, 0.1f, 30f);
        Matrix.setLookAtM(mView, 0, 3.2f, 2.2f, 4f, 0, 0.2f, 0, 0, 1, 0);
        float[] pv = new float[16];
        Matrix.multiplyMM(pv, 0, mProj, 0, mView, 0);

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        mSceneProgram.use();
        Matrix.setIdentityM(mModel, 0);
        Matrix.rotateM(mModel, 0, mTime * 40f, 0.4f, 1, 0.2f);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
        mSceneProgram.setMat4("u_mvp", mMvp);
        mCube.draw(GLES30.GL_TRIANGLES);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);

        // ---- Pass 2：卷积后处理 -> 屏幕 ----
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, mWidth, mHeight);
        GLES30.glClearColor(0, 0, 0, 1);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mColorTex);
        mPostProgram.use();
        mPostProgram.set("u_scene", 0);
        // 一个"纹素"对应的 uv 尺寸 × 步长滑条
        mPostProgram.set("u_offset", getFloat(KEY_STEP) / (float) RT_SIZE);
        mPostProgram.setFloatArray("u_kernel", KERNELS[getOptionIndex(KEY_KERNEL)]);
        mPostProgram.set("u_mix", getFloat(KEY_MIX));
        mQuad.draw(GLES30.GL_TRIANGLES);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.optionSpec(KEY_KERNEL, "卷积核", KERNEL_LABELS, 1));
        specs.add(ParamSpec.floatSpec(KEY_STEP, "采样步长(纹素倍数)", 0.5f, 6f, 1f));
        specs.add(ParamSpec.floatSpec(KEY_MIX, "效果混合强度", 0f, 1f, 1f));
        specs.add(ParamSpec.floatSpec(KEY_SPEED, "旋转速度", 0f, 2f, 0.5f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        GLES30.glDeleteFramebuffers(1, new int[]{mFBO}, 0);
        GLES30.glDeleteTextures(1, new int[]{mColorTex}, 0);
        GLES30.glDeleteRenderbuffers(1, new int[]{mDepthRBO}, 0);
        mCube.dispose();
        mQuad.dispose();
        mSceneProgram.release();
        mPostProgram.release();
    }
}
