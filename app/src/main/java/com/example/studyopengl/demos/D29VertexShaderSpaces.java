package com.example.studyopengl.demos;

import android.opengl.GLES30;
import android.opengl.Matrix;

import com.example.studyopengl.engine.BaseDemoEngine;
import com.example.studyopengl.gl.FlatShapes;
import com.example.studyopengl.gl.GeoGen;
import com.example.studyopengl.gl.Mesh;
import com.example.studyopengl.gl.ShaderProgram;
import com.example.studyopengl.gl.TextureHelper;
import com.example.studyopengl.param.ParamSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 29 · 顶点着色器：MVP 四步变换
 *
 * 4 个视口自上而下 = 同一个三角形在 4 个"空间"里的样子：
 * ① 局部空间（模型自己的坐标系，原地自转）
 * ② 世界空间（×Model：把模型"摆放"进场景：平移+旋转）
 * ③ 相机空间（×View：相机环绕——世界反向运动，相机永远在原点）
 * ④ 裁剪空间（×Projection：视锥框住可见范围，fov 张合）
 */
public class D29VertexShaderSpaces extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍顶点着色器到底算什么？（流水线第 2 站）\n"
            + "对每个顶点执行一次你写的 GLSL 程序，核心就一行：\n"
            + "    gl_Position = u_proj * u_view * u_model * vec4(a_pos, 1.0);\n"
            + "三个矩阵把顶点从\"模型自己的坐标\"一步步搬到\"屏幕坐标\"。"
            + "顺序固定 P×V×M（列向量约定下右边的先作用），【不能交换】——"
            + "类比穿衣服：先穿衣服(S)再转身(R)最后出门(T)；先出门再穿就乱套了。\n\n"
            + "▍① 局部空间 Local（视口1）\n"
            + "建模时以模型自身原点为参考——三角形绕自己的中心自转，"
            + "坐标轴（红X绿Y蓝Z）跟着一起转。美术导出的模型文件都是局部坐标。"
            + "Android 类比：View.onDraw 里 canvas.translate/rotate 画自己的内容，"
            + "完全不关心自己最终显示在屏幕哪个位置。\n\n"
            + "▍② 世界空间 World = Local × Model（视口2）\n"
            + "Model 矩阵把模型\"摆放\"进场景：平移到 (0.65,0,0) 再自转。"
            + "注意原点的世界坐标轴和左边的参照方块都不动——动的是三角形。"
            + "Android 类比：translationX / rotationY / scaleX——"
            + "这三个属性在 View 内部恰恰就是组合成矩阵生效的！\n\n"
            + "▍③ 相机空间 View = World × View（视口3）\n"
            + "关键观念：相机没有\"自己的矩阵\"，View 矩阵是\"相机世界变换的逆\"——"
            + "相机向右转 = 整个世界向左转。本视口相机在环绕，但在 GPU 眼里"
            + "相机永远固定在原点朝 -Z 看，是整个世界在反向移动。"
            + "这也是天空盒要\"去掉 View 平移\"的原因（无限远的东西不该跟着相机走）。\n\n"
            + "▍④ 裁剪空间 Clip = View × Projection（视口4）\n"
            + "Projection 定义一个四棱台\"视锥\"（fov 张角 + near/far 两个切面），"
            + "只有锥内的部分可见。它还把 z 信息编进 w 分量——之后硬件做\"除以 w\""
            + "就得到近大远小的透视（下一站细讲）。拖动 fov 滑条：张角越大（广角），"
            + "同样距离的三角形显得越小、边缘拉伸越明显。\n\n"
            + "▍为什么矩阵能打包一切？\n"
            + "旋转/平移/缩放/投影全是 4×4 矩阵，CPU 侧把 P·V·M 预乘成一个，"
            + "GPU 每个顶点只需一次矩阵乘法（4 个点积）——百万顶点也便宜。"
            + "这是\"能预计算就预计算\"性能思维的起点。";

    private static final String VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec3 a_color;\n"
            + "uniform mat4 u_mvp;\n"
            + "out vec3 v_color;\n"
            + "void main() {\n"
            + "    v_color = a_color;\n"
            + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec3 v_color;\n"
            + "out vec4 fragColor;\n"
            + "void main() { fragColor = vec4(v_color, 1.0); }\n";

    private static final String KEY_SPIN = "spin";
    private static final String KEY_ORBIT = "orbit";
    private static final String KEY_FOV = "fov";

    /** 代码示例：MVP 三个矩阵的 Java 构建 + VS 里的一次乘法。 */
    public static final String CODE = ""
            + "// 顶点着色器全文（就干一件事：算出每个顶点的最终位置）\n"
            + "String vs = \"#version 300 es\\n\"\n"
            + "    + \"layout(location=0) in vec3 a_pos;\\n\"    // 顶点数据(28课)\n"
            + "    + \"uniform mat4 u_mvp;\\n\"\n"
            + "    + \"void main() {\\n\"\n"
            + "    + \"    gl_Position = u_mvp * vec4(a_pos, 1.0);\\n\"\n"
            + "    + \"}\";\n"
            + "// 关键：u_mvp 是 CPU 侧预先乘好的 P·V·M，百万顶点共用一次乘法。\n"
            + "\n"
            + "// 【0】① 局部空间：Model 只含自转（三角形绕自己中心转）\n"
            + "float[] model = new float[16];\n"
            + "android.opengl.Matrix.setIdentityM(model, 0);\n"
            + "android.opengl.Matrix.rotateM(model, 0, angle, 0, 1, 0);\n"
            + "\n"
            + "// 【1】② 世界空间：Model = 平移 × 自转（先转再摆，顺序不能反）\n"
            + "Matrix.setIdentityM(model, 0);\n"
            + "Matrix.translateM(model, 0, 0.65f, 0, 0);  // T：摆到场景右侧\n"
            + "Matrix.rotateM(model, 0, angle, 0, 1, 0);  // R：在 T 之后调用\n"
            + "// 矩阵\"后调用的先作用\"：等效 T×R —— 先自转再平移 ✓\n"
            + "\n"
            + "// 【2】③ 相机空间：View = 相机变换的【逆】\n"
            + "// setLookAtM(eye, center, up) 内部帮你把逆求好了：\n"
            + "float[] view = new float[16];\n"
            + "Matrix.setLookAtM(view, 0,\n"
            + "        eyeX, eyeY, eyeZ,   // 相机在哪\n"
            + "        0f, 0f, 0f,         // 看向哪\n"
            + "        0f, 1f, 0f);        // 哪边是\"上\"\n"
            + "// 相机绕场景转 = eye 沿圆周动；GPU 眼里相机永远在原点。\n"
            + "\n"
            + "// 【3】④ 裁剪空间：Projection 定义视锥，把 z 编进 w\n"
            + "float[] proj = new float[16];\n"
            + "Matrix.perspectiveM(proj, 0,\n"
            + "        fovY,      // 垂直视场角：拖 fov 滑条看视锥张合\n"
            + "        aspect,    // 宽高比 = surface宽/高，弄错画面会拉伸\n"
            + "        near, far);// 近/远切面：越近精度越高(深度非线性)\n"
            + "\n"
            + "// 最后合体：mvp = proj × view × model（顺序固定！）\n"
            + "float[] pv = new float[16], mvp = new float[16];\n"
            + "Matrix.multiplyMM(pv, 0, proj, 0, view, 0);\n"
            + "Matrix.multiplyMM(mvp, 0, pv, 0, model, 0);\n"
            + "GLES30.glUniformMatrix4fv(loc, 1, false, mvp, 0); // 列主序,不转置";
    private static final String KEY_AXES = "axes";

    private static final int ROWS = 4;
    private static final float LABEL_ASPECT = 1100f / 100f;

    private ShaderProgram mProg;
    private FlatShapes mShapes;
    private Mesh mTri;
    private Mesh mAxes;
    private Mesh mCube;
    private Mesh mFrustum;
    private final int[] mLabelTex = new int[ROWS];

    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mPv = new float[16];
    private final float[] mMvp = new float[16];
    private float mRowAspect = 1f;
    private int mRowW = 1, mRowH = 1;
    private float mTime;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProg = new ShaderProgram(VS, FS);
        mShapes = new FlatShapes();

        mTri = new Mesh.Builder()
                .addBuffer(new float[]{
                        -0.45f, -0.30f, 0f, 1f, 0.30f, 0.25f,
                        0.45f, -0.25f, 0f, 0.25f, 1f, 0.40f,
                        0.02f, 0.42f, 0f, 0.30f, 0.55f, 1f
                }, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .build();

        mAxes = new Mesh.Builder()
                .addBuffer(new float[]{
                        0, 0, 0, 1f, 0.25f, 0.25f, 0.85f, 0, 0, 1f, 0.25f, 0.25f,
                        0, 0, 0, 0.3f, 1f, 0.4f, 0, 0.85f, 0, 0.3f, 1f, 0.4f,
                        0, 0, 0, 0.35f, 0.5f, 1f, 0, 0, 0.85f, 0.35f, 0.5f, 1f
                }, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .build();

        GeoGen.GeoData cube = GeoGen.cube();
        float[] cubeVerts = new float[(cube.positions.length / 3) * 6];
        for (int i = 0; i < cube.positions.length / 3; i++) {
            int face = i / 4;
            float c = face % 2 == 0 ? 0.55f : 0.35f;
            cubeVerts[i * 6] = cube.positions[i * 3];
            cubeVerts[i * 6 + 1] = cube.positions[i * 3 + 1];
            cubeVerts[i * 6 + 2] = cube.positions[i * 3 + 2];
            cubeVerts[i * 6 + 3] = c * 0.7f;
            cubeVerts[i * 6 + 4] = c;
            cubeVerts[i * 6 + 5] = c * 1.2f;
        }
        mCube = new Mesh.Builder()
                .addBuffer(cubeVerts, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .setIndices(cube.indices)
                .build();

        // 视锥线框：原点(相机) -> 远平面四角
        mFrustum = new Mesh.Builder()
                .addBuffer(new float[]{
                        0, 0, 0, 1f, 0.9f, 0.4f, -1f, -0.65f, -2.6f, 1f, 0.9f, 0.4f,
                        0, 0, 0, 1f, 0.9f, 0.4f, 1f, -0.65f, -2.6f, 1f, 0.9f, 0.4f,
                        0, 0, 0, 1f, 0.9f, 0.4f, -1f, 0.75f, -2.6f, 1f, 0.9f, 0.4f,
                        0, 0, 0, 1f, 0.9f, 0.4f, 1f, 0.75f, -2.6f, 1f, 0.9f, 0.4f,
                        -1f, -0.65f, -2.6f, 1f, 0.9f, 0.4f, 1f, -0.65f, -2.6f, 1f, 0.9f, 0.4f,
                        1f, -0.65f, -2.6f, 1f, 0.9f, 0.4f, 1f, 0.75f, -2.6f, 1f, 0.9f, 0.4f,
                        1f, 0.75f, -2.6f, 1f, 0.9f, 0.4f, -1f, 0.75f, -2.6f, 1f, 0.9f, 0.4f,
                        -1f, 0.75f, -2.6f, 1f, 0.9f, 0.4f, -1f, -0.65f, -2.6f, 1f, 0.9f, 0.4f
                }, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
                .build();

        String[] labels = {
                "① 局部空间 Local   三角形自己的坐标系",
                "② 世界空间 World   ×Model 平移+旋转进场景",
                "③ 相机空间 View   ×View 相机环绕(世界反向动)",
                "④ 裁剪空间 Clip   ×Proj 视锥=可见范围"
        };
        for (int i = 0; i < ROWS; i++) {
            mLabelTex[i] = TextureHelper.createTextTexture(labels[i], 1100, 100);
        }
    }

    /** 第 i 行（0=最上）设为视口；占屏幕上部 54%，避开 6% 标题栏。 */
    private void beginRow(int i) {
        int topInset = Math.round(mHeight * 0.06f);
        int rowH = Math.round(mHeight * 0.54f / ROWS);
        int y = mHeight - topInset - (i + 1) * rowH;
        GLES30.glViewport(3, y + 3, mWidth - 6, rowH - 6);
        mRowW = mWidth - 6;
        mRowH = rowH - 6;
        mRowAspect = (float) mRowW / (float) mRowH;
    }

    /** 行左上角文字标签（像素比例换算 NDC，不变形）。 */
    private void drawLabel(int idx) {
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mLabelTex[idx]);
        float labelHpx = mRowH / 3.4f;
        float labelWpx = Math.min(mRowW * 0.94f, labelHpx * LABEL_ASPECT);
        float wNdc = labelWpx * 2f / mRowW;
        float hNdc = labelHpx * 2f / mRowH;
        mShapes.tex(-0.98f + wNdc / 2f, 0.98f - hNdc / 2f, wNdc, hNdc, 0);
        GLES30.glDisable(GLES30.GL_BLEND);
    }

    private void mulMvp() {
        Matrix.multiplyMM(mPv, 0, mProj, 0, mView, 0);
        Matrix.multiplyMM(mMvp, 0, mPv, 0, mModel, 0);
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        mTime += deltaTime;
        float spin = mTime * getFloat(KEY_SPIN) * 60f;
        float orbit = mTime * getFloat(KEY_ORBIT) * 0.9f;
        float fov = getFloat(KEY_FOV);
        boolean axes = getBool(KEY_AXES);

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glClearColor(0.04f, 0.05f, 0.09f, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        mProg.use();

        // ════ ① 局部空间：原地自转 ════
        beginRow(0);
        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 40f, mRowAspect, 0.1f, 10f);
        Matrix.setIdentityM(mView, 0);
        Matrix.setLookAtM(mView, 0, 0, 0, 3f, 0, 0, 0, 0, 1, 0);
        Matrix.setIdentityM(mModel, 0);
        Matrix.rotateM(mModel, 0, spin, 0, 1, 0);
        mulMvp();
        mProg.setMat4("u_mvp", mMvp);
        mTri.draw(GLES30.GL_TRIANGLES);
        if (axes) {
            mAxes.draw(GLES30.GL_LINES);   // 局部轴随模型转（同一 mvp）
        }
        drawLabel(0);

        // ════ ② 世界空间：×Model 摆进场景 ════
        beginRow(1);
        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 40f, mRowAspect, 0.1f, 10f);
        Matrix.setLookAtM(mView, 0, 0, 0.5f, 3.4f, 0, 0.2f, 0, 0, 1, 0);
        Matrix.setIdentityM(mModel, 0);
        mulMvp();
        if (axes) {
            mProg.setMat4("u_mvp", mMvp);
            mAxes.draw(GLES30.GL_LINES);   // 世界轴不转
        }
        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, -0.95f, 0, 0);
        Matrix.scaleM(mModel, 0, 0.25f, 0.25f, 0.25f);
        mulMvp();
        mProg.setMat4("u_mvp", mMvp);
        mCube.draw(GLES30.GL_TRIANGLES);   // 参照方块
        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, 0.65f, 0, 0);
        Matrix.rotateM(mModel, 0, spin, 0, 1, 0);
        mulMvp();
        mProg.setMat4("u_mvp", mMvp);
        mTri.draw(GLES30.GL_TRIANGLES);
        drawLabel(1);

        // ════ ③ 相机空间：相机环绕（View = 相机变换的逆）════
        beginRow(2);
        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 40f, mRowAspect, 0.1f, 10f);
        float r = 3.6f;
        Matrix.setLookAtM(mView, 0,
                r * (float) Math.sin(orbit), 1.2f, r * (float) Math.cos(orbit),
                0, 0.2f, 0, 0, 1, 0);
        Matrix.setIdentityM(mModel, 0);
        mulMvp();
        if (axes) {
            mProg.setMat4("u_mvp", mMvp);
            mAxes.draw(GLES30.GL_LINES);
        }
        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, 0.65f, 0, 0);
        Matrix.rotateM(mModel, 0, spin, 0, 1, 0);
        mulMvp();
        mProg.setMat4("u_mvp", mMvp);
        mTri.draw(GLES30.GL_TRIANGLES);
        drawLabel(2);

        // ════ ④ 裁剪空间：视锥 fov ════
        beginRow(3);
        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 42f, mRowAspect, 0.1f, 10f);
        // 从侧上方看相机(黄色小方块)与视锥
        Matrix.setLookAtM(mView, 0, -2.4f, 1.7f, 3.0f, 0.5f, 0, -1.2f, 0, 1, 0);
        // 相机本体
        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, 0, 0, 0);
        Matrix.scaleM(mModel, 0, 0.12f, 0.12f, 0.12f);
        mulMvp();
        mProg.setMat4("u_mvp", mMvp);
        mCube.draw(GLES30.GL_TRIANGLES);
        // 视锥（远端半宽随 fov 缩放）
        float scale = (float) Math.tan(Math.toRadians(fov))
                / (float) Math.tan(Math.toRadians(40f));
        Matrix.setIdentityM(mModel, 0);
        Matrix.scaleM(mModel, 0, scale, scale, 1f);
        mulMvp();
        mProg.setMat4("u_mvp", mMvp);
        mFrustum.draw(GLES30.GL_LINES);
        // 三角形摆在视锥内部
        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, 0.1f, 0.1f, -1.5f);
        Matrix.rotateM(mModel, 0, spin * 0.5f, 0, 1, 0);
        mulMvp();
        mProg.setMat4("u_mvp", mMvp);
        mTri.draw(GLES30.GL_TRIANGLES);
        drawLabel(3);

        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glViewport(0, 0, mWidth, mHeight);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        List<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.floatSpec(KEY_SPIN, "模型自转速度", -2f, 2f, 0.8f));
        specs.add(ParamSpec.floatSpec(KEY_ORBIT, "相机环绕速度", 0f, 2f, 0.5f));
        specs.add(ParamSpec.floatSpec(KEY_FOV, "视锥 fov 张角", 20f, 100f, 40f));
        specs.add(ParamSpec.boolSpec(KEY_AXES, "显示坐标轴", true));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mTri.dispose();
        mAxes.dispose();
        mCube.dispose();
        mFrustum.dispose();
        for (int i = 0; i < ROWS; i++) {
            TextureHelper.deleteTexture(mLabelTex[i]);
        }
        mProg.release();
        mShapes.release();
    }
}
