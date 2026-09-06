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
 * 02 · 图元类型与索引绘制：glDrawElements 的全部 mode + ES3 原生重启索引。
 */
public class D02PrimitiveTypes extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍图元装配\n"
            + "顶点着色器输出进入图元装配阶段，按 mode 决定顶点如何组成图元：\n"
            + "· GL_POINTS：每顶点一个点，大小由 VS 内 gl_PointSize 设置（最大值有硬件上限）；\n"
            + "· GL_LINES / LINE_STRIP / LINE_LOOP：独立线段 / 连折线 / 闭合环；\n"
            + "· GL_TRIANGLES / TRIANGLE_STRIP / TRIANGLE_FAN：独立三角形 / 三角带 / 三角扇。\n"
            + "strip/fan 比 TRIANGLES 省顶点：n+2 个顶点即可画 n 个三角形。\n\n"
            + "▍索引绘制\n"
            + "glDrawElements(mode, count, GL_UNSIGNED_INT, 0) 配合 EBO，"
            + "顶点可被索引复用（四边形只需 4 顶点 + 6 索引）；ES3 原生支持 32 位索引，"
            + "突破 ES2 时代 GL_UNSIGNED_SHORT 的 65535 顶点上限。\n\n"
            + "▍重启索引（ES3 新增，无需扩展）\n"
            + "glEnable(GL_PRIMITIVE_RESTART_FIXED_INDEX) 后，索引值 0xFFFFFFFF（32 位）/ "
            + "0xFFFF（16 位）表示\"断开重开\"，一次 draw call 画多段不相连的图元，"
            + "省掉多次 draw call 的 CPU 提交开销。\n\n"
            + "▍本例\n"
            + "中心点 + 36 边形一圈顶点，同一份 VBO 用不同索引缓冲画出所有图元类型；"
            + "\"重启索引\"模式用两个 strip 在一次调用中画出上下两段圆弧。\n\n"
            + "▍注意\n"
            + "glLineWidth 在多数移动 GPU 上只保证 1，超过会被钳制（参数滑条可验证）；"
            + "点大小同样受 ALIASED_POINT_SIZE_RANGE 限制。";

    private static final String VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec3 a_color;\n"
            + "uniform mat4 u_mvp;\n"
            + "uniform float u_pointSize;\n"
            + "out vec3 v_color;\n"
            + "void main() {\n"
            + "    v_color = a_color;\n"
            + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
            + "    gl_PointSize = u_pointSize;\n"   // 仅 GL_POINTS 生效
            + "}\n";

    private static final String FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec3 v_color;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    fragColor = vec4(v_color, 1.0);\n"
            + "}\n";

    private static final String KEY_MODE = "mode";
    private static final String KEY_POINT = "point";
    private static final String KEY_LINE = "line";
    private static final String KEY_SPEED = "speed";

    private static final String[] MODE_LABELS = {
            "GL_POINTS", "GL_LINES", "GL_LINE_STRIP", "GL_LINE_LOOP",
            "GL_TRIANGLES", "GL_TRIANGLE_STRIP", "GL_TRIANGLE_FAN", "重启索引×2条带"
    };

    private ShaderProgram mProgram;
    private final Mesh[] mMeshes = new Mesh[8];
    private final float[] mMvp = new float[16];
    private float mAngle;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProgram = new ShaderProgram(VS, FS);

        // 顶点 0 = 圆心，1..36 = 圆周（半径 0.8，颜色按角度渐变）
        int segments = 36;
        int vertCount = segments + 2;
        float[] verts = new float[vertCount * 6];
        verts[0] = 0f;
        verts[1] = 0f;
        verts[2] = 0f;
        verts[3] = 1f;
        verts[4] = 1f;
        verts[5] = 1f;
        for (int i = 0; i <= segments; i++) {
            float a = (float) (Math.PI * 2.0 * i / segments);
            float x = (float) Math.cos(a) * 0.8f;
            float y = (float) Math.sin(a) * 0.8f;
            int o = (i + 1) * 6;
            verts[o] = x;
            verts[o + 1] = y;
            verts[o + 2] = 0f;
            float r = 0.5f + 0.5f * (float) Math.cos(a);
            float g = 0.5f + 0.5f * (float) Math.sin(a);
            verts[o + 3] = r;
            verts[o + 4] = g;
            verts[o + 5] = 1f - r;
        }

        for (int mode = 0; mode < 8; mode++) {
            mMeshes[mode] = new Mesh.Builder()
                    .addBuffer(verts, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                    .setIndices(buildIndices(mode, segments))
                    .build();
        }
    }

    /** 每种绘制模式一份索引缓冲。 */
    private int[] buildIndices(int mode, int segments) {
        switch (mode) {
            case 0: { // POINTS：圆周点
                int[] idx = new int[segments];
                for (int i = 0; i < segments; i++) idx[i] = i + 1;
                return idx;
            }
            case 1: { // LINES：成对线段
                int[] idx = new int[segments * 2];
                for (int i = 0; i < segments; i++) {
                    idx[i * 2] = i + 1;
                    idx[i * 2 + 1] = (i + 1) % segments + 1;
                }
                return idx;
            }
            case 2: { // LINE_STRIP
                int[] idx = new int[segments + 1];
                for (int i = 0; i <= segments; i++) idx[i] = i % segments + 1;
                return idx;
            }
            case 3: { // LINE_LOOP：自动闭合
                int[] idx = new int[segments];
                for (int i = 0; i < segments; i++) idx[i] = i + 1;
                return idx;
            }
            case 4: { // TRIANGLES：扇形展开
                int[] idx = new int[segments * 3];
                for (int i = 0; i < segments; i++) {
                    idx[i * 3] = 0;
                    idx[i * 3 + 1] = i + 1;
                    idx[i * 3 + 2] = (i + 1) % segments + 1;
                }
                return idx;
            }
            case 5: { // TRIANGLE_STRIP：锯齿连接
                int[] idx = new int[segments * 2];
                for (int i = 0; i < segments; i++) {
                    idx[i * 2] = i + 1;
                    idx[i * 2 + 1] = (i + 1) % segments + 1;
                }
                return idx;
            }
            case 6: { // TRIANGLE_FAN：中心 + 环
                int[] idx = new int[segments + 2];
                idx[0] = 0;
                for (int i = 0; i <= segments; i++) idx[i + 1] = i % segments + 1;
                return idx;
            }
            default: { // 重启索引：两条独立三角带一次画完
                // strip1 = 上半弧 (1..19)，strip2 = 下半弧 (19..36,1)
                int[] idx = new int[19 + 1 + 19];
                int k = 0;
                for (int i = 1; i <= 19; i++) idx[k++] = i;
                idx[k++] = 0xFFFFFFFF;                    // 固定重启索引值
                for (int i = 19; i <= 36; i++) idx[k++] = i;
                idx[k] = 1;
                return idx;
            }
        }
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        clearFrame(0.06f, 0.08f, 0.12f);
        mAngle += getFloat(KEY_SPEED) * 60f * deltaTime;

        Matrix.setIdentityM(mMvp, 0);
        Matrix.rotateM(mMvp, 0, mAngle, 0, 0, 1);

        int mode = getOptionIndex(KEY_MODE);
        mProgram.use();
        mProgram.setMat4("u_mvp", mMvp);
        mProgram.set("u_pointSize", getFloat(KEY_POINT));

        GLES30.glLineWidth(getFloat(KEY_LINE)); // 多数设备 >1 被钳制，见讲解
        if (mode == 7) {
            GLES30.glEnable(GLES30.GL_PRIMITIVE_RESTART_FIXED_INDEX);
        }
        mMeshes[mode].draw(mode == 0 ? GLES30.GL_POINTS
                : mode == 1 ? GLES30.GL_LINES
                : mode == 2 ? GLES30.GL_LINE_STRIP
                : mode == 3 ? GLES30.GL_LINE_LOOP
                : mode == 4 ? GLES30.GL_TRIANGLES
                : mode == 5 ? GLES30.GL_TRIANGLE_STRIP
                : mode == 6 ? GLES30.GL_TRIANGLE_FAN
                : GLES30.GL_TRIANGLE_STRIP);
        if (mode == 7) {
            GLES30.glDisable(GLES30.GL_PRIMITIVE_RESTART_FIXED_INDEX);
        }
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.optionSpec(KEY_MODE, "绘制模式", MODE_LABELS, 4));
        specs.add(ParamSpec.floatSpec(KEY_POINT, "点大小 gl_PointSize", 1f, 32f, 8f));
        specs.add(ParamSpec.floatSpec(KEY_LINE, "线宽 glLineWidth", 1f, 8f, 1f));
        specs.add(ParamSpec.floatSpec(KEY_SPEED, "旋转速度", -2f, 2f, 0.4f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        for (Mesh m : mMeshes) {
            if (m != null) m.dispose();
        }
        mProgram.release();
    }
}
