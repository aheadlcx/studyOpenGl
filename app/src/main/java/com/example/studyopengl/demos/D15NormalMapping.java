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
 * 15 · 法线贴图与 TBN：切线空间光照。
 */
public class D15NormalMapping extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍法线贴图的意义\n"
            + "把高频几何细节（砖缝、凹凸）烘焙成\"逐纹素法线\"，用低面数网格获得高细节光照。"
            + "贴图 RGB = 切线空间法线 × 0.5 + 0.5（128,128,255 紫蓝色=未扰动 +Z）。\n\n"
            + "▍切线空间与 TBN\n"
            + "每个顶点建一个局部坐标系：T(切线,uv 增长方向)、B(副切线)、N(法线)。"
            + "平面网格 T=(1,0,0)、B=(0,1,0)；通用网格由 uv 差分求 T："
            + "T = normalize(dP1×(dv2·dP3) 方差式)，光照时把 L/V 变进切线空间做点积，"
            + "或者反过来把采样法线乘 TBN 变到世界空间（本例后者，光源可动）。\n\n"
            + "▍强度扰动\n"
            + "sampled.xy *= normalStrength 后 normalize —— 滑条 0=无效果，越大凹凸越深；"
            + "真实项目用 sRGB 关闭的压缩纹理格式存储。\n\n"
            + "▍工程注意\n"
            + "· TBN 必须正交化（Gram-Schmidt），否则光照方向偏移；\n"
            + "· 镜像 UV 会导致手性翻转，mikktspace 导出会自动翻转切线 w 分量；\n"
            + "· 法线贴图不要开 sRGB，它是数据纹理不是颜色纹理。";

    private static final String VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec3 a_normal;\n"
            + "layout(location=2) in vec2 a_uv;\n"
            + "uniform mat4 u_mvp;\n"
            + "uniform mat4 u_model;\n"
            + "out vec3 v_worldPos;\n"
            + "out vec2 v_uv;\n"
            + "out mat3 v_tbn;\n"
            + "void main() {\n"
            + "    v_worldPos = (u_model * vec4(a_pos, 1.0)).xyz;\n"
            + "    v_uv = a_uv;\n"
            + "    // 平面墙的固定切线基（真实模型应由导出工具提供切线属性）\n"
            + "    vec3 N = normalize(mat3(u_model) * a_normal);\n"
            + "    vec3 T = normalize(mat3(u_model) * vec3(1.0, 0.0, 0.0));\n"
            + "    vec3 B = cross(N, T);\n"
            + "    v_tbn = mat3(T, B, N);\n"
            + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec3 v_worldPos;\n"
            + "in vec2 v_uv;\n"
            + "in mat3 v_tbn;\n"
            + "uniform sampler2D u_diffuse;\n"
            + "uniform sampler2D u_normalMap;\n"
            + "uniform vec3 u_lightPos;\n"
            + "uniform vec3 u_viewPos;\n"
            + "uniform float u_normalStrength;\n"
            + "uniform float u_specular;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    vec3 albedo = texture(u_diffuse, v_uv).rgb;\n"
            + "    vec3 sampled = texture(u_normalMap, v_uv).rgb * 2.0 - 1.0;\n"
            + "    sampled.xy *= u_normalStrength;\n"
            + "    vec3 N = normalize(v_tbn * normalize(sampled));\n"
            + "    vec3 L = normalize(u_lightPos - v_worldPos);\n"
            + "    vec3 V = normalize(u_viewPos - v_worldPos);\n"
            + "    vec3 ambient = 0.15 * albedo;\n"
            + "    float diff = max(dot(N, L), 0.0);\n"
            + "    vec3 diffuse = diff * vec3(1.0) * albedo;\n"
            + "    vec3 R = reflect(-L, N);\n"
            + "    float spec = pow(max(dot(R, V), 0.0), 32.0);\n"
            + "    vec3 specular = u_specular * spec * vec3(1.0);\n"
            + "    fragColor = vec4(ambient + diffuse + specular, 1.0);\n"
            + "}\n";

    private static final String KEY_STRENGTH = "strength";
    private static final String KEY_SPEC = "spec";
    private static final String KEY_ORBIT = "orbit";
    private static final String KEY_LIGHT_Z = "lightZ";

    private ShaderProgram mProgram;
    private Mesh mWall;
    private int mDiffuseTex;
    private int mNormalTex;
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mMvp = new float[16];
    private float mTime;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProgram = new ShaderProgram(VS, FS);

        // 前墙：XY 平面（朝 +Z），细分成 4x4 保持插值良好
        GeoGen.GeoData plane = planeWall(6f, 4f, 4, 3);
        int vertCount = plane.positions.length / 3;
        float[] verts = new float[vertCount * 8];
        for (int i = 0; i < vertCount; i++) {
            verts[i * 8] = plane.positions[i * 3];
            verts[i * 8 + 1] = plane.positions[i * 3 + 1];
            verts[i * 8 + 2] = plane.positions[i * 3 + 2];
            verts[i * 8 + 3] = plane.normals[i * 3];
            verts[i * 8 + 4] = plane.normals[i * 3 + 1];
            verts[i * 8 + 5] = plane.normals[i * 3 + 2];
            verts[i * 8 + 6] = plane.uvs[i * 2];
            verts[i * 8 + 7] = plane.uvs[i * 2 + 1];
        }
        mWall = new Mesh.Builder()
                .addBuffer(verts, new Mesh.Attrib(0, 3),
                        new Mesh.Attrib(1, 3), new Mesh.Attrib(2, 2))
                .setIndices(plane.indices)
                .build();

        mDiffuseTex = TextureHelper.createBrickTexture(512, 512);
        mNormalTex = TextureHelper.createBrickNormalTexture(512);
    }

    /** XY 平面墙：z=0，法线 +Z，uv 0..1。 */
    private static GeoGen.GeoData planeWall(float w, float h, int segX, int segY) {
        int vertsPerRow = segX + 1;
        int vertCount = vertsPerRow * (segY + 1);
        float[] pos = new float[vertCount * 3];
        float[] nrm = new float[vertCount * 3];
        float[] uv = new float[vertCount * 2];
        int p = 0, t = 0;
        for (int iy = 0; iy <= segY; iy++) {
            for (int ix = 0; ix <= segX; ix++) {
                pos[p++] = -w / 2f + w * ix / segX;
                pos[p++] = -h / 2f + h * iy / segY;
                pos[p++] = 0f;
                nrm[p - 3] = 0f;
                nrm[p - 2] = 0f;
                nrm[p - 1] = 1f;
                uv[t++] = (float) ix / segX * 2f;
                uv[t++] = (float) iy / segY * 2f;
            }
        }
        int[] idx = new int[segX * segY * 6];
        int k = 0;
        for (int iy = 0; iy < segY; iy++) {
            for (int ix = 0; ix < segX; ix++) {
                int a = iy * vertsPerRow + ix;
                int b = a + vertsPerRow;
                idx[k++] = a;
                idx[k++] = a + 1;
                idx[k++] = b;
                idx[k++] = a + 1;
                idx[k++] = b + 1;
                idx[k++] = b;
            }
        }
        GeoGen.GeoData data = new GeoGen.GeoData();
        data.positions = pos;
        data.normals = nrm;
        data.uvs = uv;
        data.indices = idx;
        return data;
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        clearFrame(0.06f, 0.08f, 0.12f);
        mTime += deltaTime * getFloat(KEY_ORBIT);

        float lx = (float) Math.sin(mTime * 0.9) * 4f;
        float ly = (float) Math.cos(mTime * 0.6) * 2f;

        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 45f, aspect(), 0.1f, 30f);
        Matrix.setLookAtM(mView, 0, 0, 0, 6f, 0, 0, 0, 0, 1, 0);
        float[] pv = new float[16];
        Matrix.multiplyMM(pv, 0, mProj, 0, mView, 0);
        Matrix.setIdentityM(mModel, 0);
        Matrix.multiplyMM(mMvp, 0, pv, 0, mModel, 0);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mDiffuseTex);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mNormalTex);

        mProgram.use();
        mProgram.set("u_diffuse", 0);
        mProgram.set("u_normalMap", 1);
        mProgram.set("u_lightPos", lx, ly, getFloat(KEY_LIGHT_Z));
        mProgram.set("u_viewPos", 0f, 0f, 6f);
        mProgram.set("u_normalStrength", getFloat(KEY_STRENGTH));
        mProgram.set("u_specular", getFloat(KEY_SPEC));
        mProgram.setMat4("u_mvp", mMvp);
        mProgram.setMat4("u_model", mModel);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        mWall.draw(GLES30.GL_TRIANGLES);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.floatSpec(KEY_STRENGTH, "法线强度", 0f, 3f, 1f));
        specs.add(ParamSpec.floatSpec(KEY_SPEC, "高光强度", 0f, 1.5f, 0.35f));
        specs.add(ParamSpec.floatSpec(KEY_ORBIT, "光源速度", 0f, 2f, 0.7f));
        specs.add(ParamSpec.floatSpec(KEY_LIGHT_Z, "光源距离 Z", 1f, 8f, 4f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mWall.dispose();
        TextureHelper.deleteTexture(mDiffuseTex);
        TextureHelper.deleteTexture(mNormalTex);
        mProgram.release();
    }
}
