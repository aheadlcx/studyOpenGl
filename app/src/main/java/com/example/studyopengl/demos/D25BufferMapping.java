package com.example.studyopengl.demos;

import android.opengl.GLES30;
import android.opengl.Matrix;

import com.example.studyopengl.engine.BaseDemoEngine;
import com.example.studyopengl.gl.ShaderProgram;
import com.example.studyopengl.param.ParamSpec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 25 · 缓冲映射：glMapBufferRange 流式更新顶点数据（CPU 写 GPU 读）。
 */
public class D25BufferMapping extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍动态数据的三种更新方式\n"
            + "1) glBufferData 新数据：整块重新分配（配合 data=null 先\"孤儿化\"旧块，"
            + "驱动把旧块留给还在读的 GPU，新写入不阻塞）；\n"
            + "2) glBufferSubData：不重新分配，但可能被正在使用的缓冲卡住（隐式同步）；\n"
            + "3) glMapBufferRange：把显存映射成 CPU 指针直接写，配标志位控制同步行为 —— "
            + "本例主线。\n\n"
            + "▍映射标志位（ES3 核心）\n"
            + "· GL_MAP_WRITE_BIT / READ_BIT：读写意图；\n"
            + "· GL_MAP_INVALIDATE_RANGE_BIT / BUFFER_BIT：声明旧数据不要了 → 允许驱动免同步"
            + "（等效孤儿化，但保留同一 buffer 名字）；\n"
            + "· GL_MAP_FLUSH_EXPLICIT_BIT + glFlushMappedBufferRange：只回传修改过的子区间；\n"
            + "· GL_MAP_UNSYNCHRONIZED_BIT：完全不等 GPU（风险自担）。\n\n"
            + "▍本例：CPU 每帧重算 64×64 波浪场\n"
            + "updateMode=glMapBufferRange 用 INVALIDATE_BUFFER 映射整块写；"
            + "glBufferSubData 模式写同一份 CPU 数组 —— 切换模式看 FPS 差异（数据量越大越明显）。\n\n"
            + "▍工程守则\n"
            + "· 映射期间不能对该 buffer 发起 GL 命令，用完立即 unmap；\n"
            + "· 每帧全量更新用\"invalidate + map\"或\"orphan + subData\"二选一，避免驱动隐式同步。";

    private static final String VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "uniform mat4 u_mvp;\n"
            + "uniform float u_minH;\n"
            + "uniform float u_maxH;\n"
            + "out float v_height;\n"
            + "void main() {\n"
            + "    v_height = (a_pos.y - u_minH) / (u_maxH - u_minH);\n"
            + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in float v_height;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    // 高度映射颜色：低=深蓝，高=亮青\n"
            + "    vec3 low = vec3(0.1, 0.2, 0.55);\n"
            + "    vec3 high = vec3(0.3, 0.95, 0.95);\n"
            + "    fragColor = vec4(mix(low, high, clamp(v_height, 0.0, 1.0)), 1.0);\n"
            + "}\n";

    private static final String KEY_SEG = "seg";
    private static final String KEY_FREQ = "freq";
    private static final String KEY_WAVESPEED = "waveSpeed";
    private static final String KEY_UPDATE = "update";

    private static final String[] SEG_LABELS = {"32×32", "64×64", "96×96", "128×128"};
    private static final String[] UPDATE_LABELS = {"glMapBufferRange", "glBufferSubData"};

    private ShaderProgram mProgram;
    private int mVAO;
    private int mPosVBO;
    private int mIBO;
    private int mIndexCount;
    private int mSegments = 64;
    private FloatBuffer mScratch;      // CPU 侧数组（subData 模式用）
    private FloatBuffer mMappedView;   // 映射后的视图
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mMvp = new float[16];
    private float mTime;
    private float mMinH = -0.8f, mMaxH = 0.8f;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProgram = new ShaderProgram(VS, FS);
        buildGrid(getOptionIndex(KEY_SEG));
    }

    /** 建 VAO/VBO/EBO，位置缓冲留作每帧重写。 */
    private void buildGrid(int segOpt) {
        releaseGrid();
        mSegments = new int[]{32, 64, 96, 128}[segOpt];
        int seg = mSegments;
        int vertsPerRow = seg + 1;
        int vertCount = vertsPerRow * vertsPerRow;

        int[] vao = new int[1];
        GLES30.glGenVertexArrays(1, vao, 0);
        mVAO = vao[0];
        int[] vbo = new int[1];
        GLES30.glGenBuffers(1, vbo, 0);
        mPosVBO = vbo[0];
        int[] ibo = new int[1];
        GLES30.glGenBuffers(1, ibo, 0);
        mIBO = ibo[0];

        GLES30.glBindVertexArray(mVAO);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mPosVBO);
        // GL_DYNAMIC_DRAW：每帧更新提示驱动放“对 CPU 写友好”的堆
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,
                vertCount * 3 * 4, null, GLES30.GL_DYNAMIC_DRAW);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0);
        GLES30.glEnableVertexAttribArray(0);

        // XZ 静态 + Y 动态：先填平的初始位置
        FloatBuffer init = ByteBuffer.allocateDirect(vertCount * 3 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (int i = 0; i <= seg; i++) {
            for (int j = 0; j <= seg; j++) {
                init.put(-10f + 20f * i / seg);
                init.put(0f);
                init.put(-10f + 20f * j / seg);
            }
        }
        init.position(0);
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, vertCount * 3 * 4, init);

        mIndexCount = seg * seg * 6;
        IntBufferHolder holder = new IntBufferHolder(seg, vertsPerRow);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, mIBO);
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER,
                mIndexCount * 4, holder.buffer(), GLES30.GL_STATIC_DRAW);

        GLES30.glBindVertexArray(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0);

        mScratch = ByteBuffer.allocateDirect(vertCount * 3 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    /** 小工具：组装网格索引。 */
    private static final class IntBufferHolder {
        final java.nio.IntBuffer buf;

        IntBufferHolder(int seg, int vertsPerRow) {
            buf = ByteBuffer.allocateDirect(seg * seg * 6 * 4)
                    .order(ByteOrder.nativeOrder()).asIntBuffer();
            for (int i = 0; i < seg; i++) {
                for (int j = 0; j < seg; j++) {
                    int a = i * vertsPerRow + j;
                    int b = a + vertsPerRow;
                    buf.put(a).put(b).put(a + 1);
                    buf.put(a + 1).put(b).put(b + 1);
                }
            }
            buf.position(0);
        }

        java.nio.IntBuffer buffer() {
            return buf;
        }
    }

    private void releaseGrid() {
        if (mVAO != 0) {
            GLES30.glDeleteVertexArrays(1, new int[]{mVAO}, 0);
            mVAO = 0;
        }
        if (mPosVBO != 0) {
            GLES30.glDeleteBuffers(1, new int[]{mPosVBO}, 0);
            mPosVBO = 0;
        }
        if (mIBO != 0) {
            GLES30.glDeleteBuffers(1, new int[]{mIBO}, 0);
            mIBO = 0;
        }
    }

    @Override
    public void onParamChanged(ParamSpec spec) {
        if (spec.key.equals(KEY_SEG)) {
            buildGrid(getOptionIndex(KEY_SEG));
        }
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        clearFrame(0.05f, 0.07f, 0.11f);
        mTime += deltaTime * getFloat(KEY_WAVESPEED);
        float freq = getFloat(KEY_FREQ);

        int seg = mSegments;
        int vertsPerRow = seg + 1;
        int vertCount = vertsPerRow * vertsPerRow;
        boolean useMap = getOptionIndex(KEY_UPDATE) == 0;

        // ---- CPU 端计算波浪并写回显存（本 Demo 的主角）----
        FloatBuffer target;
        if (useMap) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mPosVBO); // 映射前必须先绑定
            // invalidate 整块 => 驱动可跳过与 GPU 的同步等待
            ByteBuffer mapped = (ByteBuffer) GLES30.glMapBufferRange(GLES30.GL_ARRAY_BUFFER,
                    0, vertCount * 3 * 4,
                    GLES30.GL_MAP_WRITE_BIT | GLES30.GL_MAP_INVALIDATE_BUFFER_BIT);
            if (mapped != null) {
                mMappedView = mapped.order(ByteOrder.nativeOrder()).asFloatBuffer();
            }
            target = mMappedView;
        } else {
            target = mScratch;
            target.position(0);
        }

        if (target != null) {
            target.position(0);
            for (int i = 0; i < vertsPerRow; i++) {
                float x = -10f + 20f * i / seg;
                for (int j = 0; j < vertsPerRow; j++) {
                    float z = -10f + 20f * j / seg;
                    float h = (float) Math.sin(x * freq * 0.4 + mTime * 2.2)
                            * (float) Math.cos(z * freq * 0.35 + mTime * 1.7)
                            * 0.7f;
                    // 只改 Y：XZ 保持原值（target 已按 x,y,z 排列预填）
                    // 这里全量重写三份，简单直观
                    float prevX = (useMap && mMappedView == target)
                            ? -10f + 20f * i / seg : x; // 同值
                    target.put(prevX);
                    target.put(h);
                    target.put(z);
                }
            }
            target.position(0);
        }

        if (useMap) {
            GLES30.glUnmapBuffer(GLES30.GL_ARRAY_BUFFER); // 修改回传驱动
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        } else {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mPosVBO);
            GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0,
                    vertCount * 3 * 4, mScratch);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        }

        // ---- 绘制 ----
        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 50f, aspect(), 0.1f, 60f);
        Matrix.setLookAtM(mView, 0, 13f, 9f, 13f, 0, 0, 0, 0, 1, 0);
        Matrix.multiplyMM(mMvp, 0, mProj, 0, mView, 0);

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glEnable(GLES30.GL_CULL_FACE);
        GLES30.glCullFace(GLES30.GL_BACK);
        mProgram.use();
        mProgram.setMat4("u_mvp", mMvp);
        mProgram.set("u_minH", mMinH);
        mProgram.set("u_maxH", mMaxH);
        GLES30.glBindVertexArray(mVAO);
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, mIndexCount,
                GLES30.GL_UNSIGNED_INT, 0);
        GLES30.glBindVertexArray(0);
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.optionSpec(KEY_SEG, "网格密度", SEG_LABELS, 1));
        specs.add(ParamSpec.floatSpec(KEY_FREQ, "波频率", 0.5f, 6f, 2f));
        specs.add(ParamSpec.floatSpec(KEY_WAVESPEED, "波速", 0f, 3f, 1f));
        specs.add(ParamSpec.optionSpec(KEY_UPDATE, "更新方式(看FPS)", UPDATE_LABELS, 0));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        releaseGrid();
        mProgram.release();
    }
}
