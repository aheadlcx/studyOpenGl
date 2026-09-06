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
 * 06 · 变换矩阵：模型矩阵 T*R*S 组合 + 世界坐标轴。
 */
public class D06Transform extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍三个变换矩阵\n"
            + "MVP = Projection × View × Model（列向量约定下从右往左作用到顶点）：\n"
            + "· Model：物体局部空间 → 世界空间（本例演示）\n"
            + "· View：世界空间 → 相机空间（07 课）\n"
            + "· Projection：相机空间 → 裁剪空间，产生透视除法用的 1/w\n\n"
            + "▍模型矩阵的组合顺序不可交换\n"
            + "T·R·S 表示\"先缩放、再旋转、最后平移\"（矩阵作用在列向量上，右边先算）。"
            + "缩放必须在旋转前（否则被斜向拉伸），平移必须在最后（否则旋转会把平移分量转跑）。"
            + "试着同时调大缩放和平移，顺序错了物体会\"绕远处公转\"。\n\n"
            + "▍矩阵细节\n"
            + "· mat4 按列主序存储，android.opengl.Matrix 的布局与 glUniformMatrix4fv 的 "
            + "transpose=GL_FALSE 直接匹配；\n"
            + "· rotateM(angle, x,y,z) 是绕任意轴的罗德里格斯旋转；\n"
            + "· 法线矩阵：非等比缩放时会扭曲法线，严格做法是传 model 的逆转置 mat3"
            + "（只有旋转/等比缩放时直接用 mat3(model) 即可）。\n\n"
            + "▍本例\n"
            + "彩色立方体 + XYZ 坐标轴线（红X绿Y蓝Z），滑条实时改 T/R/S 三个分量，"
            + "直观感受矩阵组合顺序。";

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

    private static final String KEY_PX = "px";
    private static final String KEY_PY = "py";
    private static final String KEY_PZ = "pz";
    private static final String KEY_RX = "rx";
    private static final String KEY_RY = "ry";
    private static final String KEY_SCALE = "scale";
    private static final String KEY_AXIS = "axis";

    private ShaderProgram mProgram;
    private Mesh mCube;
    private Mesh mAxes;
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mMvp = new float[16];
    private float mRotX, mRotY;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProgram = new ShaderProgram(VS, FS);

        GeoGen.GeoData cube = GeoGen.cube();
        float[] verts = interleave(cube.positions, cube.normals, null);
        mCube = new Mesh.Builder()
                .addBuffer(verts, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .setIndices(cube.indices)
                .build();

        mAxes = new Mesh.Builder()
                .addBuffer(new float[]{
                        // X 轴 红色
                        0, 0, 0, 1, 0.2f, 0.2f, 2.2f, 0, 0, 1, 0.2f, 0.2f,
                        // Y 轴 绿色
                        0, 0, 0, 0.2f, 1, 0.2f, 0, 2.2f, 0, 0.2f, 1, 0.2f,
                        // Z 轴 蓝色
                        0, 0, 0, 0.3f, 0.4f, 1, 0, 0, 2.2f, 0.3f, 0.4f, 1
                }, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .build();
    }

    /** pos + color(按面着色) 交错。 */
    private static float[] interleave(float[] pos, float[] normals, float[] unused) {
        int vertCount = pos.length / 3;
        float[] out = new float[vertCount * 6];
        for (int f = 0; f < 6; f++) {
            float r = f == 0 || f == 1 ? 0.85f : 0.15f + f * 0.1f;
            float g = f == 2 || f == 3 ? 0.85f : 0.2f;
            float b = f == 4 || f == 5 ? 0.85f : 0.3f + (5 - f) * 0.1f;
            for (int v = 0; v < 4; v++) {
                int i = f * 4 + v;
                out[i * 6] = pos[i * 3];
                out[i * 6 + 1] = pos[i * 3 + 1];
                out[i * 6 + 2] = pos[i * 3 + 2];
                out[i * 6 + 3] = r;
                out[i * 6 + 4] = g;
                out[i * 6 + 5] = b;
            }
        }
        return out;
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        clearFrame(0.06f, 0.08f, 0.12f);
        mRotX += getFloat(KEY_RX) * 60f * deltaTime;
        mRotY += getFloat(KEY_RY) * 60f * deltaTime;

        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 50f, aspect(), 0.1f, 20f);
        Matrix.setLookAtM(mView, 0,
                3.2f, 2.2f, 4.2f, 0f, 0f, 0f, 0f, 1f, 0f);
        float[] pv = new float[16];
        Matrix.multiplyMM(pv, 0, mProj, 0, mView, 0);

        // Model = T * R * R * S —— 顺序不可交换！
        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0,
                getFloat(KEY_PX), getFloat(KEY_PY), getFloat(KEY_PZ));
        Matrix.rotateM(mModel, 0, mRotX, 1, 0, 0);
        Matrix.rotateM(mModel, 0, mRotY, 0, 1, 0);
        float s = getFloat(KEY_SCALE);
        Matrix.scaleM(mModel, 0, s, s, s);

        mProgram.use();
        mProgram.setMat4("u_mvp", mMvp);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
        mProgram.setMat4("u_mvp", mMvp);
        mCube.draw(GLES30.GL_TRIANGLES);

        if (getBool(KEY_AXIS)) {
            Matrix.setIdentityM(mModel, 0);
            Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
            mProgram.setMat4("u_mvp", mMvp);
            GLES30.glEnable(GLES30.GL_DEPTH_TEST);
            mAxes.draw(GLES30.GL_LINES);
        }
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.floatSpec(KEY_PX, "平移 X (T)", -2f, 2f, 0f));
        specs.add(ParamSpec.floatSpec(KEY_PY, "平移 Y", -1.5f, 1.5f, 0f));
        specs.add(ParamSpec.floatSpec(KEY_PZ, "平移 Z", -2f, 2f, 0f));
        specs.add(ParamSpec.floatSpec(KEY_RX, "旋转速度 X (R)", -2f, 2f, 0.5f));
        specs.add(ParamSpec.floatSpec(KEY_RY, "旋转速度 Y", -2f, 2f, 0.8f));
        specs.add(ParamSpec.floatSpec(KEY_SCALE, "缩放 (S)", 0.2f, 2f, 1f));
        specs.add(ParamSpec.boolSpec(KEY_AXIS, "显示坐标轴", true));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mCube.dispose();
        mAxes.dispose();
        mProgram.release();
    }
}
