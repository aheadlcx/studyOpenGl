package com.example.studyopengl.demos;

import android.opengl.GLES30;
import android.opengl.Matrix;

import com.example.studyopengl.engine.BaseDemoEngine;
import com.example.studyopengl.gl.FlatShapes;
import com.example.studyopengl.gl.Mesh;
import com.example.studyopengl.gl.ShaderProgram;
import com.example.studyopengl.gl.TextureHelper;
import com.example.studyopengl.param.ParamSpec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 30 · 光栅化：三角形怎么变成像素（放大镜对比）
 *
 * 行1：正常渲染的渐变三角形，白框标出"放大区域"。
 * 行2：GPU 放大镜——同一区域变焦渲染，颜色是【连续平滑】的。
 * 行3：CPU 模拟光栅化——同一区域铺成 n×n "片元方块"，每格颜色用重心坐标
 *      插值算出（和 GPU 干的事一模一样），看出【离散采样】的本质。
 * 对比行2/行3：GPU 的平滑渐变其实是一个个小方格各自取一个颜色。
 */
public class D30Rasterizer extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍光栅化在干嘛？（流水线第 5 站前半）\n"
            + "顶点着色器只给出了 3 个\"屏幕坐标点\"，光栅化负责把三角形\"铺\"成像素："
            + "找出这个三角形覆盖了哪些像素格，每个被覆盖的格生成一个【片元(fragment)】——"
            + "注意术语：片元=候选像素（还没通过测试），通过了才叫像素。"
            + "Android 类比：VectorDrawable 矢量图光栅化成 Bitmap 位图，"
            + "矢量=连续数学描述，位图=离散像素阵列。\n\n"
            + "▍放大镜看什么（本界面三行视口）\n"
            + "行1：正常视图。白框是我们放大的区域。\n"
            + "行2：GPU 真实渲染放大后的样子——颜色渐变连续平滑，毫无瑕疵。\n"
            + "行3：CPU 模拟\"GPU 实际做的事\"——把同一区域切成 n×n 个小方块，"
            + "每格只取【中心点】算一次颜色（采样点），整格填这一种色。"
            + "两行一对比真相大白：所谓平滑渐变，其实是一个个小方块各自一种颜色！"
            + "方格感调\"密度\"滑条：格子越少越马赛克，越多越平滑——"
            + "这正是\"分辨率\"的含义。\n\n"
            + "▍插值：颜色渐变是白送的\n"
            + "三个顶点各有颜色（红/绿/蓝），光栅化硬件对每个片元自动算出"
            + "\"三个顶点各占多少权重\"（重心坐标）：离哪个顶点近，那个权重就大，"
            + "颜色 = 权重加权和。这一步发生在片元着色器【之前】，不花你一行代码——"
            + "所以顶点色渐变、UV 坐标传递都是免费的。纹理不扭曲也是它的功劳："
            + "插值是\"透视校正\"的（除以 w 后插值再乘回去）。\n\n"
            + "▍覆盖判定与采样点\n"
            + "一个像素算不算\"被三角形覆盖\"？OpenGL 的默认规则是【中心采样】："
            + "像素中心点落在三角形内才算覆盖。行3 每格中心的暗点就是采样点——"
            + "边缘处有的格子中心在内、有的在外，这就是锯齿的来源"
            + "（MSAA 的解法：一个像素放 4~8 个采样点再平均，见第 23 课）。\n\n"
            + "▍性能直觉\n"
            + "三角形越大覆盖像素越多 → 片元越多 → 片元着色器执行次数越多。"
            + "全屏三角形=百万级片元。所以\"贴着屏幕画的大面\"最贵，"
            + "能合并的 overdraw 要省（不透明物体从近到远画）。";

    /** 三角形顶点（世界比例坐标，ortho(-a,a,-1,1)）。 */
    private static final float[] AX = {-1.15f, 1.05f, -0.15f};
    private static final float[] AY = {-0.60f, -0.35f, 0.85f};
    private static final float[] COLS = {
            1.00f, 0.30f, 0.25f,   // v0 红
            0.25f, 1.00f, 0.40f,   // v1 绿
            0.30f, 0.55f, 1.00f    // v2 蓝
    };

    private static final String KEY_SPOT = "spot";
    private static final String KEY_N = "n";
    private static final String KEY_SAMPLES = "samples";
    private static final String KEY_SPIN = "spin";

    /** 代码示例：插值与采样——光栅化阶段 GPU 自动做的事。 */
    public static final String CODE = ""
            + "// 光栅化是纯硬件，你没有代码可写——但必须理解它做了什么。\n"
            + "// 在第一行视口内拖动手指，下面三个视口会实时跟着变。\n"
            + "\n"
            + "// 【0】顶点着色器：把顶点颜色交给光栅化（out）\n"
            + "out vec3 v_color;          // VS 里声明输出\n"
            + "v_color = a_color;         // 每个顶点带上自己的颜色\n"
            + "gl_Position = u_mvp * vec4(a_pos, 1.0);  // 顺带算好屏幕位置\n"
            + "\n"
            + "// 【1】硬件插值（CPU 模拟版，即行3干的事）\n"
            + "// 对三角形覆盖到的每个像素格：\n"
            + "//   1) 取像素【中心点】(x+0.5, y+0.5) 判断是否在三角形内\n"
            + "//   2) 在内 → 用重心坐标算三个顶点的权重 (w1,w2,w3)，w1+w2+w3=1\n"
            + "w1 = ((y2-y3)*(px-x3) + (x3-x2)*(py-y3)) / det;   // Java 版见本Demo\n"
            + "v_color = w1*c1 + w2*c2 + w3*c3;   // 权重加权 → 该像素的颜色\n"
            + "// 行2(GPU)与行3(CPU)就是同一件事：GPU 全并行、每帧百万次。\n"
            + "\n"
            + "// 【2】片元着色器：插值结果作为输入（in）\n"
            + "in vec3 v_color;           // 收到的已经是插好的颜色\n"
            + "// 纹理同理：VS 传 v_uv，FS 拿到的 v_uv 也是插值过的，\n"
            + "// texture(u_tex, v_uv) 采样位置因此精确贴合几何——\n"
            + "// 且是【透视校正】插值（除 w 后插再乘回），贴图不会歪。\n"
            + "\n"
            + "// 【3】采样规则：锯齿与 MSAA 的根源\n"
            + "// 默认【中心采样】：中心点在图元内 → 生成片元；在外 → 不生成。\n"
            + "// 边缘像素\"一半在内一半在外\"只能二选一 → 台阶感(锯齿)。\n"
            + "// 行3 打开\"显示采样点\"能看到每格中心的黑点。\n"
            + "// 解法：MSAA 每像素放 4~8 个采样点取平均(第 31 课)。";

    private static final String[] SPOT_LABELS = {"角 v0（红）", "角 v1（绿）", "角 v2（蓝）", "底边中点"};

    private ShaderProgram mProg;
    private ShaderProgram mDotProg;   // 采样点专用（固定深色）
    private FlatShapes mShapes;
    private float[] mDragCenter;      // 拖动的放大区域中心（世界坐标）
    private Mesh mTri;
    private final float[] mOrtho = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mMvp = new float[16];
    private final int[] mLabelTex = new int[3];

    // CPU 片元网格：pos 静态 + color 每帧更新
    private int mVao;
    private int mPosVbo;
    private int mColVbo;
    private int mSampleVao;
    private int mSampleVbo;
    private int mGridCount;          // 方块数
    private FloatBuffer mColScratch;
    private float[] mRotVerts = new float[18];
    private int mBuiltN = -1;
    private int mBuiltSpot = -1;
    private float mTime;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProg = new ShaderProgram(
                "#version 300 es\n"
                        + "layout(location=0) in vec2 a_pos;\n"
                        + "layout(location=1) in vec4 a_color;\n"
                        + "uniform mat4 u_mvp;\n"
                        + "out vec4 v_color;\n"
                        + "void main() {\n"
                        + "    v_color = a_color;\n"
                        + "    gl_Position = u_mvp * vec4(a_pos, 0.0, 1.0);\n"
                        + "}\n",
                "#version 300 es\n"
                        + "precision mediump float;\n"
                        + "in vec4 v_color;\n"
                        + "out vec4 fragColor;\n"
                        + "void main() { fragColor = v_color; }\n");
        mDotProg = new ShaderProgram(
                "#version 300 es\n"
                        + "layout(location=0) in vec2 a_pos;\n"
                        + "void main() { gl_Position = vec4(a_pos, 0.0, 1.0); }\n",
                "#version 300 es\n"
                        + "precision mediump float;\n"
                        + "uniform vec4 u_color;\n"
                        + "out vec4 fragColor;\n"
                        + "void main() { fragColor = u_color; }\n");
        mShapes = new FlatShapes();

        String[] labels = {
                "① 正常视图   白框=放大区域",
                "② GPU 放大渲染   连续平滑（真实画面）",
                "③ CPU 模拟光栅化   离散片元方块（真相）"
        };
        for (int i = 0; i < 3; i++) {
            mLabelTex[i] = TextureHelper.createTextTexture(labels[i], 1000, 100);
        }
        rebuildGrid();
    }

    /** 放大区域中心：手指拖动优先，否则用参数枚举。 */
    private float[] spotCenter() {
        if (mDragCenter != null) {
            return mDragCenter;
        }
        switch (getOptionIndex(KEY_SPOT)) {
            case 0:
                return new float[]{AX[0], AY[0]};
            case 1:
                return new float[]{AX[1], AY[1]};
            case 2:
                return new float[]{AX[2], AY[2]};
            default:
                return new float[]{(AX[0] + AX[1]) / 2f, (AY[0] + AY[1]) / 2f};
        }
    }

    /** 在第一行视口内拖动 = 移动放大区域（行2/行3 的采样区域跟着走）。 */
    @Override
    public void onTouch(int action, float x, float y) {
        if (action != android.view.MotionEvent.ACTION_DOWN
                && action != android.view.MotionEvent.ACTION_MOVE) {
            return;
        }
        // 第一行视口的屏幕范围（与 beginRows 一致：避开6%标题，占56%的上40%）
        int topInset = Math.round(mHeight * 0.06f);
        int total = Math.round(mHeight * 0.56f);
        int h1 = Math.round(total * 0.40f);
        int rowTop = topInset;
        int rowBottom = topInset + h1;
        if (y < rowTop || y > rowBottom) return;

        // 屏幕 y → 视口 NDC y（GL 原点在左下）
        float vy = (y - rowTop) / (float) h1;         // 0=视口顶
        float yNdc = 1f - 2f * vy;
        float xNdc = 2f * x / mWidth - 1f;
        // NDC → 世界坐标（行1 ortho(-aspect,aspect,-1,1)）
        float aspect = (float) (mWidth - 6) / (float) (h1 - 6);
        float wx = xNdc * aspect;
        float wy = yNdc;
        if (mDragCenter == null) {
            mDragCenter = new float[]{wx, wy};
        } else {
            mDragCenter[0] = wx;
            mDragCenter[1] = wy;
        }
    }

    /** 行3 视口内 n×n 方块的静态 pos 与采样点 VBO。 */
    private void rebuildGrid() {
        int n = getInt(KEY_N);
        if (n == mBuiltN && getOptionIndex(KEY_SPOT) == mBuiltSpot) {
            return;
        }
        mBuiltN = n;
        mBuiltSpot = getOptionIndex(KEY_SPOT);

        releaseGrid();

        float aspect = aspect();
        float[] c = spotCenter();
        float h = 0.30f;                    // 区域半高（世界单位）
        float x0 = c[0] - h * aspect, x1 = c[0] + h * aspect;
        float y0 = c[1] - h, y1 = c[1] + h;

        // 行3 视口内：区域铺满 NDC [-0.96,0.96]²
        float ndc = 0.96f;
        float cell = (2f * ndc) / n;
        float shrink = 0.90f;               // 方块间留缝显格子感

        ArrayList<Float> pos = new ArrayList<Float>();
        ArrayList<Float> samp = new ArrayList<Float>();
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                float ndcX0 = -ndc + i * cell;
                float ndcY0 = -ndc + j * cell;
                float s = cell * shrink;
                float cx = ndcX0 + cell / 2f;
                float cy = ndcY0 + cell / 2f;
                // 单位矩形×尺寸+平移（6 顶点）
                pos.add(cx - s / 2f); pos.add(cy - s / 2f);
                pos.add(cx + s / 2f); pos.add(cy - s / 2f);
                pos.add(cx - s / 2f); pos.add(cy + s / 2f);
                pos.add(cx + s / 2f); pos.add(cy - s / 2f);
                pos.add(cx + s / 2f); pos.add(cy + s / 2f);
                pos.add(cx - s / 2f); pos.add(cy + s / 2f);
                // 采样点小方块（12%）
                float d = cell * 0.13f;
                samp.add(cx - d); samp.add(cy - d);
                samp.add(cx + d); samp.add(cy - d);
                samp.add(cx - d); samp.add(cy + d);
                samp.add(cx + d); samp.add(cy - d);
                samp.add(cx + d); samp.add(cy + d);
                samp.add(cx - d); samp.add(cy + d);
            }
        }
        mGridCount = n * n;
        float[] posArr = toArray(pos);
        float[] sampArr = toArray(samp);

        int[] bufs = new int[2];
        GLES30.glGenBuffers(2, bufs, 0);
        mPosVbo = bufs[0];
        mColVbo = bufs[1];

        int[] vaos = new int[1];
        GLES30.glGenVertexArrays(1, vaos, 0);
        mVao = vaos[0];
        GLES30.glBindVertexArray(mVao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mPosVbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, posArr.length * 4,
                FloatBuffer.wrap(posArr), GLES30.GL_STATIC_DRAW);
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mColVbo);
        mColScratch = ByteBuffer.allocateDirect(mGridCount * 6 * 4 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, mGridCount * 6 * 16,
                null, GLES30.GL_DYNAMIC_DRAW);
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, 16, 0);
        GLES30.glEnableVertexAttribArray(1);

        // 采样点（黑色半透明，静态）
        int[] svbo = new int[1];
        GLES30.glGenBuffers(1, svbo, 0);
        mSampleVbo = svbo[0];
        int[] svao = new int[1];
        GLES30.glGenVertexArrays(1, svao, 0);
        mSampleVao = svao[0];
        GLES30.glBindVertexArray(mSampleVao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mSampleVbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, sampArr.length * 4,
                FloatBuffer.wrap(sampArr), GLES30.GL_STATIC_DRAW);
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0);
        GLES30.glEnableVertexAttribArray(0);

        GLES30.glBindVertexArray(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
    }

    private void releaseGrid() {
        if (mPosVbo != 0) {
            GLES30.glDeleteBuffers(2, new int[]{mPosVbo, mColVbo}, 0);
            GLES30.glDeleteVertexArrays(1, new int[]{mVao}, 0);
            mPosVbo = 0;
            mColVbo = 0;
            mVao = 0;
        }
        if (mSampleVbo != 0) {
            GLES30.glDeleteBuffers(1, new int[]{mSampleVbo}, 0);
            GLES30.glDeleteVertexArrays(1, new int[]{mSampleVao}, 0);
            mSampleVbo = 0;
            mSampleVao = 0;
        }
    }

    private static float[] toArray(ArrayList<Float> list) {
        float[] a = new float[list.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = list.get(i);
        }
        return a;
    }

    /** 当前帧旋转后的三角形顶点（CPU/GPU 用同一份数据）。 */
    private void computeRotatedVerts(float angleRad) {
        float cx = (AX[0] + AX[1] + AX[2]) / 3f;
        float cy = (AY[0] + AY[1] + AY[2]) / 3f;
        float cos = (float) Math.cos(angleRad);
        float sin = (float) Math.sin(angleRad);
        for (int v = 0; v < 3; v++) {
            float dx = AX[v] - cx;
            float dy = AY[v] - cy;
            mRotVerts[v * 6] = cx + dx * cos - dy * sin;
            mRotVerts[v * 6 + 1] = cy + dx * sin + dy * cos;
            mRotVerts[v * 6 + 2] = 0;
            mRotVerts[v * 6 + 3] = COLS[v * 3];
            mRotVerts[v * 6 + 4] = COLS[v * 3 + 1];
            mRotVerts[v * 6 + 5] = COLS[v * 3 + 2];
        }
    }

    /** CPU 重心插值：点 p 对旋转后三角形的 (覆盖?, r,g,b)。 */
    private float[] barycentric(float px, float py) {
        float x1 = mRotVerts[0], y1 = mRotVerts[1];
        float x2 = mRotVerts[6], y2 = mRotVerts[7];
        float x3 = mRotVerts[12], y3 = mRotVerts[13];
        float det = (y2 - y3) * (x1 - x3) + (x3 - x2) * (y1 - y3);
        if (Math.abs(det) < 1e-8f) {
            return new float[]{0, 0, 0, 0};
        }
        float w1 = ((y2 - y3) * (px - x3) + (x3 - x2) * (py - y3)) / det;
        float w2 = ((y3 - y1) * (px - x3) + (x1 - x3) * (py - y3)) / det;
        float w3 = 1f - w1 - w2;
        boolean inside = w1 >= 0f && w2 >= 0f && w3 >= 0f;
        float r = w1 * COLS[0] + w2 * COLS[3] + w3 * COLS[6];
        float g = w1 * COLS[1] + w2 * COLS[4] + w3 * COLS[7];
        float b = w1 * COLS[2] + w2 * COLS[5] + w3 * COLS[8];
        return new float[]{inside ? 1f : 0f, Math.max(0, r), Math.max(0, g), Math.max(0, b)};
    }

    @Override
    public void onParamChanged(ParamSpec spec) {
        if (spec.key.equals(KEY_N) || spec.key.equals(KEY_SPOT)) {
            rebuildGrid();
        }
    }

    /** 重建三角形 mesh（旋转在 model 矩阵里做，mesh 静态）。 */
    private void ensureMesh() {
        if (mTri != null) return;
        float[] verts = new float[18];
        for (int v = 0; v < 3; v++) {
            verts[v * 6] = AX[v];
            verts[v * 6 + 1] = AY[v];
            verts[v * 6 + 2] = 0;
            verts[v * 6 + 3] = COLS[v * 3];
            verts[v * 6 + 4] = COLS[v * 3 + 1];
            verts[v * 6 + 5] = COLS[v * 3 + 2];
        }
        mTri = new Mesh.Builder()
                .addBuffer(verts, new Mesh.Attrib(0, 2), new Mesh.Attrib(1, 4))
                .build();
    }

    /** 视口分三行（高 0.40 / 0.26 / 0.34，位于屏幕上部 56% 内，避开 6% 标题）。 */
    private int[] beginRows() {
        int topInset = Math.round(mHeight * 0.06f);
        int total = Math.round(mHeight * 0.56f);
        int h1 = Math.round(total * 0.40f);
        int h2 = Math.round(total * 0.26f);
        int h3 = total - h1 - h2;
        int y1 = mHeight - topInset - h1;
        int y2 = y1 - h2;
        int y3 = y2 - h3;
        return new int[]{y1, h1, y2, h2, y3, h3};
    }

    private void drawLabel(int idx, int vx, int vy, int vw, int vh) {
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mLabelTex[idx]);
        float labelHpx = vh / 4.5f;
        float labelWpx = Math.min(vw * 0.9f, labelHpx * 10f);
        mShapes.tex(-0.98f + labelWpx / vw, 0.98f - labelHpx / vh,
                labelWpx * 2f / vw, labelHpx * 2f / vh, 0);
        GLES30.glDisable(GLES30.GL_BLEND);
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        mTime += deltaTime;
        float angle = mTime * getFloat(KEY_SPIN) * 0.8f;
        computeRotatedVerts(angle);
        ensureMesh();
        rebuildGridIfNeeded();

        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glClearColor(0.04f, 0.05f, 0.09f, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

        float a = aspect();
        float[] c = spotCenter();
        float h = 0.30f;
        int[] rows = beginRows();
        mProg.use();

        // ════ 行1：正常视图 + 放大区域白框 ════
        GLES30.glViewport(3, rows[0] + 3, mWidth - 6, rows[1] - 6);
        float vAspect1 = (float) (mWidth - 6) / (float) (rows[1] - 6);
        Matrix.orthoM(mOrtho, 0, -vAspect1, vAspect1, -1f, 1f, -1f, 1f);
        Matrix.setIdentityM(mModel, 0);
        Matrix.rotateM(mModel, 0, (float) Math.toDegrees(angle), 0, 0, 1);
        Matrix.multiplyMM(mMvp, 0, mOrtho, 0, mModel, 0);
        mProg.setMat4("u_mvp", mMvp);
        mTri.draw(GLES30.GL_TRIANGLES);
        // 白框：区域半宽(世界) = h*aspect，换算成本视口 NDC
        float cxN = c[0] / vAspect1;
        float wN = h * a / vAspect1;
        mShapes.frame(cxN, c[1], wN * 2f, h * 2f, 0.008f, 1f, 1f, 1f, 0.9f);
        drawLabel(0, 0, 0, mWidth - 6, rows[1] - 6);

        // ════ 行2：GPU 放大（ortho 窗口=放大区域）════
        GLES30.glViewport(3, rows[2] + 3, mWidth - 6, rows[3] - 6);
        float vAspect2 = (float) (mWidth - 6) / (float) (rows[3] - 6);
        Matrix.orthoM(mOrtho, 0, c[0] - h * a, c[0] + h * a, c[1] - h, c[1] + h, -1f, 1f);
        Matrix.multiplyMM(mMvp, 0, mOrtho, 0, mModel, 0);
        mProg.setMat4("u_mvp", mMvp);
        mTri.draw(GLES30.GL_TRIANGLES);
        drawLabel(1, 0, 0, mWidth - 6, rows[3] - 6);

        // ════ 行3：CPU 片元网格（动态色 VBO）════
        GLES30.glViewport(3, rows[4] + 3, mWidth - 6, rows[5] - 6);
        int n = mBuiltN;
        // 每格中心对应放大区域内的世界坐标
        mColScratch.clear();
        float x0 = c[0] - h * a, x1 = c[0] + h * a;
        float y0 = c[1] - h, y1 = c[1] + h;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                float wx = x0 + (i + 0.5f) * (x1 - x0) / n;
                float wy = y0 + (j + 0.5f) * (y1 - y0) / n;
                float[] res = barycentric(wx, wy);
                float r, g, b, al;
                if (res[0] > 0.5f) {
                    r = res[1]; g = res[2]; b = res[3]; al = 1f;
                } else {
                    r = 0.14f; g = 0.16f; b = 0.20f; al = 1f; // 未覆盖暗格
                }
                for (int k = 0; k < 6; k++) {
                    mColScratch.put(r).put(g).put(b).put(al);
                }
            }
        }
        mColScratch.position(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mColVbo);
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0,
                mGridCount * 6 * 16, mColScratch);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);

        GLES30.glBindVertexArray(mVao);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, mGridCount * 6);
        GLES30.glBindVertexArray(0);

        // 采样点（每个方格中心的暗色小方块 = 像素中心采样规则）
        if (getBool(KEY_SAMPLES)) {
            mDotProg.use();
            mDotProg.set("u_color", 0.05f, 0.06f, 0.09f, 0.85f);
            GLES30.glBindVertexArray(mSampleVao);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, mGridCount * 6);
            GLES30.glBindVertexArray(0);
        }
        drawLabel(2, 0, 0, mWidth - 6, rows[5] - 6);

        GLES30.glViewport(0, 0, mWidth, mHeight);
    }

    private void rebuildGridIfNeeded() {
        int n = getInt(KEY_N);
        if (n != mBuiltN || getOptionIndex(KEY_SPOT) != mBuiltSpot) {
            rebuildGrid();
        }
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        List<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.optionSpec(KEY_SPOT, "放大部位", SPOT_LABELS, 0));
        specs.add(ParamSpec.intSpec(KEY_N, "片元网格密度 n×n", 6, 26, 14));
        specs.add(ParamSpec.boolSpec(KEY_SAMPLES, "显示采样点", true));
        specs.add(ParamSpec.floatSpec(KEY_SPIN, "三角形缓转", 0f, 1.5f, 0.25f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        releaseGrid();
        if (mTri != null) mTri.dispose();
        for (int i = 0; i < 3; i++) {
            TextureHelper.deleteTexture(mLabelTex[i]);
        }
        mProg.release();
        mDotProg.release();
        mShapes.release();
    }
}
