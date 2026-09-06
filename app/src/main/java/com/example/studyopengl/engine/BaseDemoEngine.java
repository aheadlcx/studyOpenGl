package com.example.studyopengl.engine;

import android.opengl.GLES30;

import com.example.studyopengl.param.ParamSpec;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DemoEngine 基类：
 *  1) 参数仓库：UI 线程写入（ConcurrentHashMap 保证可见性），GL 线程每帧读取；
 *     需要结构性变更的参数通过 postGLTask 把 onParamChanged 调度回 GL 线程执行。
 *  2) 公共小工具：视口清理、HSV 转 RGB、宽高比。
 */
public abstract class BaseDemoEngine implements DemoEngine {

    /** 把任务调度到 GL 线程执行（由 GLThread 实现）。 */
    public interface GLTaskPoster {
        void postGLTask(Runnable r);
    }

    /** GL 界面点按元素时通知 Activity 滚动到对应代码段（教程系列用）。 */
    public interface OnCodeSectionListener {
        void onCodeSection(int section);
    }

    protected int mWidth;
    protected int mHeight;

    private final ConcurrentHashMap<String, Object> mValues =
            new ConcurrentHashMap<String, Object>();
    private GLTaskPoster mPoster;
    private volatile OnCodeSectionListener mCodeListener;

    /** DemoActivity 创建引擎后立即调用。 */
    public final void attachPoster(GLTaskPoster poster) {
        mPoster = poster;
    }

    /** DemoActivity 注入代码跳转回调（引擎可在 GL 线程直接调 fireCodeSection）。 */
    public final void attachCodeSectionListener(OnCodeSectionListener listener) {
        mCodeListener = listener;
    }

    /** GL 线程调用：通知 UI 滚动代码面板到第 section 段。 */
    protected final void fireCodeSection(final int section) {
        OnCodeSectionListener l = mCodeListener;
        if (l != null) {
            l.onCodeSection(section);
        }
    }

    /** 填充参数默认值，UI 构建面板前调用。 */
    public final void initParams() {
        List<ParamSpec> specs = getParamSpecs();
        if (specs == null) return;
        for (ParamSpec s : specs) {
            mValues.put(s.key, s.defaultValue);
        }
    }

    /** UI 线程调用：存值 + 把变更通知调度到 GL 线程。 */
    public final void setParamFromUi(final ParamSpec spec, Object value) {
        mValues.put(spec.key, value);
        if (mPoster != null) {
            mPoster.postGLTask(new Runnable() {
                @Override
                public void run() {
                    onParamChanged(spec);
                }
            });
        }
    }

    /** UI 层读取当前值（用于重建界面等场景）。 */
    public final Object getParamValue(String key) {
        return mValues.get(key);
    }

    // ---- GL 线程取值 ----
    protected final float getFloat(String key) {
        Object v = mValues.get(key);
        return v instanceof Number ? ((Number) v).floatValue() : 0f;
    }

    protected final int getInt(String key) {
        Object v = mValues.get(key);
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    protected final boolean getBool(String key) {
        Object v = mValues.get(key);
        return v instanceof Boolean && ((Boolean) v);
    }

    protected final int getOptionIndex(String key) {
        return getInt(key);
    }

    // ---- 生命周期默认实现 ----
    @Override
    public void onSurfaceChanged(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    @Override
    public void onTouch(int action, float x, float y) {
    }

    @Override
    public void onParamChanged(ParamSpec spec) {
        // 默认参数是每帧轮询式读取，无需处理
    }

    @Override
    public void onSurfaceDestroyed() {
    }

    // ---- 公共工具 ----
    protected final float aspect() {
        return mHeight == 0 ? 1f : (float) mWidth / (float) mHeight;
    }

    /** 视口 + 清屏（颜色/深度/模板一次清掉，Demo 可以按需忽略某个缓冲）。 */
    protected void clearFrame(float r, float g, float b) {
        GLES30.glViewport(0, 0, mWidth, mHeight);
        GLES30.glClearColor(r, g, b, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT
                | GLES30.GL_DEPTH_BUFFER_BIT
                | GLES30.GL_STENCIL_BUFFER_BIT);
    }

    /** HSV(0..1) -> RGB(0..1)，参数面板里常用色相滑条。 */
    protected static float[] hsvToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float c = v * s;
        float x = c * (1f - Math.abs((h * 6f) % 2f - 1f));
        float m = v - c;
        float r, g, b;
        if (h < 1f / 6f) { r = c; g = x; b = 0; }
        else if (h < 2f / 6f) { r = x; g = c; b = 0; }
        else if (h < 3f / 6f) { r = 0; g = c; b = x; }
        else if (h < 4f / 6f) { r = 0; g = x; b = c; }
        else if (h < 5f / 6f) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return new float[]{r + m, g + m, b + m};
    }
}
