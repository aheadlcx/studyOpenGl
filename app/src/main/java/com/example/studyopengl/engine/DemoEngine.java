package com.example.studyopengl.engine;

import com.example.studyopengl.param.ParamSpec;

import java.util.List;

/**
 * 一个技术点的渲染引擎接口。所有回调保证发生在【自建 GL 线程】上，
 * 且调用时 EGL 上下文已经 makeCurrent —— 引擎内部可以放心调用 GLES30.*。
 */
public interface DemoEngine {

    /** 上下文首次可用时调用一次：编译 shader、创建 VAO/VBO/纹理等 GL 对象。 */
    void onSurfaceCreated(int width, int height);

    /** 表面尺寸变化（含首次）。 */
    void onSurfaceChanged(int width, int height);

    /** 每帧调用（vsync 驱动）。deltaTime 单位秒。 */
    void onDrawFrame(float deltaTime);

    /** 触摸事件（action 为 MotionEvent.ACTION_* 常量），坐标为像素。 */
    void onTouch(int action, float x, float y);

    /** 声明本 Demo 可调参数（需求 6）。 */
    List<ParamSpec> getParamSpecs();

    /** UI 线程调用：更新参数值并调度 onParamChanged 到 GL 线程。 */
    void setParamFromUi(ParamSpec spec, Object value);

    /** UI 线程读取当前参数值。 */
    Object getParamValue(String key);

    /**
     * 某个参数被 UI 修改后回调（GL 线程）。适合做结构性重建：
     * 重建缓冲、更新 UBO、切换 glTexParameteri 等；
     * 纯每帧读取的参数（颜色/速度）可以不实现，直接在 onDrawFrame 里读值。
     */
    void onParamChanged(ParamSpec spec);

    /** 上下文即将销毁：删除 GL 对象。 */
    void onSurfaceDestroyed();
}
