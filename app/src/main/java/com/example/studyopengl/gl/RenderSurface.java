package com.example.studyopengl.gl;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * 普通 SurfaceView + SurfaceHolder.Callback，把表面事件转发给 GLThread。
 * 刻意不用 GLSurfaceView：它的 GLThread 是私有的，EGL 上下文我们拿不到，
 * 也就无法演示 EGL 细节（需求 3）。
 */
public class RenderSurface extends SurfaceView implements SurfaceHolder.Callback {

    private GLThread mGLThread;

    public RenderSurface(Context context) {
        super(context);
        init();
    }

    public RenderSurface(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        getHolder().addCallback(this);
    }

    public void setGLThread(GLThread thread) {
        mGLThread = thread;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mGLThread != null) {
            mGLThread.surfaceAvailable(holder.getSurface());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mGLThread != null) {
            mGLThread.surfaceChanged(width, height);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // 阻塞直到 GL 线程真正释放了 EGL 表面
        if (mGLThread != null) {
            mGLThread.surfaceDestroyedBlocking();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mGLThread != null) {
            final int action = event.getActionMasked();
            final float x = event.getX();
            final float y = event.getY();
            // 触摸事件投递到 GL 线程，引擎内部无锁读取
            mGLThread.postTouch(action, x, y);
        }
        return true;
    }
}
