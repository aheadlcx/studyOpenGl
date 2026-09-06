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
 * 09 · 混合 Blending：glBlendFunc / glBlendEquation 全家族。
 */
public class D09Blending extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍混合公式\n"
            + "启用 GL_BLEND 后，片元颜色不再直接覆盖，而是：\n"
            + "    C = F_src × C_src  [EQUATION]  F_dst × C_dst\n"
            + "C_src 是片元着色器输出，C_dst 是帧缓冲已有颜色，[EQUATION] 默认 GL_FUNC_ADD，"
            + "还有 SUBTRACT / REVERSE_SUBTRACT / MIN / MAX（ES3 新增 MIN/MAX）。\n\n"
            + "▍因子因子速查\n"
            + "GL_ZERO / GL_ONE / GL_SRC_ALPHA / GL_ONE_MINUS_SRC_ALPHA / "
            + "GL_DST_ALPHA / GL_ONE_MINUS_DST_ALPHA / GL_SRC_COLOR / GL_CONSTANT_ALPHA…\n"
            + "标准半透明 = (SRC_ALPHA, ONE_MINUS_SRC_ALPHA)；预乘 alpha 纹理 = (ONE, ONE_MINUS_SRC_ALPHA)；"
            + "叠加发光 = (SRC_ALPHA, ONE)。\n\n"
            + "▍常量颜色\n"
            + "glBlendColor(r,g,b,a) 设定的常量供 GL_CONSTANT_* 因子使用，"
            + "不需要在 shader 里输出 alpha 也能调透明度。\n\n"
            + "▍三条铁律\n"
            + "1) 画不透明物体必须先关 GL_BLEND（避免多余混合计算）；\n"
            + "2) 混合时关闭深度写入（glDepthMask(false)），但保留深度测试 —— "
            + "否则先画的半透明面会挡住后面的；\n"
            + "3) 半透明物体按\"从远到近\"排序绘制 —— 混合不满足交换律，"
            + "打开\"反转顺序\"开关就能看到错误顺序下的颜色差异。\n\n"
            + "▍RGB/A 分离\n"
            + "glBlendFuncSeparate(srcRGB, dstRGB, srcA, dstA) 可以对颜色和 alpha "
            + "用不同因子，渲染到带 alpha 的离屏纹理（合成到 UI）时必用。";

    private static final String VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec4 a_color;\n"
            + "uniform mat4 u_mvp;\n"
            + "out vec4 v_color;\n"
            + "void main() {\n"
            + "    v_color = a_color;\n"
            + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec4 v_color;\n"
            + "uniform float u_alpha;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    fragColor = vec4(v_color.rgb, v_color.a * u_alpha);\n"
            + "}\n";

    private static final String KEY_SRC = "src";
    private static final String KEY_DST = "dst";
    private static final String KEY_EQ = "eq";
    private static final String KEY_ALPHA = "alpha";
    private static final String KEY_REVERSE = "reverse";
    private static final String KEY_DEPTHWRITE = "depthWrite";
    private static final String KEY_SPEED = "speed";

    private static final String[] FACTOR_LABELS = {
            "GL_ZERO", "GL_ONE", "GL_SRC_ALPHA", "GL_ONE_MINUS_SRC_ALPHA",
            "GL_DST_ALPHA", "GL_ONE_MINUS_DST_ALPHA", "GL_SRC_COLOR", "GL_CONSTANT_ALPHA"
    };
    private static final int[] FACTOR_VALUES = {
            GLES30.GL_ZERO, GLES30.GL_ONE, GLES30.GL_SRC_ALPHA,
            GLES30.GL_ONE_MINUS_SRC_ALPHA, GLES30.GL_DST_ALPHA,
            GLES30.GL_ONE_MINUS_DST_ALPHA, GLES30.GL_SRC_COLOR, GLES30.GL_CONSTANT_ALPHA
    };
    private static final String[] EQ_LABELS = {
            "GL_FUNC_ADD", "GL_FUNC_SUBTRACT", "GL_FUNC_REVERSE_SUBTRACT", "GL_MIN", "GL_MAX"
    };
    private static final int[] EQ_VALUES = {
            GLES30.GL_FUNC_ADD, GLES30.GL_FUNC_SUBTRACT, GLES30.GL_FUNC_REVERSE_SUBTRACT,
            GLES30.GL_MIN, GLES30.GL_MAX
    };

    private ShaderProgram mProgram;
    private Mesh mBack;
    private Mesh mQuad;
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mMvp = new float[16];
    private float mTime;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProgram = new ShaderProgram(VS, FS);

        // 不透明背景：深色棋盘
        float[] back = new float[36];
        for (int i = 0; i < 6; i++) {
            back[i * 6] = -3f + (i == 1 || i == 3 || i == 4 ? 6f : 0f);
            back[i * 6 + 1] = -2f + (i >= 2 && i != 5 ? 4f : 0f);
            back[i * 6 + 2] = -2f;
            back[i * 6 + 3] = 0.12f;
            back[i * 6 + 4] = 0.14f;
            back[i * 6 + 5] = 0.18f;
        }
        mBack = new Mesh.Builder()
                .addBuffer(back, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .build();

        // 半透明彩色四边形
        mQuad = new Mesh.Builder()
                .addBuffer(new float[]{
                        -1f, -1f, 0, 1, 1, 0, 0, 0.55f,
                        1f, -1f, 0, 1, 1, 0, 0, 0.55f,
                        -1f, 1f, 0, 1, 1, 0, 0, 0.55f,
                        1f, -1f, 0, 1, 1, 0, 0, 0.55f,
                        1f, 1f, 0, 1, 1, 0, 0, 0.55f,
                        -1f, 1f, 0, 1, 1, 0, 0, 0.55f
                }, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 4))
                .build();
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        clearFrame(0.06f, 0.08f, 0.12f);
        mTime += deltaTime * getFloat(KEY_SPEED);

        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 45f, aspect(), 0.1f, 20f);
        Matrix.setLookAtM(mView, 0, 0, 0, 5f, 0, 0, 0, 0, 1, 0);
        float[] pv = new float[16];
        Matrix.multiplyMM(pv, 0, mProj, 0, mView, 0);

        mProgram.use();

        // 1) 先画不透明背景（关混合 + 开深度写入）
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glDepthMask(true);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        Matrix.setIdentityM(mModel, 0);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
        mProgram.setMat4("u_mvp", mMvp);
        mProgram.set("u_alpha", 1f);
        mBack.draw(GLES30.GL_TRIANGLES);

        // 2) 半透明三连板：开混合 + 关深度写入
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glDepthMask(getBool(KEY_DEPTHWRITE));
        GLES30.glBlendFunc(FACTOR_VALUES[getOptionIndex(KEY_SRC)],
                FACTOR_VALUES[getOptionIndex(KEY_DST)]);
        GLES30.glBlendEquation(EQ_VALUES[getOptionIndex(KEY_EQ)]);
        // 常量颜色：供 GL_CONSTANT_ALPHA 因子使用
        float ca = getFloat(KEY_ALPHA);
        GLES30.glBlendColor(0.3f, 0.6f, 0.9f, ca);
        mProgram.set("u_alpha", ca);

        float[] hues = {0.0f, 0.33f, 0.6f};
        for (int i = 0; i < 3; i++) {
            int order = getBool(KEY_REVERSE) ? 2 - i : i; // 反转绘制顺序
            float z = -1.5f + order * 1.2f;
            Matrix.setIdentityM(mModel, 0);
            Matrix.translateM(mModel, 0,
                    (order - 1) * 1.1f * (float) Math.sin(mTime),
                    (order - 1) * -0.7f * (float) Math.sin(mTime),
                    z);
            Matrix.rotateM(mModel, 0, mTime * 40f + order * 30f, 0, 0, 1);
            Matrix.rotateM(mModel, 0, 60f, 1, 0, 0);
            Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
            mProgram.setMat4("u_mvp", mMvp);
            mQuad.draw(GLES30.GL_TRIANGLES);
        }

        GLES30.glBlendEquation(GLES30.GL_FUNC_ADD);
        GLES30.glDepthMask(true);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.optionSpec(KEY_SRC, "源因子 glBlendFunc", FACTOR_LABELS, 2));
        specs.add(ParamSpec.optionSpec(KEY_DST, "目标因子", FACTOR_LABELS, 3));
        specs.add(ParamSpec.optionSpec(KEY_EQ, "混合方程 glBlendEquation", EQ_LABELS, 0));
        specs.add(ParamSpec.floatSpec(KEY_ALPHA, "片元/常量 alpha", 0f, 1f, 0.55f));
        specs.add(ParamSpec.boolSpec(KEY_REVERSE, "反转绘制顺序(看排序问题)", false));
        specs.add(ParamSpec.boolSpec(KEY_DEPTHWRITE, "半透明阶段深度写入", false));
        specs.add(ParamSpec.floatSpec(KEY_SPEED, "动画速度", -1f, 2f, 0.4f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mBack.dispose();
        mQuad.dispose();
        mProgram.release();
    }
}
