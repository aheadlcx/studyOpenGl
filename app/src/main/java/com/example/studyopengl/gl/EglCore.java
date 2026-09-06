package com.example.studyopengl.gl;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.util.Log;
import android.view.Surface;

/**
 * EGL 封装：不经过 GLSurfaceView，自己完成 display/config/context/surface 的全套创建。
 * 这是"自建 GL 线程"的地基 —— GL 线程持有 EglCore，在任意 Surface 上建立渲染环境。
 *
 * 关键 EGL 概念（面试高频）：
 *  - EGLDisplay：与显示服务器的连接，eglInitialize 后才能使用；
 *  - EGLConfig：帧缓冲属性描述（颜色位数、深度/模板位数、是否支持 ES3 等），
 *    通过 eglChooseConfig 按属性过滤挑选；
 *  - EGLContext：GL 状态机容器（对象、状态），通过 EGL_CONTEXT_CLIENT_VERSION=3
 *    声明要创建 OpenGL ES 3.0 上下文；同一时刻一个 context 只能 current 到一个线程；
 *  - EGLSurface：渲染目标，这里用窗口表面（ANativeWindow），也可用 PBuffer 离屏表面。
 */
public class EglCore {

    private static final String TAG = "EglCore";

    /** EGL_OPENGL_ES3_BIT = 0x40（EGL14 常量表未收录，用字面值） */
    private static final int EGL_OPENGL_ES3_BIT = 0x40;

    private EGLDisplay mDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mContext = EGL14.EGL_NO_CONTEXT;
    private EGLConfig mConfig;
    private int mEglMajorVersion;
    private int mEglMinorVersion;

    public EglCore() {
        // 1. 连接默认显示
        mDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("eglGetDisplay 失败");
        }

        // 2. 初始化，拿到 EGL 版本号
        int[] major = new int[1];
        int[] minor = new int[1];
        if (!EGL14.eglInitialize(mDisplay, major, 0, minor, 0)) {
            throw new RuntimeException("eglInitialize 失败 0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }
        mEglMajorVersion = major[0];
        mEglMinorVersion = minor[0];

        // 3. 挑选帧缓冲配置
        mConfig = chooseConfig();

        // 4. 创建 OpenGL ES 3.0 上下文
        mContext = createContext(mConfig);

        Log.i(TAG, "EGL " + mEglMajorVersion + "." + mEglMinorVersion
                + " 初始化完成, config=" + mConfig);
    }

    /**
     * 选择支持 ES3 的窗口配置：RGBA8888 + 24 位深度 + 8 位模板。
     * eglChooseConfig 按"属性重要性顺序"过滤，驱动返回至少满足要求的配置。
     */
    private EGLConfig chooseConfig() {
        int[] attrs = new int[]{
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT, // ES3 能力位
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 24,   // 深度测试/消隐需要
                EGL14.EGL_STENCIL_SIZE, 8,  // 模板测试 Demo 需要
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (EGL14.eglChooseConfig(mDisplay, attrs, 0, configs, 0, 1, numConfigs, 0)
                && numConfigs[0] > 0) {
            return configs[0];
        }

        // 兜底：放弃深度/模板要求（部分驱动配置不全）
        Log.w(TAG, "首选 EGLConfig 不可用，降级重试");
        int[] fallback = new int[]{
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE
        };
        if (EGL14.eglChooseConfig(mDisplay, fallback, 0, configs, 0, 1, numConfigs, 0)
                && numConfigs[0] > 0) {
            return configs[0];
        }
        throw new RuntimeException("设备不支持 OpenGL ES 3.0 的 EGLConfig");
    }

    private EGLContext createContext(EGLConfig config) {
        // EGL_CONTEXT_CLIENT_VERSION = 3 表示创建 ES3 上下文（0x3098）
        int[] attrs = new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE};
        EGLContext context = EGL14.eglCreateContext(
                mDisplay, config, EGL14.EGL_NO_CONTEXT, attrs, 0);
        checkEglError("eglCreateContext");
        if (context == EGL14.EGL_NO_CONTEXT) {
            throw new RuntimeException("eglCreateContext 失败");
        }
        return context;
    }

    /** 用已有 context 在新的 android.view.Surface 上建窗口表面。 */
    public EGLSurface createWindowSurface(Surface surface) {
        int[] attrs = new int[]{EGL14.EGL_NONE};
        EGLSurface eglSurface = EGL14.eglCreateWindowSurface(
                mDisplay, mConfig, surface, attrs, 0);
        checkEglError("eglCreateWindowSurface");
        if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException("eglCreateWindowSurface 失败");
        }
        return eglSurface;
    }

    /** 把 context 绑定到当前线程（渲染前必须调用，且只能被一个线程持有）。 */
    public void makeCurrent(EGLSurface surface) {
        if (!EGL14.eglMakeCurrent(mDisplay, surface, surface, mContext)) {
            throw new RuntimeException("eglMakeCurrent 失败 0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }
    }

    /** 解除当前线程绑定（销毁表面前调用）。 */
    public void makeNothingCurrent() {
        EGL14.eglMakeCurrent(mDisplay,
                EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
    }

    /** 后缓冲 -> 前缓冲，真正显示到屏幕。返回 false 表示表面已失效。 */
    public boolean swapBuffers(EGLSurface surface) {
        return EGL14.eglSwapBuffers(mDisplay, surface);
    }

    public int querySurface(EGLSurface surface, int what) {
        int[] value = new int[1];
        EGL14.eglQuerySurface(mDisplay, surface, what, value, 0);
        return value[0];
    }

    public void destroySurface(EGLSurface surface) {
        EGL14.eglDestroySurface(mDisplay, surface);
    }

    public EGLDisplay getDisplay() {
        return mDisplay;
    }

    public int getEglMajorVersion() {
        return mEglMajorVersion;
    }

    /** 线程退出时调用：销毁 context 并终结 display。 */
    public void release() {
        if (mDisplay != EGL14.EGL_NO_DISPLAY) {
            makeNothingCurrent();
            if (mContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(mDisplay, mContext);
            }
            EGL14.eglTerminate(mDisplay);
        }
        mDisplay = EGL14.EGL_NO_DISPLAY;
        mContext = EGL14.EGL_NO_CONTEXT;
        mConfig = null;
    }

    private void checkEglError(String op) {
        int error = EGL14.eglGetError();
        if (error != EGL14.EGL_SUCCESS) {
            Log.w(TAG, op + " 返回 EGL 错误 0x" + Integer.toHexString(error));
        }
    }
}
