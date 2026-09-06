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
 * 08 · 深度测试：glDepthFunc 全家族、深度掩码、多边形偏移、z-fighting。
 */
public class D08DepthTest extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍深度缓冲怎么工作\n"
            + "每个片元带着 1/w 插值出的深度值 [0,1]，通过 glDepthFunc 设定的比较后才能写颜色："
            + "默认 GL_LESS = 更近者胜。比较失败只丢颜色，深度掩码 "
            + "glDepthMask(GL_FALSE) 则连深度也不写 —— 画透明物体的标配"
            + "（它们不遮挡别人，见 09 课）。\n\n"
            + "▍全部深度函数\n"
            + "GL_NEVER 永不通过 / GL_LESS 更小才过 / GL_EQUAL 相等才过 / "
            + "GL_LEQUAL 小于等于 / GL_GREATER / GL_NOTEQUAL / GL_GEQUAL / GL_ALWAYS。"
            + "切到 GL_GREATER 会看到\"远者胜\"的幽灵画面；GL_EQUAL 可用来做同一几何体二次渲染的精确匹配"
            + "（配合偏移就是本例的描线）。\n\n"
            + "▍z-fighting 与多边形偏移\n"
            + "两个共面几何体深度几乎相同，光栅化舍入导致颜色随机闪烁。"
            + "glPolygonOffset(factor, units) 把片元深度整体推远："
            + "offset = m×factor + r×units（m=深度斜率，r=最小可分辨差），"
            + "配套 glEnable(GL_POLYGON_OFFSET_FILL)。经典用法：实心面推远，让共面线框稳定浮在上面。\n\n"
            + "▍ Early-Z\n"
            + "片元着色器之前就有深度测试（early-z），画不透明的物体请\"从近到远\"画，"
            + "被遮挡的片元直接跳过昂贵的 FS；开了 discard 或写深度会退化成 late-z。";

    private static final String VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec3 a_color;\n"
            + "uniform mat4 u_mvp;\n"
            + "out vec3 v_color;\n"
            + "out vec3 v_worldPos;\n"
            + "void main() {\n"
            + "    v_color = a_color;\n"
            + "    v_worldPos = a_pos;\n"
            + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec3 v_color;\n"
            + "in vec3 v_worldPos;\n"
            + "uniform float u_grid;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    vec3 c = v_color;\n"
            + "    if (u_grid > 0.5) {\n"
            + "        // 地面网格线：与地板共面 -> 用来演示 z-fighting/偏移\n"
            + "        vec2 g = abs(fract(v_worldPos.xz) - 0.5);\n"
            + "        if (min(g.x, g.y) < 0.03) c = vec3(0.9);\n"
            + "    }\n"
            + "    fragColor = vec4(c, 1.0);\n"
            + "}\n";

    private static final String KEY_FUNC = "func";
    private static final String KEY_MASK = "mask";
    private static final String KEY_OFFSET = "offset";
    private static final String KEY_FACTOR = "factor";
    private static final String KEY_UNITS = "units";
    private static final String KEY_SPEED = "speed";

    private static final String[] FUNC_LABELS = {
            "GL_NEVER", "GL_LESS（默认）", "GL_EQUAL", "GL_LEQUAL",
            "GL_GREATER", "GL_NOTEQUAL", "GL_GEQUAL", "GL_ALWAYS"
    };
    private static final int[] FUNC_VALUES = {
            GLES30.GL_NEVER, GLES30.GL_LESS, GLES30.GL_EQUAL, GLES30.GL_LEQUAL,
            GLES30.GL_GREATER, GLES30.GL_NOTEQUAL, GLES30.GL_GEQUAL, GLES30.GL_ALWAYS
    };

    private ShaderProgram mProgram;
    private Mesh mGround;
    private Mesh mCubeA;
    private Mesh mCubeB;
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mMvp = new float[16];
    private float mAngle;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProgram = new ShaderProgram(VS, FS);

        GeoGen.GeoData ground = GeoGen.gridPlane(1, 20f, 1f);
        float[] g = interleave(ground.positions, solidColors(ground.positions.length / 3,
                0.25f, 0.3f, 0.36f));
        mGround = new Mesh.Builder()
                .addBuffer(g, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .setIndices(ground.indices)
                .build();

        GeoGen.GeoData cube = GeoGen.cube();
        mCubeA = new Mesh.Builder()
                .addBuffer(interleave(cube.positions,
                        solidColors(cube.positions.length / 3, 0.9f, 0.35f, 0.3f)),
                        new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .setIndices(cube.indices)
                .build();
        mCubeB = new Mesh.Builder()
                .addBuffer(interleave(cube.positions,
                        solidColors(cube.positions.length / 3, 0.3f, 0.55f, 0.95f)),
                        new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .setIndices(cube.indices)
                .build();
    }

    private static float[] solidColors(int vertCount, float r, float g, float b) {
        float[] c = new float[vertCount * 3];
        for (int i = 0; i < vertCount; i++) {
            c[i * 3] = r;
            c[i * 3 + 1] = g;
            c[i * 3 + 2] = b;
        }
        return c;
    }

    private static float[] interleave(float[] pos, float[] color) {
        int n = pos.length / 3;
        float[] out = new float[n * 6];
        for (int i = 0; i < n; i++) {
            out[i * 6] = pos[i * 3];
            out[i * 6 + 1] = pos[i * 3 + 1];
            out[i * 6 + 2] = pos[i * 3 + 2];
            out[i * 6 + 3] = color[i * 3];
            out[i * 6 + 4] = color[i * 3 + 1];
            out[i * 6 + 5] = color[i * 3 + 2];
        }
        return out;
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        clearFrame(0.06f, 0.08f, 0.12f);
        mAngle += getFloat(KEY_SPEED) * 60f * deltaTime;

        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 50f, aspect(), 0.1f, 50f);
        Matrix.setLookAtM(mView, 0,
                3.5f, 2.6f, 4.5f, 0, 0.6f, 0, 0, 1, 0);
        float[] pv = new float[16];
        Matrix.multiplyMM(pv, 0, mProj, 0, mView, 0);

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthFunc(FUNC_VALUES[getOptionIndex(KEY_FUNC)]);   // 深度比较函数
        GLES30.glDepthMask(getBool(KEY_MASK));                       // 深度写入开关

        // ---- 地板（带网格线，可能 z-fight）----
        boolean useOffset = getBool(KEY_OFFSET);
        if (useOffset) {
            GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL);
            // offset = m*factor + r*units：把填充面往后推，网格线即可稳定浮出
            GLES30.glPolygonOffset(getFloat(KEY_FACTOR), getFloat(KEY_UNITS));
        }
        mProgram.use();
        Matrix.setIdentityM(mModel, 0);
        mProgram.set("u_grid", 1f);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
        mProgram.setMat4("u_mvp", mMvp);
        mGround.draw(GLES30.GL_TRIANGLES);
        if (useOffset) {
            GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL);
        }

        // ---- 两个相交旋转的立方体 ----
        mProgram.set("u_grid", 0f);
        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, -0.55f, 0.75f, 0.15f);
        Matrix.rotateM(mModel, 0, mAngle, 1, 0.6f, 0);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
        mProgram.setMat4("u_mvp", mMvp);
        mCubeA.draw(GLES30.GL_TRIANGLES);

        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, 0.55f, 0.9f, -0.1f);
        Matrix.rotateM(mModel, 0, -mAngle * 1.3f, 0.4f, 1, 0.2f);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
        mProgram.setMat4("u_mvp", mMvp);
        mCubeB.draw(GLES30.GL_TRIANGLES);

        GLES30.glDepthMask(true); // 恢复
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.optionSpec(KEY_FUNC, "深度函数 glDepthFunc", FUNC_LABELS, 1));
        specs.add(ParamSpec.boolSpec(KEY_MASK, "深度写入 glDepthMask", true));
        specs.add(ParamSpec.boolSpec(KEY_OFFSET, "多边形偏移(防 z-fight)", true));
        specs.add(ParamSpec.floatSpec(KEY_FACTOR, "偏移 factor", 0f, 8f, 2f));
        specs.add(ParamSpec.floatSpec(KEY_UNITS, "偏移 units", 0f, 8f, 2f));
        specs.add(ParamSpec.floatSpec(KEY_SPEED, "旋转速度", -2f, 2f, 0.5f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mGround.dispose();
        mCubeA.dispose();
        mCubeB.dispose();
        mProgram.release();
    }
}
