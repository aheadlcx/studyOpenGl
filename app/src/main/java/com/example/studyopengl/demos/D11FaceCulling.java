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
 * 11 · 面剔除：绕序、glFrontFace、glCullFace。
 */
public class D11FaceCulling extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍绕序（winding order）\n"
            + "把三角形投影到屏幕后，按顶点顺序计算有向面积：逆时针 CCW / 顺时针 CW。"
            + "本工程的几何全部按\"从外部看 CCW\"生成（front face 默认 GL_CCW）。\n\n"
            + "▍剔除开关\n"
            + "glEnable(GL_CULL_FACE) 后，背向面在光栅化前就被丢弃：\n"
            + "· glCullFace(GL_BACK)：剔除背面（默认），封闭物体可省约一半片元；\n"
            + "· glCullFace(GL_FRONT)：剔除正面 —— 本例会呈现\"里外翻转\"的奇怪画面；\n"
            + "· glCullFace(GL_FRONT_AND_BACK)：全剔除，物体消失；\n"
            + "· glFrontFace(GL_CW)：把\"正面\"定义改成 CW，绕序约定反转。\n\n"
            + "▍剔除发生在哪个阶段\n"
            + "面剔除在图元装配后、光栅化前 —— 比\"画了再被深度测试丢弃\"省掉整批片元着色，"
            + "所以不透明封闭体永远应该开剔除。注意它剔除的是\"三角形\"，GL_LINES/POINTS 不受影响。\n\n"
            + "▍高频 bug 来源\n"
            + "· 负值缩放（scale.x = -1）镜像翻转绕序，物体突然\"消失\"；\n"
            + "· 手写顶点数据绕序错了几个面，某些角度穿帮；\n"
            + "· 从其他引擎导入模型绕序相反 —— 用 glFrontFace 切换而不是翻转数据。";

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
            + "out vec4 fragColor;\n"
            + "void main() { fragColor = vec4(v_color, 1.0); }\n";

    private static final String KEY_CULL = "cull";
    private static final String KEY_WINDING = "winding";
    private static final String KEY_SPEED = "speed";

    private static final String[] CULL_LABELS = {
            "关闭剔除", "GL_BACK（常用）", "GL_FRONT", "GL_FRONT_AND_BACK"
    };
    private static final String[] WINDING_LABELS = {"正面 = CCW（默认）", "正面 = CW"};

    private ShaderProgram mProgram;
    private Mesh mCube;
    private Mesh mSphere;
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mMvp = new float[16];
    private float mAngle;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProgram = new ShaderProgram(VS, FS);

        GeoGen.GeoData cube = GeoGen.cube();
        mCube = new Mesh.Builder()
                .addBuffer(faceColored(cube.positions),
                        new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .setIndices(cube.indices)
                .build();

        GeoGen.GeoData sphere = GeoGen.sphere(24, 32, 0.55f);
        mSphere = new Mesh.Builder()
                .addBuffer(interleave(sphere.positions, 0.9f, 0.6f, 0.2f),
                        new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .setIndices(sphere.indices)
                .build();
    }

    private static float[] faceColored(float[] pos) {
        int n = pos.length / 3;
        float[] out = new float[n * 6];
        float[][] faceColor = {
                {0.9f, 0.3f, 0.3f}, {0.3f, 0.9f, 0.4f},
                {0.35f, 0.5f, 0.95f}, {0.95f, 0.8f, 0.2f},
                {0.8f, 0.35f, 0.9f}, {0.3f, 0.85f, 0.85f}
        };
        for (int i = 0; i < n; i++) {
            int face = i / 4;
            out[i * 6] = pos[i * 3];
            out[i * 6 + 1] = pos[i * 3 + 1];
            out[i * 6 + 2] = pos[i * 3 + 2];
            out[i * 6 + 3] = faceColor[face][0];
            out[i * 6 + 4] = faceColor[face][1];
            out[i * 6 + 5] = faceColor[face][2];
        }
        return out;
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
        clearFrame(0.06f, 0.08f, 0.12f);
        mAngle += getFloat(KEY_SPEED) * 60f * deltaTime;

        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 50f, aspect(), 0.1f, 20f);
        Matrix.setLookAtM(mView, 0, 2.6f, 1.8f, 3.4f, 0, 0, 0, 0, 1, 0);
        float[] pv = new float[16];
        Matrix.multiplyMM(pv, 0, mProj, 0, mView, 0);

        int cull = getOptionIndex(KEY_CULL);
        if (cull != 0) {
            GLES30.glEnable(GLES30.GL_CULL_FACE);
            GLES30.glCullFace(cull == 1 ? GLES30.GL_BACK
                    : cull == 2 ? GLES30.GL_FRONT
                    : GLES30.GL_FRONT_AND_BACK);
        }
        GLES30.glFrontFace(getOptionIndex(KEY_WINDING) == 0
                ? GLES30.GL_CCW : GLES30.GL_CW);

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        mProgram.use();

        Matrix.setIdentityM(mModel, 0);
        Matrix.rotateM(mModel, 0, mAngle, 0.4f, 1, 0.2f);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
        mProgram.setMat4("u_mvp", mMvp);
        mCube.draw(GLES30.GL_TRIANGLES);

        Matrix.setIdentityM(mModel, 0);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
        mProgram.setMat4("u_mvp", mMvp);
        mSphere.draw(GLES30.GL_TRIANGLES);

        GLES30.glFrontFace(GLES30.GL_CCW);
        if (cull != 0) {
            GLES30.glDisable(GLES30.GL_CULL_FACE);
        }
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.optionSpec(KEY_CULL, "剔除面 glCullFace", CULL_LABELS, 1));
        specs.add(ParamSpec.optionSpec(KEY_WINDING, "绕序 glFrontFace", WINDING_LABELS, 0));
        specs.add(ParamSpec.floatSpec(KEY_SPEED, "旋转速度", -2f, 2f, 0.5f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mCube.dispose();
        mSphere.dispose();
        mProgram.release();
    }
}
