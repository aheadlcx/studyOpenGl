package com.example.studyopengl.gl;

import android.opengl.GLES30;

import java.util.ArrayList;

/**
 * 顶点数组对象(VAO) + 顶点缓冲(VBO) + 索引缓冲(EBO) 的封装。
 *
 * VAO 是 ES3 的核心概念：它"录制"了 glVertexAttribPointer/glEnableVertexAttribArray
 * 以及 ELEMENT_ARRAY_BUFFER 绑定，一次配置，之后每帧只需 glBindVertexArray 一次，
 * 避免重复设置属性指针（ES2 时代每帧都要重设）。
 *
 * Builder 支持两种布局：
 *  - 单个 interleaved 缓冲（多个属性交错存放，缓存局部性好）
 *  - 多个独立缓冲（比如顶点流 + 实例流），addInstancedBuffer 配合 glVertexAttribDivisor
 *    实现实例化渲染（每 N 个实例步进一次）。
 */
public class Mesh {

    private int mVaoId;
    private final ArrayList<Integer> mBufferIds = new ArrayList<Integer>();
    private int mIndexCount;
    private int mVertexCount;
    private boolean mIndexed;

    /** 属性描述：location 对应 GLSL 里 layout(location=N) in 变量。 */
    public static final class Attrib {
        public final int location;
        public final int size; // 分量数 1..4

        public Attrib(int location, int size) {
            this.location = location;
            this.size = size;
        }
    }

    private static final class BufferSpec {
        float[] data;
        Attrib[] attribs;
        int divisor; // 0=每顶点, 1=每实例
    }

    public static final class Builder {
        private final ArrayList<BufferSpec> mBuffers = new ArrayList<BufferSpec>();
        private int[] mIndices;

        /** 每顶点缓冲。data 中的多个属性按声明顺序交错（stride 自动计算）。 */
        public Builder addBuffer(float[] data, Attrib... attribs) {
            BufferSpec spec = new BufferSpec();
            spec.data = data;
            spec.attribs = attribs;
            spec.divisor = 0;
            mBuffers.add(spec);
            return this;
        }

        /** 实例缓冲：divisor=1 表示整个实例步进一次该属性的值。 */
        public Builder addInstancedBuffer(float[] data, int divisor, Attrib... attribs) {
            BufferSpec spec = new BufferSpec();
            spec.data = data;
            spec.attribs = attribs;
            spec.divisor = divisor;
            mBuffers.add(spec);
            return this;
        }

        public Builder setIndices(int[] indices) {
            mIndices = indices;
            return this;
        }

        public Mesh build() {
            Mesh mesh = new Mesh();
            int[] vao = new int[1];
            GLES30.glGenVertexArrays(1, vao, 0);
            mesh.mVaoId = vao[0];
            GLES30.glBindVertexArray(mesh.mVaoId);

            for (BufferSpec spec : mBuffers) {
                int floatsPerVertex = 0;
                for (Attrib a : spec.attribs) floatsPerVertex += a.size;

                int[] vbo = new int[1];
                GLES30.glGenBuffers(1, vbo, 0);
                mesh.mBufferIds.add(vbo[0]);
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0]);
                java.nio.FloatBuffer fb = java.nio.FloatBuffer.wrap(spec.data);
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,
                        spec.data.length * 4, fb, GLES30.GL_STATIC_DRAW);

                int strideBytes = floatsPerVertex * 4;
                int offsetBytes = 0;
                for (Attrib a : spec.attribs) {
                    GLES30.glVertexAttribPointer(a.location, a.size,
                            GLES30.GL_FLOAT, false, strideBytes, offsetBytes);
                    GLES30.glEnableVertexAttribArray(a.location);
                    if (spec.divisor > 0) {
                        // 实例化关键 API：该属性每 divisor 个实例才更新一次
                        GLES30.glVertexAttribDivisor(a.location, spec.divisor);
                    }
                    offsetBytes += a.size * 4;
                }
                if (spec.divisor == 0 && mesh.mVertexCount == 0) {
                    mesh.mVertexCount = spec.data.length / floatsPerVertex;
                }
            }

            if (mIndices != null) {
                int[] ibo = new int[1];
                GLES30.glGenBuffers(1, ibo, 0);
                mesh.mBufferIds.add(ibo[0]);
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo[0]);
                java.nio.IntBuffer ib = java.nio.IntBuffer.wrap(mIndices);
                GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER,
                        mIndices.length * 4, ib, GLES30.GL_STATIC_DRAW);
                mesh.mIndexCount = mIndices.length;
                mesh.mIndexed = true;
            }

            GLES30.glBindVertexArray(0);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
            return mesh;
        }
    }

    /** 绑定 VAO 并绘制非索引/索引图元。 */
    public void draw(int mode) {
        GLES30.glBindVertexArray(mVaoId);
        if (mIndexed) {
            // ES3 原生支持 32 位索引（大顶点量必备），类型 GL_UNSIGNED_INT
            GLES30.glDrawElements(mode, mIndexCount, GLES30.GL_UNSIGNED_INT, 0);
        } else {
            GLES30.glDrawArrays(mode, 0, mVertexCount);
        }
        GLES30.glBindVertexArray(0);
    }

    /** 实例化绘制：一次 draw call 画出 instanceCount 份几何体。 */
    public void drawInstanced(int mode, int instanceCount) {
        GLES30.glBindVertexArray(mVaoId);
        if (mIndexed) {
            GLES30.glDrawElementsInstanced(mode, mIndexCount,
                    GLES30.GL_UNSIGNED_INT, 0, instanceCount);
        } else {
            GLES30.glDrawArraysInstanced(mode, 0, mVertexCount, instanceCount);
        }
        GLES30.glBindVertexArray(0);
    }

    public int getVertexCount() {
        return mVertexCount;
    }

    public void dispose() {
        if (mVaoId != 0) {
            GLES30.glDeleteVertexArrays(1, new int[]{mVaoId}, 0);
            mVaoId = 0;
        }
        for (Integer id : mBufferIds) {
            GLES30.glDeleteBuffers(1, new int[]{id}, 0);
        }
        mBufferIds.clear();
    }
}
