package com.example.studyopengl.demos;

import android.opengl.GLES30;
import android.opengl.Matrix;

import com.example.studyopengl.engine.BaseDemoEngine;
import com.example.studyopengl.gl.GeoGen;
import com.example.studyopengl.gl.Mesh;
import com.example.studyopengl.gl.ShaderProgram;
import com.example.studyopengl.gl.TextureHelper;
import com.example.studyopengl.param.ParamSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 26 · 2D 纹理数组：glTexImage3D + sampler2DArray，实例化逐实例选层。
 */
public class D26TextureArray extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍纹理数组是什么\n"
            + "GL_TEXTURE_2D_ARRAY 把 N 张等尺寸 2D 纹理叠成一摞："
            + "glTexImage3D(target, 0, GL_RGBA8, w, h, layers, 0, GL_RGBA, UNSIGNED_BYTE, data)，"
            + "第三个维度就是\"层号\"。shader 里 sampler2DArray 用 vec3(u, v, layer) 采样 —— "
            + "layer 可以是顶点属性/实例属性，每个实例贴不同图，一次 draw call 搞定。\n\n"
            + "▍vs 图集（atlas）\n"
            + "图集也能多图一纹理，但 mipmap 会把相邻图块混色（溢色），需要 padding、"
            + "uv 夹紧技巧一堆；纹理数组的每层独立生成 mip，天然无接缝。"
            + "与 cubemap 的区别：cubemap 是\"方向采样\"（6 面法线球），数组是\"显式层号采样\"。\n\n"
            + "▍状态与限制\n"
            + "· 所有层必须同尺寸同格式（建好后可 glFramebufferTextureLayer 挂单层当 RTT）；\n"
            + "· glGenerateMipmap(GL_TEXTURE_2D_ARRAY) 一次为全部层生成 mip 链；\n"
            + "· 层数上限 GL_MAX_ARRAY_TEXTURE_LAYERS（ES3 下限 256）；\n"
            + "· ES2 需要 EXT_texture_array，ES3 核心。\n\n"
            + "▍本例\n"
            + "4 层（棋盘/砖墙/噪点/色环）× 8 个实例四边形；"
            + "\"层偏移\"滑条整体轮换各实例的层号，\"轮播\"开关自动循环 —— "
            + "注意看每个实例纹理独立切换，draw call 始终只有一次。";

    private static final String VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec2 a_uv;\n"
            + "layout(location=2) in vec4 i_meta;\n" // divisor=1: xy=偏移 z=层号 w=相位
            + "uniform mat4 u_mvp;\n"
            + "uniform float u_time;\n"
            + "uniform float u_cycle;\n"
            + "uniform float u_layerOffset;\n"
            + "out vec2 v_uv;\n"
            + "out float v_layer;\n"
            + "out float v_wobble;\n"
            + "void main() {\n"
            + "    v_uv = a_uv;\n"
            + "    // 层号 = (实例层 + 全局偏移 + 轮播) 取模 —— 顶点属性直接进采样层\n"
            + "    v_layer = mod(i_meta.z + u_layerOffset + floor(u_cycle), 4.0);\n"
            + "    v_wobble = sin(u_time * 2.0 + i_meta.w) * 0.08;\n"
            + "    vec3 p = a_pos * 0.42;\n"
            + "    p.xy = mat2(cos(i_meta.w), -sin(i_meta.w),\n"
            + "                sin(i_meta.w),  cos(i_meta.w)) * p.xy;\n"
            + "    gl_Position = u_mvp * vec4(p.xy + i_meta.xy, p.z, 1.0);\n"
            + "}\n";

    private static final String FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "precision lowp sampler2DArray;\n" // sampler2DArray 无默认精度，必须显式声明
            + "in vec2 v_uv;\n"
            + "in float v_layer;\n"
            + "in float v_wobble;\n"
            + "uniform sampler2DArray u_array;\n"   // 数组采样器
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    // 第三分量 = 层号（0..layers-1，浮点自动就近取整）\n"
            + "    vec4 tex = texture(u_array, vec3(v_uv, v_layer));\n"
            + "    fragColor = vec4(tex.rgb * (1.0 + v_wobble * 2.0), tex.a);\n"
            + "}\n";

    private static final String KEY_OFFSET = "offset";
    private static final String KEY_CYCLE = "cycle";
    private static final String KEY_SPIN = "spin";

    private ShaderProgram mProgram;
    private Mesh mQuads;
    private int mArrayTex;
    private static final int INSTANCE_COUNT = 8;
    private final float[] mMvp = new float[16];
    private float mTime;
    private float mCycle;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProgram = new ShaderProgram(VS, FS);
        mArrayTex = TextureHelper.createTexture2DArray(256, 4);

        // 单位四边形
        float[] quad = new float[]{
                -0.5f, -0.5f, 0, 0, 0,
                0.5f, -0.5f, 0, 1, 0,
                -0.5f, 0.5f, 0, 0, 1,
                0.5f, -0.5f, 0, 1, 0,
                0.5f, 0.5f, 0, 1, 1,
                -0.5f, 0.5f, 0, 0, 1
        };

        // 实例数据：xy 屏幕位置(裁剪空间)、z 初始层号、w 旋转相位
        float[] instances = new float[INSTANCE_COUNT * 4];
        for (int i = 0; i < INSTANCE_COUNT; i++) {
            float angle = (float) (Math.PI * 2.0 * i / INSTANCE_COUNT);
            instances[i * 4] = (float) Math.cos(angle) * 0.55f;
            instances[i * 4 + 1] = (float) Math.sin(angle) * 0.55f;
            instances[i * 4 + 2] = i % 4;
            instances[i * 4 + 3] = angle;
        }

        mQuads = new Mesh.Builder()
                .addBuffer(quad, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 2))
                .addInstancedBuffer(instances, 1, new Mesh.Attrib(2, 4))
                .build();
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        clearFrame(0.06f, 0.08f, 0.12f);
        mTime += deltaTime * getFloat(KEY_SPIN) * 40f;
        if (getBool(KEY_CYCLE)) {
            mCycle += deltaTime * getFloat(KEY_SPIN) * 2f;
        }

        Matrix.setIdentityM(mMvp, 0); // 直接在裁剪空间摆位

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, mArrayTex);
        mProgram.use();
        mProgram.setMat4("u_mvp", mMvp);
        mProgram.set("u_array", 0);
        mProgram.set("u_time", mTime);
        mProgram.set("u_cycle", mCycle);
        mProgram.set("u_layerOffset", getFloat(KEY_OFFSET));
        // 一次 draw 画 8 个实例，各自采样不同的数组层
        mQuads.drawInstanced(GLES30.GL_TRIANGLES, INSTANCE_COUNT);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.floatSpec(KEY_OFFSET, "层偏移 mod 4", 0f, 4f, 0f, "%.1f"));
        specs.add(ParamSpec.boolSpec(KEY_CYCLE, "自动轮播层", true));
        specs.add(ParamSpec.floatSpec(KEY_SPIN, "动画速度", 0f, 2f, 0.5f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mQuads.dispose();
        TextureHelper.deleteTexture(mArrayTex);
        mProgram.release();
    }
}
