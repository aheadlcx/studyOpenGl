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
 * 01 · 三角形与着色器管线
 *
 * 一个三角形从数据到像素的完整旅程：
 *   顶点数据 -> VBO(显存缓冲) -> VAO(属性布局快照) -> 顶点着色器 -> 图元装配
 *   -> 光栅化 -> 片元着色器 -> 测试与混合 -> 帧缓冲 -> 屏幕交换
 */
public class D01HelloTriangle extends BaseDemoEngine {

    /** 技术讲解（需求 7），由 DemoCatalog 取用显示在面板上。 */
    public static final String DESCRIPTION = ""
            + "▍管线全流程\n"
            + "顶点数据 → VBO(显存缓冲) → VAO(属性布局快照) → 顶点着色器 → 图元装配 → 光栅化 → "
            + "片元着色器 → 逐片元测试/混合 → 帧缓冲 → eglSwapBuffers 上屏。\n\n"
            + "▍关键 API\n"
            + "· glGenVertexArrays/glBindVertexArray：VAO 记录属性布局，一次配置长期使用；\n"
            + "· glGenBuffers/glBufferData：把 CPU 内存拷入显存，GL_STATIC_DRAW 提示驱动做放置优化；\n"
            + "· glVertexAttribPointer(loc,size,GL_FLOAT,normalzed,stride,offset)："
            + "本例 stride=24 字节（6 个 float），offset 依次 0/12 —— 交错布局；\n"
            + "· glDrawArrays(GL_TRIANGLES,0,3)：无索引顺序绘制；\n"
            + "· glUseProgram：一个 program 是一次 VS+FS 链接的完整状态机。\n\n"
            + "▍GLSL ES 3.00 要点\n"
            + "· 首行 #version 300 es（之前不能有空行/注释）；\n"
            + "· attribute/varying 换成 in/out；片元要自声明 out vec4（替代 gl_FragColor）；\n"
            + "· 片元必须声明浮点精度 precision mediump float；\n"
            + "· layout(location=N) 显式绑定属性槽，与 Mesh.Attrib 的 N 一一对应。\n\n"
            + "▍调试技巧\n"
            + "驱动可能把未使用的 uniform 优化掉，glGetUniformLocation 返回 -1 不是错误；"
            + "编译/链接必须检查 GL_COMPILE_STATUS / GL_LINK_STATUS，日志用 glGetShaderInfoLog 获取。\n\n"
            + "▍参数\n"
            + "色调滑条改的是 uniform u_tint（每帧上传）；旋转速度改模型矩阵角度；"
            + "开关控制第二个 draw call 是否执行。";

    // ---- GLSL ES 3.00 着色器（Java 字符串内嵌，#version 必须是第一行）----
    private static final String VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"   // 顶点属性：显式槽位 0
            + "layout(location=1) in vec3 a_color;\n"  // 顶点颜色：槽位 1
            + "uniform mat4 u_mvp;\n"                  // 变换矩阵（列主序）
            + "out vec3 v_color;\n"                    // out 替代 ES2 的 varying
            + "void main() {\n"
            + "    v_color = a_color;\n"
            + "    vec4 p = vec4(a_pos, 1.0);\n"       // w=1 表示点而非方向
            + "    gl_Position = u_mvp * p;\n"         // 裁剪空间坐标
            + "}\n";

    private static final String FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"             // 片元着色器必须声明浮点精度
            + "in vec3 v_color;\n"                     // 接收顶点阶段插值结果
            + "uniform vec3 u_tint;\n"                 // 参数面板控制的色调
            + "out vec4 fragColor;\n"                  // ES3 自己声明输出，不用 gl_FragColor
            + "void main() {\n"
            + "    fragColor = vec4(v_color * u_tint, 1.0);\n"
            + "}\n";

    private static final String KEY_HUE = "hue";
    private static final String KEY_SPEED = "speed";
    private static final String KEY_SECOND = "second";

    private ShaderProgram mProgram;
    private Mesh mMainTriangle;
    private Mesh mSecondTriangle;
    private final float[] mModel = new float[16];
    private final float[] mMvp = new float[16];
    private float mAngle;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProgram = new ShaderProgram(VS, FS);

        // 交错(interleaved)布局：pos(xyz) + color(rgb) 紧挨存放，一次拷贝进显存
        mMainTriangle = new Mesh.Builder()
                .addBuffer(new float[]{
                        -0.6f, -0.5f, 0f, 1f, 0.2f, 0.2f,
                        0.6f, -0.5f, 0f, 0.2f, 1f, 0.2f,
                        0f, 0.62f, 0f, 0.25f, 0.4f, 1f
                }, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .build();

        mSecondTriangle = new Mesh.Builder()
                .addBuffer(new float[]{
                        0.65f, 0.15f, 0f, 1f, 1f, 0.2f,
                        0.98f, -0.55f, 0f, 0.2f, 1f, 1f,
                        0.32f, -0.55f, 0f, 1f, 0.2f, 1f
                }, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .build();
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        clearFrame(0.06f, 0.08f, 0.12f);

        mAngle += getFloat(KEY_SPEED) * 60f * deltaTime;

        // MVP = Projection * View * Model；这里只有 Model（旋转）
        Matrix.setIdentityM(mModel, 0);
        Matrix.rotateM(mModel, 0, mAngle, 0, 0, 1);
        Matrix.setIdentityM(mMvp, 0);
        Matrix.multiplyMM(mMvp, 0, mMvp, 0, mModel, 0); // I * model

        float[] tint = hsvToRgb(getFloat(KEY_HUE), 0.85f, 1f);

        mProgram.use();
        mProgram.setMat4("u_mvp", mMvp);
        mProgram.set("u_tint", tint[0], tint[1], tint[2]);

        mMainTriangle.draw(GLES30.GL_TRIANGLES);
        if (getBool(KEY_SECOND)) {
            mSecondTriangle.draw(GLES30.GL_TRIANGLES);
        }
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.floatSpec(KEY_HUE, "色调 u_tint", 0f, 1f, 0.55f));
        specs.add(ParamSpec.floatSpec(KEY_SPEED, "旋转速度", -2f, 2f, 0.5f));
        specs.add(ParamSpec.boolSpec(KEY_SECOND, "显示第二个三角形", true));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mMainTriangle.dispose();
        mSecondTriangle.dispose();
        mProgram.release();
    }
}
