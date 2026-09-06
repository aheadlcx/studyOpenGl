package com.example.studyopengl.demos;

import android.opengl.GLES30;
import android.opengl.Matrix;

import com.example.studyopengl.engine.BaseDemoEngine;
import com.example.studyopengl.gl.FlatShapes;
import com.example.studyopengl.gl.Mesh;
import com.example.studyopengl.gl.ShaderProgram;
import com.example.studyopengl.gl.TextureHelper;
import com.example.studyopengl.param.ParamSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 28 · 顶点数据：从 float 数组到三角形（VBO / VAO / 交错布局）
 *
 * 上半屏：把显存画成"内存条"——3 个顶点 × (xyz + rgb) 共 18 个 float，
 * 每个方块一个数据，颜色区分含义；切换"交错/分离"布局对比两种组织方式。
 * 下半屏：同一份数据构成的 3D 三角形。选中某个顶点时，
 * 内存条上对应的 6 个方块放大高亮、三角形上的顶点脉冲——建立"数据↔几何"直觉。
 */
public class D28VertexData extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍这一步在干嘛？（流水线第 1 站）\n"
            + "GPU 不认识你的 Java 对象，它只认\"连续的二进制数字流\"。所以渲染的第一步是："
            + "把顶点数据从 Java 数组拷进 GPU 显存，并附一份\"说明书\"告诉它怎么读。"
            + "三件套：\n"
            + "· VBO(Vertex Buffer Object)：显存里的一块数组——就是数据的\"搬运目的地\"；\n"
            + "· VAO(Vertex Array Object)：说明书——记录\"第 0 列开始 3 个 float 是位置，"
            + "第 3 列开始 3 个 float 是颜色，每组共 24 字节(stride)，颜色偏移 12 字节(offset)\"；\n"
            + "· 索引 EBO：顶点复用表——正方形只存 4 个顶点，靠索引 0,1,2,2,1,3 拼 2 个三角形。\n\n"
            + "▍Android 类比（秒懂）\n"
            + "VBO ≈ RecyclerView 的 List&lt;Item&gt;（数据本体）；\n"
            + "VAO ≈ ViewHolder（记住每行里哪个位置是 title、哪个是 icon，一次绑定反复用）；\n"
            + "glVertexAttribPointer ≈ findViewById + setText——告诉管线\"这个下标的数据"
            + "对应 shader 里的哪个 in 变量\"。\n\n"
            + "▍交错布局 vs 分离布局（上半屏可切换）\n"
            + "交错(interleaved)：[x y z r g b][x y z r g b]…——一个顶点的所有属性挤在一起，"
            + "缓存局部性好，GPU 读一个顶点一次搞定（主流选择，本 App 全部用交错）；\n"
            + "分离(separate)：positions 全放一条[]、colors 全放另一条[]——"
            + "改某类属性时只动一条缓冲，适合动态更新单一属性（比如只更新位置做动画）。\n\n"
            + "▍stride / offset 是初学者最易懵的点\n"
            + "stride=相邻两个顶点同一属性的跨度（字节），offset=本属性在本顶点块内的起点。"
            + "交错布局里两者都不为 0；分离布局可以全 0（让 GL 自动算）。"
            + "本界面选中一个顶点后，高亮的 6 个方块连起来正是\"一个顶点块\"——"
            + "stride 就是两个块起点之间的距离。\n\n"
            + "▍下半屏在看什么\n"
            + "那 18 个 float 真的被画成了三角形。选 v0/v1/v2 观察：内存里的一段数字，"
            + "如何对应空间里的一个点和一种颜色。这就是\"数据驱动渲染\"的全部含义——"
            + "之后所有 Demo 的第一步都是它。";

    /** 与可视化和讲解一致的示例数据（3 顶点 × pos3 + color3）。 */
    static final float[] VERTS = {
            // x      y      z     r     g     b
            -0.75f, -0.55f, 0f, 1.00f, 0.30f, 0.25f,   // v0 红
            0.85f, -0.45f, 0f, 0.25f, 1.00f, 0.40f,   // v1 绿
            0.05f, 0.80f, 0f, 0.30f, 0.55f, 1.00f     // v2 蓝
    };

    private static final String KEY_LAYOUT = "layout";

    /** 代码示例：顶点数据三件套——VBO 搬运 / VAO 登记 / 绘制。 */
    public static final String CODE = ""
            + "// 上半屏\"内存条\"里每个方块 = 下面代码里的一个 float。\n"
            + "\n"
            + "// 【0】定义数据：交错布局（一个顶点的属性挤在一起）\n"
            + "float[] interleaved = {\n"
            + "    // x      y     z      r     g     b\n"
            + "    -0.75f, -0.55f, 0f,  1.00f, 0.30f, 0.25f,   // v0\n"
            + "     0.85f, -0.45f, 0f,  0.25f, 1.00f, 0.40f,   // v1\n"
            + "     0.05f,  0.80f, 0f,  0.30f, 0.55f, 1.00f    // v2\n"
            + "};\n"
            + "// 分离布局（切\"内存布局\"对比）：位置、颜色各自一条数组\n"
            + "float[] positions = {-0.75f,-0.55f,0f, 0.85f,-0.45f,0f, 0.05f,0.80f,0f};\n"
            + "float[] colors    = {1.00f,0.30f,0.25f, 0.25f,1.00f,0.40f, 0.30f,0.55f,1.00f};\n"
            + "\n"
            + "// 【1】拷进显存：一次 glBufferData 整批搬运\n"
            + "int[] vbo = new int[1];\n"
            + "GLES30.glGenBuffers(1, vbo, 0);\n"
            + "GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0]);\n"
            + "GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,\n"
            + "        interleaved.length * 4, FloatBuffer.wrap(interleaved),\n"
            + "        GLES30.GL_STATIC_DRAW);   // STATIC:写一次读N帧 DYNAMIC:常改\n"
            + "\n"
            + "// 【2】登记\"怎么读\"——stride/offset 就在这一步！\n"
            + "// 交错布局：stride=24(6个float×4字节)，位置 offset=0，颜色 offset=12\n"
            + "GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT,\n"
            + "        false, 24, 0);    // location0=位置：每24字节跳一组，从0开始\n"
            + "GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT,\n"
            + "        false, 24, 12);   // location1=颜色：同样步长，偏移12字节\n"
            + "GLES30.glEnableVertexAttribArray(0);\n"
            + "GLES30.glEnableVertexAttribArray(1);\n"
            + "// 分离布局则 stride=0(自动紧凑)、两条 VBO 各配各的。\n"
            + "\n"
            + "// 【3】VAO：把上面这些\"读法\"打包存档，之后一次绑定全恢复\n"
            + "int[] vao = new int[1];\n"
            + "GLES30.glGenVertexArrays(1, vao, 0);\n"
            + "GLES30.glBindVertexArray(vao[0]); // 绑定后第2步的设置都会被它记住\n"
            + "// ...做第1、2步...\n"
            + "GLES30.glBindVertexArray(0);\n"
            + "\n"
            + "// 【4】绘制：绑定 VAO，一条命令画完\n"
            + "GLES30.glBindVertexArray(vao[0]);\n"
            + "GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);\n"
            + "// 带索引时(EBO)：glDrawElements(GL_TRIANGLES,6,UNSIGNED_INT,0)\n"
            + "// 正方形 4 顶点+6 索引，顶点复用省显存——大模型差距巨大。";
    private static final String KEY_SEL = "sel";
    private static final String KEY_SPIN = "spin";
    private static final String KEY_VALS = "vals";

    private static final String[] SEL_LABELS = {"全部顶点", "只看 v0", "只看 v1", "只看 v2"};
    private static final String[] LAYOUT_LABELS = {"交错 interleaved（推荐）", "分离 separate"};

    private FlatShapes mShapes;
    private ShaderProgram mTriProg;
    private Mesh mTri;
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mMvp = new float[16];
    private float mTime;

    /** 18 个数值的标签纹理（如 "-0.75"），交错/分离两种排列复用。 */
    private final int[] mValTex = new int[18];
    private int mTitleInterleaved;
    private int mTitleSeparate;
    private int mTitlePos;
    private int mTitleCol;
    private int mLegendPos;
    private int mLegendCol;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mShapes = new FlatShapes();
        mTriProg = new ShaderProgram(
                "#version 300 es\n"
                        + "layout(location=0) in vec3 a_pos;\n"
                        + "layout(location=1) in vec3 a_color;\n"
                        + "uniform mat4 u_mvp;\n"
                        + "uniform float u_psize;\n"
                        + "out vec3 v_color;\n"
                        + "void main() {\n"
                        + "    v_color = a_color;\n"
                        + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
                        + "    gl_PointSize = u_psize;\n"
                        + "}\n",
                "#version 300 es\n"
                        + "precision mediump float;\n"
                        + "in vec3 v_color;\n"
                        + "out vec4 fragColor;\n"
                        + "void main() { fragColor = vec4(v_color, 1.0); }\n");
        mTri = new Mesh.Builder()
                .addBuffer(VERTS, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .build();

        // 数值标签：位置用蓝底白字、颜色按 rgb 分量自身着色底
        for (int i = 0; i < 18; i++) {
            String s = String.format(java.util.Locale.US, "%.2f", VERTS[i]);
            mValTex[i] = TextureHelper.createTextTexture(s, 256, 96);
        }
        mTitleInterleaved = TextureHelper.createTextTexture(
                "交错布局：一个顶点的 6 个数字挤在一起（stride=24字节）", 1280, 96);
        mTitleSeparate = TextureHelper.createTextTexture(
                "分离布局：位置一条[]、颜色一条[]（各条内部 stride 才连续）", 1280, 96);
        mTitlePos = TextureHelper.createTextTexture(
                "positions[]  x/y/z × 3顶点", 1280, 96);
        mTitleCol = TextureHelper.createTextTexture(
                "colors[]  r/g/b × 3顶点", 1280, 96);
        mLegendPos = TextureHelper.createTextTexture("■ 位置 x/y/z", 320, 96);
        mLegendCol = TextureHelper.createTextTexture("■ 颜色 r/g/b", 320, 96);
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        mTime += deltaTime;
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glViewport(0, 0, mWidth, mHeight);
        GLES30.glClearColor(0.04f, 0.05f, 0.09f, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

        boolean interleaved = getOptionIndex(KEY_LAYOUT) == 0;
        int sel = getOptionIndex(KEY_SEL);   // 0=全部 1..3=v0..v2

        // ════ 上半屏：内存条可视化（NDC y ∈ [0.14, 0.86]）════
        if (interleaved) {
            drawTitle(mTitleInterleaved, 0.84f);
            drawMemoryStrip(0.42f, 18, 6, sel, true);
            drawLegend(-0.62f, 0.18f);
        } else {
            drawTitle(mTitleSeparate, 0.84f);
            // 位置条 9 格（3 顶点×3）+ 颜色条 9 格，各占一行
            drawLabeledStrip(mTitlePos, 0.56f, 0, 9, 3, 0, sel);
            drawLabeledStrip(mTitleCol, 0.30f, 3, 9, 3, 1, sel);
        }

        // ════ 下半屏：数据长成的三角形（NDC y ∈ [-0.90, -0.02]）════
        GLES30.glViewport(0, Math.round(mHeight * 0.03f), mWidth,
                Math.round(mHeight * 0.44f));
        float vAspect = (float) mWidth / (float) (mHeight * 0.44f);
        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 45f, vAspect, 0.1f, 10f);
        Matrix.setIdentityM(mView, 0);
        Matrix.setLookAtM(mView, 0, 0f, 0f, 3f, 0, 0, 0, 0, 1, 0);
        Matrix.setIdentityM(mModel, 0);
        Matrix.rotateM(mModel, 0, mTime * getFloat(KEY_SPIN) * 40f, 0, 1, 0);
        Matrix.multiplyMM(mMvp, 0, mProj, 0, mView, 0);
        float[] mvp = new float[16];
        Matrix.multiplyMM(mvp, 0, mMvp, 0, mModel, 0);

        mTriProg.use();
        mTriProg.setMat4("u_mvp", mvp);
        mTriProg.set("u_psize", 1f);
        mTri.draw(GLES30.GL_TRIANGLES);
        // 顶点画成大点；选中某个顶点时点大小脉冲放大
        if (sel > 0) {
            float pulse = 30f + 26f * (0.5f + 0.5f * (float) Math.sin(mTime * 6f));
            mTriProg.set("u_psize", pulse);
        } else {
            mTriProg.set("u_psize", 26f);
        }
        mTri.draw(GLES30.GL_POINTS);

        GLES30.glViewport(0, 0, mWidth, mHeight);
    }

    private void drawTitle(int tex, float y) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex);
        float h = 0.05f;
        float w = Math.min(1.96f, h * (1280f / 96f));
        mShapes.tex(0, y, w, h, 0);
    }

    private void drawLegend(float x, float y) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mLegendPos);
        float h = 0.05f, w = h * (320f / 96f);
        mShapes.tex(x, y, w, h, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mLegendCol);
        mShapes.tex(x + 0.30f, y, w, h, 0);
    }

    /**
     * 交错布局内存条：18 个方块一长条。
     * groupSize=6（一个顶点块），用于选中高亮分组。
     */
    private void drawMemoryStrip(float cy, int count, int groupSize, int sel, boolean inter) {
        float totalW = 1.96f;
        float gap = 0.008f;
        float cell = (totalW - gap * (count - 1)) / count;
        float cellH = 0.16f;
        for (int i = 0; i < count; i++) {
            int vertex = i / groupSize;
            boolean inSel = (sel == 0) || (sel - 1 == vertex);
            boolean isPos = (i % groupSize) < 3;

            float cx = -0.98f + cell / 2f + i * (cell + gap);
            // 底色：位置蓝系 / 颜色用该分量数值本身着色
            float r, g, b;
            if (isPos) {
                r = 0.16f; g = 0.32f; b = 0.55f;
            } else {
                float v = Math.max(0f, Math.min(1f, VERTS[i]));
                r = v; g = v; b = v;
                int comp = i % 3;
                if (comp == 0) { r = v; g = v * 0.3f; b = v * 0.3f; }
                else if (comp == 1) { r = v * 0.3f; g = v; b = v * 0.3f; }
                else { r = v * 0.3f; g = v * 0.3f; b = v; }
            }
            float scale = inSel ? 1.25f : 1f;
            mShapes.rect(cx, cy, cell * scale, cellH * scale,
                    inSel ? Math.min(1, r + 0.25f) : r,
                    inSel ? Math.min(1, g + 0.25f) : g,
                    inSel ? Math.min(1, b + 0.25f) : b, inSel ? 1f : 0.8f);
            mShapes.frame(cx, cy, cell * scale, cellH * scale, 0.004f,
                    inSel ? 1f : 0.5f, inSel ? 1f : 0.55f, inSel ? 1f : 0.6f,
                    inSel ? 1f : 0.35f);

            if (getBool(KEY_VALS) && inSel) {
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mValTex[i]);
                float th = cellH * 0.55f;
                float tw = th * (256f / 96f);
                mShapes.tex(cx, cy + cellH * 0.95f, Math.min(tw, cell * 2.4f), th, 0);
            }
        }
        // 顶点分组括线：每组下画一条 + v0/v1/v2 标注
        for (int v = 0; v < 3; v++) {
            float x0 = -0.98f + v * groupSize * (cell + gap);
            float x1 = x0 + groupSize * cell + (groupSize - 1) * gap;
            float yLine = cy - cellH * 0.75f;
            mShapes.rect((x0 + x1) / 2f, yLine, x1 - x0, 0.006f, 0.5f, 0.6f, 0.7f,
                    sel == 0 || sel - 1 == v ? 0.9f : 0.3f);
        }
    }

    /** 分离布局的带标题数据条。kind 0=位置 1=颜色。 */
    private void drawLabeledStrip(int titleTex, float cy, int base, int count,
                                  int groupSize, int kind, int sel) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, titleTex);
        float h = 0.05f;
        mShapes.tex(-0.35f, cy + 0.14f, Math.min(1.5f, h * (1280f / 96f)), h, 0);

        float totalW = 1.70f;
        float gap = 0.01f;
        float cell = (totalW - gap * (count - 1)) / count;
        float cellH = 0.15f;
        for (int i = 0; i < count; i++) {
            int vertex = i / groupSize;
            boolean inSel = (sel == 0) || (sel - 1 == vertex);
            int globalIdx = base + vertex * 6 + (i % groupSize);
            float cx = -0.90f + cell / 2f + i * (cell + gap);
            float r, g, b;
            if (kind == 0) {
                r = 0.16f; g = 0.32f; b = 0.55f;
            } else {
                float v = Math.max(0f, Math.min(1f, VERTS[globalIdx]));
                int comp = i % 3;
                if (comp == 0) { r = v; g = v * 0.3f; b = v * 0.3f; }
                else if (comp == 1) { r = v * 0.3f; g = v; b = v * 0.3f; }
                else { r = v * 0.3f; g = v * 0.3f; b = v; }
            }
            float scale = inSel ? 1.22f : 1f;
            mShapes.rect(cx, cy, cell * scale, cellH * scale,
                    inSel ? Math.min(1, r + 0.25f) : r,
                    inSel ? Math.min(1, g + 0.25f) : g,
                    inSel ? Math.min(1, b + 0.25f) : b, inSel ? 1f : 0.8f);
        }
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        List<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.optionSpec(KEY_LAYOUT, "内存布局", LAYOUT_LABELS, 0));
        specs.add(ParamSpec.optionSpec(KEY_SEL, "选中顶点", SEL_LABELS, 0));
        specs.add(ParamSpec.boolSpec(KEY_VALS, "显示数值", true));
        specs.add(ParamSpec.floatSpec(KEY_SPIN, "三角形自转", 0f, 2f, 0.6f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        for (int i = 0; i < 18; i++) {
            TextureHelper.deleteTexture(mValTex[i]);
        }
        TextureHelper.deleteTexture(mTitleInterleaved);
        TextureHelper.deleteTexture(mTitleSeparate);
        TextureHelper.deleteTexture(mTitlePos);
        TextureHelper.deleteTexture(mTitleCol);
        TextureHelper.deleteTexture(mLegendPos);
        TextureHelper.deleteTexture(mLegendCol);
        mShapes.release();
        mTri.dispose();
        mTriProg.release();
    }
}
