package com.example.studyopengl.demos;

import android.opengl.GLES30;

import com.example.studyopengl.engine.BaseDemoEngine;
import com.example.studyopengl.gl.FlatShapes;
import com.example.studyopengl.gl.TextureHelper;
import com.example.studyopengl.param.ParamSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 32 · 片元测试与上屏：四道关卡 + 双缓冲
 *
 * 上半屏【闯关动画】：一个片元从左侧出发，依次经过 4 道测试关卡
 * （裁剪框→模板→深度→混合，顺序固定不可换）。哪道关被你关掉/判负，
 * 片元就在哪里倒下（变红坠落重来）；全部通过才能写入右侧帧缓冲格子。
 *
 * 下半屏【双缓冲】：左边后缓冲一格一格填充（GPU 正在画的画面），
 * 填满瞬间与右边前缓冲\"交换\"——屏幕只显示前缓冲，交换在 vsync 瞬间完成，
 * 所以你永远看不到\"画了一半\"的画面（防撕裂的原理）。
 */
public class D32TestsAndSwap extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍片元写进屏幕前的最后四道关（流水线第 7 站）\n"
            + "片元着色器算完颜色后【不是直接上屏】，而是按固定顺序过四道测试：\n"
            + "① Scissor 裁剪框：只许画进指定矩形（像给屏幕贴了个遮罩胶带），"
            + "glClear 也受它管；\n"
            + "② Stencil 模板：用之前画进模板缓冲的形状当\"镂空模板\"，"
            + "只有模板允许的位置能画（经典用法：描边、镜面范围）；\n"
            + "③ Depth 深度：拿片元深度和深度缓冲里已存的值比（默认\"更近者胜\"），"
            + "输了直接出局——这就是\"近处物体遮挡远处物体\"的实现机制；\n"
            + "④ Blend 混合：过关 survivor 与帧缓冲里已有的颜色按公式混合"
            + "（半透明的实现：C = 源色×α + 旧色×(1-α)）。\n"
            + "顺序【固定不可换】（硬件流水线决定）。任何一道失败 = 片元被丢弃，"
            + "后面的测试统统跳过。\n\n"
            + "▍Early-Z：聪明的深度测试\n"
            + "现代 GPU 会在片元着色器【之前】先做深度测试（early-z）："
            + "被挡住的片元连 shader 都不用跑，省掉大量计算——前提是\"不透明物体从近到远画\"。"
            + "但 FS 里用了 discard 或手改深度，early-z 就失效（硬件不敢提前判死刑）。\n\n"
            + "▍双缓冲：为什么看不到\"画一半\"的画面\n"
            + "屏幕 60 次/秒刷新，GPU 画一帧需要时间——如果直接画在\"正在显示\"的那块上，"
            + "刷新瞬间可能拿到半新半旧的画面（撕裂）。解法：两块缓冲轮流用：\n"
            + "· 后缓冲：GPU 正在画（下半屏左边，一格一格填充）；\n"
            + "· 前缓冲：屏幕正在显示；\n"
            + "· 一帧画完 → eglSwapBuffers 交换两块（下半屏的 SWAP 闪动）；\n"
            + "交换对齐 vsync（垂直同步）信号——和 Android 的 Choreographer/"
            + "SurfaceFlinger 是同一套思想：App 画离屏 buffer，合成器 vsync 时上屏。\n\n"
            + "▍动手玩\n"
            + "把关卡开关逐个关掉，看片元在哪里倒下；把\"深度比较\"切到失败，"
            + "观察片元倒在第 3 关；加速填充观察双缓冲交换节奏。";

    private static final String KEY_SCISSOR = "scissor";
    private static final String KEY_STENCIL = "stencil";
    private static final String KEY_DEPTH = "depth";
    private static final String KEY_BLEND = "blend";
    private static final String KEY_SPEED = "speed";

    /** 代码示例：四道测试关卡 + 双缓冲渲染循环（// 【N】 为跳转锚点）。 */
    public static final String CODE = ""
            + "// 片元过四道关：这些都是\"状态开关\"——设一次，之后每个片元都按它来。\n"
            + "// 点屏幕上的关卡柱切换通过/失败，对应代码段高亮。\n"
            + "\n"
            + "// 【0】第①关 Scissor 裁剪框：像素级矩形遮罩\n"
            + "GLES30.glEnable(GLES30.GL_SCISSOR_TEST);\n"
            + "GLES30.glScissor(0, 0, width / 2, height);  // 只许画左半屏\n"
            + "GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT); // 注意：清屏也只清这个矩形！\n"
            + "// 用途：分屏小地图、部分重绘。用完记得 glDisable。\n"
            + "\n"
            + "// 【1】第②关 Stencil 模板：用之前画好的形状当\"镂空纸\"\n"
            + "GLES30.glEnable(GLES30.GL_STENCIL_TEST);\n"
            + "GLES30.glStencilFunc(GLES30.GL_EQUAL, 1, 0xFF); // 模板值==1 才放行\n"
            + "GLES30.glStencilOp(GLES30.GL_KEEP, GLES30.GL_KEEP, GLES30.GL_KEEP);\n"
            + "// 经典用法：先画一个圆写入模板值 1，再用 EQUAL 只在圆内画镜子内容\n"
            + "// （描边、传送门、小地图遮罩都靠它，见第 16 课）\n"
            + "\n"
            + "// 【2】第③关 Depth 深度：\"更近者胜\"的遮挡机制\n"
            + "GLES30.glEnable(GLES30.GL_DEPTH_TEST);\n"
            + "GLES30.glDepthFunc(GLES30.GL_LESS);   // 新片元更近才通过(默认)\n"
            + "GLES30.glDepthMask(true);             // 深度写入开关(画半透明时关掉)\n"
            + "// 性能关键：现代 GPU 在片元着色器【之前】先测深度(early-z)，\n"
            + "// 被挡住的片元连 shader 都不跑——所以不透明物体要从近到远画！\n"
            + "\n"
            + "// 【3】第④关 Blend 混合：半透明的实现\n"
            + "GLES30.glEnable(GLES30.GL_BLEND);\n"
            + "GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,      // 新色 × 它的alpha\n"
            + "        GLES30.GL_ONE_MINUS_SRC_ALPHA);      // 旧色 × (1-alpha)\n"
            + "// 公式：最终色 = 新色×a + 旧色×(1-a)。a=0.5 就是一半透明。\n"
            + "// 铁律：混合时要 glDepthMask(false)，且从远到近画。\n"
            + "\n"
            + "// 【4】渲染循环与双缓冲：每帧都是这三步\n"
            + "while (true) {\n"
            + "    GLES30.glClear(COLOR | DEPTH);   // 1. 清空后缓冲(整块画布擦净)\n"
            + "    glDrawScene();                   // 2. 所有物体画进后缓冲\n"
            + "    eglSwapBuffers(display, surface);// 3. 后缓冲↔前缓冲 整屏交换\n"
            + "}\n"
            + "// 交换在 vsync 信号瞬间完成，屏幕只显示完整的前缓冲——\n"
            + "// 这就是为什么你永远看不到\"画了一半\"的画面(防撕裂)。\n"
            + "// GLThread 里用 Choreographer 对齐 vsync，和 Android 刷新机制同源。";

    private static final String[] DEPTH_LABELS = {"通过（更近者胜）", "失败（被挡住）"};

    private FlatShapes mShapes;
    private final boolean[] mGateTouched = new boolean[4];
    private final boolean[] mGateState = {true, true, true, true};
    private final int[] mGateTex = new int[4];
    private int mFbTex;
    private int mBackTex;
    private int mFrontTex;
    private int mSwapTex;

    // 关卡几何
    private static final float TRACK_Y = 0.60f;
    private static final float[] GATE_X = {-0.52f, -0.14f, 0.24f, 0.62f};
    private static final float FRAG_X0 = -0.92f;
    private static final float FRAG_X1 = 0.78f;

    private float mFragT;        // 0..1 旅程进度
    private int mFbFilled;       // 已点亮的帧缓冲格子
    private float mFailFlash;    // 坠落闪烁
    private int mFailGate = -1;
    private float mFillT;        // 双缓冲填充进度(格数,浮点)
    private int mFrontPattern;   // 前缓冲当前显示的填充数
    private float mSwapFlash;

    private static final int FB_COLS = 4;
    private static final int FB_ROWS = 4;
    private static final int DB_COLS = 6;
    private static final int DB_ROWS = 3;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mShapes = new FlatShapes();
        String[] gates = {
                "① 裁剪框", "② 模板", "③ 深度", "④ 混合"
        };
        for (int i = 0; i < 4; i++) {
            mGateTex[i] = TextureHelper.createTextTexture(gates[i], 280, 100);
        }
        mFbTex = TextureHelper.createTextTexture("帧缓冲", 240, 100);
        mBackTex = TextureHelper.createTextTexture("后缓冲·GPU正在画", 760, 100);
        mFrontTex = TextureHelper.createTextTexture("前缓冲·屏幕显示", 760, 100);
        mSwapTex = TextureHelper.createTextTexture("SWAP!", 300, 110);
    }

    /** 点关卡柱：切换该道测试通过/失败（覆盖参数面板），并跳到对应代码段。 */
    @Override
    public void onTouch(int action, float x, float y) {
        if (action != android.view.MotionEvent.ACTION_DOWN) return;
        float xNdc = 2f * x / mWidth - 1f;
        float yNdc = 1f - 2f * y / mHeight;
        if (yNdc < 0.28f || yNdc > 0.95f) return; // 只响应上半屏关卡区
        for (int i = 0; i < 4; i++) {
            if (Math.abs(xNdc - GATE_X[i]) < 0.06f) {
                mGateTouched[i] = true;
                mGateState[i] = !gatePasses(i);
                fireCodeSection(i);
                break;
            }
        }
    }

    private boolean gatePasses(int i) {
        if (mGateTouched[i]) {
            return mGateState[i]; // 点按开关优先于参数面板
        }
        switch (i) {
            case 0:
                return getBool(KEY_SCISSOR);
            case 1:
                return getBool(KEY_STENCIL);
            case 2:
                return getOptionIndex(KEY_DEPTH) == 0;
            default:
                return getBool(KEY_BLEND);
        }
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        float speed = getFloat(KEY_SPEED);
        mFailFlash = Math.max(0f, mFailFlash - deltaTime * 1.8f);
        mSwapFlash = Math.max(0f, mSwapFlash - deltaTime * 2.2f);

        // ── 片元闯关推进 ──
        mFragT += deltaTime * speed * 0.30f;
        float fx = FRAG_X0 + (FRAG_X1 - FRAG_X0) * mFragT;
        int hitGate = -1;
        for (int i = 0; i < 4; i++) {
            if (fx >= GATE_X[i]) {
                hitGate = i;
                break;
            }
        }
        if (hitGate >= 0 && !gatePasses(hitGate) && mFailFlash <= 0f && mFragT < 0.999f) {
            mFailFlash = 1f;
            mFailGate = hitGate;
            mFragT = 0f; // 重生
        }
        if (mFragT >= 1f) {
            if (mFbFilled < FB_COLS * FB_ROWS) {
                mFbFilled++;
            } else {
                mFbFilled = 1;
            }
            mFragT = 0f;
        }

        // ── 双缓冲填充 ──
        int total = DB_COLS * DB_ROWS;
        mFillT += deltaTime * speed * 10f;
        if (mFillT >= total) {
            mFrontPattern = total;
            mFillT = 0f;
            mSwapFlash = 1f;
        }

        // ═════════ 绘制 ═════════
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glViewport(0, 0, mWidth, mHeight);
        GLES30.glClearColor(0.04f, 0.05f, 0.09f, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

        // ── 上半屏：轨道 + 关卡 ──
        mShapes.rect(0, TRACK_Y, 1.9f, 0.012f, 0.25f, 0.3f, 0.4f, 0.8f);

        for (int i = 0; i < 4; i++) {
            boolean open = gatePasses(i);
            boolean isFail = (i == mFailGate && mFailFlash > 0f);
            // 关卡柱：绿=通过 红=拒绝
            float gr = open ? 0.20f : 0.45f;
            float gg = open ? 0.85f : 0.20f;
            float gb = open ? 0.45f : 0.20f;
            float lift = isFail ? 0.25f * mFailFlash : 0f;
            float gh = 0.30f + lift * 0.2f;
            mShapes.rect(GATE_X[i], TRACK_Y, 0.055f, gh,
                    Math.min(1f, gr + lift), gg, gb, 1f);
            mShapes.frame(GATE_X[i], TRACK_Y, 0.055f, gh, 0.006f, 1f, 1f, 1f, 0.35f);
            // 关卡名
            bindTex(mGateTex[i]);
            float th = 0.055f;
            float tw = th * (280f / 100f);
            mShapes.tex(GATE_X[i], TRACK_Y + gh / 2f + th, tw, th, 0);
        }

        // 帧缓冲格子（最右）
        float fbX = 0.845f;
        float cell = 0.055f;
        float fbW = FB_COLS * cell + (FB_COLS - 1) * 0.008f;
        float fbH = FB_ROWS * cell + (FB_ROWS - 1) * 0.008f;
        mShapes.frame(fbX, TRACK_Y, fbW, fbH, 0.008f, 0.8f, 0.85f, 0.95f, 0.9f);
        for (int r = 0; r < FB_ROWS; r++) {
            for (int c = 0; c < FB_COLS; c++) {
                int idx = r * FB_COLS + c;
                boolean on = idx < mFbFilled;
                float x = fbX - fbW / 2f + cell / 2f + c * (cell + 0.008f);
                float y = TRACK_Y + fbH / 2f - cell / 2f - r * (cell + 0.008f);
                mShapes.rect(x, y, cell, cell,
                        on ? 0.35f : 0.10f, on ? 0.90f : 0.13f,
                        on ? 0.60f : 0.18f, on ? 1f : 0.7f);
            }
        }
        bindTex(mFbTex);
        mShapes.tex(fbX, TRACK_Y + fbH / 2f + 0.045f, 0.16f, 0.05f, 0);

        // 片元本体（黄色方块）
        float fragY = TRACK_Y + (mFailFlash > 0f
                ? -0.4f * (1f - mFailFlash) : 0f);
        float fs = 0.052f * (1f + (mFailFlash > 0f ? mFailFlash * 0.6f : 0f));
        mShapes.rect(fx, fragY, fs, fs,
                mFailFlash > 0f ? 1f : 1f,
                mFailFlash > 0f ? 0.25f : 0.85f,
                mFailFlash > 0f ? 0.2f : 0.25f, 1f);
        // 失败十字闪
        if (mFailFlash > 0f && mFailGate >= 0) {
            mShapes.frame(GATE_X[mFailGate], TRACK_Y,
                    0.14f * (2f - mFailFlash), 0.5f * (2f - mFailFlash),
                    0.012f, 1f, 0.3f, 0.2f, mFailFlash);
        }

        // ── 下半屏：双缓冲 ──
        drawDbGrid(-0.5f, -0.42f, (int) mFillT, mFillT - (int) mFillT, false);
        drawDbGrid(0.5f, -0.42f, mFrontPattern, 0f, true);

        // SWAP 箭头与闪光
        float swapA = 0.35f + mSwapFlash * 0.65f;
        mShapes.rect(0, -0.42f, 0.42f, 0.02f, 0.4f, 0.9f, 1f, swapA);
        mShapes.triDown(0.24f, -0.42f, 0.07f, 0.05f, 0.4f, 0.9f, 1f, swapA);
        if (mSwapFlash > 0f) {
            bindTex(mSwapTex);
            float sh = 0.09f * (1f + mSwapFlash * 0.4f);
            mShapes.tex(0, -0.42f + 0.14f, sh * (300f / 110f), sh, 0);
        }
    }

    /** 双缓冲格子图：fillCount 个已填 + 当前格按 frac 渐亮。 */
    private void drawDbGrid(float cx, float cy, int fillCount, float frac, boolean front) {
        float cell = 0.10f;
        float gap = 0.012f;
        float w = DB_COLS * cell + (DB_COLS - 1) * gap;
        float h = DB_ROWS * cell + (DB_ROWS - 1) * gap;
        // 标题
        bindTex(front ? mFrontTex : mBackTex);
        float th = 0.05f;
        mShapes.tex(cx, cy + h / 2f + th * 0.9f,
                Math.min(0.8f, th * (760f / 100f)), th, 0);
        // 外框（前缓冲亮一些——屏幕正在显示）
        mShapes.frame(cx, cy, w, h, 0.008f,
                front ? 1f : 0.45f, front ? 0.85f : 0.65f, front ? 0.35f : 0.95f,
                front ? 0.95f : 0.6f);
        int total = DB_COLS * DB_ROWS;
        for (int r = 0; r < DB_ROWS; r++) {
            for (int c = 0; c < DB_COLS; c++) {
                int idx = r * DB_COLS + c;
                boolean on = idx < fillCount;
                boolean partial = idx == fillCount && frac > 0f && !front;
                float x = cx - w / 2f + cell / 2f + c * (cell + gap);
                float y = cy + h / 2f - cell / 2f - r * (cell + gap);
                float br = on ? 0.35f : partial ? 0.15f + 0.2f * frac : 0.09f;
                float bg = on ? 0.80f : partial ? 0.35f + 0.35f * frac : 0.12f;
                float bb = on ? 0.95f : partial ? 0.45f + 0.4f * frac : 0.17f;
                mShapes.rect(x, y, cell, cell, br, bg, bb, 1f);
            }
        }
        // 后缓冲画完满格即待交换提示
        if (!front && fillCount >= total) {
            mShapes.frame(cx, cy, w + 0.04f, h + 0.04f, 0.01f, 1f, 0.9f, 0.4f, 0.8f);
        }
    }

    private void bindTex(int tex) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        List<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.boolSpec(KEY_SCISSOR, "① 裁剪框：通过", true));
        specs.add(ParamSpec.boolSpec(KEY_STENCIL, "② 模板：通过", true));
        specs.add(ParamSpec.optionSpec(KEY_DEPTH, "③ 深度比较结果", DEPTH_LABELS, 0));
        specs.add(ParamSpec.boolSpec(KEY_BLEND, "④ 混合：通过", true));
        specs.add(ParamSpec.floatSpec(KEY_SPEED, "动画速度", 0.3f, 3f, 1f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        for (int i = 0; i < 4; i++) {
            TextureHelper.deleteTexture(mGateTex[i]);
        }
        TextureHelper.deleteTexture(mFbTex);
        TextureHelper.deleteTexture(mBackTex);
        TextureHelper.deleteTexture(mFrontTex);
        TextureHelper.deleteTexture(mSwapTex);
        mShapes.release();
    }
}
