package com.example.studyopengl.demos;

import android.opengl.GLES30;
import android.opengl.Matrix;
import android.view.MotionEvent;

import com.example.studyopengl.engine.BaseDemoEngine;
import com.example.studyopengl.gl.GeoGen;
import com.example.studyopengl.gl.Mesh;
import com.example.studyopengl.gl.ShaderProgram;
import com.example.studyopengl.gl.TextureHelper;
import com.example.studyopengl.param.ParamSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 07 · 相机与透视投影：fov/near/far 对画面与深度精度的影响，触摸轨道相机。
 */
public class D07Camera extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍视图矩阵 View\n"
            + "相机没有\"矩阵\"，视图矩阵是相机世界变换的逆：setLookAtM(eye, center, up) "
            + "构造的矩阵把整个世界反向移动。天空盒之所以要\"去掉平移\"，"
            + "就是因为平移分量会被逆进去。\n\n"
            + "▍透视投影 Projection\n"
            + "perspectiveM(fovy, aspect, near, far) 定义一个视锥台：\n"
            + "· fovy：垂直视场角。调大 = 广角畸变（边缘拉伸）；调小 = 长焦压缩；\n"
            + "· aspect：宽高比，必须跟随 surface 尺寸，否则画面被拉伸；\n"
            + "· near/far：裁剪面。顶点 z 超出 [-w, w] 的部分被裁掉 —— "
            + "把 near 拉小再贴近物体可以直接\"看穿\"近处三角形。\n\n"
            + "▍深度非线性（重要！）\n"
            + "透视投影后设备深度 = f(z) 是 1/z 型曲线：near 附近精度极高，far 附近急剧变差。"
            + "near/far 比值越大精度越差 —— 这就是\"z-fighting 要拉近 far 或抬 near\"的原理。"
            + "深度可视化见 20 课（RTT 深度可视化开关）。\n\n"
            + "▍触摸轨道相机\n"
            + "本例把触摸拖动映射为 yaw/pitch，再换成 setLookAtM 的 eye 坐标；"
            + "触摸事件从 UI 线程经 GLThread.postTouch 投递，GL 线程帧内无锁消费。";

    private static final String VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec2 a_uv;\n"
            + "uniform mat4 u_mvp;\n"
            + "out vec2 v_uv;\n"
            + "void main() {\n"
            + "    v_uv = a_uv;\n"
            + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec2 v_uv;\n"
            + "uniform sampler2D u_tex;\n"
            + "uniform vec3 u_tint;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    fragColor = vec4(texture(u_tex, v_uv).rgb * u_tint, 1.0);\n"
            + "}\n";

    private static final String KEY_FOV = "fov";
    private static final String KEY_NEAR = "near";
    private static final String KEY_FAR = "far";
    private static final String KEY_ORBIT = "orbit";

    private ShaderProgram mProgram;
    private Mesh mGround;
    private Mesh mCube;
    private int mTexture;
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mMvp = new float[16];
    private float mYaw = 35f;
    private float mPitch = 25f;
    private float mLastTouchX, mLastTouchY;
    private boolean mDragging;
    private float mAutoYaw;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProgram = new ShaderProgram(VS, FS);
        mTexture = TextureHelper.createLogoTexture(256);

        GeoGen.GeoData cube = GeoGen.cube();
        float[] cubeVerts = new float[(cube.positions.length / 3) * 5];
        for (int i = 0; i < cube.positions.length / 3; i++) {
            cubeVerts[i * 5] = cube.positions[i * 3];
            cubeVerts[i * 5 + 1] = cube.positions[i * 3 + 1];
            cubeVerts[i * 5 + 2] = cube.positions[i * 3 + 2];
            cubeVerts[i * 5 + 3] = cube.uvs[i * 2];
            cubeVerts[i * 5 + 4] = cube.uvs[i * 2 + 1];
        }
        mCube = new Mesh.Builder()
                .addBuffer(cubeVerts, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 2))
                .setIndices(cube.indices)
                .build();

        GeoGen.GeoData ground = GeoGen.gridPlane(64, 40f, 10f);
        float[] gVerts = new float[(ground.positions.length / 3) * 5];
        for (int i = 0; i < ground.positions.length / 3; i++) {
            gVerts[i * 5] = ground.positions[i * 3];
            gVerts[i * 5 + 1] = ground.positions[i * 3 + 1];
            gVerts[i * 5 + 2] = ground.positions[i * 3 + 2];
            gVerts[i * 5 + 3] = ground.uvs[i * 2];
            gVerts[i * 5 + 4] = ground.uvs[i * 2 + 1];
        }
        mGround = new Mesh.Builder()
                .addBuffer(gVerts, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 2))
                .setIndices(ground.indices)
                .build();
    }

    @Override
    public void onTouch(int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN) {
            mDragging = true;
            mLastTouchX = x;
            mLastTouchY = y;
        } else if (action == MotionEvent.ACTION_MOVE && mDragging) {
            mYaw += (x - mLastTouchX) * 0.3f;
            mPitch = clamp(mPitch + (y - mLastTouchY) * 0.3f, 2f, 80f);
            mLastTouchX = x;
            mLastTouchY = y;
        } else if (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL) {
            mDragging = false;
        }
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : v > max ? max : v;
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        clearFrame(0.35f, 0.55f, 0.75f);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);

        mAutoYaw += getFloat(KEY_ORBIT) * 20f * deltaTime;
        float yaw = (float) Math.toRadians(mYaw + mAutoYaw);
        float pitch = (float) Math.toRadians(mPitch);
        float radius = 14f;
        float eyeX = radius * (float) Math.cos(pitch) * (float) Math.sin(yaw);
        float eyeY = radius * (float) Math.sin(pitch);
        float eyeZ = radius * (float) Math.cos(pitch) * (float) Math.cos(yaw);

        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, getFloat(KEY_FOV), aspect(),
                getFloat(KEY_NEAR), getFloat(KEY_FAR));
        Matrix.setLookAtM(mView, 0, eyeX, eyeY, eyeZ, 0, 0, 0, 0, 1, 0);
        float[] pv = new float[16];
        Matrix.multiplyMM(pv, 0, mProj, 0, mView, 0);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mTexture);
        mProgram.use();
        mProgram.set("u_tex", 0);

        Matrix.setIdentityM(mModel, 0);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
        mProgram.setMat4("u_mvp", mMvp);
        mProgram.set("u_tint", 0.85f, 0.9f, 1f);
        mGround.draw(GLES30.GL_TRIANGLES);

        // 5x5 立方体阵，各自不同 tint
        int n = 5;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                Matrix.setIdentityM(mModel, 0);
                Matrix.translateM(mModel, 0,
                        (i - n / 2) * 2.4f, 0.6f, (j - n / 2) * 2.4f);
                float hue = (i * n + j) / (float) (n * n);
                float[] tint = hsvToRgb(hue, 0.6f, 1f);
                mProgram.set("u_tint", tint[0], tint[1], tint[2]);
                Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
                mProgram.setMat4("u_mvp", mMvp);
                mCube.draw(GLES30.GL_TRIANGLES);
            }
        }
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.floatSpec(KEY_FOV, "视场角 fovy(度)", 15f, 120f, 50f));
        specs.add(ParamSpec.floatSpec(KEY_NEAR, "近平面 near", 0.05f, 3f, 0.5f));
        specs.add(ParamSpec.floatSpec(KEY_FAR, "远平面 far", 10f, 80f, 40f));
        specs.add(ParamSpec.floatSpec(KEY_ORBIT, "自动旋转速度", -2f, 2f, 0f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mCube.dispose();
        mGround.dispose();
        TextureHelper.deleteTexture(mTexture);
        mProgram.release();
    }
}
