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
 * 24 · 多渲染目标 MRT：glDrawBuffers + layout(location=N) out。
 */
public class D24Mrt extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍一次 draw 写多张纹理\n"
            + "MRT（Multiple Render Targets）让片元着色器同时向 FBO 的多个颜色附件输出：\n"
            + "· GLSL 侧：layout(location=0) out vec4 o_albedo; layout(location=1) out vec4 o_normal;\n"
            + "· GL 侧：glDrawBuffers(2, {GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1})，"
            + "location i 写进第 i 个绑定的附件；\n"
            + "· 数量上限查询 GL_MAX_DRAW_BUFFERS（ES3 下限 4）。\n\n"
            + "▍用途：延迟渲染 G-Buffer\n"
            + "Pass1（本例）：把 albedo / 世界法线 / 位置分别写进多张纹理；"
            + "Pass2 光照计算只对\"屏幕上可见的像素\"执行 —— 复杂光源场景下省掉海量被遮挡的着色开销。"
            + "PBR 材质输出、 pick-by-color 点选拾取也是 MRT。\n\n"
            + "▍完整性注意\n"
            + "FBO 的所有 draw buffer 都必须有有效附件（或显式设 GL_NONE），"
            + "否则状态不完整；各附件尺寸/格式要求一致（分层纹理除外）。\n\n"
            + "▍本例\n"
            + "Pass1 写两张图：attachment0=反照率棋盘格，attachment1=法线伪彩(n*0.5+0.5)；"
            + "Pass2 下拉框切换显示哪张，或左右分屏对比。";

    private static final String GBUFFER_VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec3 a_normal;\n"
            + "uniform mat4 u_mvp;\n"
            + "uniform mat4 u_model;\n"
            + "out vec3 v_normal;\n"
            + "out vec3 v_worldPos;\n"
            + "void main() {\n"
            + "    v_normal = mat3(u_model) * a_normal;\n"
            + "    v_worldPos = (u_model * vec4(a_pos, 1.0)).xyz;\n"
            + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String GBUFFER_FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec3 v_normal;\n"
            + "in vec3 v_worldPos;\n"
            + "layout(location=0) out vec4 o_albedo;\n"   // 反照率（棋盘格）
            + "layout(location=1) out vec4 o_normal;\n"   // 法线伪彩
            + "void main() {\n"
            + "    float checker = mod(floor(v_worldPos.x * 2.0) + floor(v_worldPos.y * 2.0)\n"
            + "                          + floor(v_worldPos.z * 2.0), 2.0);\n"
            + "    vec3 albedo = mix(vec3(0.85, 0.4, 0.25), vec3(0.95, 0.9, 0.8), checker);\n"
            + "    o_albedo = vec4(albedo, 1.0);\n"
            + "    o_normal = vec4(normalize(v_normal) * 0.5 + 0.5, 1.0);\n"
            + "}\n";

    private static final String SHOW_VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec2 a_uv;\n"
            + "out vec2 v_uv;\n"
            + "void main() {\n"
            + "    v_uv = a_uv;\n"
            + "    gl_Position = vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String SHOW_FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec2 v_uv;\n"
            + "uniform sampler2D u_tex0;\n"
            + "uniform sampler2D u_tex1;\n"
            + "uniform int u_mode;\n"   // 0=albedo 1=normal 2=分屏
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    vec3 a = texture(u_tex0, v_uv).rgb;\n"
            + "    vec3 n = texture(u_tex1, v_uv).rgb;\n"
            + "    if (u_mode == 0) fragColor = vec4(a, 1.0);\n"
            + "    else if (u_mode == 1) fragColor = vec4(n, 1.0);\n"
            + "    else fragColor = vec4(v_uv.x < 0.5 ? a : n, 1.0);\n"
            + "}\n";

    private static final String KEY_MODE = "mode";
    private static final String KEY_SPEED = "speed";

    private static final String[] MODE_LABELS = {"附件0 反照率", "附件1 法线伪彩", "左右分屏"};

    private ShaderProgram mGBufferProgram;
    private ShaderProgram mShowProgram;
    private Mesh mCube;
    private Mesh mQuad;
    private int mFBO;
    private final int[] mColorTex = new int[2];
    private int mDepthRBO;
    private static final int RT_SIZE = 1024;
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mMvp = new float[16];
    private float mTime;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mGBufferProgram = new ShaderProgram(GBUFFER_VS, GBUFFER_FS);
        mShowProgram = new ShaderProgram(SHOW_VS, SHOW_FS);

        GeoGen.GeoData cube = GeoGen.cube();
        float[] verts = new float[(cube.positions.length / 3) * 6];
        for (int i = 0; i < cube.positions.length / 3; i++) {
            verts[i * 6] = cube.positions[i * 3];
            verts[i * 6 + 1] = cube.positions[i * 3 + 1];
            verts[i * 6 + 2] = cube.positions[i * 3 + 2];
            verts[i * 6 + 3] = cube.normals[i * 3];
            verts[i * 6 + 4] = cube.normals[i * 3 + 1];
            verts[i * 6 + 5] = cube.normals[i * 3 + 2];
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

        // FBO：两个颜色附件 + 深度
        GLES30.glGenTextures(2, mColorTex, 0);
        for (int i = 0; i < 2; i++) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mColorTex[i]);
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
                    RT_SIZE, RT_SIZE, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
            com.example.studyopengl.gl.TextureHelper.setParams(
                    GLES30.GL_CLAMP_TO_EDGE, GLES30.GL_CLAMP_TO_EDGE,
                    GLES30.GL_LINEAR, GLES30.GL_LINEAR);
        }
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
        for (int i = 0; i < 2; i++) {
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,
                    GLES30.GL_COLOR_ATTACHMENT0 + i, GLES30.GL_TEXTURE_2D, mColorTex[i], 0);
        }
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER,
                GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, mDepthRBO);

        // 关键：启用两个 draw buffer，与 FS 的 layout(location=0/1) 对应
        GLES30.glDrawBuffers(2, new int[]{
                GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_COLOR_ATTACHMENT1}, 0);

        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
                != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("MRT FBO 不完整");
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glDrawBuffers(1, new int[]{GLES30.GL_COLOR_ATTACHMENT0}, 0); // 恢复默认
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        mTime += deltaTime * getFloat(KEY_SPEED);

        // ---- Pass 1：G-Buffer（MRT 两份输出）----
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mFBO);
        GLES30.glDrawBuffers(2, new int[]{
                GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_COLOR_ATTACHMENT1}, 0);
        GLES30.glViewport(0, 0, RT_SIZE, RT_SIZE);
        GLES30.glClearColor(0, 0, 0, 1);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 50f, 1f, 0.1f, 30f);
        Matrix.setLookAtM(mView, 0, 2.8f, 2f, 3.6f, 0, 0.2f, 0, 0, 1, 0);
        float[] pv = new float[16];
        Matrix.multiplyMM(pv, 0, mProj, 0, mView, 0);

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        mGBufferProgram.use();
        Matrix.setIdentityM(mModel, 0);
        Matrix.rotateM(mModel, 0, mTime * 40f, 0.4f, 1, 0.2f);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
        mGBufferProgram.setMat4("u_mvp", mMvp);
        mGBufferProgram.setMat4("u_model", mModel);
        mCube.draw(GLES30.GL_TRIANGLES);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);

        // ---- Pass 2：显示某个附件 ----
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glDrawBuffers(1, new int[]{GLES30.GL_COLOR_ATTACHMENT0}, 0);
        GLES30.glViewport(0, 0, mWidth, mHeight);
        GLES30.glClearColor(0, 0, 0, 1);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mColorTex[0]);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mColorTex[1]);

        mShowProgram.use();
        mShowProgram.set("u_tex0", 0);
        mShowProgram.set("u_tex1", 1);
        mShowProgram.set("u_mode", getOptionIndex(KEY_MODE));
        mQuad.draw(GLES30.GL_TRIANGLES);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.optionSpec(KEY_MODE, "显示哪个附件", MODE_LABELS, 2));
        specs.add(ParamSpec.floatSpec(KEY_SPEED, "旋转速度", 0f, 2f, 0.5f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        GLES30.glDeleteFramebuffers(1, new int[]{mFBO}, 0);
        GLES30.glDeleteTextures(2, mColorTex, 0);
        GLES30.glDeleteRenderbuffers(1, new int[]{mDepthRBO}, 0);
        mCube.dispose();
        mQuad.dispose();
        mGBufferProgram.release();
        mShowProgram.release();
    }
}
