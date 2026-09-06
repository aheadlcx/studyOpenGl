package com.example.studyopengl.demos;

import android.opengl.GLES30;
import android.opengl.Matrix;

import com.example.studyopengl.engine.BaseDemoEngine;
import com.example.studyopengl.gl.GeoGen;
import com.example.studyopengl.gl.Mesh;
import com.example.studyopengl.gl.ShaderProgram;
import com.example.studyopengl.param.ParamSpec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * 18 · Uniform Buffer Object（UBO）：std140 布局 + 多 program 共享。
 */
public class D18UniformBlock extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍Uniform Block 解决什么问题\n"
            + "传统 uniform 属于单个 program：10 个 shader 共享一个光照参数就要 glUseProgram×10 "
            + "+ glUniform×10。UBO 把一块 uniform 放进 Buffer Object，"
            + "多个 program 通过 binding 槽位指向同一块显存，一次 glBufferSubData 全部生效。\n\n"
            + "▍建立连接的三步\n"
            + "1) GLSL：layout(std140) uniform LightBlock { vec4 color; float intensity; };\n"
            + "2) glGetUniformBlockIndex(prog, \"LightBlock\") → glUniformBlockBinding(prog, idx, 0)\n"
            + "   （GLSL ES 3.00 不支持 layout(binding=N)，必须运行时绑定）\n"
            + "3) glBindBufferBase(GL_UNIFORM_BUFFER, 0, ubo) 把缓冲挂到槽位 0。\n\n"
            + "▍std140 布局规则（重点）\n"
            + "· float/vec2/vec4 按 4/8/16 字节对齐；\n"
            + "· vec3 按 16 字节对齐（所以常写成 vec4 避免 padding 坑）；\n"
            + "· 数组元素一律 16 字节步进；mat4 每列占一个 vec4。\n"
            + "本例布局：offset0 vec4 color(16B)，offset16 float intensity + 12B 填充，共 32 字节。\n\n"
            + "▍本例\n"
            + "两个 program（平滑光照 / 阶梯光照）共享同一个 LightBlock UBO；"
            + "拖动色相/强度滑条只执行一次 glBufferSubData，两块立方体同时变色 —— 这就是共享的意义。";

    private static final String VS = ""
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

    // 两个 FS 共享同一个 uniform block，只有光照计算不同
    private static final String FS_SMOOTH = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "layout(std140) uniform LightBlock {\n"
            + "    vec4  lightColor;\n"     // offset 0
            + "    float intensity;\n"     // offset 16
            + "};\n"
            + "in vec3 v_normal;\n"
            + "in vec3 v_worldPos;\n"
            + "uniform vec3 u_objectColor;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    vec3 N = normalize(v_normal);\n"
            + "    vec3 L = normalize(vec3(0.6, 1.0, 0.8));\n"
            + "    float diff = max(dot(N, L), 0.0);\n"
            + "    vec3 c = u_objectColor * (0.2 + 0.8 * diff) * lightColor.rgb * intensity;\n"
            + "    fragColor = vec4(c, 1.0);\n"
            + "}\n";

    private static final String FS_STEPPED = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "layout(std140) uniform LightBlock {\n"
            + "    vec4  lightColor;\n"
            + "    float intensity;\n"
            + "};\n"
            + "in vec3 v_normal;\n"
            + "in vec3 v_worldPos;\n"
            + "uniform vec3 u_objectColor;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    vec3 N = normalize(v_normal);\n"
            + "    vec3 L = normalize(vec3(0.6, 1.0, 0.8));\n"
            + "    // 阶梯化光照：把连续 diff 量化成 3 档（卡通着色 toon 风格）\n"
            + "    float diff = max(dot(N, L), 0.0);\n"
            + "    diff = floor(diff * 3.0) / 3.0;\n"
            + "    vec3 c = u_objectColor * (0.25 + 0.75 * diff) * lightColor.rgb * intensity;\n"
            + "    fragColor = vec4(c, 1.0);\n"
            + "}\n";

    private static final String KEY_HUE = "hue";
    private static final String KEY_INTENSITY = "intensity";
    private static final String KEY_SPIN = "spin";

    private static final int LIGHT_BINDING = 0;   // UBO 绑定槽位
    private static final int LIGHT_BLOCK_SIZE = 32; // std140: vec4 + float(+pad12)

    private ShaderProgram mProgramA;
    private ShaderProgram mProgramB;
    private Mesh mCubeA;
    private Mesh mCubeB;
    private int mUBO;
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mMvp = new float[16];
    private float mTime;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProgramA = new ShaderProgram(VS, FS_SMOOTH);
        mProgramB = new ShaderProgram(VS, FS_STEPPED);

        // 两个 program 都把 LightBlock 绑到槽位 0
        bindBlock(mProgramA);
        bindBlock(mProgramB);

        // 创建 UBO 并分配显存
        int[] ubo = new int[1];
        GLES30.glGenBuffers(1, ubo, 0);
        mUBO = ubo[0];
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, mUBO);
        ByteBuffer data = ByteBuffer.allocateDirect(LIGHT_BLOCK_SIZE)
                .order(ByteOrder.nativeOrder());
        data.putFloat(1f).putFloat(1f).putFloat(1f).putFloat(1f); // color
        data.putFloat(1f);                                        // intensity
        data.putFloat(0).putFloat(0).putFloat(0);                 // padding
        data.position(0);
        GLES30.glBufferData(GLES30.GL_UNIFORM_BUFFER, LIGHT_BLOCK_SIZE,
                data, GLES30.GL_DYNAMIC_DRAW);
        // 挂到 binding 槽位 0（ 之后所有 program 自动共享 ）
        GLES30.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, LIGHT_BINDING, mUBO);
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, 0);

        GeoGen.GeoData cube = GeoGen.cube();
        float[] verts = interleave(cube.positions, cube.normals);
        mCubeA = new Mesh.Builder()
                .addBuffer(verts, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .setIndices(cube.indices)
                .build();
        mCubeB = new Mesh.Builder()
                .addBuffer(verts, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .setIndices(cube.indices)
                .build();
    }

    private void bindBlock(ShaderProgram program) {
        program.use();
        int index = GLES30.glGetUniformBlockIndex(program.getProgramId(), "LightBlock");
        GLES30.glUniformBlockBinding(program.getProgramId(), index, LIGHT_BINDING);
    }

    private static float[] interleave(float[] pos, float[] normal) {
        int n = pos.length / 3;
        float[] out = new float[n * 6];
        for (int i = 0; i < n; i++) {
            out[i * 6] = pos[i * 3];
            out[i * 6 + 1] = pos[i * 3 + 1];
            out[i * 6 + 2] = pos[i * 3 + 2];
            out[i * 6 + 3] = normal[i * 3];
            out[i * 6 + 4] = normal[i * 3 + 1];
            out[i * 6 + 5] = normal[i * 3 + 2];
        }
        return out;
    }

    /** 拖动滑条 -> 一次 glBufferSubData 更新共享块。 */
    private void updateUbo() {
        float[] rgb = hsvToRgb(getFloat(KEY_HUE), 0.55f, 1f);
        ByteBuffer data = ByteBuffer.allocateDirect(LIGHT_BLOCK_SIZE)
                .order(ByteOrder.nativeOrder());
        data.putFloat(rgb[0]).putFloat(rgb[1]).putFloat(rgb[2]).putFloat(1f);
        data.putFloat(getFloat(KEY_INTENSITY));
        data.putFloat(0).putFloat(0).putFloat(0);
        data.position(0);
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, mUBO);
        GLES30.glBufferSubData(GLES30.GL_UNIFORM_BUFFER, 0, LIGHT_BLOCK_SIZE, data);
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, 0);
    }

    @Override
    public void onParamChanged(ParamSpec spec) {
        if (spec.key.equals(KEY_HUE) || spec.key.equals(KEY_INTENSITY)) {
            updateUbo();
        }
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        clearFrame(0.06f, 0.08f, 0.12f);
        mTime += deltaTime * getFloat(KEY_SPIN) * 40f;

        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 50f, aspect(), 0.1f, 30f);
        Matrix.setLookAtM(mView, 0, 3.2f, 2.2f, 5f, 0, 0.5f, 0, 0, 1, 0);
        float[] pv = new float[16];
        Matrix.multiplyMM(pv, 0, mProj, 0, mView, 0);

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);

        // 立方体 A：平滑光照 program（共享 UBO 槽位 0）
        mProgramA.use();
        mProgramA.set("u_objectColor", 0.95f, 0.5f, 0.3f);
        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, -1.1f, 0.6f, 0);
        Matrix.rotateM(mModel, 0, mTime, 0.3f, 1, 0);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
        mProgramA.setMat4("u_mvp", mMvp);
        mProgramA.setMat4("u_model", mModel);
        mCubeA.draw(GLES30.GL_TRIANGLES);

        // 立方体 B：卡通光照 program —— 未设任何光照 uniform，数据来自共享 UBO
        mProgramB.use();
        mProgramB.set("u_objectColor", 0.35f, 0.65f, 0.95f);
        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, 1.1f, 0.6f, 0);
        Matrix.rotateM(mModel, 0, -mTime, 0.3f, 1, 0);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
        mProgramB.setMat4("u_mvp", mMvp);
        mProgramB.setMat4("u_model", mModel);
        mCubeB.draw(GLES30.GL_TRIANGLES);

        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.floatSpec(KEY_HUE, "光照颜色 (UBO)", 0f, 1f, 0.12f));
        specs.add(ParamSpec.floatSpec(KEY_INTENSITY, "光照强度 (UBO)", 0.2f, 3f, 1f));
        specs.add(ParamSpec.floatSpec(KEY_SPIN, "旋转速度", -2f, 2f, 0.6f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mCubeA.dispose();
        mCubeB.dispose();
        GLES30.glDeleteBuffers(1, new int[]{mUBO}, 0);
        mProgramA.release();
        mProgramB.release();
    }
}
