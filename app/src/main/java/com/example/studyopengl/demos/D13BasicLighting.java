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
 * 13 · Phong 光照：环境/漫反射/镜面三通道，逐片元计算。
 */
public class D13BasicLighting extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍ADS 光照模型（Phong）\n"
            + "最终颜色 = 环境光 + 漫反射 + 镜面高光，逐片元计算（Phong shading）：\n"
            + "· ambient  = 光颜色 × 材质环境系数 —— 常数打底，避免全黑；\n"
            + "· diffuse  = max(dot(N, L), 0) —— 面朝光源越正越亮，塑造立体感；\n"
            + "· specular = pow(max(dot(R, V), 0), shininess) —— R=reflect(-L,N)，"
            + "V=normalize(cameraPos-pos)，shininess 越大高光越小越锐。\n\n"
            + "▍逐顶点 vs 逐片元\n"
            + "Gouraud（VS 里算光照，插值颜色）便宜但高光会\"糊掉\"；"
            + "Phong（FS 里逐片元算）在移动端已是标配成本。本例是标准逐片元实现。\n\n"
            + "▍工程细节\n"
            + "· 法线随模型矩阵变换时要用法线矩阵（model 的逆转置 mat3）；"
            + "只有旋转+等比缩放时可直接用 mat3(model)，本例如此简化；\n"
            + "· 插值出来的法线长度会偏差，FS 里必须重新 normalize()；\n"
            + "· 光源位置放 uniform（世界空间），相机位置也要传 uniform（eye 坐标）；\n"
            + "· 点光衰减 attenuation = 1/(kc + kl·d + kq·d²) 属于可选第四通道。\n\n"
            + "▍调参建议\n"
            + "镜面强度拉到 0 观察纯漫反射；shininess 从 8 拉到 128 观察高光收敛；"
            + "环境光拉满会\"洗白\"失去立体感 —— 真实项目环境光一般 0.05~0.2。";

    private static final String VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec3 a_normal;\n"
            + "uniform mat4 u_mvp;\n"
            + "uniform mat4 u_model;\n"
            + "out vec3 v_normal;\n"
            + "out vec3 v_worldPos;\n"
            + "void main() {\n"
            + "    v_normal = mat3(u_model) * a_normal;\n" // 简化法线矩阵
            + "    v_worldPos = (u_model * vec4(a_pos, 1.0)).xyz;\n"
            + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec3 v_normal;\n"
            + "in vec3 v_worldPos;\n"
            + "uniform vec3 u_lightPos;\n"
            + "uniform vec3 u_viewPos;\n"
            + "uniform vec3 u_lightColor;\n"
            + "uniform float u_ambient;\n"
            + "uniform float u_diffuse;\n"
            + "uniform float u_specular;\n"
            + "uniform float u_shininess;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    vec3 N = normalize(v_normal);\n"
            + "    vec3 L = normalize(u_lightPos - v_worldPos);\n"
            + "    vec3 V = normalize(u_viewPos - v_worldPos);\n"
            + "    vec3 R = reflect(-L, N);\n"
            + "    vec3 objectColor = vec3(0.9, 0.45, 0.25);\n"
            + "    vec3 ambient  = u_ambient * u_lightColor;\n"
            + "    float diff = max(dot(N, L), 0.0);\n"
            + "    vec3 diffuse  = u_diffuse * diff * u_lightColor;\n"
            + "    float spec = pow(max(dot(R, V), 0.0), u_shininess);\n"
            + "    vec3 specular = u_specular * spec * u_lightColor;\n"
            + "    fragColor = vec4((ambient + diffuse + specular) * objectColor, 1.0);\n"
            + "}\n";

    private static final String KEY_AMBIENT = "ambient";
    private static final String KEY_DIFFUSE = "diffuse";
    private static final String KEY_SPECULAR = "specular";
    private static final String KEY_SHINE = "shininess";
    private static final String KEY_HUE = "hue";
    private static final String KEY_ORBIT = "orbit";

    private ShaderProgram mProgram;
    private ShaderProgram mLightProgram;
    private Mesh mCube;
    private Mesh mGround;
    private Mesh mLightBox;
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mMvp = new float[16];
    private float mTime;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProgram = new ShaderProgram(VS, FS);

        // 光源用纯色 unlit 着色器
        mLightProgram = new ShaderProgram(
                "#version 300 es\n"
                        + "layout(location=0) in vec3 a_pos;\n"
                        + "uniform mat4 u_mvp;\n"
                        + "void main() { gl_Position = u_mvp * vec4(a_pos, 1.0); }\n",
                "#version 300 es\n"
                        + "precision mediump float;\n"
                        + "uniform vec3 u_color;\n"
                        + "out vec4 fragColor;\n"
                        + "void main() { fragColor = vec4(u_color, 1.0); }\n");

        GeoGen.GeoData cube = GeoGen.cube();
        float[] cubeVerts = interleave(cube.positions, cube.normals, null);
        mCube = new Mesh.Builder()
                .addBuffer(cubeVerts, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .setIndices(cube.indices)
                .build();

        GeoGen.GeoData ground = GeoGen.gridPlane(1, 30f, 1f);
        float[] gVerts = interleave(ground.positions, ground.normals, null);
        mGround = new Mesh.Builder()
                .addBuffer(gVerts, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .setIndices(ground.indices)
                .build();

        GeoGen.GeoData lightCube = GeoGen.cube();
        mLightBox = new Mesh.Builder()
                .addBuffer(interleave(lightCube.positions, lightCube.normals, null),
                        new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .setIndices(lightCube.indices)
                .build();
    }

    private static float[] interleave(float[] pos, float[] normal, float[] unused) {
        int n = pos.length / 3;
        float[] out = new float[n * 6];
        for (int i = 0; i < n; i++) {
            out[i * 6] = pos[i * 3];
            out[i * 6 + 1] = pos[i * 3 + 1];
            out[i * 6 + 2] = pos[i * 3 + 2];
            out[i * 6 + 3] = normal[i * 3];
            out[i * 6 + 4] = normal[i * 3 + 1];
            out[i * 6 + 5] = normal[i * 3 + 2];
        }
        return out;
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        clearFrame(0.06f, 0.08f, 0.12f);
        mTime += deltaTime;

        float orbit = getFloat(KEY_ORBIT) * 2f;
        float lx = (float) Math.sin(mTime * orbit) * 4.5f;
        float lz = (float) Math.cos(mTime * orbit) * 4.5f;
        float ly = 2.5f + (float) Math.sin(mTime * 0.7) * 1.2f;

        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 50f, aspect(), 0.1f, 60f);
        Matrix.setLookAtM(mView, 0, 5f, 4f, 6f, 0, 0.5f, 0, 0, 1, 0);
        float[] pv = new float[16];
        Matrix.multiplyMM(pv, 0, mProj, 0, mView, 0);

        float[] lightColor = hsvToRgb(getFloat(KEY_HUE), 0.35f, 1f);

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        mProgram.use();
        mProgram.set("u_lightPos", lx, ly, lz);
        mProgram.set("u_viewPos", 5f, 4f, 6f);
        mProgram.set("u_lightColor", lightColor[0], lightColor[1], lightColor[2]);
        mProgram.set("u_ambient", getFloat(KEY_AMBIENT));
        mProgram.set("u_diffuse", getFloat(KEY_DIFFUSE));
        mProgram.set("u_specular", getFloat(KEY_SPECULAR));
        mProgram.set("u_shininess", getFloat(KEY_SHINE));

        // 主立方体
        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, 0, 0.75f, 0);
        Matrix.rotateM(mModel, 0, mTime * 30f, 0.2f, 1, 0.1f);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
        mProgram.setMat4("u_mvp", mMvp);
        mProgram.setMat4("u_model", mModel);
        mCube.draw(GLES30.GL_TRIANGLES);

        // 地面
        Matrix.setIdentityM(mModel, 0);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
        mProgram.setMat4("u_mvp", mMvp);
        mProgram.setMat4("u_model", mModel);
        mGround.draw(GLES30.GL_TRIANGLES);

        // 光源指示（unlit 小方块）
        mLightProgram.use();
        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, lx, ly, lz);
        Matrix.scaleM(mModel, 0, 0.18f, 0.18f, 0.18f);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
        mLightProgram.setMat4("u_mvp", mMvp);
        mLightProgram.set("u_color", lightColor[0], lightColor[1], lightColor[2]);
        mLightBox.draw(GLES30.GL_TRIANGLES);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.floatSpec(KEY_AMBIENT, "环境光强度", 0f, 1f, 0.15f));
        specs.add(ParamSpec.floatSpec(KEY_DIFFUSE, "漫反射强度", 0f, 2f, 0.9f));
        specs.add(ParamSpec.floatSpec(KEY_SPECULAR, "镜面强度", 0f, 2f, 0.6f));
        specs.add(ParamSpec.floatSpec(KEY_SHINE, "反光度 shininess", 2f, 128f, 32f, "%.0f"));
        specs.add(ParamSpec.floatSpec(KEY_HUE, "光源颜色", 0f, 1f, 0.12f));
        specs.add(ParamSpec.floatSpec(KEY_ORBIT, "光源环绕速度", 0f, 2f, 0.5f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mCube.dispose();
        mGround.dispose();
        mLightBox.dispose();
        mProgram.release();
        mLightProgram.release();
    }
}
