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
            + "· 节点6 完整组合：纹理 + 光照 + 旋转 + 地面，最终交付效果。\n"
            + "· 节点7 MVP 变换探针：M(模型平移/旋转/缩放)、V(相机位置)、"
            + "P(视场角FOV/近平面) 三层各给滑杆，配合红X绿Y蓝Z世界坐标轴，"
            + "拖一下就能看清\"这一层矩阵到底动了什么\"——比如 FOV 拉大画面"
            + "\"广角变形\"、近平面推过物体会把物体\"削掉\"；\n"
            + "· 节点8 Viewport 探针：glViewport 只是个\"屏幕上的矩形\"，"
            + "决定 NDC 的 -1..1 映射到哪。拖位置/大小滑杆、开画中画式小窗，"
            + "再对比\"是否按视口长宽比修正投影\"——不修正画面就会被拉扁"
            + "（直播推流分辨率适配就是这一招）。\n\n"
            + "▍调试心法（本章真正想教的）\n"
            + "GL 出错不崩溃、只\"画错\"。排查思路是把管线拆开，给每个环节做一个"
            + "\"可视化探针\"：\n"
            + "· 顶点/uv 对不对 → 铺梯度色；\n"
            + "· 法线对不对 → 把法线当颜色显示，或用光照照射观察；\n"
            + "· 深度/遮挡对不对 → 关深度、开线框、放半透明参照物对比；\n"
            + "· 纹理对不对 → 给每面贴不同的带编号贴图。";

    /** 节点7 小节讲解：MVP 三层拆开拖。 */
    public static final String DETAIL_NODE7 = ""
            + "▍这一节看什么\n"
            + "MVP 是三个 4x4 矩阵连乘：M 把物体摆进世界，V 把世界搬进相机，"
            + "P 把三维压成屏幕坐标。拆开拖，才知道每一层\"动的是什么\"。\n\n"
            + "▍怎么玩\n"
            + "· M·模型平移X：立方体沿红轴(X)滑动——轴不动物体动，这就是\"模型在世界里的位置\"；\n"
            + "· M·模型绕Y旋转°：物体自转；\n"
            + "· M·模型缩放：以原点为中心放大缩小；\n"
            + "· V·相机X位置：物体不动、\"你绕着它走\"，画面里物体反向移动；\n"
            + "· P·视场角FOV°：大FOV=广角，边缘拉伸变形；小FOV=长焦拉近；\n"
            + "· P·近平面距离：近平面推大，物体会被\"削掉一块\"——近处裁剪就是它干的。\n\n"
            + "▍对应代码\n"
            + "perspectiveM(FOV, aspect, near, far) 生成 P；setLookAtM( eye, center, up ) 生成 V；"
            + "setIdentityM→translateM→rotateM→scaleM 生成 M；multiplyMM(P,V) 再乘 M 得 MVP 上传 u_mvp。";

    /** 节点8 小节讲解：glViewport 是屏幕上的矩形。 */
    public static final String DETAIL_NODE8 = ""
            + "▍这一节看什么\n"
            + "glViewport(x, y, w, h) 只是\"屏幕上的一个矩形\"（像素单位，原点在左下角）。"
            + "NDC 的 -1..1 会被拉伸映射进这个矩形——它不裁剪、不缩放内容，"
            + "只决定\"画到哪、占多大\"。\n\n"
            + "▍怎么玩\n"
            + "· 视口左下角X/Y：矩形挪到任意角落（Y=0 是屏幕底部，GL 的 Y 轴朝上）；\n"
            + "· 视口宽度/高度：矩形变大变小，立方体跟着被拉伸；\n"
            + "· 按视口长宽比修正投影：关→画面被拉扁（投影还按全屏比例算）；"
            + "开→恢复正常（投影 aspect 换成视口的 w/h）。这一开一关就是直播推流的\"画面适配\"。\n\n"
            + "▍直播里怎么用\n"
            + "推流分辨率与屏幕比例不一致时的适配 = 换 viewport + 换投影 aspect；"
            + "小窗模式、连麦布局、画中画 = 多个 viewport 各画各的；"
            + "再配合 glScissor 还能只清除/重画矩形区域（本节那块亮底就是 scissor 清的）。\n\n"
            + "▍对应代码\n"
            + "onDrawFrame 的节点8分支：glScissor 清亮视口区域 → glViewport 切矩形 → "
            + "perspectiveM 的 aspect 按\"是否修正\"二选一 → 画立方体 → glViewport 还原全屏。";

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

    // 节点7（MVP 探针）/ 节点8（Viewport 探针）的交互参数
    private static final String K_M_ROT = "m_rot";       // 模型绕 Y 旋转角
    private static final String K_M_TX = "m_tx";         // 模型沿 X 平移
    private static final String K_M_SCALE = "m_scale";   // 模型整体缩放
    private static final String K_V_EYE_X = "v_eye_x";   // 相机 X 位置
    private static final String K_P_FOV = "p_fov";       // 视场角
    private static final String K_P_NEAR = "p_near";     // 近平面距离
    private static final String K_VP_X = "vp_x";         // 视口左下角 X（比例）
    private static final String K_VP_Y = "vp_y";         // 视口左下角 Y（比例）
    private static final String K_VP_W = "vp_w";         // 视口宽度（比例）
    private static final String K_VP_H = "vp_h";         // 视口高度（比例）
    private static final String K_VP_FIX = "vp_fix";     // 是否按视口长宽比修正投影

    private ShaderProgram mTexProg;   // 纹理/数组/光照共用（u_node 区分分支）
    private ShaderProgram mFlatProg;  // 棱线/内芯/地面（顶点色）
    private Mesh mCubeLayer;          // pos3+normal3+uv2+layer1（节点1/3/6）
    private final Mesh[] mFaceMesh = new Mesh[6];   // 逐面（节点2/4）
    private Mesh mEdges;              // 棱线（节点5/6）
    private Mesh mInner;              // 半透明内芯（节点5）
    private Mesh mGround;
    private Mesh mAxes;               // 世界坐标轴（节点7：红X/绿Y/蓝Z）
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
        buildAxes();
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

    /** 世界坐标轴：红=X 绿=Y 蓝=Z，配合 MVP 探针直观看"平移/缩放改了什么"。 */
    private void buildAxes() {
        float L = 1.8f;
        float[] v = {
                0, 0, 0, 1f, 0.25f, 0.25f, 1f,   L, 0, 0, 1f, 0.25f, 0.25f, 1f,
                0, 0, 0, 0.3f, 1f, 0.3f, 1f,     0, L, 0, 0.3f, 1f, 0.3f, 1f,
                0, 0, 0, 0.35f, 0.55f, 1f, 1f,   0, 0, L, 0.35f, 0.55f, 1f, 1f,
        };
        mAxes = new Mesh.Builder()
                .addBuffer(v, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 4))
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

        // ═══ 节点7：MVP 变换探针（M/V/P 三层各自可调，轴系做参照物）═══
        if (node == 7) {
            float fov = Math.max(10f, getFloat(K_P_FOV));
            float near = Math.max(0.05f, getFloat(K_P_NEAR));
            Matrix.setIdentityM(mProj, 0);
            Matrix.perspectiveM(mProj, 0, fov, (float) mWidth / mHeight, near, 100f);
            Matrix.setIdentityM(mView, 0);
            Matrix.setLookAtM(mView, 0, getFloat(K_V_EYE_X), 0.8f, 4.5f, 0, 0, 0, 0, 1, 0);
            Matrix.multiplyMM(mPv, 0, mProj, 0, mView, 0);

            // M：平移 → 旋转 → 缩放（顺序不同结果不同，这也是教学点）
            Matrix.setIdentityM(mModel, 0);
            Matrix.translateM(mModel, 0, getFloat(K_M_TX), 0, 0);
            Matrix.rotateM(mModel, 0, getFloat(K_M_ROT), 0, 1, 0);
            float sc = Math.max(0.05f, getFloat(K_M_SCALE));
            Matrix.scaleM(mModel, 0, sc, sc, sc);
            mvpOf(); // 内部同时上传 u_model
            mTexProg.setMat4("u_mvp", mMvp);

            glActiveTexBind(mUvTex);
            mTexProg.set("u_tex", 0);
            mTexProg.set("u_node", 1);
            mCubeLayer.draw(GLES30.GL_TRIANGLES);

            // 世界坐标轴（模型矩阵不参与：轴不动才能对照出模型动了多少）
            glUseProgramSafe(mFlatProg);
            Matrix.setIdentityM(mModel, 0);
            Matrix.multiplyMM(mMvp, 0, mPv, 0, mModel, 0);
            mFlatProg.setMat4("u_mvp", mMvp);
            mAxes.draw(GLES30.GL_LINES);
        }

        // ═══ 节点8：Viewport 探针（glViewport 决定 NDC 映射到屏幕哪块矩形）═══
        if (node == 8) {
            int vx = Math.round(getFloat(K_VP_X) * mWidth);
            int vy = Math.round(getFloat(K_VP_Y) * mHeight);
            int vw = Math.max(48, Math.round(getFloat(K_VP_W) * mWidth));
            int vh = Math.max(48, Math.round(getFloat(K_VP_H) * mHeight));

            // 用 scissor 把视口区域清亮一点：不画任何东西也能看清"视口是块矩形"
            GLES30.glEnable(GLES30.GL_SCISSOR_TEST);
            GLES30.glScissor(vx, vy, vw, vh);
            GLES30.glClearColor(0.10f, 0.14f, 0.22f, 1f);
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
            GLES30.glDisable(GLES30.GL_SCISSOR_TEST);

            GLES30.glViewport(vx, vy, vw, vh);

            // 教学点：投影 aspect 若不跟着视口走，画面就会被拉伸变形
            boolean fixAspect = getBool(K_VP_FIX);
            float aspect = fixAspect ? (float) vw / vh : (float) mWidth / mHeight;
            Matrix.setIdentityM(mProj, 0);
            Matrix.perspectiveM(mProj, 0, 55f, aspect, 0.1f, 30f);
            Matrix.setIdentityM(mView, 0);
            Matrix.setLookAtM(mView, 0, 0, 0.8f, 4.6f, 0, 0.1f, 0, 0, 1, 0);
            Matrix.multiplyMM(mPv, 0, mProj, 0, mView, 0);
            matSpinOnly(mSpin * 0.4f);
            mvpOf();
            mTexProg.setMat4("u_mvp", mMvp);

            glActiveTexBind(mUvTex);
            mTexProg.set("u_tex", 0);
            mTexProg.set("u_node", 1);
            mCubeLayer.draw(GLES30.GL_TRIANGLES);

            GLES30.glViewport(0, 0, mWidth, mHeight); // 还原，避免影响下一帧清屏
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
                "节点6 · 完整组合",
                "节点7 · MVP 变换探针",
                "节点8 · Viewport 探针"
        }, 5));
        specs.add(ParamSpec.floatSpec(KEY_SPEED, "旋转速度", 0f, 2f, 0.5f));
        // ---- 节点7：MVP 三层各自可调 ----
        specs.add(ParamSpec.floatSpec(K_M_ROT, "M·模型绕Y旋转°", 0f, 360f, 0f));
        specs.add(ParamSpec.floatSpec(K_M_TX, "M·模型平移X", -1.5f, 1.5f, 0f));
        specs.add(ParamSpec.floatSpec(K_M_SCALE, "M·模型缩放", 0.2f, 2.0f, 1f));
        specs.add(ParamSpec.floatSpec(K_V_EYE_X, "V·相机X位置", -2.5f, 2.5f, 0f));
        specs.add(ParamSpec.floatSpec(K_P_FOV, "P·视场角FOV°", 15f, 110f, 60f));
        specs.add(ParamSpec.floatSpec(K_P_NEAR, "P·近平面距离", 0.05f, 2.5f, 0.5f));
        // ---- 节点8：Viewport 位置/大小/长宽比 ----
        specs.add(ParamSpec.floatSpec(K_VP_X, "视口左下角X(0~1)", 0f, 0.9f, 0f));
        specs.add(ParamSpec.floatSpec(K_VP_Y, "视口左下角Y(0~1)", 0f, 0.9f, 0f));
        specs.add(ParamSpec.floatSpec(K_VP_W, "视口宽度(0~1)", 0.1f, 1f, 1f));
        specs.add(ParamSpec.floatSpec(K_VP_H, "视口高度(0~1)", 0.1f, 1f, 1f));
        specs.add(ParamSpec.boolSpec(K_VP_FIX, "按视口长宽比修正投影", false));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mCubeLayer.dispose();
        for (Mesh m : mFaceMesh) if (m != null) m.dispose();
        if (mEdges != null) mEdges.dispose();
        if (mInner != null) mInner.dispose();
        if (mGround != null) mGround.dispose();
        if (mAxes != null) mAxes.dispose();
        GLES30.glDeleteTextures(1, new int[]{mUvTex}, 0);
        GLES30.glDeleteTextures(6, mFaceTex, 0);
        GLES30.glDeleteTextures(1, new int[]{mArrayTex}, 0);
        mTexProg.release();
        mFlatProg.release();
    }
}
