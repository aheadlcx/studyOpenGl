package com.example.studyopengl.demos;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.opengl.GLES30;
import android.opengl.Matrix;

import com.example.studyopengl.engine.BaseDemoEngine;
import com.example.studyopengl.gl.GeoGen;
import com.example.studyopengl.gl.Mesh;
import com.example.studyopengl.gl.ShaderProgram;
import com.example.studyopengl.param.ParamSpec;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 综合实战 · 调试工坊：渲染一个六面不同贴图的立方体。
 * u_node 选择当前节点（每个节点 = 一个独立的小节界面）：
 *  1 UV 检验   2 六面六纹理   3 纹理数组六面
 *  4 Phong 光照 5 深度/绕序检查  6 完整组合
 */
public class D41DebugShowcase extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍这一章做什么\n"
            + "综合实战：渲染一个【六面贴着不同纹理图片】的 3D 立方体。\n"
            + "它由 6 个技术节点一步步搭出来——每个节点解决一个具体问题，"
            + "同时也是一次\"调试\"：用可视化手段验证这个环节是否正确。\n\n"
            + "▍六个节点（二级目录页可切换）\n"
            + "· 节点1 UV 与顶点数据：六面铺 uv 梯度色。若某面花纹方向不对，"
            + "说明该面 uv 或绕序写错了——贴图歪斜的第一排查手段；\n"
            + "· 节点2 六面六纹理：给 6 个面各绑一张纹理、分 6 次 draw。"
            + "核对每个数字落在正确的面上，验证\"面-纹理\"对应关系；\n"
            + "· 节点3 纹理数组：同样效果改用 TEXTURE_2D_ARRAY，一次 draw 完成（第 19 章）；\n"
            + "· 节点4 光照与法线：Phong 打光。若某面亮度不对，就是法线错了——"
            + "光照是法线方向的\"探伤仪\"；\n"
            + "· 节点5 深度与绕序检查：线框叠加看三角形剖分，半透明内芯验证遮挡；\n"
            + "· 节点6 完整组合：纹理 + 光照 + 旋转 + 地面，最终交付效果。\n\n"
            + "▍调试心法（本章真正想教的）\n"
            + "GL 出错不崩溃、只\"画错\"。排查思路是把管线拆开，给每个环节做一个"
            + "\"可视化探针\"：\n"
            + "· 顶点/uv 对不对 → 铺梯度色；\n"
            + "· 法线对不对 → 把法线当颜色显示，或用光照照射观察；\n"
            + "· 深度/遮挡对不对 → 关深度、开线框、放半透明参照物对比；\n"
            + "· 纹理对不对 → 给每面贴不同的带编号贴图。";

    private static final String TEX_VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec3 a_normal;\n"
            + "layout(location=2) in vec2 a_uv;\n"
            + "layout(location=3) in float a_layer;\n"
            + "uniform mat4 u_mvp;\n"
            + "uniform mat4 u_model;\n"
            + "out vec3 v_normal;\n"
            + "out vec3 v_worldPos;\n"
            + "out vec2 v_uv;\n"
            + "out float v_layer;\n"
            + "void main() {\n"
            + "    v_normal = mat3(u_model) * a_normal;\n"
            + "    v_worldPos = (u_model * vec4(a_pos, 1.0)).xyz;\n"
            + "    v_uv = a_uv;\n"
            + "    v_layer = a_layer;\n"
            + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String TEX_FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "precision lowp sampler2DArray;\n"
            + "in vec3 v_normal;\n"
            + "in vec3 v_worldPos;\n"
            + "in vec2 v_uv;\n"
            + "in float v_layer;\n"
            + "uniform sampler2D u_tex;\n"
            + "uniform sampler2DArray u_array;\n"
            + "uniform int u_node;\n"
            + "uniform vec3 u_lightPos;\n"
            + "uniform vec3 u_viewPos;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    vec4 c;\n"
            + "    if (u_node == 1) {\n"
            + "        c = texture(u_tex, v_uv);                   // 节点1：UV 梯度检验\n"
            + "    } else if (u_node == 3 || u_node == 6) {\n"
            + "        c = texture(u_array, vec3(v_uv, v_layer));  // 节点3/6：纹理数组\n"
            + "    } else {\n"
            + "        c = texture(u_tex, v_uv);                   // 节点2/4：逐面纹理\n"
            + "    }\n"
            + "    if (u_node == 4 || u_node == 6) {               // 节点4/6：Phong 光照\n"
            + "        vec3 N = normalize(v_normal);\n"
            + "        vec3 L = normalize(u_lightPos - v_worldPos);\n"
            + "        vec3 V = normalize(u_viewPos - v_worldPos);\n"
            + "        vec3 R = reflect(-L, N);\n"
            + "        float lit = 0.35 + 1.0 * max(dot(N, L), 0.0)\n"
            + "                  + 0.4 * pow(max(dot(R, V), 0.0), 32.0);\n"
            + "        c.rgb *= lit;\n"
            + "    }\n"
            + "    fragColor = vec4(c.rgb, 1.0);\n"
            + "}\n";

    private static final String FLAT_VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec4 a_color;\n"
            + "uniform mat4 u_mvp;\n"
            + "out vec4 v_color;\n"
            + "void main() {\n"
            + "    v_color = a_color;\n"
            + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String FLAT_FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec4 v_color;\n"
            + "out vec4 fragColor;\n"
            + "void main() { fragColor = v_color; }\n";

    private static final String KEY_NODE = "node";
    private static final String KEY_SPEED = "speed";

    private ShaderProgram mTexProg;   // 纹理/数组/光照共用（u_node 区分分支）
    private ShaderProgram mFlatProg;  // 棱线/内芯/地面（顶点色）
    private Mesh mCubeLayer;          // pos3+normal3+uv2+layer1（节点1/3/6）
    private final Mesh[] mFaceMesh = new Mesh[6];   // 逐面（节点2/4）
    private Mesh mEdges;              // 棱线（节点5/6）
    private Mesh mInner;              // 半透明内芯（节点5）
    private Mesh mGround;
    private int mUvTex;
    private final int[] mFaceTex = new int[6];
    private int mArrayTex;
    private float mSpin;
    private float mTime;

    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mPv = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mMvp = new float[16];

    @Override
    public void onSurfaceCreated(int width, int height) {
        mTexProg = new ShaderProgram(TEX_VS, TEX_FS);
        mFlatProg = new ShaderProgram(FLAT_VS, FLAT_FS);
        // 采样器单元分工：u_tex 固定 0 号单元、u_array 固定 1 号单元。
        // 1) glUniform 作用于"当前 use 的程序"，必须先 use 再设；
        // 2) ES3 规定同一程序里不同类型的活跃采样器指向同一单元时 draw 报
        //    GL_INVALID_OPERATION（不画任何东西）——这是本章埋的"实战坑"之一。
        mTexProg.use();
        mTexProg.set("u_tex", 0);
        mTexProg.set("u_array", 1);

        buildCubeWithLayer();
        buildFaceMeshes();
        buildEdges();
        buildInner();
        mGround = makeGround();

        mUvTex = texFromPixels(uvPixels(128), 128);
        int[][] bg = {
                {190, 60, 60}, {60, 170, 70}, {60, 90, 210},
                {220, 170, 50}, {160, 60, 190}, {50, 180, 180}
        };
        for (int i = 0; i < 6; i++) {
            mFaceTex[i] = texFromBitmap(numberImage(128, i + 1, bg[i]), true);
        }
        mArrayTex = buildFaceArray(128, bg);
    }

    // ---------- 几何构建 ----------
    private void buildCubeWithLayer() {
        GeoGen.GeoData g = GeoGen.cube();
        int vc = g.positions.length / 3;
        float[] data = new float[vc * 9];             // pos3+normal3+uv2+layer1
        for (int i = 0; i < vc; i++) {
            int f = i / 4;                             // 该顶点所属面 = 数组层号
            data[i * 9]      = g.positions[i * 3];
            data[i * 9 + 1]  = g.positions[i * 3 + 1];
            data[i * 9 + 2]  = g.positions[i * 3 + 2];
            data[i * 9 + 3]  = g.normals[i * 3];
            data[i * 9 + 4]  = g.normals[i * 3 + 1];
            data[i * 9 + 5]  = g.normals[i * 3 + 2];
            data[i * 9 + 6]  = g.uvs[i * 2];
            data[i * 9 + 7]  = g.uvs[i * 2 + 1];
            data[i * 9 + 8]  = (float) f;
        }
        mCubeLayer = new Mesh.Builder()
                .addBuffer(data, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3),
                        new Mesh.Attrib(2, 2), new Mesh.Attrib(3, 1))
                .setIndices(g.indices)
                .build();
    }

    private void buildFaceMeshes() {
        GeoGen.GeoData g = GeoGen.cube();
        int[] order = {0, 1, 2, 2, 1, 3};
        for (int f = 0; f < 6; f++) {
            List<Float> v = new ArrayList<>();
            List<Integer> idx = new ArrayList<>();
            for (int k = 0; k < 6; k++) {
                int vi = f * 4 + order[k];
                v.add(g.positions[vi * 3]);
                v.add(g.positions[vi * 3 + 1]);
                v.add(g.positions[vi * 3 + 2]);
                v.add(g.normals[vi * 3]);
                v.add(g.normals[vi * 3 + 1]);
                v.add(g.normals[vi * 3 + 2]);
                v.add(g.uvs[vi * 2]);
                v.add(g.uvs[vi * 2 + 1]);
            }
            for (int i = 0; i < 6; i++) idx.add(i);
            mFaceMesh[f] = new Mesh.Builder()
                    .addBuffer(toF(v), new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3),
                            new Mesh.Attrib(2, 2))
                    .setIndices(toI(idx))
                    .build();
        }
    }

    private void buildEdges() {
        float h = 0.502f;
        float[][] cube = {{-1,-1,-1},{1,-1,-1},{1,-1,1},{-1,-1,1},
                          {-1,1,-1},{1,1,-1},{1,1,1},{-1,1,1}};
        int[][] pairs = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},
                         {0,4},{1,5},{2,6},{3,7}};
        List<Float> ev = new ArrayList<>();
        for (int[] pr : pairs)
            for (int k = 0; k < 2; k++) {
                ev.add(cube[pr[k]][0] * h);
                ev.add(cube[pr[k]][1] * h);
                ev.add(cube[pr[k]][2] * h);
                ev.add(1.0f); ev.add(1.0f); ev.add(0.2f); ev.add(1.0f);
            }
        mEdges = new Mesh.Builder()
                .addBuffer(toF(ev), new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 4))
                .build();
    }

    private void buildInner() {
        float h = 0.14f;
        float[][] c8 = {{-1,-1,-1},{1,-1,-1},{1,-1,1},{-1,-1,1},
                        {-1,1,-1},{1,1,-1},{1,1,1},{-1,1,1}};
        int[][] fs = {{0,1,2},{0,2,3},{4,5,6},{4,6,7},{7,6,2},{7,2,3},{0,3,7},{0,7,4}};
        List<Float> v = new ArrayList<>();
        for (int[] tri : fs)
            for (int i = 0; i < 3; i++) {
                int cIdx = tri[i];
                v.add(c8[cIdx][0] * h);
                v.add(c8[cIdx][1] * h);
                v.add(c8[cIdx][2] * h);
                v.add(1.0f); v.add(0.4f); v.add(0.3f); v.add(0.45f);
            }
        mInner = new Mesh.Builder()
                .addBuffer(toF(v), new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 4))
                .build();
    }

    private Mesh makeGround() {
        float s = 8.0f;
        List<Float> v = new ArrayList<>();
        float[][] q = {{-s,-0.9f,-s},{s,-0.9f,-s},{-s,-0.9f,s},
                       {s,-0.9f,-s},{s,-0.9f,s},{-s,-0.9f,s}};
        for (float[] p : q) {
            v.add(p[0]); v.add(p[1]); v.add(p[2]);
            v.add(0.3f); v.add(0.35f); v.add(0.4f); v.add(1.0f);
        }
        return new Mesh.Builder()
                .addBuffer(toF(v), new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 4))
                .build();
    }

    private static float[] toF(List<Float> l) {
        float[] a = new float[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    private static int[] toI(List<Integer> l) {
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    // ---------- 程序化贴图 ----------
    private static List<Float> uvPixels(int size) {
        List<Float> px = new ArrayList<>();
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                px.add((float) x / size);
                px.add((float) y / size);
                px.add(0.15f);
                px.add(1.0f);
            }
        return px;
    }

    private int texFromPixels(List<Float> px, int size) {
        ByteBuffer buf = ByteBuffer.allocateDirect(px.size());
        for (float f : px) buf.put((byte) (f * 255.0f));
        buf.position(0);
        int[] ids = new int[1];
        GLES30.glGenTextures(1, ids, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0]);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
                size, size, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        return ids[0];
    }

    private int texFromBitmap(Bitmap bmp, boolean mipmap) {
        int[] ids = new int[1];
        GLES30.glGenTextures(1, ids, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0]);
        android.opengl.GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MIN_FILTER, mipmap ? GLES30.GL_LINEAR_MIPMAP_LINEAR : GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);
        return ids[0];
    }

    /** 面编号贴图：彩色底 + 白色大数字 + 白描边框，调试\"面-纹理\"对应最直观。 */
    private static Bitmap numberImage(int size, int num, int[] bg) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.rgb(bg[0], bg[1], bg[2]));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(size / 16.0f);
        p.setColor(Color.WHITE);
        float inset = size / 14.0f;
        canvas.drawRect(inset, inset, size - inset, size - inset, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(size * 0.55f);
        p.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(String.valueOf(num), size / 2.0f, size * 0.68f, p);
        return bmp;
    }

    /** 六张编号图打包成 GLES30.GL_TEXTURE_2D_ARRAY。 */
    private int buildFaceArray(int size, int[][] bg) {
        ByteBuffer all = ByteBuffer.allocateDirect(size * size * 4 * 6);
        for (int f = 0; f < 6; f++) {
            Bitmap bmp = numberImage(size, f + 1, bg[f]);
            for (int y = 0; y < size; y++)
                for (int x = 0; x < size; x++) {
                    int c = bmp.getPixel(x, y);
                    all.put((byte) Color.red(c));
                    all.put((byte) Color.green(c));
                    all.put((byte) Color.blue(c));
                    all.put((byte) Color.alpha(c));
                }
        }
        all.position(0);

        int[] ids = new int[1];
        GLES30.glGenTextures(1, ids, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, ids[0]);
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_2D_ARRAY, 0, GLES30.GL_RGBA8,
                size, size, 6, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, all);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY,
                GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY,
                GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D_ARRAY);
        return ids[0];
    }

    // ---------- 相机/矩阵 ----------
    private void setupCamera(float ex, float ey, float ez, float cx, float cy, float cz) {
        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 45.0f, (float) mWidth / mHeight, 0.1f, 30.0f);
        Matrix.setIdentityM(mView, 0);
        Matrix.setLookAtM(mView, 0, ex, ey, ez, cx, cy, cz, 0, 1, 0);
        Matrix.multiplyMM(mPv, 0, mProj, 0, mView, 0);
    }

    private void mvpOf() {
        Matrix.multiplyMM(mMvp, 0, mPv, 0, mModel, 0);
        // u_model 供着色器算世界空间的法线/位置（光照用）。
        // GL uniform 默认值是"全零矩阵"而非单位阵，不赋值法线会归零、光照只剩环境项。
        mTexProg.setMat4("u_model", mModel);
    }

    private void modelSpin() {
        Matrix.setIdentityM(mModel, 0);
        Matrix.rotateM(mModel, 0, mSpin, 0.3f, 1, 0.1f);
    }

    private void modelSpinAt(float tx, float ty, float tz) {
        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, tx, ty, tz);
        Matrix.rotateM(mModel, 0, mSpin, 0.3f, 1, 0.1f);
    }

    private void glActiveTexBind(int tex) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex);
    }

    private void glActiveTexBindArray(int tex) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, tex);
    }

    private void glUseProgramSafe(ShaderProgram p) {
        GLES30.glUseProgram(p.getProgramId());
    }

    private void glUniform4fSafe(ShaderProgram p, String name, float a, float b, float c, float d) {
        GLES30.glUniform4f(p.loc(name), a, b, c, d);
    }

    // ---------- 绘制 ----------
    @Override
    public void onDrawFrame(float deltaTime) {
        mSpin += deltaTime * getFloat(KEY_SPEED) * 40.0f;
        mTime += deltaTime;
        int node = isLocked(KEY_NODE) ? getOptionIndex(KEY_NODE) + 1 : 6;

        if (node == 6) clearFrame(0.30f, 0.35f, 0.10f);
        else clearFrame(0.06f, 0.08f, 0.12f);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);

        glUseProgramSafe(mTexProg);
        mTexProg.set("u_lightPos", (float) Math.sin(mTime * 0.8) * 4.0f, 3.0f, 3.0f);
        mTexProg.set("u_viewPos", 0f, 0.8f, 5.6f);

        // ═══ 节点1：UV 梯度铺满六面（检验 uv/绕序）═══
        if (node == 1) {
            setupCamera(0, 0, 5.6f, 0, 0, 0);
            glActiveTexBind(mUvTex);
            mTexProg.set("u_tex", 0);
            mTexProg.set("u_node", 1);
            matSpinOnly(mSpin);
            mvpOf();
            mTexProg.setMat4("u_mvp", mMvp);
            mCubeLayer.draw(GLES30.GL_TRIANGLES);
        }

        // ═══ 节点2：六面六纹理（逐面绑定绘制）═══
        if (node == 2) {
            setupCamera(0, 0.8f, 5.6f, 0, 0.1f, 0);
            mTexProg.set("u_node", 2);
            matSpinAt(0.2f, 0, 0, mSpin);
            mvpOf();
            mTexProg.setMat4("u_mvp", mMvp);
            for (int f = 0; f < 6; f++) {
                glActiveTexBind(mFaceTex[f]);
                mTexProg.set("u_tex", 0);
                mFaceMesh[f].draw(GLES30.GL_TRIANGLES);
            }
        }

        // ═══ 节点3：纹理数组一次 draw ═══
        if (node == 3) {
            setupCamera(0, 0.8f, 5.6f, 0, 0.1f, 0);
            mTexProg.set("u_node", 3);
            glActiveTexBindArray(mArrayTex);
            mTexProg.set("u_array", 1);
            matSpinAt(0.2f, 0, 0, mSpin);
            mvpOf();
            mTexProg.setMat4("u_mvp", mMvp);
            mCubeLayer.draw(GLES30.GL_TRIANGLES);
        }

        // ═══ 节点4：Phong 光照检验法线 ═══
        if (node == 4) {
            setupCamera(0, 0.8f, 5.6f, 0, 0.1f, 0);
            mTexProg.set("u_node", 4);
            matSpinAt(0.2f, 0, 0, mSpin);
            mvpOf();
            mTexProg.setMat4("u_mvp", mMvp);
            for (int f = 0; f < 6; f++) {
                glActiveTexBind(mFaceTex[f]);
                mTexProg.set("u_tex", 0);
                mFaceMesh[f].draw(GLES30.GL_TRIANGLES);
            }
        }

        // ═══ 节点5：深度与绕序检查（半透明内芯 + 黄色棱线）═══
        if (node == 5) {
            setupCamera(3.0f, 2.2f, 3.7f, 0, 0, 0);
            Matrix.setIdentityM(mModel, 0);
            Matrix.rotateM(mModel, 0, mSpin, 0.3f, 1, 0.1f);
            mvpOf();
            // 半透明红盒（参与混合，深度只读）
            glUseProgramSafe(mFlatProg);
            GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
            GLES30.glDepthMask(false);
            glUniform4fSafe(mFlatProg, "u_color", 1.0f, 0.4f, 0.3f, 0.45f);
            mInner.draw(GLES30.GL_TRIANGLES);
            GLES30.glDepthMask(true);
            GLES30.glDisable(GLES30.GL_BLEND);
            // 黄色棱线
            glUseProgramSafe(mFlatProg);
            mFlatProg.setMat4("u_mvp", mMvp);
            glUniform4fSafe(mFlatProg, "u_color", 1.0f, 1.0f, 0.2f, 1.0f);
            mEdges.draw(GLES30.GL_LINES);
        }

        // ═══ 节点6：完整组合（纹理数组 + 光照 + 地面 + 棱线）═══
        if (node == 6) {
            setupCamera(3.0f, 2.2f, 3.7f, 0, 0, 0);
            mTexProg.set("u_viewPos", 3.0f, 2.2f, 3.7f);
            mTexProg.set("u_node", 6);
            glActiveTexBindArray(mArrayTex);
            mTexProg.set("u_array", 1);
            matSpinOnly(mSpin);
            mvpOf();
            mTexProg.setMat4("u_mvp", mMvp);
            mCubeLayer.draw(GLES30.GL_TRIANGLES);

            // 地面（世界坐标，恒等模型）
            glUseProgramSafe(mFlatProg);
            Matrix.setIdentityM(mModel, 0);
            Matrix.multiplyMM(mMvp, 0, mPv, 0, mModel, 0);
            mFlatProg.setMat4("u_mvp", mMvp);
            glUniform4fSafe(mFlatProg, "u_color", 0.3f, 0.35f, 0.4f, 1.0f);
            mGround.draw(GLES30.GL_TRIANGLES);

            // 黄色棱线点缀
            matSpinOnly(mSpin);
            mvpOf();
            mFlatProg.setMat4("u_mvp", mMvp);
            glUniform4fSafe(mFlatProg, "u_color", 1.0f, 1.0f, 0.2f, 1.0f);
            mEdges.draw(GLES30.GL_LINES);
        }

        glActiveTexBind(0);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
    }

    private void matSpinOnly(float deg) {
        Matrix.setIdentityM(mModel, 0);
        Matrix.rotateM(mModel, 0, deg, 0, 1, 0);
    }

    private void matSpinAt(float tx, float ty, float tz, float deg) {
        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, tx, ty, tz);
        Matrix.rotateM(mModel, 0, deg, 0.3f, 1, 0.1f);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        List<ParamSpec> specs = new ArrayList<>();
        specs.add(ParamSpec.optionSpec(KEY_NODE, "调试节点", new String[]{
                "节点1 · UV 与顶点数据",
                "节点2 · 六面六纹理",
                "节点3 · 纹理数组六面",
                "节点4 · 光照与法线",
                "节点5 · 深度与绕序检查",
                "节点6 · 完整组合"
        }, 5));
        specs.add(ParamSpec.floatSpec(KEY_SPEED, "旋转速度", 0f, 2f, 0.5f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mCubeLayer.dispose();
        for (Mesh m : mFaceMesh) if (m != null) m.dispose();
        if (mEdges != null) mEdges.dispose();
        if (mInner != null) mInner.dispose();
        if (mGround != null) mGround.dispose();
        GLES30.glDeleteTextures(1, new int[]{mUvTex}, 0);
        GLES30.glDeleteTextures(6, mFaceTex, 0);
        GLES30.glDeleteTextures(1, new int[]{mArrayTex}, 0);
        mTexProg.release();
        mFlatProg.release();
    }
}
