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
 * 23 · 多重采样 MSAA：multisample renderbuffer + glBlitFramebuffer 解析。
 */
public class D23Msaa extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍走样从哪来\n"
            + "三角形边缘是连续边界落在离散像素网格上，产生锯齿。"
            + "MSAA 的思路：每像素放 N 个采样点，片元着色器只算一次，"
            + "\"覆盖样本数\"决定颜色写入比例 —— 几何边缘一次变柔和，成本远低于 4 倍分辨率。\n\n"
            + "▍ES3 的 MSAA 流程（本例完整实现）\n"
            + "1) 创建 MSAA FBO：颜色/深度都挂 glRenderbufferStorageMultisample(target, samples, ...)；\n"
            + "2) 场景画进 MSAA FBO；\n"
            + "3) glBlitFramebuffer(0,0,w,h, 0,0,w,h, GL_COLOR_BUFFER_BIT, GL_NEAREST) "
            + "把 MSAA FBO \"解析(resolve)\"到普通 FBO（颜色位必须 GL_NEAREST）；\n"
            + "4) 解析后的纹理作为全屏四边形采样输出到屏幕。\n"
            + "注意：ES 3.0 的 FBO 颜色附件不支持多重采样纹理（那是 ES3.1 的 glTexStorage2DMultisample），"
            + "所以中间必须用 renderbuffer + blit。\n\n"
            + "▍采样数上限\n"
            + "glGetIntegerv(GL_MAX_SAMPLES) 查询设备支持上限（常见 4~8），"
            + "超过会创建失败 —— 本例下拉框做了钳制。\n\n"
            + "▍MSAA 治不了什么\n"
            + "它只平滑\"几何边\"：纹理缩小摩尔纹、alpha-test 镂空边、镜面闪烁都不归它管"
            + "（那些靠 mipmap / 各向异性过滤 / TAA）。关掉开关对比拉出的细线就能看到差别。";

    private static final String VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec3 a_color;\n"
            + "uniform mat4 u_mvp;\n"
            + "out vec3 v_color;\n"
            + "void main() {\n"
            + "    v_color = a_color;\n"
            + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec3 v_color;\n"
            + "out vec4 fragColor;\n"
            + "void main() { fragColor = vec4(v_color, 1.0); }\n";

    private static final String DISPLAY_VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec2 a_uv;\n"
            + "out vec2 v_uv;\n"
            + "void main() {\n"
            + "    v_uv = a_uv;\n"
            + "    gl_Position = vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String DISPLAY_FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec2 v_uv;\n"
            + "uniform sampler2D u_resolved;\n"
            + "out vec4 fragColor;\n"
            + "void main() { fragColor = texture(u_resolved, v_uv); }\n";

    private static final String KEY_MSAA = "msaa";
    private static final String KEY_SAMPLES = "samples";
    private static final String KEY_SPEED = "speed";

    private static final String[] SAMPLE_LABELS = {"关闭", "2x", "4x", "8x"};

    private ShaderProgram mProgram;
    private ShaderProgram mDisplayProgram;
    private Mesh mContent;
    private Mesh mQuad;
    private int mMsaaFBO;
    private int mMsaaColorRBO;
    private int mMsaaDepthRBO;
    private int mResolveFBO;
    private int mResolveTex;
    private int mWidth2x, mHeight2x;
    private int mMaxSamples = 4;
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mMvp = new float[16];
    private float mTime;

    @Override
    public void onSurfaceCreated(int width, int height) {
        int[] max = new int[1];
        GLES30.glGetIntegerv(GLES30.GL_MAX_SAMPLES, max, 0);
        mMaxSamples = Math.max(1, max[0]);

        mProgram = new ShaderProgram(VS, FS);
        mDisplayProgram = new ShaderProgram(DISPLAY_VS, DISPLAY_FS);

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

        rebuildTargets();
        rebuildContent();
    }

    /** MSAA 目标尺寸跟随分辨率一半即可演示（省内存）。 */
    private void rebuildTargets() {
        releaseTargets();
        mWidth2x = Math.max(64, mWidth / 2);
        mHeight2x = Math.max(64, mHeight / 2);
        int samples = currentSampleCount();

        // MSAA FBO：颜色 + 深度都是多重采样 renderbuffer
        int[] colorRbo = new int[1];
        GLES30.glGenRenderbuffers(1, colorRbo, 0);
        mMsaaColorRBO = colorRbo[0];
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, mMsaaColorRBO);
        GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples,
                GLES30.GL_RGBA8, mWidth2x, mHeight2x);

        int[] depthRbo = new int[1];
        GLES30.glGenRenderbuffers(1, depthRbo, 0);
        mMsaaDepthRBO = depthRbo[0];
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, mMsaaDepthRBO);
        GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples,
                GLES30.GL_DEPTH_COMPONENT24, mWidth2x, mHeight2x);

        int[] msaaFbo = new int[1];
        GLES30.glGenFramebuffers(1, msaaFbo, 0);
        mMsaaFBO = msaaFbo[0];
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mMsaaFBO);
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_RENDERBUFFER, mMsaaColorRBO);
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER,
                GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, mMsaaDepthRBO);

        // 解析目标：普通 FBO + 纹理
        int[] tex = new int[1];
        GLES30.glGenTextures(1, tex, 0);
        mResolveTex = tex[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mResolveTex);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
                mWidth2x, mHeight2x, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
        com.example.studyopengl.gl.TextureHelper.setParams(
                GLES30.GL_CLAMP_TO_EDGE, GLES30.GL_CLAMP_TO_EDGE,
                GLES30.GL_LINEAR, GLES30.GL_LINEAR);

        int[] resolveFbo = new int[1];
        GLES30.glGenFramebuffers(1, resolveFbo, 0);
        mResolveFBO = resolveFbo[0];
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mResolveFBO);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, mResolveTex, 0);

        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
                != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Resolve FBO 不完整");
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
    }

    private int currentSampleCount() {
        int opt = getOptionIndex(KEY_MSAA);
        if (opt == 0) return 0;
        int want = (int) Math.pow(2, opt); // 2/4/8
        return Math.min(want, mMaxSamples);
    }

    /** 高对比细线场景：锯齿最容易被看见。 */
    private void rebuildContent() {
        if (mContent != null) mContent.dispose();
        mContent = new Mesh.Builder()
                .addBuffer(new float[]{
                        // 背板
                        -3f, -3f, -0.5f, 0.1f, 0.12f, 0.2f,
                        3f, -3f, -0.5f, 0.1f, 0.12f, 0.2f,
                        -3f, 3f, -0.5f, 0.1f, 0.12f, 0.2f,
                        3f, -3f, -0.5f, 0.1f, 0.12f, 0.2f,
                        3f, 3f, -0.5f, 0.1f, 0.12f, 0.2f,
                        -3f, 3f, -0.5f, 0.1f, 0.12f, 0.2f,
                        // 亮白细十字（细=锯齿明显）
                        -2.8f, -0.03f, 0f, 1f, 1f, 1f,
                        2.8f, -0.03f, 0f, 1f, 1f, 1f,
                        -2.8f, 0.03f, 0f, 1f, 1f, 1f,
                        2.8f, -0.03f, 0f, 1f, 1f, 1f,
                        2.8f, 0.03f, 0f, 1f, 1f, 1f,
                        -2.8f, 0.03f, 0f, 1f, 1f, 1f,
                        -0.03f, -2.8f, 0.1f, 1f, 1f, 1f,
                        0.03f, -2.8f, 0.1f, 1f, 1f, 1f,
                        -0.03f, 2.8f, 0.1f, 1f, 1f, 1f,
                        0.03f, -2.8f, 0.1f, 1f, 1f, 1f,
                        0.03f, 2.8f, 0.1f, 1f, 1f, 1f,
                        -0.03f, 2.8f, 0.1f, 1f, 1f, 1f
                }, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .build();
    }

    private void releaseTargets() {
        if (mMsaaFBO != 0) GLES30.glDeleteFramebuffers(1, new int[]{mMsaaFBO}, 0);
        if (mResolveFBO != 0) GLES30.glDeleteFramebuffers(1, new int[]{mResolveFBO}, 0);
        if (mMsaaColorRBO != 0) GLES30.glDeleteRenderbuffers(1, new int[]{mMsaaColorRBO}, 0);
        if (mMsaaDepthRBO != 0) GLES30.glDeleteRenderbuffers(1, new int[]{mMsaaDepthRBO}, 0);
        if (mResolveTex != 0) GLES30.glDeleteTextures(1, new int[]{mResolveTex}, 0);
        mMsaaFBO = 0;
        mResolveFBO = 0;
        mMsaaColorRBO = 0;
        mMsaaDepthRBO = 0;
        mResolveTex = 0;
    }

    @Override
    public void onParamChanged(ParamSpec spec) {
        if (spec.key.equals(KEY_MSAA)) {
            rebuildTargets(); // 采样数变了要重建 renderbuffer
        }
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        mTime += deltaTime * getFloat(KEY_SPEED);

        // ---- Pass 1：画进 MSAA FBO ----
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mMsaaFBO);
        GLES30.glViewport(0, 0, mWidth2x, mHeight2x);
        GLES30.glClearColor(0.05f, 0.06f, 0.1f, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 50f, 1f, 0.1f, 30f);
        Matrix.setLookAtM(mView, 0, 0, 0, 7f, 0, 0, 0, 0, 1, 0);
        float[] pv = new float[16];
        Matrix.multiplyMM(pv, 0, mProj, 0, mView, 0);

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        mProgram.use();
        Matrix.setIdentityM(mModel, 0);
        Matrix.rotateM(mModel, 0, mTime * 30f, 0, 0, 1);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
        mProgram.setMat4("u_mvp", mMvp);
        mContent.draw(GLES30.GL_TRIANGLES);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);

        // ---- Pass 2：blit 解析 MSAA -> 普通纹理（颜色位必须 NEAREST）----
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, mMsaaFBO);
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, mResolveFBO);
        GLES30.glBlitFramebuffer(0, 0, mWidth2x, mHeight2x,
                0, 0, mWidth2x, mHeight2x,
                GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);

        // ---- Pass 3：全屏输出 ----
        GLES30.glViewport(0, 0, mWidth, mHeight);
        GLES30.glClearColor(0, 0, 0, 1);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mResolveTex);
        mDisplayProgram.use();
        mDisplayProgram.set("u_resolved", 0);
        mQuad.draw(GLES30.GL_TRIANGLES);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.optionSpec(KEY_MSAA, "多重采样", SAMPLE_LABELS, 2));
        specs.add(ParamSpec.floatSpec(KEY_SPEED, "旋转速度", 0f, 2f, 0.4f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        releaseTargets();
        if (mContent != null) mContent.dispose();
        mQuad.dispose();
        mProgram.release();
        mDisplayProgram.release();
    }
}
