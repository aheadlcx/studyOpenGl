package com.example.studyopengl.gl;

import android.opengl.GLES30;
import android.util.Log;

import java.util.HashMap;

/**
 * 着色器程序封装：编译 -> 链接 -> 校验，并缓存 uniform location。
 *
 * 管线要点：
 *  - 顶点着色器每个顶点跑一次，片元着色器每个"片元"(潜在像素)跑一次；
 *  - GLSL ES 3.00 必须以 #version 300 es 开头（必须是第一行，前面不能有空行）；
 *  - in/out 替代 ES2 的 attribute/varying；片元着色器需要自己声明 out 变量；
 *  - 片元着色器必须声明浮点精度（precision mediump float;），顶点着色器默认 highp；
 *  - layout(location=N) 显式指定顶点属性槽位，配合 VAO 里的 glVertexAttribPointer。
 */
public class ShaderProgram {

    private static final String TAG = "ShaderProgram";

    private final int mProgramId;
    private final HashMap<String, Integer> mUniformCache = new HashMap<String, Integer>();
    private final HashMap<String, Integer> mAttribCache = new HashMap<String, Integer>();

    public ShaderProgram(String vertexSource, String fragmentSource) {
        int vs = compile(GLES30.GL_VERTEX_SHADER, vertexSource);
        int fs = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource);

        mProgramId = GLES30.glCreateProgram();
        if (mProgramId == 0) {
            throw new RuntimeException("glCreateProgram 失败");
        }
        GLES30.glAttachShader(mProgramId, vs);
        GLES30.glAttachShader(mProgramId, fs);
        GLES30.glLinkProgram(mProgramId);

        int[] status = new int[1];
        GLES30.glGetProgramiv(mProgramId, GLES30.GL_LINK_STATUS, status, 0);
        GLES30.glDeleteShader(vs); // 链接后 shader 可标记删除
        GLES30.glDeleteShader(fs);
        if (status[0] == 0) {
            String log = GLES30.glGetProgramInfoLog(mProgramId);
            GLES30.glDeleteProgram(mProgramId);
            throw new RuntimeException("程序链接失败:\n" + log);
        }
    }

    public void use() {
        GLES30.glUseProgram(mProgramId);
    }

    public int getProgramId() {
        return mProgramId;
    }

    /** uniform location（带缓存）。返回 -1 表示该名字被优化掉了，属正常现象。 */
    public int loc(String name) {
        Integer cached = mUniformCache.get(name);
        if (cached != null) return cached;
        int location = GLES30.glGetUniformLocation(mProgramId, name);
        mUniformCache.put(name, location);
        return location;
    }

    public int attrib(String name) {
        Integer cached = mAttribCache.get(name);
        if (cached != null) return cached;
        int location = GLES30.glGetAttribLocation(mProgramId, name);
        mAttribCache.put(name, location);
        return location;
    }

    // ---- uniform 便捷方法 ----
    public void set(String name, float v) {
        GLES30.glUniform1f(loc(name), v);
    }

    public void set(String name, float a, float b) {
        GLES30.glUniform2f(loc(name), a, b);
    }

    public void set(String name, float a, float b, float c) {
        GLES30.glUniform3f(loc(name), a, b, c);
    }

    public void set(String name, float a, float b, float c, float d) {
        GLES30.glUniform4f(loc(name), a, b, c, d);
    }

    public void set(String name, int v) {
        GLES30.glUniform1i(loc(name), v);
    }

    /** 4x4 矩阵，按列主序（与 android.opengl.Matrix 一致）。 */
    public void setMat4(String name, float[] m) {
        GLES30.glUniformMatrix4fv(loc(name), 1, false, m, 0);
    }

    public void setFloatArray(String name, float[] values) {
        GLES30.glUniform1fv(loc(name), values.length, values, 0);
    }

    public void release() {
        GLES30.glDeleteProgram(mProgramId);
    }

    /** 编译单个 shader，失败时抛出带信息日志的异常。 */
    public static int compile(int type, String source) {
        int shader = GLES30.glCreateShader(type);
        GLES30.glShaderSource(shader, source);
        GLES30.glCompileShader(shader);
        int[] status = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES30.glGetShaderInfoLog(shader);
            String kind = type == GLES30.GL_VERTEX_SHADER ? "vertex" : "fragment";
            GLES30.glDeleteShader(shader);
            Log.e(TAG, kind + " shader 源码:\n" + source);
            throw new RuntimeException(kind + " shader 编译失败:\n" + log);
        }
        return shader;
    }
}
