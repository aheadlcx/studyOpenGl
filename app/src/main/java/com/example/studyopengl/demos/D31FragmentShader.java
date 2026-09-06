package com.example.studyopengl.demos;

import android.opengl.GLES30;
import android.opengl.Matrix;

import com.example.studyopengl.engine.BaseDemoEngine;
import com.example.studyopengl.gl.Mesh;
import com.example.studyopengl.gl.ShaderProgram;
import com.example.studyopengl.gl.TextureHelper;
import com.example.studyopengl.param.ParamSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 31 · 片元着色器：每个像素的小程序
 *
 * 同一个三角形，用 6 种\"FS 里能拿到什么\"的视角渲染：
 * 0 顶点插值色——光栅化白送的渐变
 * 1 gl_FragCoord——片元的屏幕像素坐标（画条纹）
 * 2 uv 坐标——美术用来定位贴图的坐标（R=G=uv）
 * 3 程序化花纹——纯数学画圆环
 * 4 棋盘格——fract 取整技巧
 * 5 discard——FS 能\"毙掉\"自己（镂空）
 */
public class D31FragmentShader extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍片元着色器是什么？（流水线第 6 站）\n"
            + "一个 mini 程序，对【每个片元执行一次】，唯一任务：算出一个 RGBA 颜色。"
            + "三角形覆盖 1 万个像素，它就跑 1 万次——但 GPU 有几千个核心并行跑，"
            + "所以瞬间完成。Android 类比：把 onDraw 里逐像素的 for 循环"
            + "交给几千个线程同时执行（其实 Bitmap 逐像素操作正是该搬进 FS 的典型场景）。\n\n"
            + "▍FS 能拿到哪些输入？（本界面 6 个模式逐一展示）\n"
            + "① v_color（模式0）：顶点色经插值后的值——红绿蓝渐变是光栅化【白送】的，"
            + "FS 里直接用；\n"
            + "② gl_FragCoord（模式1）：内建变量，片元在屏幕上的像素坐标"
            + "（x+0.5,y+0.5 是像素中心）——条纹随窗口位置变化，平移画面条纹不动；\n"
            + "③ v_uv（模式2）：也是插值输入，0~1 的\"贴图定位坐标\"，"
            + "把它当颜色显示就是红绿梯度图——纹理采样 texture(tex, v_uv) 用的就是它；\n"
            + "④ 纯数学（模式3/4）：length()、fract()、step() 等函数"
            + "可以在 FS 里\"无中生有\"画花纹——圆环、棋盘、噪声都是数学算出来的；\n"
            + "⑤ discard（模式5）：FS 唯一能\"否决自己\"的手段——"
            + "命中条件的片元直接丢弃（镂空/圆形贴图不写矩形黑边）。\n\n"
            + "▍和顶点着色器的成本差\n"
            + "VS 每顶点一次（3 次），FS 每片元一次（可能百万次）。"
            + "所以同一计算能放 VS 就别放 FS——比如光照的方向、矩阵，先在 VS/CDN 算好传下来。"
            + "反过来，逐像素必须精细的效果（法线贴图光照）只能放 FS。\n\n"
            + "▍精度声明\n"
            + "FS 必须写 precision mediump float;（顶点着色器默认 highp）——"
            + "mediump 在老 GPU 上快，但大世界坐标会抖动，需要精度时声明 highp。\n\n"
            + "▍动手玩\n"
            + "· 【点屏幕左/右半边】直接切换 6 种模式（比拉下拉框快）；\n"
            + "· 每种模式对应的真实 GLSL 片段在【代码】页签，点屏幕会自动跳过去；\n"
            + "· 密度滑条同时控制条纹和棋盘的疏密——改一下立刻看到 FS 是逐像素执行的。";

    private static final String VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec3 a_color;\n"
            + "layout(location=2) in vec2 a_uv;\n"
            + "uniform mat4 u_mvp;\n"
            + "out vec3 v_color;\n"
            + "out vec2 v_uv;\n"
            + "void main() {\n"
            + "    v_color = a_color;\n"
            + "    v_uv = a_uv;\n"
            + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec3 v_color;\n"
            + "in vec2 v_uv;\n"
            + "uniform int u_mode;\n"
            + "uniform float u_density;\n"   // 条纹/棋盘密度
            + "uniform float u_time;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    vec3 c = v_color;\n"
            + "    if (u_mode == 1) {\n"
            + "        // gl_FragCoord：屏幕像素坐标\n"
            + "        float s = step(0.5, fract(gl_FragCoord.x / u_density));\n"
            + "        c = v_color * (0.55 + 0.45 * s);\n"
            + "    } else if (u_mode == 2) {\n"
            + "        // uv 可视化：R=水平 G=垂直\n"
            + "        c = vec3(v_uv.x, v_uv.y, 0.35);\n"
            + "    } else if (u_mode == 3) {\n"
            + "        // 程序化圆环：到 uv 中心的距离\n"
            + "        float d = length(v_uv - 0.5);\n"
            + "        float ring = 0.5 + 0.5 * sin(d * 40.0 - u_time * 3.0);\n"
            + "        c = mix(vec3(0.95, 0.55, 0.20), vec3(0.20, 0.55, 0.95), ring);\n"
            + "    } else if (u_mode == 4) {\n"
            + "        // 棋盘：fract 取整经典技巧\n"
            + "        vec2 g = floor(v_uv * u_density * 0.25);\n"
            + "        float ck = mod(g.x + g.y, 2.0);\n"
            + "        c = mix(vec3(0.92, 0.92, 0.95), vec3(0.16, 0.20, 0.32), ck);\n"
            + "    } else if (u_mode == 5) {\n"
            + "        // discard 镂空：圆孔区域直接丢弃片元\n"
            + "        float d = length(v_uv - vec2(0.5, 0.45));\n"
            + "        if (d < 0.22 + 0.04 * sin(u_time * 2.0)) discard;\n"
            + "        c = vec3(0.30, 0.85, 0.60);\n"
            + "    }\n"
            + "    fragColor = vec4(c, 1.0);\n"
            + "}\n";

    private static final String KEY_MODE = "mode";
    private static final String KEY_DENSITY = "density";
    private static final String KEY_SPIN = "spin";

    private static final String[] MODE_LABELS = {
            "插值色 v_color（白送渐变）",
            "gl_FragCoord 屏幕坐标（条纹）",
            "v_uv 坐标可视化（红绿梯度）",
            "程序化圆环（纯数学）",
            "棋盘格（fract 技巧）",
            "discard 镂空（否决自己）"
    };

    /** 代码示例：6 个模式对应的真实 FS 片段，点屏幕左/右半边切换模式会跳到这里。 */
    public static final String CODE = ""
            + "// 片元着色器：GPU 对【每个像素】并行执行的函数，输入→输出一个颜色。\n"
            + "// 下面 6 段对应界面上可切换的 6 种模式（点屏幕左右可切换）。\n"
            + "\n"
            + "// 【0】模式0：v_color 插值色——光栅化白送，直接用\n"
            + "// 顶点着色器里: out vec3 v_color; v_color = a_color;\n"
            + "// 光栅化硬件自动把 3 个顶点的颜色按重心坐标插到每个像素\n"
            + "vec3 c = v_color;   // 渐变就是这么来的，零成本\n"
            + "\n"
            + "// 【1】模式1：gl_FragCoord——当前像素的屏幕坐标（内建，只读）\n"
            + "float s = step(0.5, fract(gl_FragCoord.x / u_density));\n"
            + "c = v_color * (0.55 + 0.45 * s);   // 竖条纹，与三角形位置无关\n"
            + "// 用途：屏幕空间效果（雾的抖动、 SSAO 噪声、像素化）\n"
            + "\n"
            + "// 【2】模式2：v_uv——贴图定位坐标(0~1)，也是插值来的\n"
            + "c = vec3(v_uv.x, v_uv.y, 0.35);    // R=水平 G=垂直 → 梯度图\n"
            + "// 真实用法：采一张贴图 texture(u_tex, v_uv)，u_tex 是图片\n"
            + "// 见第 17 课：纹理就是\"一张记录颜色的数组 + uv 去查\"\n"
            + "\n"
            + "// 【3】模式3：程序化圆环——纯数学画图（贴图都不用）\n"
            + "float d = length(v_uv - 0.5);              // 到 uv 中心的距离\n"
            + "float ring = 0.5 + 0.5 * sin(d * 40.0 - u_time * 3.0);\n"
            + "c = mix(vec3(0.95,0.55,0.20), vec3(0.20,0.55,0.95), ring);\n"
            + "// length/sin/mix 都是内建函数；d×40 = 每单位 40 圈环\n"
            + "\n"
            + "// 【4】模式4：棋盘格——floor 取整经典技巧\n"
            + "vec2 g = floor(v_uv * u_density * 0.25);   // uv 乘密度再取整\n"
            + "float ck = mod(g.x + g.y, 2.0);            // 格子坐标和的奇偶\n"
            + "c = mix(vec3(0.92,0.92,0.95), vec3(0.16,0.20,0.32), ck);\n"
            + "\n"
            + "// 【5】模式5：discard——片元自我否决（镂空/打孔）\n"
            + "float d = length(v_uv - vec2(0.5, 0.45));\n"
            + "if (d < 0.22 + 0.04 * sin(u_time * 2.0)) discard; // 圆孔内直接丢\n"
            + "c = vec3(0.30, 0.85, 0.60);\n"
            + "// 注意：discard 会破坏 early-z 优化，能不用尽量少用\n";

    private ShaderProgram mProg;
    private Mesh mQuad;
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mMvp = new float[16];
    private int mTouchMode = -1;   // 点屏幕切换的模式（-1=跟随参数）
    private float mTime;

    /** 点屏幕左半=上一模式，右半=下一模式，并跳到对应代码段。 */
    @Override
    public void onTouch(int action, float x, float y) {
        if (action != android.view.MotionEvent.ACTION_DOWN) return;
        int cur = mTouchMode >= 0 ? mTouchMode : getOptionIndex(KEY_MODE);
        int next;
        if (x < mWidth / 2f) {
            next = (cur + MODE_LABELS.length - 1) % MODE_LABELS.length;
        } else {
            next = (cur + 1) % MODE_LABELS.length;
        }
        mTouchMode = next;
        fireCodeSection(next);
    }

    @Override
    public void onParamChanged(ParamSpec spec) {
        if (spec.key.equals(KEY_MODE)) {
            mTouchMode = -1; // 参数面板选择优先
        }
    }

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProg = new ShaderProgram(VS, FS);
        // 大四边形（6 顶点，pos3+color3+uv2），中心稍上避开面板
        mQuad = new Mesh.Builder()
                .addBuffer(new float[]{
                        -0.80f, -0.55f, 0, 0.95f, 0.35f, 0.30f, 0, 0,
                        0.80f, -0.55f, 0, 0.30f, 0.95f, 0.45f, 1, 0,
                        -0.80f, 0.62f, 0, 0.95f, 0.45f, 0.25f, 0, 1,
                        0.80f, -0.55f, 0, 0.30f, 0.95f, 0.45f, 1, 0,
                        0.80f, 0.62f, 0, 0.35f, 0.60f, 0.95f, 1, 1,
                        -0.80f, 0.62f, 0, 0.95f, 0.45f, 0.25f, 0, 1
                }, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3), new Mesh.Attrib(2, 2))
                .build();
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        mTime += deltaTime;
        clearFrame(0.04f, 0.05f, 0.09f);

        float vAspect = aspect();
        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 45f, vAspect, 0.1f, 10f);
        Matrix.setLookAtM(mView, 0, 0, 0, 3.2f, 0, 0, 0, 0, 1, 0);
        Matrix.setIdentityM(mModel, 0);
        Matrix.rotateM(mModel, 0, mTime * getFloat(KEY_SPIN) * 20f, 0, 1, 0);
        Matrix.multiplyMM(mMvp, 0, mProj, 0, mView, 0);
        float[] mvp = new float[16];
        Matrix.multiplyMM(mvp, 0, mMvp, 0, mModel, 0);

        mProg.use();
        mProg.setMat4("u_mvp", mvp);
        mProg.set("u_mode", mTouchMode >= 0 ? mTouchMode : getOptionIndex(KEY_MODE));
        mProg.set("u_density", getFloat(KEY_DENSITY));
        mProg.set("u_time", mTime);
        mQuad.draw(GLES30.GL_TRIANGLES);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        List<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.optionSpec(KEY_MODE, "FS 输入视角", MODE_LABELS, 0));
        specs.add(ParamSpec.floatSpec(KEY_DENSITY, "条纹/棋盘密度", 6f, 60f, 18f, "%.0f"));
        specs.add(ParamSpec.floatSpec(KEY_SPIN, "缓转速度", 0f, 2f, 0.5f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mQuad.dispose();
        mProg.release();
    }
}
