package com.example.studyopengl.demos;

import android.opengl.GLES30;
import android.opengl.Matrix;

import com.example.studyopengl.engine.BaseDemoEngine;
import com.example.studyopengl.gl.GeoGen;
import com.example.studyopengl.gl.Mesh;
import com.example.studyopengl.gl.ShaderProgram;
import com.example.studyopengl.gl.TextureHelper;
import com.example.studyopengl.param.ParamSpec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 19 · Transform Feedback：粒子状态全部留在 GPU 上更新（物理在 VS 里算）。
 */
public class D19TransformFeedback extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍Transform Feedback (XFB) 是什么\n"
            + "把顶点着色器的输出（varyings）捕获到 Buffer Object，而不是送去光栅化。"
            + "粒子系统因此可以完全在 GPU 上演化：位置/速度从不回读 CPU，"
            + "没有 glReadBack 的同步停顿 —— ES2 时代要靠 EXT 扩展，ES3 已是核心功能。\n\n"
            + "▍实现四要素\n"
            + "1) glTransformFeedbackVaryings(program, [\"v_pos\",\"v_vel\"], GL_INTERLEAVED_ATTRIBS)"
            + " —— 必须在 glLinkProgram 之前调用！本工程为演示直接用 GLES30 底层 API 构建；\n"
            + "2) XFB 对象：glGenTransformFeedbacks / glBindTransformFeedback(GL_TRANSFORM_FEEDBACK, xfb)，"
            + "glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, 0, vbo) 指定写入目标；\n"
            + "3) glEnable(GL_RASTERIZER_DISCARD)：更新 pass 丢弃光栅化，只跑 VS；\n"
            + "4) glBeginTransformFeedback(GL_POINTS) → glDrawArrays → glEndTransformFeedback。\n\n"
            + "▍乒乓双缓冲（必须）\n"
            + "同一帧里\"读 A 写 A\"在 GL 里是未定义行为，所以要两个 VBO："
            + "偶数帧读 A 写 B，奇数帧读 B 写 A，渲染始终画最新的一份。\n\n"
            + "▍本例\n"
            + "2000 个粒子在盒子里弹跳（VS 内做积分与边界反弹），渲染 pass 用 "
            + "gl_PointCoord 采样软圆纹理画成光点；粒子数/重力/速度实时可调。";

    // 更新阶段 VS：只算物理，不输出 gl_Position（光栅化被丢弃）
    private static final String UPDATE_VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec4 a_posLife;\n"   // xyz=位置 w=寿命
            + "layout(location=1) in vec4 a_velSeed;\n"   // xyz=速度 w=随机种子
            + "uniform float u_dt;\n"
            + "uniform float u_gravity;\n"
            + "uniform float u_speed;\n"
            + "out vec4 v_posLife;\n"
            + "out vec4 v_velSeed;\n"
            + "void main() {\n"
            + "    vec3 pos = a_posLife.xyz;\n"
            + "    vec3 vel = a_velSeed.xyz * u_speed;\n"
            + "    float life = a_posLife.w;\n"
            + "    vel.y -= u_gravity * u_dt * 4.0;\n"
            + "    pos += vel * u_dt;\n"
            + "    // 盒子边界反弹\n"
            + "    vec3 lo = vec3(-2.4, -1.6, -1.0);\n"
            + "    vec3 hi = vec3( 2.4,  1.6,  1.0);\n"
            + "    for (int i = 0; i < 3; i++) {\n"
            + "        if (pos[i] < lo[i]) { pos[i] = lo[i]; vel[i] = abs(vel[i]); }\n"
            + "        if (pos[i] > hi[i]) { pos[i] = hi[i]; vel[i] = -abs(vel[i]); }\n"
            + "    }\n"
            + "    life -= u_dt;\n"
            + "    if (life <= 0.0) {\n"
            + "        // 重置粒子到顶部随机位置（用种子伪随机）\n"
            + "        float s = a_velSeed.w;\n"
            + "        pos = vec3(sin(s * 12.9898) * 2.2, 1.4, cos(s * 78.233) * 0.8);\n"
            + "        vel = vec3(sin(s * 43.1), -0.4, cos(s * 91.7)) * 0.4;\n"
            + "        life = 2.5 + s * 2.0;\n"
            + "    }\n"
            + "    v_posLife = vec4(pos, life);\n"
            + "    v_velSeed = vec4(vel / u_speed, a_velSeed.w);\n"
            + "}\n";

    private static final String UPDATE_FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "out vec4 fragColor;\n"
            + "void main() { fragColor = vec4(1.0); }\n" // 不会执行（光栅化被丢弃）
            ;

    // 渲染阶段：点精灵
    private static final String RENDER_VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec4 a_posLife;\n"
            + "uniform mat4 u_mvp;\n"
            + "uniform float u_pointSize;\n"
            + "out float v_life;\n"
            + "void main() {\n"
            + "    v_life = a_posLife.w;\n"
            + "    gl_Position = u_mvp * vec4(a_posLife.xyz, 1.0);\n"
            + "    gl_PointSize = u_pointSize;\n"
            + "}\n";

    private static final String RENDER_FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in float v_life;\n"
            + "uniform sampler2D u_sprite;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    // gl_PointCoord：点精灵内部坐标 0..1（左上原点）\n"
            + "    vec4 tex = texture(u_sprite, gl_PointCoord);\n"
            + "    vec3 cool = vec3(0.35, 0.75, 1.0);\n"
            + "    vec3 warm = vec3(1.0, 0.75, 0.35);\n"
            + "    vec3 color = mix(cool, warm, clamp(v_life * 0.4, 0.0, 1.0));\n"
            + "    fragColor = vec4(color, tex.a * clamp(v_life, 0.0, 1.0));\n"
            + "}\n";

    private static final String KEY_COUNT = "count";
    private static final String KEY_GRAVITY = "gravity";
    private static final String KEY_SPEED = "speed";
    private static final String KEY_SIZE = "size";

    private int mUpdateProgram;   // 直接用 GLES30 API（要在 link 前注册 varyings）
    private ShaderProgram mRenderProgram;
    private final int[] mParticleVBOs = new int[2]; // 乒乓双缓冲
    private final int[] mParticleVAOs = new int[2];
    private boolean mParticlesCreated;
    private int mXFB;
    private int mParticleTexture;
    private int mCount;
    private int mCurSrc; // 0 / 1：当前作为输入的 VBO
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mMvp = new float[16];

    @Override
    public void onSurfaceCreated(int width, int height) {
        mCount = getInt(KEY_COUNT);
        mParticleTexture = TextureHelper.createParticleTexture(64);
        buildParticleProgram();
        rebuildParticles(mCount);
    }

    /** XFB varyings 必须在链接前注册 —— 用底层 API 手动编译链接。 */
    private void buildParticleProgram() {
        int vs = ShaderProgram.compile(GLES30.GL_VERTEX_SHADER, UPDATE_VS);
        int fs = ShaderProgram.compile(GLES30.GL_FRAGMENT_SHADER, UPDATE_FS);
        mUpdateProgram = GLES30.glCreateProgram();
        GLES30.glAttachShader(mUpdateProgram, vs);
        GLES30.glAttachShader(mUpdateProgram, fs);
        // 关键：捕获哪些 varying、怎么排布（交错 / 分离）
        GLES30.glTransformFeedbackVaryings(mUpdateProgram,
                new String[]{"v_posLife", "v_velSeed"}, GLES30.GL_INTERLEAVED_ATTRIBS);
        GLES30.glLinkProgram(mUpdateProgram);
        int[] status = new int[1];
        GLES30.glGetProgramiv(mUpdateProgram, GLES30.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            throw new RuntimeException("XFB program link 失败: "
                    + GLES30.glGetProgramInfoLog(mUpdateProgram));
        }
        GLES30.glDeleteShader(vs);
        GLES30.glDeleteShader(fs);

        mRenderProgram = new ShaderProgram(RENDER_VS, RENDER_FS);
    }

    /** 两份粒子缓冲（乒乓）+ 两个 VAO + 1 个 XFB 对象。 */
    private void rebuildParticles(int count) {
        mCount = count;
        disposeParticles();

        int floatsPerParticle = 8; // posLife(4) + velSeed(4) 交错
        ByteBuffer init = ByteBuffer.allocateDirect(count * floatsPerParticle * 4)
                .order(ByteOrder.nativeOrder());
        Random rnd = new Random(1234);
        for (int i = 0; i < count; i++) {
            float sx = rnd.nextFloat() * 4.4f - 2.2f;
            float sy = rnd.nextFloat() * 3.0f - 1.5f;
            float sz = rnd.nextFloat() * 1.8f - 0.9f;
            init.putFloat(sx).putFloat(sy).putFloat(sz).putFloat(rnd.nextFloat() * 4f);
            init.putFloat(rnd.nextFloat() - 0.5f)
                    .putFloat(rnd.nextFloat() * 0.4f)
                    .putFloat(rnd.nextFloat() - 0.5f)
                    .putFloat(rnd.nextFloat() * 10f);
        }
        init.position(0);

        int[] vbos = mParticleVBOs;
        int[] vaos = mParticleVAOs;
        GLES30.glGenBuffers(2, vbos, 0);
        GLES30.glGenVertexArrays(2, vaos, 0);
        mParticlesCreated = true;

        for (int i = 0; i < 2; i++) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbos[i]);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,
                    count * floatsPerParticle * 4, init, GLES30.GL_DYNAMIC_COPY);
            GLES30.glBindVertexArray(vaos[i]);
            GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, 32, 0);
            GLES30.glEnableVertexAttribArray(0);
            GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, 32, 16);
            GLES30.glEnableVertexAttribArray(1);
            GLES30.glBindVertexArray(0);
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);

        int[] xfbs = new int[1];
        GLES30.glGenTransformFeedbacks(1, xfbs, 0);
        mXFB = xfbs[0];
        GLES30.glBindTransformFeedback(GLES30.GL_TRANSFORM_FEEDBACK, mXFB);
        GLES30.glBindBufferBase(GLES30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, vbos[1]);
        GLES30.glBindTransformFeedback(GLES30.GL_TRANSFORM_FEEDBACK, 0);

        mCurSrc = 0;
    }

    private void disposeParticles() {
        if (mParticlesCreated) {
            GLES30.glDeleteBuffers(2, mParticleVBOs, 0);
            GLES30.glDeleteVertexArrays(2, mParticleVAOs, 0);
            mParticlesCreated = false;
        }
        if (mXFB != 0) {
            GLES30.glDeleteTransformFeedbacks(1, new int[]{mXFB}, 0);
            mXFB = 0;
        }
    }

    @Override
    public void onParamChanged(ParamSpec spec) {
        if (spec.key.equals(KEY_COUNT)) {
            int n = Math.max(200, getInt(KEY_COUNT));
            if (Math.abs(n - mCount) > 99) {
                rebuildParticles(n); // 结构性重建必须在 GL 线程
            }
        }
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        float[] bg = {0.04f, 0.05f, 0.09f};
        GLES30.glClearColor(bg[0], bg[1], bg[2], 1f);
        GLES30.glViewport(0, 0, mWidth, mHeight);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 50f, aspect(), 0.1f, 30f);
        Matrix.setLookAtM(mView, 0, 0, 0.4f, 6.5f, 0, 0, 0, 0, 1, 0);
        Matrix.multiplyMM(mMvp, 0, mProj, 0, mView, 0);

        int[] vbos = mParticleVBOs;
        int[] vaos = mParticleVAOs;
        int dst = 1 - mCurSrc;

        // ---- Pass 1：物理更新（VS 输出捕获到 VBO[dst]）----
        GLES30.glUseProgram(mUpdateProgram);
        GLES30.glBindVertexArray(vaos[mCurSrc]);
        GLES30.glUniform1f(GLES30.glGetUniformLocation(mUpdateProgram, "u_dt"),
                Math.min(deltaTime, 0.033f));
        GLES30.glUniform1f(GLES30.glGetUniformLocation(mUpdateProgram, "u_gravity"),
                getBool(KEY_GRAVITY) ? 1f : 0f);
        GLES30.glUniform1f(GLES30.glGetUniformLocation(mUpdateProgram, "u_speed"),
                getFloat(KEY_SPEED));
        // XFB 写入目标绑定到 XFB 对象上
        GLES30.glBindTransformFeedback(GLES30.GL_TRANSFORM_FEEDBACK, mXFB);
        GLES30.glBindBufferBase(GLES30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, vbos[dst]);
        GLES30.glEnable(GLES30.GL_RASTERIZER_DISCARD); // 只跑 VS
        GLES30.glBeginTransformFeedback(GLES30.GL_POINTS);
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, mCount);
        GLES30.glEndTransformFeedback();
        GLES30.glDisable(GLES30.GL_RASTERIZER_DISCARD);
        GLES30.glBindBufferBase(GLES30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, 0);
        GLES30.glBindTransformFeedback(GLES30.GL_TRANSFORM_FEEDBACK, 0);
        GLES30.glBindVertexArray(0);

        // ---- Pass 2：渲染刚更新的那份 ----
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE); // 加色发光
        GLES30.glDepthMask(false);
        mRenderProgram.use();
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mParticleTexture);
        mRenderProgram.setMat4("u_mvp", mMvp);
        mRenderProgram.set("u_sprite", 0);
        mRenderProgram.set("u_pointSize", getFloat(KEY_SIZE));
        GLES30.glBindVertexArray(vaos[dst]);
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, mCount);
        GLES30.glBindVertexArray(0);
        GLES30.glDepthMask(true);
        GLES30.glDisable(GLES30.GL_BLEND);

        mCurSrc = dst; // 乒乓交换
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.intSpec(KEY_COUNT, "粒子数", 200, 6000, 2000));
        specs.add(ParamSpec.boolSpec(KEY_GRAVITY, "重力", true));
        specs.add(ParamSpec.floatSpec(KEY_SPEED, "速度倍率", 0.2f, 3f, 1f));
        specs.add(ParamSpec.floatSpec(KEY_SIZE, "点大小", 2f, 24f, 8f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        disposeParticles();
        TextureHelper.deleteTexture(mParticleTexture);
        GLES30.glDeleteProgram(mUpdateProgram);
        mRenderProgram.release();
    }
}
