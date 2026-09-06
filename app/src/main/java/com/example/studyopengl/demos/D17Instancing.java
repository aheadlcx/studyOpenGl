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
 * 17 · 实例化渲染：glDrawElementsInstanced + glVertexAttribDivisor + gl_InstanceID。
 */
public class D17Instancing extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍为什么需要实例化\n"
            + "每次 draw call 有 CPU 侧提交开销（驱动校验、状态切换、命令队列）。"
            + "画 1000 棵树=1000 次 draw 的旧方案在手机上直接爆 CPU。"
            + "实例化把\"同一几何体 × N 份\"压成 1 次 draw："
            + "glDrawArraysInstanced(mode, first, count, instanceCount) / "
            + "glDrawElementsInstanced(...)，ES3 核心支持（ES2 需 EXT_instanced_arrays）。\n\n"
            + "▍逐实例数据怎么进 shader\n"
            + "· glVertexAttribDivisor(loc, n)：该顶点属性每 n 个实例步进一次。"
            + "n=0 普通，n=1 每实例一份（位置/颜色/随机相位都放这里）；\n"
            + "· gl_InstanceID：内建变量，VS 里可直接用（本例用它算色相与波动相位）。\n\n"
            + "▍本例结构\n"
            + "VBO0：单位立方体（每顶点）\n"
            + "VBO1：实例缓冲 vec4(offset.xyz, scale) + vec4(color)，divisor=1\n"
            + "VS：worldPos = rotate(instance_id) * pos * scale + offset，"
            + "一行代码完成所有实例的差异。\n\n"
            + "▍参数实验\n"
            + "网格 1→40（1 → 1600 实例）拖动滑条观察帧率：2000 实例以内基本无压力，"
            + "对比传统 1600 次 draw call 的 CPU 开销。\"用 ID 着色\"开关切换逐实例颜色来源。";

    private static final String VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec3 a_normal;\n"
            + "layout(location=2) in vec4 i_offset;\n"  // divisor=1: xyz 偏移, w 缩放
            + "layout(location=3) in vec3 i_color;\n"    // divisor=1: 逐实例颜色
            + "uniform mat4 u_vp;\n"
            + "uniform float u_time;\n"
            + "uniform float u_wave;\n"
            + "uniform float u_useIdColor;\n"
            + "out vec3 v_normal;\n"
            + "out vec3 v_color;\n"
            + "out vec3 v_worldPos;\n"
            + "void main() {\n"
            + "    float id = float(gl_InstanceID);\n"
            + "    // 每个实例绕自己 Y 轴转，相位由 ID 错开\n"
            + "    float a = u_time + id * 0.7;\n"
            + "    float c = cos(a), s = sin(a);\n"
            + "    vec3 p = a_pos * i_offset.w;\n"
            + "    p = vec3(p.x * c - p.z * s, p.y, p.x * s + p.z * c);\n"
            + "    // 波浪起伏\n"
            + "    p.y += sin(u_time * 2.0 + i_offset.x * 2.0 + i_offset.z * 2.0) * u_wave;\n"
            + "    vec3 world = p + i_offset.xyz;\n"
            + "    v_worldPos = world;\n"
            + "    v_normal = vec3(s, 0.4, c);\n"
            + "    // ID 渐变色 or 实例缓冲颜色\n"
            + "    vec3 idColor = vec3(0.5 + 0.5 * sin(id * 0.37),\n"
            + "                        0.5 + 0.5 * sin(id * 0.37 + 2.1),\n"
            + "                        0.5 + 0.5 * sin(id * 0.37 + 4.2));\n"
            + "    v_color = mix(i_color, idColor, u_useIdColor);\n"
            + "    gl_Position = u_vp * vec4(world, 1.0);\n"
            + "}\n";

    private static final String FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec3 v_normal;\n"
            + "in vec3 v_color;\n"
            + "in vec3 v_worldPos;\n"
            + "uniform vec3 u_lightDir;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    float diff = max(dot(normalize(v_normal), normalize(u_lightDir)), 0.0);\n"
            + "    fragColor = vec4(v_color * (0.35 + 0.65 * diff), 1.0);\n"
            + "}\n";

    private static final String KEY_GRID = "grid";
    private static final String KEY_WAVE = "wave";
    private static final String KEY_SPIN = "spin";
    private static final String KEY_IDCOLOR = "idColor";

    private ShaderProgram mProgram;
    private Mesh mInstancedMesh;
    private int mInstanceCount;
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mVp = new float[16];
    private float mTime;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProgram = new ShaderProgram(VS, FS);
        rebuild(getFloat(KEY_GRID));
    }

    /** 参数变化（GL 线程）重建实例缓冲。 */
    private void rebuild(float gridF) {
        int grid = Math.max(1, Math.round(gridF));
        if (mInstancedMesh != null) mInstancedMesh.dispose();

        GeoGen.GeoData cube = GeoGen.cube();
        float[] cubeVerts = new float[(cube.positions.length / 3) * 6];
        for (int i = 0; i < cube.positions.length / 3; i++) {
            cubeVerts[i * 6] = cube.positions[i * 3];
            cubeVerts[i * 6 + 1] = cube.positions[i * 3 + 1];
            cubeVerts[i * 6 + 2] = cube.positions[i * 3 + 2];
            cubeVerts[i * 6 + 3] = cube.normals[i * 3];
            cubeVerts[i * 6 + 4] = cube.normals[i * 3 + 1];
            cubeVerts[i * 6 + 5] = cube.normals[i * 3 + 2];
        }

        mInstanceCount = grid * grid;
        float spacing = 96f / grid / grid + 0.55f; // 实例越多排越密
        spacing = Math.min(spacing, 2.2f);
        float half = (grid - 1) * spacing / 2f;
        float[] instanceData = new float[mInstanceCount * 7];
        int o = 0;
        for (int i = 0; i < grid; i++) {
            for (int j = 0; j < grid; j++) {
                instanceData[o++] = i * spacing - half;                 // x
                instanceData[o++] = 0.55f;                              // y
                instanceData[o++] = j * spacing - half;                 // z
                instanceData[o++] = 0.25f + (i + j) % 3 * 0.12f;        // scale
                float hue = (i * grid + j) / (float) mInstanceCount;
                float[] rgb = hsvToRgbStatic(hue, 0.75f, 1f);
                instanceData[o++] = rgb[0];
                instanceData[o++] = rgb[1];
                instanceData[o++] = rgb[2];
            }
        }

        mInstancedMesh = new Mesh.Builder()
                .addBuffer(cubeVerts, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .addInstancedBuffer(instanceData, 1,
                        new Mesh.Attrib(2, 4), new Mesh.Attrib(3, 3))
                .setIndices(cube.indices)
                .build();
    }

    private static float[] hsvToRgbStatic(float h, float s, float v) {
        float c = v * s;
        float x = c * (1f - Math.abs((h * 6f) % 2f - 1f));
        float m = v - c;
        float r, g, b;
        if (h < 1f / 6f) { r = c; g = x; b = 0; }
        else if (h < 2f / 6f) { r = x; g = c; b = 0; }
        else if (h < 3f / 6f) { r = 0; g = c; b = x; }
        else if (h < 4f / 6f) { r = 0; g = x; b = c; }
        else if (h < 5f / 6f) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return new float[]{r + m, g + m, b + m};
    }

    @Override
    public void onParamChanged(ParamSpec spec) {
        if (spec.key.equals(KEY_GRID)) {
            rebuild(getFloat(KEY_GRID));
        }
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        clearFrame(0.06f, 0.08f, 0.12f);
        mTime += deltaTime * getFloat(KEY_SPIN) * 2f;

        float grid = Math.max(1f, getFloat(KEY_GRID));
        float cameraDist = 8f + grid * 0.45f;

        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 50f, aspect(), 0.1f, 200f);
        Matrix.setLookAtM(mView, 0,
                cameraDist * 0.7f, cameraDist * 0.55f, cameraDist,
                0, 0, 0, 0, 1, 0);
        Matrix.multiplyMM(mVp, 0, mProj, 0, mView, 0);

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glEnable(GLES30.GL_CULL_FACE);
        GLES30.glCullFace(GLES30.GL_BACK);

        mProgram.use();
        mProgram.setMat4("u_vp", mVp);
        mProgram.set("u_time", mTime);
        mProgram.set("u_wave", getFloat(KEY_WAVE));
        mProgram.set("u_useIdColor", getBool(KEY_IDCOLOR) ? 1f : 0f);
        mProgram.set("u_lightDir", 0.5f, 1f, 0.7f);
        // 一次 draw call 画出全部实例
        mInstancedMesh.drawInstanced(GLES30.GL_TRIANGLES, mInstanceCount);

        GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.floatSpec(KEY_GRID, "网格 N（N² 实例）", 1f, 40f, 10f, "%.0f"));
        specs.add(ParamSpec.floatSpec(KEY_WAVE, "波浪幅度", 0f, 1.5f, 0.4f));
        specs.add(ParamSpec.floatSpec(KEY_SPIN, "动画速度", 0f, 2f, 0.5f));
        specs.add(ParamSpec.boolSpec(KEY_IDCOLOR, "用 gl_InstanceID 着色", true));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        if (mInstancedMesh != null) mInstancedMesh.dispose();
        mProgram.release();
    }
}
