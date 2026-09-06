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
 * 12 · Viewport / Scissor：多视口分屏渲染。
 */
public class D12ViewportScissor extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍视口变换\n"
            + "裁剪空间 [-1,1]³ 到窗口像素的映射由 glViewport(ox, oy, w, h) 决定：\n"
            + "    x_win = (x_ndc + 1) / 2 × w + ox （y 从底部起算）\n"
            + "glViewport 不做裁剪！NDC 之外的图元照样画出视口矩形 —— "
            + "超出部分会\"溢出\"到整个窗口，这就是本例开关 scissor 的对比意义。\n\n"
            + "▍裁剪框\n"
            + "glScissor(x, y, w, h) + glEnable(GL_SCISSOR_TEST) 在光栅化前做像素级矩形裁剪，"
            + "只影响片元测试阶段，不改变坐标变换。经典用途：\n"
            + "· 限定 glClear 只清某个区域（glClear 受 scissor 影响！）；\n"
            + "· 分屏 UI、小地图；\n"
            + "· 部分重绘（配合 EGL_BUFFER_PRESERVED 或全量重画）。\n\n"
            + "▍多视口渲染\n"
            + "同一场景对每个视口重复\"设置视口 → 清区域 → 画\"，一次场景提交画多机位。"
            + "VR 双目、后视镜、画中画都是这个套路；更细粒度的 GLES3.1 还有 "
            + "glViewportArrayv + gl_ViewportIndex（几何着色器阶段指定）。\n\n"
            + "▍本例\n"
            + "N×N 个视口各画一个旋转立方体；视口故意比格小 20%，"
            + "关掉 scissor 时能看到立方体\"越界\"画到相邻格子。";

    private static final String VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec3 a_color;\n"
            + "uniform mat4 u_mvp;\n"
            + "out vec3 v_color;\n"
            + "void main() {\n"
            + "    v_color = a_color;\n"
            + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec3 v_color;\n"
            + "uniform vec3 u_tint;\n"
            + "out vec4 fragColor;\n"
            + "void main() { fragColor = vec4(v_color * u_tint, 1.0); }\n";

    private static final String KEY_GRID = "grid";
    private static final String KEY_SCISSOR = "scissor";
    private static final String KEY_SPEED = "speed";

    private ShaderProgram mProgram;
    private Mesh mCube;
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mMvp = new float[16];
    private float mTime;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProgram = new ShaderProgram(VS, FS);
        GeoGen.GeoData cube = GeoGen.cube();
        float[] verts = new float[(cube.positions.length / 3) * 6];
        for (int i = 0; i < cube.positions.length / 3; i++) {
            verts[i * 6] = cube.positions[i * 3];
            verts[i * 6 + 1] = cube.positions[i * 3 + 1];
            verts[i * 6 + 2] = cube.positions[i * 3 + 2];
            verts[i * 6 + 3] = 1f; // 颜色用 uniform tint 乘
            verts[i * 6 + 4] = 1f;
            verts[i * 6 + 5] = 1f;
        }
        mCube = new Mesh.Builder()
                .addBuffer(verts, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .setIndices(cube.indices)
                .build();
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        mTime += deltaTime * getFloat(KEY_SPEED);

        int grid = getInt(KEY_GRID);
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST);
        GLES30.glViewport(0, 0, mWidth, mHeight);
        GLES30.glClearColor(0.03f, 0.04f, 0.07f, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        boolean useScissor = getBool(KEY_SCISSOR);
        if (useScissor) {
            GLES30.glEnable(GLES30.GL_SCISSOR_TEST);
        }
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 50f, 1f, 0.1f, 20f); // 视口方形，aspect=1
        Matrix.setLookAtM(mView, 0, 3f, 2.4f, 3f, 0, 0, 0, 0, 1, 0);
        float[] pv = new float[16];
        Matrix.multiplyMM(pv, 0, mProj, 0, mView, 0);

        mProgram.use();
        float cellW = mWidth / (float) grid;
        float cellH = mHeight / (float) grid;

        for (int gy = 0; gy < grid; gy++) {
            for (int gx = 0; gx < grid; gx++) {
                int ox = Math.round(gx * cellW);
                int oy = Math.round(gy * cellH);
                int cw = Math.round(cellW);
                int ch = Math.round(cellH);

                if (useScissor) {
                    // 先用 scissor 限定清屏区域，画出格子底色
                    GLES30.glScissor(ox, oy, cw, ch);
                    float[] bg = hsvToRgb((gx + gy * grid) / (float) (grid * grid), 0.35f, 0.25f);
                    GLES30.glClearColor(bg[0], bg[1], bg[2], 1f);
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
                }

                // 视口比格子小 20%：不裁剪时立方体会溢出格子
                int inset = Math.round(Math.min(cw, ch) * 0.1f);
                GLES30.glViewport(ox + inset, oy + inset,
                        cw - inset * 2, ch - inset * 2);

                Matrix.setIdentityM(mModel, 0);
                Matrix.rotateM(mModel, 0, mTime * 90f + (gx + gy) * 45f, 0.5f, 1, 0.3f);
                Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
                mProgram.setMat4("u_mvp", mMvp);
                float[] tint = hsvToRgb((gx * 0.13f + gy * 0.31f) % 1f, 0.8f, 1f);
                mProgram.set("u_tint", tint[0], tint[1], tint[2]);
                mCube.draw(GLES30.GL_TRIANGLES);
            }
        }

        GLES30.glViewport(0, 0, mWidth, mHeight);
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.intSpec(KEY_GRID, "网格 N（N×N 视口）", 1, 4, 3));
        specs.add(ParamSpec.boolSpec(KEY_SCISSOR, "启用 GL_SCISSOR_TEST", true));
        specs.add(ParamSpec.floatSpec(KEY_SPEED, "旋转速度", 0f, 2f, 0.3f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mCube.dispose();
        mProgram.release();
    }
}
