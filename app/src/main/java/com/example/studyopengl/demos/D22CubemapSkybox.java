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
 * 22 · 立方体贴图与天空盒：samplerCube 方向采样。
 */
public class D22CubemapSkybox extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍立方体贴图 Cubemap\n"
            + "6 张等尺寸正方形纹理装进一个对象：glBindTexture(GL_TEXTURE_CUBE_MAP, id) 后对 "
            + "GL_TEXTURE_CUBE_MAP_POSITIVE_X..NEGATIVE_Z 依次 glTexImage2D。"
            + "shader 里 samplerCube 不用 uv —— 用一个 3D 方向向量 texture(u_cube, dir)，"
            + "GL 按向量的主轴选面、其余两轴做面内 uv（方向=从立方体中心射出）。\n\n"
            + "▍天空盒三板斧\n"
            + "1) 视图矩阵去掉平移（只用旋转），天空永远\"无限远\"；\n"
            + "2) VS 里 gl_Position = pos.xyww，令 z=w → 深度恒为 1.0（远平面）；\n"
            + "3) glDepthFunc(GL_LEQUAL)（默认 LESS 会把 z==1 的天空剔掉）+ 先画或后画均可。\n\n"
            + "▍采样状态\n"
            + "· wrap 需要 S/T/R 三个方向都是 CLAMP_TO_EDGE（跨面接缝才正确）；\n"
            + "· GLES30.glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS) 消除 mip 跨面接缝（ES3 核心）；\n"
            + "· 环境反射：反射向量 R=reflect(I,N) 直接采 cubemap —— 本例天空盒旋转时观察立方体颜色。";

    private static final String SKY_VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "uniform mat4 u_viewRot;\n"   // 只含旋转的视图矩阵
            + "uniform mat4 u_proj;\n"
            + "uniform float u_rotZ;\n"
            + "out vec3 v_dir;\n"
            + "void main() {\n"
            + "    // 方向 = 本地位置本身（以原点为中心的单位盒）\n"
            + "    float c = cos(u_rotZ), s = sin(u_rotZ);\n"
            + "    v_dir = vec3(a_pos.x * c - a_pos.y * s,\n"
            + "                 a_pos.x * s + a_pos.y * c, a_pos.z);\n"
            + "    vec4 p = u_proj * u_viewRot * vec4(a_pos, 1.0);\n"
            + "    gl_Position = p.xyww;  // z=w -> 深度=1.0，永远最远\n"
            + "}\n";

    private static final String SKY_FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec3 v_dir;\n"
            + "uniform samplerCube u_sky;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    fragColor = texture(u_sky, v_dir); // 方向采样，无 uv\n"
            + "}\n";

    private static final String CUBE_VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec3 a_normal;\n"
            + "uniform mat4 u_mvp;\n"
            + "uniform mat4 u_model;\n"
            + "out vec3 v_normal;\n"
            + "out vec3 v_worldPos;\n"
            + "void main() {\n"
            + "    v_normal = mat3(u_model) * a_normal;\n"
            + "    v_worldPos = (u_model * vec4(a_pos, 1.0)).xyz;\n"
            + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String CUBE_FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec3 v_normal;\n"
            + "in vec3 v_worldPos;\n"
            + "uniform samplerCube u_sky;\n"
            + "uniform vec3 u_viewPos;\n"
            + "uniform float u_reflect;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    vec3 N = normalize(v_normal);\n"
            + "    vec3 I = normalize(v_worldPos - u_viewPos);\n"
            + "    vec3 R = reflect(I, N);\n"
            + "    vec3 envColor = texture(u_sky, R).rgb;\n"
            + "    vec3 base = vec3(0.25, 0.3, 0.4);\n"
            + "    float diff = max(dot(N, normalize(vec3(0.5, 1.0, 0.6))), 0.0);\n"
            + "    vec3 lit = base * (0.4 + 0.6 * diff);\n"
            + "    fragColor = vec4(mix(lit, envColor, u_reflect), 1.0);\n"
            + "}\n";

    private static final String KEY_SKYSPIN = "skySpin";
    private static final String KEY_FOV = "fov";
    private static final String KEY_SHOWCUBE = "cube";
    private static final String KEY_REFLECT = "reflect";

    private ShaderProgram mSkyProgram;
    private ShaderProgram mCubeProgram;
    private Mesh mSkyBox;
    private Mesh mCube;
    private int mSkyTexture;
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mViewRot = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mMvp = new float[16];
    private float mYaw = 30f;
    private float mPitch = 12f;
    private float mLastX, mLastY;
    private float mTime;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mSkyProgram = new ShaderProgram(SKY_VS, SKY_FS);
        mCubeProgram = new ShaderProgram(CUBE_VS, CUBE_FS);

        // 天空盒：单位立方体只有位置属性
        GeoGen.GeoData data = GeoGen.cube();
        mSkyBox = new Mesh.Builder()
                .addBuffer(scalePos(data.positions, 50f), new Mesh.Attrib(0, 3))
                .setIndices(data.indices)
                .build();

        GeoGen.GeoData cube = GeoGen.cube();
        float[] cubeVerts = interleave(cube.positions, cube.normals);
        mCube = new Mesh.Builder()
                .addBuffer(cubeVerts, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .setIndices(cube.indices)
                .build();

        mSkyTexture = TextureHelper.createStarCubemap(256);
        // GL_TEXTURE_CUBE_MAP_SEAMLESS = 0x884F（GLES30 常量表未收录，用字面值）
        GLES30.glEnable(0x884F);
    }

    private static float[] scalePos(float[] pos, float s) {
        float[] out = new float[pos.length];
        for (int i = 0; i < pos.length; i++) {
            out[i] = pos[i] * s;
        }
        return out;
    }

    private static float[] interleave(float[] pos, float[] nrm) {
        int n = pos.length / 3;
        float[] out = new float[n * 6];
        for (int i = 0; i < n; i++) {
            out[i * 6] = pos[i * 3];
            out[i * 6 + 1] = pos[i * 3 + 1];
            out[i * 6 + 2] = pos[i * 3 + 2];
            out[i * 6 + 3] = nrm[i * 3];
            out[i * 6 + 4] = nrm[i * 3 + 1];
            out[i * 6 + 5] = nrm[i * 3 + 2];
        }
        return out;
    }

    @Override
    public void onTouch(int action, float x, float y) {
        if (action == MotionEvent.ACTION_MOVE) {
            mYaw += (x - mLastX) * 0.3f;
            mPitch = Math.max(-60f, Math.min(60f, mPitch + (y - mLastY) * 0.3f));
        }
        mLastX = x;
        mLastY = y;
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        GLES30.glClearColor(0.02f, 0.02f, 0.04f, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        mTime += deltaTime;

        float yaw = (float) Math.toRadians(mYaw);
        float pitch = (float) Math.toRadians(mPitch);
        float r = 7f;
        float ex = r * (float) Math.cos(pitch) * (float) Math.sin(yaw);
        float ey = r * (float) Math.sin(pitch);
        float ez = r * (float) Math.cos(pitch) * (float) Math.cos(yaw);

        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, getFloat(KEY_FOV), aspect(), 0.1f, 200f);
        Matrix.setLookAtM(mView, 0, ex, ey, ez, 0, 0, 0, 0, 1, 0);
        // 去掉平移：把视图矩阵最后一列（平移分量）清零
        System.arraycopy(mView, 0, mViewRot, 0, 16);
        mViewRot[12] = 0;
        mViewRot[13] = 0;
        mViewRot[14] = 0;

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);

        // ---- 天空盒：深度函数 LEQUAL + 深度只读 ----
        GLES30.glDepthFunc(GLES30.GL_LEQUAL);
        GLES30.glDepthMask(false);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, mSkyTexture);
        mSkyProgram.use();
        mSkyProgram.setMat4("u_viewRot", mViewRot);
        mSkyProgram.setMat4("u_proj", mProj);
        mSkyProgram.set("u_sky", 0);
        mSkyProgram.set("u_rotZ", mTime * getFloat(KEY_SKYSPIN));
        mSkyBox.draw(GLES30.GL_TRIANGLES);
        GLES30.glDepthMask(true);

        // ---- 中央立方体：环境反射采样同一张 cubemap ----
        if (getBool(KEY_SHOWCUBE)) {
            mCubeProgram.use();
            mCubeProgram.set("u_sky", 0);
            mCubeProgram.set("u_viewPos", ex, ey, ez);
            mCubeProgram.set("u_reflect", getFloat(KEY_REFLECT));
            Matrix.setIdentityM(mModel, 0);
            Matrix.rotateM(mModel, 0, mTime * 30f, 0.3f, 1, 0.1f);
            Matrix.multiplyMM(mMvp, 0, mProj, 0, mView, 0);
            float[] mvp = new float[16];
            Matrix.multiplyMM(mvp, 0, mMvp, 0, mModel, 0);
            mCubeProgram.setMat4("u_mvp", mvp);
            mCubeProgram.setMat4("u_model", mModel);
            mCube.draw(GLES30.GL_TRIANGLES);
        }

        GLES30.glDepthFunc(GLES30.GL_LESS);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(0x884F); // GL_TEXTURE_CUBE_MAP_SEAMLESS
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.floatSpec(KEY_SKYSPIN, "天空旋转速度", -1f, 1f, 0.1f));
        specs.add(ParamSpec.floatSpec(KEY_FOV, "视场角", 30f, 110f, 60f));
        specs.add(ParamSpec.boolSpec(KEY_SHOWCUBE, "显示中央立方体", true));
        specs.add(ParamSpec.floatSpec(KEY_REFLECT, "环境反射强度", 0f, 1f, 0.7f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mSkyBox.dispose();
        mCube.dispose();
        TextureHelper.deleteTexture(mSkyTexture);
        mSkyProgram.release();
        mCubeProgram.release();
    }
}
