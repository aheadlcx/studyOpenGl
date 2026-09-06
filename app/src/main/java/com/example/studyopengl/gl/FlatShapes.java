package com.example.studyopengl.gl;

import android.opengl.GLES30;

/**
 * 2D 图形绘制工具：NDC 坐标直接定位的矩形/边框/三角形/纹理块。
 * 供"管线教学"类 Demo 画流程图、内存条、关卡门等示意图使用。
 * 所有绘制 z=0，调用方自行管理混合开关。
 */
public class FlatShapes {

    private static final String VS_COLOR = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec2 a_pos;\n"
            + "uniform vec4 u_rect;\n"    // xy=NDC中心, zw=全宽全高
            + "void main() {\n"
            + "    gl_Position = vec4(a_pos * u_rect.zw + u_rect.xy, 0.0, 1.0);\n"
            + "}\n";

    private static final String FS_COLOR = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "uniform vec4 u_color;\n"
            + "out vec4 fragColor;\n"
            + "void main() { fragColor = u_color; }\n";

    private static final String VS_TEX = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec2 a_pos;\n"
            + "layout(location=1) in vec2 a_uv;\n"
            + "uniform vec4 u_rect;\n"
            + "out vec2 v_uv;\n"
            + "void main() {\n"
            + "    // 位图行0(文字顶部)对应 v=0，而矩形 v=0 在底部——翻转保文字正立\n"
            + "    v_uv = vec2(a_uv.x, 1.0 - a_uv.y);\n"
            + "    gl_Position = vec4(a_pos * u_rect.zw + u_rect.xy, 0.0, 1.0);\n"
            + "}\n";

    private static final String FS_TEX = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec2 v_uv;\n"
            + "uniform sampler2D u_tex;\n"
            + "out vec4 fragColor;\n"
            + "void main() { fragColor = texture(u_tex, v_uv); }\n";

    private ShaderProgram mColorProg;
    private ShaderProgram mTexProg;
    private Mesh mUnitQuad;   // 纯位置（矩形/三角用）
    private Mesh mUnitQuadUV; // 位置+UV（文字纹理用）
    private Mesh mTriDown;    // 尖朝下三角（流程图箭头）
    private Mesh mTriUp;      // 尖朝上三角

    public FlatShapes() {
        mColorProg = new ShaderProgram(VS_COLOR, FS_COLOR);
        mTexProg = new ShaderProgram(VS_TEX, FS_TEX);
        mUnitQuad = new Mesh.Builder()
                .addBuffer(new float[]{
                        -0.5f, -0.5f,
                        0.5f, -0.5f,
                        -0.5f, 0.5f,
                        0.5f, -0.5f,
                        0.5f, 0.5f,
                        -0.5f, 0.5f
                }, new Mesh.Attrib(0, 2))
                .build();
        mUnitQuadUV = new Mesh.Builder()
                .addBuffer(new float[]{
                        -0.5f, -0.5f, 0f, 0f,
                        0.5f, -0.5f, 1f, 0f,
                        -0.5f, 0.5f, 0f, 1f,
                        0.5f, -0.5f, 1f, 0f,
                        0.5f, 0.5f, 1f, 1f,
                        -0.5f, 0.5f, 0f, 1f
                }, new Mesh.Attrib(0, 2), new Mesh.Attrib(1, 2))
                .build();
        mTriDown = new Mesh.Builder()
                .addBuffer(new float[]{
                        -0.5f, 0.5f,
                        0.5f, 0.5f,
                        0f, -0.5f
                }, new Mesh.Attrib(0, 2))
                .build();
        mTriUp = new Mesh.Builder()
                .addBuffer(new float[]{
                        -0.5f, -0.5f,
                        0.5f, -0.5f,
                        0f, 0.5f
                }, new Mesh.Attrib(0, 2))
                .build();
    }

    /** 填充矩形（NDC）。 */
    public void rect(float cx, float cy, float w, float h,
                     float r, float g, float b, float a) {
        mColorProg.use();
        mColorProg.set("u_rect", cx, cy, w, h);
        mColorProg.set("u_color", r, g, b, a);
        mUnitQuad.draw(GLES30.GL_TRIANGLES);
    }

    /** 空心边框：4 条细矩形拼成。t 为边框厚度(NDC)。 */
    public void frame(float cx, float cy, float w, float h, float t,
                      float r, float g, float b, float a) {
        rect(cx, cy + h / 2f - t / 2f, w, t, r, g, b, a);   // 上
        rect(cx, cy - h / 2f + t / 2f, w, t, r, g, b, a);   // 下
        rect(cx - w / 2f + t / 2f, cy, t, h - t * 2f, r, g, b, a); // 左
        rect(cx + w / 2f - t / 2f, cy, t, h - t * 2f, r, g, b, a); // 右
    }

    /** 尖朝下的三角（向下箭头头部）。 */
    public void triDown(float cx, float cy, float w, float h,
                        float r, float g, float b, float a) {
        mColorProg.use();
        mColorProg.set("u_rect", cx, cy, w, h);
        mColorProg.set("u_color", r, g, b, a);
        mTriDown.draw(GLES30.GL_TRIANGLES);
    }

    /** 尖朝上的三角（向右箭头头部旋转用法见 demo 内）。 */
    public void triUp(float cx, float cy, float w, float h,
                      float r, float g, float b, float a) {
        mColorProg.use();
        mColorProg.set("u_rect", cx, cy, w, h);
        mColorProg.set("u_color", r, g, b, a);
        mTriUp.draw(GLES30.GL_TRIANGLES);
    }

    /** 纹理块（文字标签等，NDC 定位；需使用带 UV 的矩形）。 */
    public void tex(float cx, float cy, float w, float h, int unit) {
        mTexProg.use();
        mTexProg.set("u_rect", cx, cy, w, h);
        mTexProg.set("u_tex", unit);
        mUnitQuadUV.draw(GLES30.GL_TRIANGLES);
    }

    public void release() {
        mColorProg.release();
        mTexProg.release();
        mUnitQuad.dispose();
        mUnitQuadUV.dispose();
        mTriDown.dispose();
        mTriUp.dispose();
    }
}
