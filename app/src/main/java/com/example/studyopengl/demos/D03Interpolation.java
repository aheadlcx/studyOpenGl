package com.example.studyopengl.demos;

import android.opengl.GLES30;
import android.opengl.Matrix;

import com.example.studyopengl.engine.BaseDemoEngine;
import com.example.studyopengl.gl.Mesh;
import com.example.studyopengl.gl.ShaderProgram;
import com.example.studyopengl.param.ParamSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 03 · Varying 插值：smooth / flat / noperspective 三种插值限定符对比。
 */
public class D03Interpolation extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍插值是怎么发生的\n"
            + "光栅化时，顶点着色器输出的 out 变量在图元内部按\"透视校正\"方式插值后交给片元："
            + "先除以 w 转到屏幕空间做线性插值，再乘回 1/w，保证三维透视下依然正确"
            + "（这就是贴在斜面上的纹理不会扭曲的原因）。\n\n"
            + "▍插值限定符（GLSL ES 3.00 只有两种可用）\n"
            + "· smooth（默认）：透视校正插值，颜色沿 3D 面平滑过渡；\n"
            + "· flat：不插值，整个图元取\"激起顶点(provoking vertex)\"的值 —— "
            + "OpenGL 约定为图元最后一个顶点，因此三角形显示的是第 3 个顶点（蓝色）的颜色；\n"
            + "· ⚠ noperspective：桌面 GLSL 支持\"屏幕空间线性插值\"，"
            + "但在 GLSL ES 3.00 中它是【保留字却未实现】—— 写了直接编译错误"
            + "（ERROR: Illegal use of reserved word），这是从桌面移植 shader 的高频坑！\n\n"
            + "▍为什么插值有开销\n"
            + "每个片元要对所有 varying 做加权插值，varying 越多带宽越大；"
            + "flat 限定符还能让驱动省掉插值硬件工作，也是传\"每图元常量\"（如朝向 ID、材质索引）的标准做法。\n\n"
            + "▍本例\n"
            + "同一个 RGB 大三角形绕 X 轴旋转（顶点深度持续变化），切换 smooth/flat 对比："
            + "smooth 三色平滑流动，flat 整块纯色。";

    private static final String VS_FORMAT = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec3 a_color;\n"
            + "uniform mat4 u_mvp;\n"
            + "%INTERP% out vec3 v_color;\n"          // smooth / flat / noperspective
            + "void main() {\n"
            + "    v_color = a_color;\n"
            + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String FS_FORMAT = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "%INTERP% in vec3 v_color;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    fragColor = vec4(v_color, 1.0);\n"
            + "}\n";

    private static final String KEY_MODE = "mode";
    private static final String KEY_SPEED = "speed";

    private static final String[] MODE_LABELS = {
            "smooth 透视校正（默认）", "flat 平直（取激起顶点）"
    };

    private final ShaderProgram[] mPrograms = new ShaderProgram[2];
    private Mesh mMesh;
    private final float[] mModel = new float[16];
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mMvp = new float[16];
    private float mAngle;

    @Override
    public void onSurfaceCreated(int width, int height) {
        // 注意：GLSL ES 3.00 里 noperspective 是保留字且未实现，不能编译，
        // 所以这里只提供 smooth / flat 两种合法插值模式
        String[] interps = {"smooth", "flat"};
        for (int i = 0; i < interps.length; i++) {
            mPrograms[i] = new ShaderProgram(
                    VS_FORMAT.replace("%INTERP%", interps[i]),
                    FS_FORMAT.replace("%INTERP%", interps[i]));
        }

        // RGB 大三角形，顶点颜色差异明显
        mMesh = new Mesh.Builder()
                .addBuffer(new float[]{
                        -0.9f, -0.55f, -0.4f, 1f, 0.1f, 0.1f,
                        0.9f, -0.55f, -0.4f, 0.1f, 1f, 0.1f,
                        0f, 0.85f, 0.6f, 0.15f, 0.3f, 1f
                }, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .build();
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        clearFrame(0.06f, 0.08f, 0.12f);
        mAngle += getFloat(KEY_SPEED) * 60f * deltaTime;

        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 50f, aspect(), 0.1f, 10f);
        Matrix.setIdentityM(mView, 0);
        Matrix.setLookAtM(mView, 0, 0, 0, 2.4f, 0, 0, 0, 0, 1, 0);

        Matrix.setIdentityM(mModel, 0);
        Matrix.rotateM(mModel, 0, mAngle, 1, 0, 0); // 绕 X 轴转，制造深度差

        float[] pv = new float[16];
        Matrix.multiplyMM(pv, 0, mProj, 0, mView, 0);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);

        int mode = getOptionIndex(KEY_MODE);
        mPrograms[mode].use();
        mPrograms[mode].setMat4("u_mvp", mMvp);
        mMesh.draw(GLES30.GL_TRIANGLES);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.optionSpec(KEY_MODE, "插值限定符", MODE_LABELS, 0));
        specs.add(ParamSpec.floatSpec(KEY_SPEED, "旋转速度（绕X轴）", -2f, 2f, 0.6f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mMesh.dispose();
        for (int i = 0; i < mPrograms.length; i++) {
            if (mPrograms[i] != null) mPrograms[i].release();
        }
    }
}
