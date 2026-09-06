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
 * 10 · 模板测试：物体描边经典三段式。
 */
public class D10StencilTest extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍模板缓冲\n"
            + "每像素一个 8 位整数（本 App 的 EGL 配置请求了 EGL_STENCIL_SIZE=8），"
            + "它是\"用几何写、用几何读\"的掩码。经典应用：描边、镜面/传送门、阴影体、共面绘制优先级。\n\n"
            + "▍两个核心函数\n"
            + "· glStencilFunc(func, ref, mask)：片元的模板值 (s & mask) 与 (ref & mask) 比较；\n"
            + "· glStencilOp(sfail, dfail, dpass)：三种结果各自如何回写 —— "
            + "GL_KEEP / GL_ZERO / GL_REPLACE / GL_INCR / GL_DECR / GL_INVERT（含 WRAP 变体）。\n"
            + "还有 FuncSeparate/OpSeparate 可以对正反面用不同规则（阴影体必需）。\n\n"
            + "▍描边三段式（本例）\n"
            + "1) 正常画物体，同时 glStencilFunc(ALWAYS,1) + glStencilOp(KEEP,KEEP,REPLACE) "
            + "把物体覆盖区写成 1；\n"
            + "2) glStencilFunc(GL_NOTEQUAL,1)：只允许模板值 ≠1 的片元通过；\n"
            + "3) 放大 1.1 倍再画一次纯色物体 —— 只有轮廓外的环带能画出来，即描边。\n"
            + "第二遍通常还要 glStencilMask(0x00) 禁止回写、glDepthMask(false) 不污染深度。\n\n"
            + "▍注意\n"
            + "EGL 窗口配置必须有模板位（见 EglCore），否则永远不通过；"
            + "glClear 要带上 GL_STENCIL_BUFFER_BIT，并可用 glClearStencil 设初值。";

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
            + "uniform float u_override;\n"   // 1=纯色描边, 0=用顶点色
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    fragColor = vec4(mix(v_color, u_tint, u_override), 1.0);\n"
            + "}\n";

    private static final String KEY_ON = "outline";
    private static final String KEY_SCALE = "scale";
    private static final String KEY_HUE = "hue";
    private static final String KEY_SPEED = "speed";

    private ShaderProgram mProgram;
    private Mesh mGround;
    private Mesh mCube;
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mMvp = new float[16];
    private float mAngle;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProgram = new ShaderProgram(VS, FS);

        GeoGen.GeoData ground = GeoGen.gridPlane(1, 24f, 1f);
        mGround = new Mesh.Builder()
                .addBuffer(interleave(ground.positions, 0.22f, 0.28f, 0.34f),
                        new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .setIndices(ground.indices)
                .build();

        GeoGen.GeoData cube = GeoGen.cube();
        mCube = new Mesh.Builder()
                .addBuffer(interleave(cube.positions, 0.35f, 0.75f, 0.4f),
                        new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .setIndices(cube.indices)
                .build();
    }

    private static float[] interleave(float[] pos, float r, float g, float b) {
        int n = pos.length / 3;
        float[] out = new float[n * 6];
        for (int i = 0; i < n; i++) {
            out[i * 6] = pos[i * 3];
            out[i * 6 + 1] = pos[i * 3 + 1];
            out[i * 6 + 2] = pos[i * 3 + 2];
            out[i * 6 + 3] = r;
            out[i * 6 + 4] = g;
            out[i * 6 + 5] = b;
        }
        return out;
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        clearFrame(0.06f, 0.08f, 0.12f); // 同时清 stencil=0（基类固定清三种缓冲）
        mAngle += getFloat(KEY_SPEED) * 60f * deltaTime;

        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 50f, aspect(), 0.1f, 50f);
        Matrix.setLookAtM(mView, 0, 4f, 3f, 5f, 0, 1, 0, 0, 1, 0);
        float[] pv = new float[16];
        Matrix.multiplyMM(pv, 0, mProj, 0, mView, 0);

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        mProgram.use();
        mProgram.set("u_tint", 1f, 0.6f, 0.1f);
        mProgram.set("u_override", 0f);

        // 地板：屏蔽模板写入（glStencilMask(0) 让它不污染模板缓冲）
        GLES30.glStencilMask(0x00);
        Matrix.setIdentityM(mModel, 0);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
        mProgram.setMat4("u_mvp", mMvp);
        mGround.draw(GLES30.GL_TRIANGLES);

        // Pass 1：正常画立方体，覆盖区模板写入 1
        GLES30.glEnable(GLES30.GL_STENCIL_TEST);
        GLES30.glStencilFunc(GLES30.GL_ALWAYS, 1, 0xFF);
        GLES30.glStencilOp(GLES30.GL_KEEP, GLES30.GL_KEEP, GLES30.GL_REPLACE);
        GLES30.glStencilMask(0xFF);
        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, 0, 1.05f, 0);
        Matrix.rotateM(mModel, 0, mAngle, 0.3f, 1, 0.2f);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
        mProgram.setMat4("u_mvp", mMvp);
        mCube.draw(GLES30.GL_TRIANGLES);

        // Pass 2+3：描边
        if (getBool(KEY_ON)) {
            float s = getFloat(KEY_SCALE);
            float[] outline = hsvToRgb(getFloat(KEY_HUE), 0.9f, 1f);
            // 只允许模板值 != 1 的片元通过
            GLES30.glStencilFunc(GLES30.GL_NOTEQUAL, 1, 0xFF);
            GLES30.glStencilMask(0x00);   // 禁止回写模板
            GLES30.glDepthMask(false);    // 不污染深度
            Matrix.setIdentityM(mModel, 0);
            Matrix.translateM(mModel, 0, 0, 1.05f, 0);
            Matrix.rotateM(mModel, 0, mAngle, 0.3f, 1, 0.2f);
            Matrix.scaleM(mModel, 0, s, s, s);
            Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
            mProgram.setMat4("u_mvp", mMvp);
            mProgram.set("u_tint", outline[0], outline[1], outline[2]);
            mProgram.set("u_override", 1f);
            mCube.draw(GLES30.GL_TRIANGLES);
            mProgram.set("u_override", 0f);
            GLES30.glDepthMask(true);
        }

        GLES30.glStencilMask(0xFF);
        GLES30.glDisable(GLES30.GL_STENCIL_TEST);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.boolSpec(KEY_ON, "显示描边", true));
        specs.add(ParamSpec.floatSpec(KEY_SCALE, "描边缩放", 1.02f, 1.6f, 1.12f));
        specs.add(ParamSpec.floatSpec(KEY_HUE, "描边颜色", 0f, 1f, 0.08f));
        specs.add(ParamSpec.floatSpec(KEY_SPEED, "旋转速度", -2f, 2f, 0.6f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mGround.dispose();
        mCube.dispose();
        mProgram.release();
    }
}
