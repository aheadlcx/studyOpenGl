package com.example.studyopengl.demos;

import android.opengl.GLES30;
import android.view.MotionEvent;

import com.example.studyopengl.engine.BaseDemoEngine;
import com.example.studyopengl.gl.FlatShapes;
import com.example.studyopengl.gl.TextureHelper;
import com.example.studyopengl.param.ParamSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 27 · 渲染管线全景（零基础向）
 *
 * 把 GPU 画成一条"工厂流水线"：7 道工序自上而下，橙色=可编程（写 shader），
 * 蓝色=固定功能（只能拧开关），灰色=CPU 侧工作。
 * 一个黄色"数据包"沿流水线流动，流到哪道工序哪道亮——
 * 直观展示一次 draw call 里顶点数据变成屏幕像素的完整旅程。
 */
public class D27PipelineJourney extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍先建立一个心智模型：GPU 是一家『画三角形的高速工厂』\n"
            + "你写的 Java 代码跑在 CPU 上，CPU 是\"设计师\"：准备好原料（顶点数据）、"
            + "写好两台机器的加工规则（着色器程序），然后把整批活儿一次性交给 GPU 工厂。"
            + "GPU 工厂内部是一条固定顺序的流水线，7 道工序中【只有 2 道可以编程】"
            + "（顶点着色器、片元着色器，橙色节点），其余 5 道是\"固定机器\"——"
            + "你不能改它的逻辑，只能拧它的旋钮（开关/函数/参数，蓝色节点）。\n\n"
            + "▍七道工序一句话版（自上而下对应屏幕上的流水线）\n"
            + "① 顶点数据（CPU→显存）：把 float 数组拷进 GPU 的显存(VBO)，"
            + "并登记\"每个顶点怎么读\"(VAO)。类比 RecyclerView：VBO=数据列表，"
            + "VAO=ViewHolder（记住每行哪几列是坐标、哪几列是颜色）。\n"
            + "② 顶点着色器【可编程】：对【每个顶点】执行一次你写的 GLSL 小程序，"
            + "算出它最终应出现在屏幕的什么位置（MVP 矩阵乘法）。类比：给每个 View "
            + "算 translationX/Y + scale + rotation，只不过用一次矩阵乘法全包了。\n"
            + "③ 图元装配：每 3 个顶点绑成一个三角形（按 drawMode），"
            + "背对相机的三角形直接丢弃（面剔除）。\n"
            + "④ 裁剪与透视除法：把摄像机视野(视锥)之外的部分砍掉；"
            + "再把坐标除以 w——\"近大远小\"的透视效果就发生在这一步（纯硬件行为）。\n"
            + "⑤ 视口变换与光栅化：把三角形\"铺\"成像素点阵，每个点叫一个片元(fragment)；"
            + "顶点带的颜色会被自动插值（渐变是白送的）。类比：VectorDrawable 矢量图"
            + "光栅化成 Bitmap 位图。\n"
            + "⑥ 片元着色器【可编程】：对【每个片元】执行一次你的 GLSL 程序，"
            + "决定这个像素的颜色——采样贴图、算光照、画花纹都在这里。"
            + "全屏百万像素=百万次并行执行，这就是 GPU 快的原因。\n"
            + "⑦ 测试与混合：片元要过 4 道关（裁剪框→模板→深度→混合），"
            + "全部过关才能写入帧缓冲；一帧画完 eglSwapBuffers 整屏上屏。\n\n"
            + "▍和 Android 绘制体系的对照\n"
            + "CPU 准备数据 ≈ 你在 onDraw() 里准备 Path/Bitmap；"
            + "GPU 流水线 ≈ 硬件加速的 RenderThread；"
            + "eglSwapBuffers ≈ Choreographer 的 vsync 信号触发下一帧。"
            + "区别：Canvas 是\"逐条指令\"画，OpenGL 是\"整批数据\"交给并行工厂。\n\n"
            + "▍看什么\n"
            + "黄色小方块=一个顶点数据包正在流水线上流动，经过哪道工序该节点就会亮起；"
            + "右侧灰字是每道工序的输入→输出。后面 5 个界面（28~32）会逐站拆开细讲。\n\n"
            + "▍动手玩\n"
            + "· 【点按】流水线上任意节点：高亮该站，并自动跳到【代码】页签的对应代码段；\n"
            + "· 再点一次同一节点恢复\"跟随数据包\"模式；\n"
            + "· 底部切到【代码】页签：画一个三角形所需的全部真实代码（7 段，带注释），"
            + "配合流水线从上到下读一遍，GPU 的工作方式就通了。";

    /** 代码示例：画一个三角形需要的全部代码，按流水线 7 站分段（// 【N】 为跳转锚点）。 */
    public static final String CODE = ""
            + "// 画一个三角形，全部代码就这么多。点流水线节点跳到对应段。\n"
            + "\n"
            + "// 【0】第①站 顶点数据：float 数组 → 显存 VBO\n"
            + "float[] vertices = {          // 3 个顶点 × (x y z + r g b)\n"
            + "    -0.5f, -0.5f, 0f,  1f, 0f, 0f,   // v0 左下·红\n"
            + "     0.5f, -0.5f, 0f,  0f, 1f, 0f,   // v1 右下·绿\n"
            + "     0.0f,  0.5f, 0f,  0f, 0f, 1f    // v2 顶上·蓝\n"
            + "};\n"
            + "int[] vbo = new int[1];\n"
            + "GLES30.glGenBuffers(1, vbo, 0);              // 领一块显存\n"
            + "GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0]);\n"
            + "GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,\n"
            + "        vertices.length * 4,          // 18 个 float × 4 字节\n"
            + "        FloatBuffer.wrap(vertices),   // 一次整批搬运\n"
            + "        GLES30.GL_STATIC_DRAW);       // 提示：写一次、读 N 帧\n"
            + "\n"
            + "// 【1】第②站 顶点着色器：每个顶点执行一次的 GLSL 程序\n"
            + "String vs = \"#version 300 es\\n\"            // 必须是第一行\n"
            + "    + \"layout(location=0) in vec3 a_pos;\\n\"  // 从显存读来的位置\n"
            + "    + \"layout(location=1) in vec3 a_color;\\n\"// 位置旁附带的颜色\n"
            + "    + \"uniform mat4 u_mvp;\\n\"                // 变换矩阵(CPU算好传入)\n"
            + "    + \"out vec3 v_color;\\n\"                  // 输出：交给下一站插值\n"
            + "    + \"void main() {\\n\"\n"
            + "    + \"    v_color = a_color;\\n\"\n"
            + "    + \"    gl_Position = u_mvp * vec4(a_pos, 1.0);\\n\" // 核心！\n"
            + "    + \"}\";\n"
            + "// 编译：glCreateShader→glShaderSource→glCompileShader(见 ShaderProgram)\n"
            + "\n"
            + "// 【2】第③站 图元装配：告诉 GPU 顶点怎么绑成三角形\n"
            + "// 不用写逻辑，一个函数参数搞定：\n"
            + "GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);\n"
            + "//                    └ 每 3 个顶点绑 1 个三角形\n"
            + "// 另有 GL_LINES / LINE_STRIP / TRIANGLE_STRIP / FAN…\n"
            + "// (硬件顺手做面剔除：背对相机的三角形整颗丢弃)\n"
            + "\n"
            + "// 【3】第④站 裁剪+透视除法：纯硬件，你只需给对 w\n"
            + "// gl_Position 是 vec4：硬件检查 |x|,|y|,|z| ≤ w，超出部分裁掉；\n"
            + "// 然后所有分量除以 w（=透视效果），得到 NDC 坐标 [-1,1]。\n"
            + "// near/far/fov 都编在投影矩阵里，见第 29 课 perspectiveM。\n"
            + "\n"
            + "// 【4】第⑤站 视口变换+光栅化：还是纯硬件\n"
            + "GLES30.glViewport(0, 0, width, height); // NDC → 窗口像素\n"
            + "// 硬件把三角形铺成片元(fragment)，顶点的 v_color 按重心坐标\n"
            + "// 自动插值：三个顶点不同色 → 三角形渐变，一行代码都不用写。\n"
            + "\n"
            + "// 【5】第⑥站 片元着色器：每个像素执行一次的 GLSL 程序\n"
            + "String fs = \"#version 300 es\\n\"\n"
            + "    + \"precision mediump float;\\n\"        // 片元必须声明精度\n"
            + "    + \"in vec3 v_color;\\n\"                // 收到插值后的颜色\n"
            + "    + \"out vec4 fragColor;\\n\"\n"
            + "    + \"void main() {\\n\"\n"
            + "    + \"    fragColor = vec4(v_color, 1.0);\\n\" // 决定每像素颜色\n"
            + "    + \"}\";\n"
            + "// 纹理采样、光照计算都在这个程序里写——自由度最大的地方。\n"
            + "\n"
            + "// 【6】第⑦站 测试混合 → 帧缓冲 → 上屏\n"
            + "GLES30.glEnable(GLES30.GL_DEPTH_TEST);   // 开深度测试(遮挡)\n"
            + "GLES30.glEnable(GLES30.GL_BLEND);        // 开混合(半透明)\n"
            + "GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,\n"
            + "        GLES30.GL_ONE_MINUS_SRC_ALPHA);  // 经典半透明公式\n"
            + "// 一帧里所有物体画完后：\n"
            + "eglSwapBuffers(display, surface); // 后缓冲整屏交换(见 GLThread)\n";

    private static final int STAGES = 7;

    /** 工序元数据：名称 / 类型(0=CPU 1=可编程 2=固定) / 输入 / 输出 */
    private static final String[] STAGE_NAMES = {
            "① 顶点数据  CPU→显存",
            "② 顶点着色器  每顶点一次",
            "③ 图元装配  绑成三角形",
            "④ 裁剪·透视除法  砍视野外",
            "⑤ 视口·光栅化  变成片元",
            "⑥ 片元着色器  每片元一次",
            "⑦ 测试·混合  写入帧缓冲"
    };
    private static final int[] STAGE_TYPES = {0, 1, 2, 2, 2, 1, 2};
    private static final String[] STAGE_IO = {
            "float数组 → VBO/VAO",
            "顶点属性 → gl_Position",
            "顶点流 → 三角形",
            "裁剪坐标 → NDC[-1,1]",
            "窗口三角形 → 片元+插值",
            "片元属性 → RGBA颜色",
            "片元 → 屏幕像素"
    };

    private static final String KEY_SPEED = "speed";
    private static final String KEY_FOLLOW = "follow";
    private static final String KEY_SEL = "sel";
    private static final String KEY_IO = "io";

    private static final String[] SEL_LABELS = {
            "1 顶点数据", "2 顶点着色器", "3 图元装配", "4 裁剪除法",
            "5 光栅化", "6 片元着色器", "7 测试混合"
    };

    private FlatShapes mShapes;
    private final int[] mLabelTex = new int[STAGES];
    private final int[] mIoTex = new int[STAGES];
    private int mTypeProgTex;
    private int mTypeFixTex;
    private int mTypeCpuTex;
    private int mTouchSel = -1;   // 点按选中的工序（-1=跟随数据包/参数）

    /** 点流水线节点：选中该站并跳到对应代码段；再点一次恢复跟随。 */
    @Override
    public void onTouch(int action, float x, float y) {
        if (action != MotionEvent.ACTION_DOWN) return;
        float yNdc = 1f - 2f * y / mHeight;
        float areaTop = 0.88f;
        float rowH = 1.08f / STAGES;
        int idx = (int) Math.floor((areaTop - yNdc) / rowH);
        if (idx < 0 || idx >= STAGES) return;
        if (mTouchSel == idx) {
            mTouchSel = -1;
        } else {
            mTouchSel = idx;
            fireCodeSection(idx);
        }
    }

    private float mPacketT;      // 0..1 数据包在整条流水线上的进度
    private int mActiveStage = -1;
    private float mFlash;        // 节点点亮后的余晖

    @Override
    public void onSurfaceCreated(int width, int height) {
        mShapes = new FlatShapes();
        for (int i = 0; i < STAGES; i++) {
            mLabelTex[i] = TextureHelper.createTextTexture(STAGE_NAMES[i], 720, 112);
            mIoTex[i] = TextureHelper.createTextTexture(STAGE_IO[i], 560, 96);
        }
        mTypeProgTex = TextureHelper.createTextTexture("可编程 GLSL", 300, 96);
        mTypeFixTex = TextureHelper.createTextTexture("固定功能", 300, 96);
        mTypeCpuTex = TextureHelper.createTextTexture("CPU 侧", 300, 96);
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        // 数据包沿流水线匀速循环
        float speed = getFloat(KEY_SPEED);
        mPacketT += deltaTime * speed * 0.28f;
        if (mPacketT > 1f) mPacketT -= 1f;

        int reached = (int) Math.floor(mPacketT * STAGES);
        if (reached != mActiveStage) {
            mActiveStage = reached;
            mFlash = 1f;
        }
        mFlash = Math.max(0f, mFlash - deltaTime * 1.6f);

        int selStage = mTouchSel >= 0 ? mTouchSel
                : (getFollow() ? mActiveStage : getOptionIndex(KEY_SEL));

        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glViewport(0, 0, mWidth, mHeight);
        GLES30.glClearColor(0.04f, 0.05f, 0.09f, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

        // ── 流水线区域：避开顶部标题栏(屏幕 6%=NDC 0.12)，占屏幕上部 54%(NDC 1.08) ──
        float areaTop = 0.88f;
        float usable = 1.08f;
        float areaBottom = areaTop - usable;

        float rowH = usable / STAGES;
        float nodeW = 0.66f;            // 节点宽（NDC，总宽 2）
        float nodeH = rowH * 0.68f;

        for (int i = 0; i < STAGES; i++) {
            // 行中心 y（第 0 行在最上）
            float cy = areaTop - rowH * (i + 0.5f);
            float cx = -0.98f + nodeW / 2f + 0.02f;
            boolean active = (i == selStage);

            // 类型配色：CPU 灰 / 可编程 橙 / 固定 蓝
            float br, bg, bb;
            float er, eg, eb;
            int type = STAGE_TYPES[i];
            if (type == 1) {
                br = 0.32f; bg = 0.20f; bb = 0.07f;
                er = 1.00f; eg = 0.62f; eb = 0.20f;
            } else if (type == 0) {
                br = 0.16f; bg = 0.18f; bb = 0.21f;
                er = 0.62f; eg = 0.67f; eb = 0.72f;
            } else {
                br = 0.07f; bg = 0.15f; bb = 0.27f;
                er = 0.35f; eg = 0.66f; eb = 1.00f;
            }
            // 高亮：填充提亮 + 白边框 + 呼吸
            float lift = active ? 0.10f + 0.06f * mFlash : 0f;
            mShapes.rect(cx, cy, nodeW, nodeH, br + lift, bg + lift, bb + lift, 1f);
            float et = active ? 0.010f + 0.004f * mFlash : 0.006f;
            float er2 = active ? 1f : er;
            float eg2 = active ? 1f : eg;
            float eb2 = active ? 1f : eb;
            mShapes.frame(cx, cy, nodeW, nodeH, et, er2, eg2, eb2, active ? 1f : 0.85f);

            // 节点名称标签（行高 1/3，不变形）
            float lh = nodeH * 0.52f;
            float lw = Math.min(nodeW * 0.9f, lh * (720f / 112f));
            drawTexLabel(mLabelTex[i], cx, cy, lw, lh);

            // 右侧 IO 标签
            if (getBool(KEY_IO)) {
                float ioX = cx + nodeW / 2f + 0.03f + 0.24f;
                float ih = nodeH * 0.44f;
                float iw = ih * (560f / 96f);
                drawTexLabel(mIoTex[i], ioX, cy, Math.min(iw, 0.62f), ih);
            }

            // 类型小标签（节点左上角内侧）
            int typeTex = type == 1 ? mTypeProgTex : type == 0 ? mTypeCpuTex : mTypeFixTex;
            float th = nodeH * 0.26f;
            float tw = th * (300f / 96f);
            drawTexLabel(typeTex,
                    cx - nodeW / 2f + tw / 2f + 0.012f,
                    cy + nodeH / 2f - th / 2f - 0.008f, tw, th);

            // 行间向下箭头
            if (i < STAGES - 1) {
                float gapTop = cy - nodeH / 2f;
                float gapBottom = cy - rowH + rowH * 0.32f;
                float shaftH = (gapTop - gapBottom) * 0.55f;
                mShapes.rect(cx, gapTop - shaftH / 2f - 0.004f, 0.012f, shaftH,
                        0.45f, 0.52f, 0.62f, 0.9f);
                mShapes.triDown(cx, gapBottom, 0.045f, 0.036f,
                        0.45f, 0.52f, 0.62f, 0.95f);
            }
        }

        // ── 数据包：沿节点中心线流动的黄色方块 ──
        if (speed > 0.01f) {
            float py = areaTop - rowH * (mPacketT * STAGES + 0.5f);
            // 到站瞬间放大闪一下
            float s = 0.036f * (1f + mFlash * 0.7f);
            mShapes.rect(-0.65f, py, s, s * 0.55f, 1f, 0.85f, 0.25f, 1f);
            mShapes.frame(-0.65f, py, s * 1.35f, s * 0.9f, 0.006f, 1f, 0.95f, 0.5f, 0.6f);
        }
    }

    private boolean getFollow() {
        return getBool(KEY_FOLLOW);
    }

    /** 绘制文字纹理（自动绑定纹理单元 0，开启混合）。 */
    private void drawTexLabel(int tex, float cx, float cy, float w, float h) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex);
        mShapes.tex(cx, cy, w, h, 0);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        List<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.floatSpec(KEY_SPEED, "数据包流速", 0f, 3f, 1f));
        specs.add(ParamSpec.boolSpec(KEY_FOLLOW, "高亮跟随数据包", true));
        specs.add(ParamSpec.optionSpec(KEY_SEL, "手动选择工序", SEL_LABELS, 0));
        specs.add(ParamSpec.boolSpec(KEY_IO, "显示输入→输出", true));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        for (int i = 0; i < STAGES; i++) {
            TextureHelper.deleteTexture(mLabelTex[i]);
            TextureHelper.deleteTexture(mIoTex[i]);
        }
        TextureHelper.deleteTexture(mTypeProgTex);
        TextureHelper.deleteTexture(mTypeFixTex);
        TextureHelper.deleteTexture(mTypeCpuTex);
        mShapes.release();
    }
}
