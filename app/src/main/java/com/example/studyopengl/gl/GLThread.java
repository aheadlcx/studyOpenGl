package com.example.studyopengl.gl;

import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES30;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Choreographer;
import android.view.Surface;

import com.example.studyopengl.engine.BaseDemoEngine;
import com.example.studyopengl.engine.DemoEngine;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 自建 GL 渲染线程（需求 3 的核心）。
 *
 * 与 GLSurfaceView 内置 GLThread 的区别：
 *  - 我们直接持有 EGLDisplay/EGLContext/EGLSurface，完全掌控创建/销毁/切换时机；
 *  - 帧调度用 Choreographer（vsync 对齐，不空转烧 CPU）；
 *  - 引擎所有 GL 回调都运行在本线程；UI 线程通过 postGLTask 把任务投递进来，
 *     保证 GL 对象只被创建它的线程访问（GL 对象不是线程安全的）。
 *
 * 生命周期（对齐 SurfaceHolder.Callback 与 Activity）：
 *  surfaceAvailable -> 建 EGLSurface + makeCurrent + onSurfaceCreated
 *  surfaceChanged   -> 记录尺寸，下一帧开始时通知引擎 onSurfaceChanged
 *  surfaceDestroyed -> 同步等待 GL 线程释放表面后 UI 线程才返回（防 Surface 被复用）
 *  pause/resume     -> Activity 后台时停帧省电；context 保留，surface 销毁
 *  exit             -> 引擎释放 GL 对象 -> 销毁表面/context -> 退出 Looper
 */
public class GLThread extends Thread implements BaseDemoEngine.GLTaskPoster {

    private static final String TAG = "GLThread";

    public interface GlInfoListener {
        /** 上下文建立后回调（GL 线程），参数是拼接好的设备 GL 能力信息。 */
        void onGlInfo(String info);
    }

    private static final int MSG_SURFACE_AVAILABLE = 1;
    private static final int MSG_SURFACE_CHANGED = 2;
    private static final int MSG_SURFACE_DESTROYED = 3;
    private static final int MSG_PAUSE = 4;
    private static final int MSG_RESUME = 5;
    private static final int MSG_EXIT = 6;

    private static class Msg {
        Surface surface;
        int w, h;
        CountDownLatch latch;
    }

    private final Object mTaskLock = new Object();
    private final ArrayList<Runnable> mTasks = new ArrayList<Runnable>();
    private final CountDownLatch mStartedLatch = new CountDownLatch(1);

    private Handler mHandler;                    // 绑定本线程的 Looper
    private Choreographer mChoreographer;        // vsync 帧调度器
    private EglCore mEgl;
    private EGLSurface mEglSurface;
    private volatile DemoEngine mEngine;
    private volatile GlInfoListener mGlInfoListener;

    private boolean mSurfaceReady;
    private volatile boolean mPaused;
    private volatile boolean mExited;
    private boolean mFrameScheduled;

    private int mWidth, mHeight;
    private int mPendingW = -1, mPendingH = -1;
    private long mLastFrameNanos;
    private volatile float mFps;

    public GLThread() {
        super("GLRenderThread");
        start();
        try {
            // 等 handler 创建完成，保证后续消息不丢失
            mStartedLatch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        Looper.prepare();
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_SURFACE_AVAILABLE:
                        handleSurfaceAvailable((Surface) msg.obj);
                        break;
                    case MSG_SURFACE_CHANGED:
                        mPendingW = msg.arg1;
                        mPendingH = msg.arg2;
                        break;
                    case MSG_SURFACE_DESTROYED:
                        handleSurfaceDestroyed();
                        break;
                    case MSG_PAUSE:
                        mPaused = true;
                        break;
                    case MSG_RESUME:
                        mPaused = false;
                        scheduleFrame();
                        break;
                    case MSG_EXIT:
                        handleExit();
                        break;
                }
            }
        };
        mStartedLatch.countDown();
        Looper.loop(); // 阻塞直到 quit()
    }

    // ==================== UI 线程 API ====================

    public void setEngine(DemoEngine engine) {
        mEngine = engine;
    }

    public void setGlInfoListener(GlInfoListener listener) {
        mGlInfoListener = listener;
    }

    /** SurfaceHolder.surfaceCreated 回调转发。 */
    public void surfaceAvailable(Surface surface) {
        if (mHandler == null || mExited) return;
        Message msg = Message.obtain(mHandler, MSG_SURFACE_AVAILABLE);
        msg.obj = surface;
        mHandler.sendMessage(msg);
    }

    /** SurfaceHolder.surfaceChanged 回调转发。 */
    public void surfaceChanged(int width, int height) {
        if (mHandler == null || mExited) return;
        mHandler.sendMessage(Message.obtain(mHandler, MSG_SURFACE_CHANGED, width, height));
    }

    /**
     * SurfaceHolder.surfaceDestroyed 回调转发。
     * 必须同步等待：返回后系统可能立即回收/复用 Surface，
     * 若 GL 线程还在 swapBuffers 会崩溃或产生黑屏残影。
     */
    public void surfaceDestroyedBlocking() {
        if (mHandler == null || mExited) return;
        final CountDownLatch latch = new CountDownLatch(1);
        Message msg = Message.obtain(mHandler, MSG_SURFACE_DESTROYED);
        msg.obj = latch;
        mHandler.sendMessage(msg);
        try {
            latch.await(1500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void pauseRendering() {
        if (mHandler == null) return;
        mHandler.sendEmptyMessage(MSG_PAUSE);
    }

    public void resumeRendering() {
        if (mHandler == null || mExited) return;
        mHandler.sendEmptyMessage(MSG_RESUME);
    }

    /** Activity 销毁：释放所有 GL 资源并结束线程。 */
    public void exitAndWait() {
        if (mHandler == null || mExited) return;
        mHandler.sendEmptyMessage(MSG_EXIT);
        try {
            join(2500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 需求 6 的管线：UI 改参数 -> 投递到 GL 线程的待执行队列。 */
    @Override
    public void postGLTask(Runnable r) {
        if (mExited) return;
        synchronized (mTaskLock) {
            mTasks.add(r);
        }
    }

    /** 触摸事件转发：在 GL 线程上回调 engine.onTouch。 */
    public void postTouch(final int action, final float x, final float y) {
        postGLTask(new Runnable() {
            @Override
            public void run() {
                DemoEngine e = mEngine;
                if (e != null) {
                    e.onTouch(action, x, y);
                }
            }
        });
    }

    public float getFps() {
        return mFps;
    }

    // ==================== GL 线程内部 ====================

    private final Choreographer.FrameCallback mFrameCallback =
            new Choreographer.FrameCallback() {
                @Override
                public void doFrame(long frameTimeNanos) {
                    renderFrame(frameTimeNanos);
                }
            };

    private void scheduleFrame() {
        if (mExited || mPaused || !mSurfaceReady || mFrameScheduled) return;
        if (mChoreographer == null) {
            // Choreographer 是 ThreadLocal 的，必须在 GL 线程获取
            mChoreographer = Choreographer.getInstance();
        }
        mFrameScheduled = true;
        mChoreographer.postFrameCallback(mFrameCallback);
    }

    private void renderFrame(long nanos) {
        mFrameScheduled = false;
        if (mExited || mPaused || !mSurfaceReady || mEngine == null) return;

        drainTasks();

        // 尺寸变化统一在这里生效，保证 onSurfaceChanged 也在 GL 线程
        if (mPendingW >= 0 && (mPendingW != mWidth || mPendingH != mHeight)) {
            mWidth = mPendingW;
            mHeight = mPendingH;
            mPendingW = -1;
            mEngine.onSurfaceChanged(mWidth, mHeight);
        }

        long delta = mLastFrameNanos == 0 ? 16666666L : nanos - mLastFrameNanos;
        mLastFrameNanos = nanos;
        float seconds = Math.min(0.1f, delta / 1000000000f);

        try {
            mEngine.onDrawFrame(seconds);
        } catch (Throwable t) {
            Log.e(TAG, "onDrawFrame 异常", t);
        }

        // 前后缓冲交换；返回 false 说明窗口表面已失效，等待新的 surfaceCreated
        if (mEglSurface != null && mEgl != null && !mEgl.swapBuffers(mEglSurface)) {
            Log.w(TAG, "eglSwapBuffers 失败 0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }

        // FPS 指数平滑
        float instant = 1000000000f / Math.max(1L, delta);
        mFps = mFps == 0f ? instant : mFps * 0.9f + instant * 0.1f;

        scheduleFrame(); // 预约下一个 vsync
    }

    private void drainTasks() {
        ArrayList<Runnable> batch;
        synchronized (mTaskLock) {
            if (mTasks.isEmpty()) return;
            batch = new ArrayList<Runnable>(mTasks);
            mTasks.clear();
        }
        for (Runnable r : batch) {
            try {
                r.run();
            } catch (Throwable t) {
                Log.e(TAG, "GL task 异常", t);
            }
        }
    }

    private void handleSurfaceAvailable(Surface surface) {
        if (mExited || mSurfaceReady) return;
        try {
            if (mEgl == null) {
                mEgl = new EglCore();
            }
            // Surface 销毁再重建时复用同一个 context（纹理/VBO 不用重建）
            mEglSurface = mEgl.createWindowSurface(surface);
            mEgl.makeCurrent(mEglSurface);

            // 必须先 makeCurrent（有当前上下文）才能调用任何 GLES30.* 查询
            if (mGlInfoListener != null) {
                mGlInfoListener.onGlInfo(buildGlInfo());
            }

            mWidth = mEgl.querySurface(mEglSurface, EGL14.EGL_WIDTH);
            mHeight = mEgl.querySurface(mEglSurface, EGL14.EGL_HEIGHT);
            mPendingW = mWidth;
            mPendingH = mHeight;

            if (mEngine != null) {
                mEngine.onSurfaceCreated(mWidth, mHeight);
                mEngine.onSurfaceChanged(mWidth, mHeight);
            }
            mSurfaceReady = true;
            mLastFrameNanos = 0;
            Log.i(TAG, "GL 线程开始渲染 " + mWidth + "x" + mHeight);
            scheduleFrame();
        } catch (RuntimeException e) {
            Log.e(TAG, "初始化 EGL 失败", e);
        }
    }

    private void handleSurfaceDestroyed() {
        if (mEglSurface != null && mEgl != null) {
            mSurfaceReady = false;
            // 先解绑再销毁表面；context 保留供下一个 surface 复用
            mEgl.makeNothingCurrent();
            mEgl.destroySurface(mEglSurface);
            mEglSurface = null;
        }
    }

    private void handleExit() {
        mExited = true;
        mSurfaceReady = false;
        if (mEglSurface != null && mEgl != null) {
            try {
                mEgl.makeCurrent(mEglSurface);
                if (mEngine != null) {
                    mEngine.onSurfaceDestroyed();
                }
            } catch (Throwable t) {
                Log.w(TAG, "引擎资源释放异常", t);
            }
            mEgl.makeNothingCurrent();
            mEgl.destroySurface(mEglSurface);
            mEglSurface = null;
        }
        if (mEgl != null) {
            mEgl.release();
            mEgl = null;
        }
        mHandler.getLooper().quit();
    }

    /** 拼接设备 GL 能力信息，展示在讲解面板底部。 */
    private String buildGlInfo() {
        int[] maxSamples = new int[1];
        int[] maxTexSize = new int[1];
        int[] maxAttribs = new int[1];
        int[] maxUnits = new int[1];
        int[] maxDrawBuffers = new int[1];
        int[] maxUboSize = new int[1];
        GLES30.glGetIntegerv(GLES30.GL_MAX_SAMPLES, maxSamples, 0);
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxTexSize, 0);
        GLES30.glGetIntegerv(GLES30.GL_MAX_VERTEX_ATTRIBS, maxAttribs, 0);
        GLES30.glGetIntegerv(GLES30.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS, maxUnits, 0);
        GLES30.glGetIntegerv(GLES30.GL_MAX_DRAW_BUFFERS, maxDrawBuffers, 0);
        GLES30.glGetIntegerv(GLES30.GL_MAX_UNIFORM_BLOCK_SIZE, maxUboSize, 0);
        return "GL_RENDERER: " + GLES30.glGetString(GLES30.GL_RENDERER) + "\n"
                + "GL_VERSION: " + GLES30.glGetString(GLES30.GL_VERSION) + "\n"
                + "GL_VENDOR: " + GLES30.glGetString(GLES30.GL_VENDOR) + "\n"
                + "GLSL: " + GLES30.glGetString(GLES30.GL_SHADING_LANGUAGE_VERSION) + "\n"
                + "MAX_TEXTURE_SIZE=" + maxTexSize[0]
                + "  MAX_VERTEX_ATTRIBS=" + maxAttribs[0]
                + "  MAX_TEXTURE_UNITS=" + maxUnits[0] + "\n"
                + "MAX_SAMPLES=" + maxSamples[0]
                + "  MAX_DRAW_BUFFERS=" + maxDrawBuffers[0]
                + "  MAX_UNIFORM_BLOCK_SIZE=" + maxUboSize[0];
    }
}
