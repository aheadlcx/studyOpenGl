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
 * 14 · 材质与光照贴图：漫反射/镜面/自发光三张贴图，多纹理单元。
 */
public class D14LightMaps extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍材质 = 一组参数 + 一组贴图\n"
            + "把 Phong 的系数升级为贴图采样：\n"
            + "· diffuse map：基础颜色（本例程序化砖墙）；\n"
            + "· specular map：逐纹素高光强度（白噪点图），砖面反光、灰浆不反光；\n"
            + "· emission map：自发光（条纹图），不受光照影响的\"亮起\"部分。\n"
            + "材质用 GLSL struct 组织：struct Material { sampler2D diffuse; sampler2D specular; float shininess; };\n\n"
            + "▍多纹理单元绑定流程\n"
            + "1) glActiveTexture(GL_TEXTURE0) → glBindTexture(漫反射)\n"
            + "2) glActiveTexture(GL_TEXTURE1) → glBindTexture(镜面)\n"
            + "3) glActiveTexture(GL_TEXTURE2) → glBindTexture(自发光)\n"
            + "4) glUniform1i(loc(u_material.diffuse), 0)… 告诉 sampler 各自读哪个单元\n"
            + "单元数量查询 GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS（ES3 下限 32）。\n\n"
            + "▍经典坑\n"
            + "· 忘记 glUniform1i，sampler 默认单元 0 —— 三张贴图全采成同一张；\n"
            + "· 切换单元后忘记切回 GL_TEXTURE0，下一帧绑定错乱；\n"
            + "· 采样后忘记归一化颜色空间，gamma 不一致画面发灰。\n\n"
            + "▍本例\n"
            + "三张贴图全部 Canvas 程序化生成，滑条实时改 shininess / 镜面强度 / 自发光强度 / uv 缩放，"
            + "光源自动环绕照亮砖墙。";

    private static final String FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "struct Material {\n"
            + "    sampler2D diffuse;\n"
            + "    sampler2D specular;\n"
            + "    sampler2D emission;\n"
            + "    float shininess;\n"
            + "};\n"
            + "uniform Material u_material;\n"
            + "uniform vec3 u_lightPos;\n"
            + "uniform vec3 u_viewPos;\n"
            + "uniform vec3 u_lightColor;\n"
            + "uniform float u_specStrength;\n"
            + "uniform float u_emisStrength;\n"
            + "in vec3 v_normal;\n"
            + "in vec3 v_worldPos;\n"
            + "in vec2 v_uv;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    vec3 N = normalize(v_normal);\n"
            + "    vec3 L = normalize(u_lightPos - v_worldPos);\n"
            + "    vec3 V = normalize(u_viewPos - v_worldPos);\n"
            + "    vec3 R = reflect(-L, N);\n"
            + "    vec3 diffuseTex = texture(u_material.diffuse, v_uv).rgb;\n"
            + "    float specMask = texture(u_material.specular, v_uv).r;\n"
            + "    vec3 emission = texture(u_material.emission, v_uv).rgb;\n"
            + "    vec3 ambient = 0.12 * u_lightColor;\n"
            + "    float diff = max(dot(N, L), 0.0);\n"
            + "    vec3 diffuse = diff * u_lightColor * diffuseTex;\n"
            + "    float spec = pow(max(dot(R, V), 0.0), u_material.shininess);\n"
            + "    vec3 specular = u_specStrength * spec * specMask * u_lightColor;\n"
            + "    vec3 color = ambient + diffuse + specular + emission * u_emisStrength;\n"
            + "    fragColor = vec4(color, 1.0);\n"
            + "}\n";

    private static final String VS_HEADER_FIX = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec3 a_normal;\n"
            + "layout(location=2) in vec2 a_uv;\n"
            + "uniform mat4 u_mvp;\n"
            + "uniform mat4 u_model;\n"
            + "uniform float u_uvScale;\n"
            + "out vec3 v_normal;\n"
            + "out vec3 v_worldPos;\n"
            + "out vec2 v_uv;\n"
            + "void main() {\n"
            + "    v_normal = mat3(u_model) * a_normal;\n"
            + "    v_worldPos = (u_model * vec4(a_pos, 1.0)).xyz;\n"
            + "    v_uv = a_uv * u_uvScale;\n"
            + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String KEY_SHINE = "shininess";
    private static final String KEY_SPEC = "spec";
    private static final String KEY_EMISSION = "emission";
    private static final String KEY_UVSCALE = "uvScale";
    private static final String KEY_ORBIT = "orbit";

    private ShaderProgram mProgram;
    private Mesh mWall;
    private int mDiffuseTex;
    private int mSpecularTex;
    private int mEmissionTex;
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mMvp = new float[16];
    private float mTime;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProgram = new ShaderProgram(VS_HEADER_FIX, FS);

        GeoGen.GeoData cube = GeoGen.cube();
        int vertCount = cube.positions.length / 3;
        float[] verts = new float[vertCount * 8];
        for (int i = 0; i < vertCount; i++) {
            verts[i * 8] = cube.positions[i * 3];
            verts[i * 8 + 1] = cube.positions[i * 3 + 1];
            verts[i * 8 + 2] = cube.positions[i * 3 + 2];
            verts[i * 8 + 3] = cube.normals[i * 3];
            verts[i * 8 + 4] = cube.normals[i * 3 + 1];
            verts[i * 8 + 5] = cube.normals[i * 3 + 2];
            verts[i * 8 + 6] = cube.uvs[i * 2];
            verts[i * 8 + 7] = cube.uvs[i * 2 + 1];
        }
        mWall = new Mesh.Builder()
                .addBuffer(verts, new Mesh.Attrib(0, 3),
                        new Mesh.Attrib(1, 3), new Mesh.Attrib(2, 2))
                .setIndices(cube.indices)
                .build();

        mDiffuseTex = TextureHelper.createBrickTexture(512, 512);
        mSpecularTex = TextureHelper.createNoiseTexture(256, 256, 42);
        mEmissionTex = TextureHelper.createStripesTexture(256);
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        clearFrame(0.06f, 0.08f, 0.12f);
        mTime += deltaTime * getFloat(KEY_ORBIT);

        float lx = (float) Math.sin(mTime * 0.8) * 4f;
        float lz = (float) Math.cos(mTime * 0.8) * 4f;

        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 45f, aspect(), 0.1f, 30f);
        Matrix.setLookAtM(mView, 0, 0, 0.6f, 5.5f, 0, 0, 0, 0, 1, 0);
        float[] pv = new float[16];
        Matrix.multiplyMM(pv, 0, mProj, 0, mView, 0);

        Matrix.setIdentityM(mModel, 0);
        Matrix.rotateM(mModel, 0, -20f, 0, 1, 0);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);

        // 三个纹理单元
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mDiffuseTex);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mSpecularTex);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mEmissionTex);

        mProgram.use();
        mProgram.set("u_material.diffuse", 0);
        mProgram.set("u_material.specular", 1);
        mProgram.set("u_material.emission", 2);
        mProgram.set("u_material.shininess", getFloat(KEY_SHINE));
        mProgram.set("u_lightPos", lx, 2.5f, lz);
        mProgram.set("u_viewPos", 0f, 0.6f, 5.5f);
        mProgram.set("u_lightColor", 1f, 0.96f, 0.88f);
        mProgram.set("u_specStrength", getFloat(KEY_SPEC));
        mProgram.set("u_emisStrength", getFloat(KEY_EMISSION));
        mProgram.set("u_uvScale", getFloat(KEY_UVSCALE));
        mProgram.setMat4("u_mvp", mMvp);
        mProgram.setMat4("u_model", mModel);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        mWall.draw(GLES30.GL_TRIANGLES);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0); // 恢复默认单元
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.floatSpec(KEY_SHINE, "反光度 shininess", 2f, 128f, 48f, "%.0f"));
        specs.add(ParamSpec.floatSpec(KEY_SPEC, "镜面贴图强度", 0f, 2f, 0.8f));
        specs.add(ParamSpec.floatSpec(KEY_EMISSION, "自发光强度", 0f, 2f, 0.4f));
        specs.add(ParamSpec.floatSpec(KEY_UVSCALE, "UV 缩放", 0.5f, 4f, 1.5f));
        specs.add(ParamSpec.floatSpec(KEY_ORBIT, "光源环绕速度", 0f, 2f, 0.6f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mWall.dispose();
        TextureHelper.deleteTexture(mDiffuseTex);
        TextureHelper.deleteTexture(mSpecularTex);
        TextureHelper.deleteTexture(mEmissionTex);
        mProgram.release();
    }
}
