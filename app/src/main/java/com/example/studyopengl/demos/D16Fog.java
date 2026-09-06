package com.example.studyopengl.demos;

import android.opengl.GLES30;
import android.opengl.Matrix;

import com.example.studyopengl.engine.BaseDemoEngine;
import com.example.studyopengl.gl.GeoGen;
import com.example.studyopengl.gl.Mesh;
import com.example.studyopengl.gl.ShaderProgram;
import com.example.studyopengl.gl.TextureHelper;
import com.example.studyopengl.param.ParamSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 16 · 雾效：线性 / EXP / EXP2 三种距离雾。
 */
public class D16Fog extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍雾的作用\n"
            + "① 距离感知（大气透视）；② 掩盖远平面裁剪\"突兀消失\"；③ 渲染性能工具 —— "
            + "远处细节被雾吞掉，可以把 far 拉近、LOD 提前切换。\n\n"
            + "▍三种经典公式（d = 片元到相机距离）\n"
            + "· 线性：factor = (end − d) / (end − start)，clamp 到 [0,1]，start 前全清晰、end 后全雾；\n"
            + "· EXP：factor = 1 − exp(−density·d)，指数衰减，无硬边界；\n"
            + "· EXP2：factor = 1 − exp(−(density·d)²)，衰减更陡，\"实心雾墙\"感更强。\n"
            + "最终 color = mix(物体色, 雾色, factor)。\n\n"
            + "▍实现要点\n"
            + "· 雾必须在光照之后、输出之前混合（本例 FS 末尾）；\n"
            + "· 雾色 = 天空清屏色，否则地平线出现\"接缝\"；\n"
            + "· 距离可以用 length(v_viewPos)（相机空间）或与相机位置的欧氏距离（世界空间）；\n"
            + "· 高度雾/体积雾在此基础上再加 y 或射线步进的项。";

    private static final String VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec2 a_uv;\n"
            + "uniform mat4 u_mvp;\n"
            + "uniform mat4 u_model;\n"
            + "out vec2 v_uv;\n"
            + "out vec3 v_worldPos;\n"
            + "void main() {\n"
            + "    v_uv = a_uv;\n"
            + "    v_worldPos = (u_model * vec4(a_pos, 1.0)).xyz;\n"
            + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec2 v_uv;\n"
            + "in vec3 v_worldPos;\n"
            + "uniform sampler2D u_tex;\n"
            + "uniform vec3 u_cameraPos;\n"
            + "uniform vec3 u_fogColor;\n"
            + "uniform int u_fogType;\n"        // 0无 1线性 2exp 3exp2
            + "uniform float u_density;\n"
            + "uniform float u_fogEnd;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    vec3 color = texture(u_tex, v_uv).rgb;\n"
            + "    float d = length(v_worldPos - u_cameraPos);\n"
            + "    float factor = 0.0;\n"
            + "    if (u_fogType == 1) {\n"
            + "        factor = clamp((u_fogEnd - d) / (u_fogEnd * 0.6), 0.0, 1.0);\n"
            + "    } else if (u_fogType == 2) {\n"
            + "        factor = 1.0 - exp(-u_density * d);\n"
            + "    } else if (u_fogType == 3) {\n"
            + "        factor = 1.0 - exp(-u_density * u_density * d * d);\n"
            + "    }\n"
            + "    fragColor = vec4(mix(color, u_fogColor, factor), 1.0);\n"
            + "}\n";

    private static final String KEY_TYPE = "type";
    private static final String KEY_DENSITY = "density";
    private static final String KEY_END = "end";
    private static final String KEY_SPEED = "speed";

    private static final String[] TYPE_LABELS = {"无雾", "线性 Linear", "指数 EXP", "平方指数 EXP2"};

    private ShaderProgram mProgram;
    private Mesh mGround;
    private Mesh[] mPillars = new Mesh[6];
    private int mTexture;
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mMvp = new float[16];
    private float mScroll;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProgram = new ShaderProgram(VS, FS);

        GeoGen.GeoData ground = GeoGen.gridPlane(64, 120f, 30f);
        float[] verts = new float[(ground.positions.length / 3) * 5];
        for (int i = 0; i < ground.positions.length / 3; i++) {
            verts[i * 5] = ground.positions[i * 3];
            verts[i * 5 + 1] = ground.positions[i * 3 + 1];
            verts[i * 5 + 2] = ground.positions[i * 3 + 2];
            verts[i * 5 + 3] = ground.uvs[i * 2];
            verts[i * 5 + 4] = ground.uvs[i * 2 + 1];
        }
        mGround = new Mesh.Builder()
                .addBuffer(verts, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 2))
                .setIndices(ground.indices)
                .build();

        GeoGen.GeoData cube = GeoGen.cube();
        for (int i = 0; i < mPillars.length; i++) {
            float[] pv = new float[(cube.positions.length / 3) * 5];
            for (int j = 0; j < cube.positions.length / 3; j++) {
                pv[j * 5] = cube.positions[j * 3];
                pv[j * 5 + 1] = cube.positions[j * 3 + 1];
                pv[j * 5 + 2] = cube.positions[j * 3 + 2];
                pv[j * 5 + 3] = cube.uvs[j * 2];
                pv[j * 5 + 4] = cube.uvs[j * 2 + 1];
            }
            mPillars[i] = new Mesh.Builder()
                    .addBuffer(pv, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 2))
                    .setIndices(cube.indices)
                    .build();
        }

        mTexture = TextureHelper.createCheckerTexture(256);
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        float[] fogColor = {0.55f, 0.65f, 0.75f};
        GLES30.glClearColor(fogColor[0], fogColor[1], fogColor[2], 1f);
        GLES30.glViewport(0, 0, mWidth, mHeight);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        mScroll += getFloat(KEY_SPEED) * 8f * deltaTime;

        float camX = 0f, camY = 1.6f, camZ = 10f;
        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 55f, aspect(), 0.1f, 90f);
        Matrix.setLookAtM(mView, 0, camX, camY, camZ, 0, 0.8f, -12f, 0, 1, 0);
        float[] pv = new float[16];
        Matrix.multiplyMM(pv, 0, mProj, 0, mView, 0);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mTexture);

        mProgram.use();
        mProgram.set("u_tex", 0);
        mProgram.set("u_cameraPos", camX, camY, camZ);
        mProgram.set("u_fogColor", fogColor[0], fogColor[1], fogColor[2]);
        mProgram.set("u_fogType", getOptionIndex(KEY_TYPE));
        mProgram.set("u_density", getFloat(KEY_DENSITY));
        mProgram.set("u_fogEnd", getFloat(KEY_END));

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);

        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, 0, -0.5f, -30f);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
        mProgram.setMat4("u_mvp", mMvp);
        mProgram.setMat4("u_model", mModel);
        mGround.draw(GLES30.GL_TRIANGLES);

        // 两排立柱形成纵深
        for (int i = 0; i < 6; i++) {
            float z = -4f - i * 8f + (mScroll % 8f);
            Matrix.setIdentityM(mModel, 0);
            Matrix.translateM(mModel, 0, i % 2 == 0 ? -2.6f : 2.6f, 1.4f, z);
            Matrix.scaleM(mModel, 0, 0.9f, 2.8f, 0.9f);
            Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);
            mProgram.setMat4("u_mvp", mMvp);
            mProgram.setMat4("u_model", mModel);
            mPillars[i].draw(GLES30.GL_TRIANGLES);
        }
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.optionSpec(KEY_TYPE, "雾类型", TYPE_LABELS, 2));
        specs.add(ParamSpec.floatSpec(KEY_DENSITY, "密度 density", 0f, 0.4f, 0.06f));
        specs.add(ParamSpec.floatSpec(KEY_END, "线性雾 end 距离", 5f, 60f, 25f));
        specs.add(ParamSpec.floatSpec(KEY_SPEED, "前进速度", 0f, 2f, 0.4f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mGround.dispose();
        for (Mesh m : mPillars) {
            if (m != null) m.dispose();
        }
        TextureHelper.deleteTexture(mTexture);
        mProgram.release();
    }
}
