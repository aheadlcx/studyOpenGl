# 附录 A · EGL 与自建 GL 线程源码解读

> 对应源码：`gl/EglCore.java`、`gl/GLThread.java`、`gl/RenderSurface.java`
> 前置：第 01 章管线全景

## 你将搞懂

- EGL 在"Java 代码 → 屏幕像素"之间的位置
- Display / Config / Context / Surface 四个概念
- 为什么要自建 GL 线程，GLSurfaceView 不香吗
- Surface 生命周期与 GL 资源的关系

## 先讲人话：EGL 是 GPU 与窗口系统之间的"装修队"

OpenGL ES 只负责"往一块内存上画"，但它不管：
- 这块内存从哪来（窗口系统给）
- 多个 App 怎么共享 GPU（需要协调）
- 画好的东西何时送到屏幕

这些**平台相关**的杂活由 **EGL** 负责（桌面是 WGL/GLX）。所以任何 GL 程序的第一步永远是：通过 EGL 把"画布+画笔环境"搭起来。

> **Android 类比**：GPU 是画家，OpenGL ES 是画画的技法，**EGL 是布展公司**——负责安排画室（Context）、画布（Surface）和挂画上墙（SwapBuffers）。

## 原理图解

```text
 Java 侧对象关系：

 ┌─────────────── EGLDisplay ───────────────┐
 │  与显示服务器的连接（一个进程一个）          │
 │                                          │
 │  ┌─ EGLConfig ─┐   ┌─ EGLContext ─┐      │
 │  │ 帧缓冲的"规格"│   │ GL 状态机：    │      │
 │  │ RGBA8888     │   │ 纹理/VBO/program│    │
 │  │ depth=24     │   └──────┬───────┘      │
 │  │ stencil=8    │          │ makeCurrent  │
 │  └─────────────┘            ▼              │
 │                   ┌─ EGLSurface ──┐        │
 │                   │ 窗口表面(屏幕)  │        │
 │                   │ 或 PBuffer(离屏)│       │
 └──────────────────────────────────────────┘
```

**关键规则**：
- Context（状态机）和 Surface（画布）是**分离**的，`eglMakeCurrent` 把它们"接线"到当前线程；
- 一个 Context **同一时刻只能在一个线程** current —— 这就是 GL 操作必须集中在"GL 线程"的根本原因；
- Surface 销毁后 **Context 可以保留**——重建 Surface 后纹理/VBO 全都在，不用重加载。

## EglCore 源码逐步拆解（对应 App 第 01~02 站的"地基"）

```java
// ① 连接显示服务器
mDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
eglInitialize(mDisplay, major, 0, minor, 0);        // 拿 EGL 版本

// ② 挑帧缓冲规格：RGBA8888 + 24位深度 + 8位模板 + "必须支持ES3"
int[] attrs = {
    EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
    EGL_RENDERABLE_TYPE, 0x40 /*EGL_OPENGL_ES3_BIT*/,
    EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
    EGL_DEPTH_SIZE, 24,       // 深度测试用（第14章）
    EGL_STENCIL_SIZE, 8,      // 模板测试用（第16章）
    EGL_NONE };
eglChooseConfig(mDisplay, attrs, configs, 1, numConfigs);

// ③ 创建 ES3 上下文：0x3098 = EGL_CONTEXT_CLIENT_VERSION
int[] ctxAttrs = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
mContext = eglCreateContext(mDisplay, config, EGL_NO_CONTEXT, ctxAttrs, 0);

// ④ 在 Android Surface 上建窗口表面
mSurface = eglCreateWindowSurface(mDisplay, config, androidSurface, attrs, 0);

// ⑤ 接线到当前线程 —— 之后这个线程里的 GLES30.* 调用才合法
eglMakeCurrent(mDisplay, mSurface, mSurface, mContext);
```

**容错细节**（都是真机会踩的）：
- `eglChooseConfig` 可能找不到完全匹配的配置——要做**降级重试**（去掉深度/模板要求再试一次）；
- 每次 EGL 调用后 `eglGetError()` 检查，错误码 `0x30xx` 各有含义。

## GLThread：自建渲染线程的正确姿势

### 为什么不用 GLSurfaceView？

| | GLSurfaceView | 自建（本工程） |
|---|---|---|
| 上手 | 快 | 需要理解 EGL |
| GL 上下文 | 内部私有，**拿不到** | 完全掌控 |
| EGL 能力 | 无法选择 stencil 等配置 | 全部可配 |
| 多 Surface / 离屏渲染 | 受限 | 自由（FBO/SurfaceTexture 均可） |
| 帧调度 | 固定连续渲染或按需 | 自己接 Choreographer（vsync 对齐） |
| 教学价值 | 黑盒 | ❶ 每一步都能讲清楚 |

**自建的意义**：学 GLES 的目的通常是做相机滤镜/视频处理/自定义引擎——这些场景 GLSurfaceView 都不够用，必须自己管 EGL。

### 线程模型（`GLThread.java` 核心逻辑）

```text
UI 线程                          GL 线程（本类自己）
─────────                        ─────────────────
onCreate:
  创建 engine
  new GLThread()  ────▶ Looper 准备好，等消息
onResume          ──MSG_RESUME──▶ mPaused=false, 排帧
SurfaceView
  surfaceCreated  ──MSG──▶ eglCreateWindowSurface
                              makeCurrent
                              engine.onSurfaceCreated()
                              Choreographer.postFrameCallback ← 开始逐帧
  surfaceChanged  ──MSG──▶ 记录尺寸，下一帧生效
  surfaceDestroyed ──同步阻塞──▶ 释放 EGLSurface 后才返回 ★
onPause           ──MSG_PAUSE──▶ 停止排帧（省电）
onDestroy         ──MSG_EXIT──▶ 释放资源 → looper.quit()
```

**帧循环**（`renderFrame`）：

```java
// Choreographer 回调（vsync 对齐，和 Android 刷新机制同源）
private void renderFrame(long nanos) {
    drainTasks();                        // ① 执行 UI 投递的任务（改参数等）
    if (尺寸变化) mEngine.onSurfaceChanged(w, h);
    mEngine.onDrawFrame(deltaTime);      // ② 引擎画一帧（管线 7 站跑一遍）
    eglSwapBuffers(...);                 // ③ 上屏
    mChoreographer.postFrameCallback(this);  // ④ 预约下一个 vsync
}
```

### 三个必须做对的细节

1. **surfaceDestroyed 必须同步等待**：系统在回调返回后可能立即复用 Surface。若 GL 线程还在 swapBuffers，会崩溃或黑屏。做法：发消息给 GL 线程 + `CountDownLatch.await()`，GL 线程处理完 `countDown()`。

2. **UI → GL 的数据传递**：GL 对象不是线程安全的，UI 线程**绝不能**直接调 GLES。本工程的管线：`ConcurrentHashMap` 存参数 → 任务队列 → GL 线程每帧开头 `drainTasks()` 执行——无锁且时序正确。

3. **Context 保留**：Surface 销毁只 `eglDestroySurface`，Context 留着；新 Surface 到来时直接 `makeCurrent` 继续——用户从后台回来，纹理/VBO 全都在，秒恢复。

## RenderSurface：10 行的胶水

```java
public class RenderSurface extends SurfaceView implements SurfaceHolder.Callback {
    public void surfaceCreated(SurfaceHolder h) { mGLThread.surfaceAvailable(h.getSurface()); }
    public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) { mGLThread.surfaceChanged(w, hh); }
    public void surfaceDestroyed(SurfaceHolder h) { mGLThread.surfaceDestroyedBlocking(); }
    public boolean onTouchEvent(MotionEvent e) { mGLThread.postTouch(...); return true; }
}
```

它存在的唯一意义：把 Surface 事件转发给 GL 线程。**刻意不用 GLSurfaceView**，因为它的 GLThread 是私有的，我们拿不到 EGL 上下文。

## 常见坑

- **在 UI 线程调 GLES**：`eglMakeCurrent` 没接到这个线程，调用要么报错要么静默失败——所有 GLES 调用必须在 GL 线程。
- **Activity 旋转重建**：默认 Activity 会销毁重建。要么 Manifest 里 `configChanges` 拦截，要么正确走 destroy/recreate（本 App 选择后者，因为 GLThread 会完整走一遍退出/重建）。
- **后台不暂停渲染**：`onPause` 必须停帧，否则后台耗电 + 可能被系统杀。
- **RDP/模拟器降级**：软件渲染的 GL 可能只有 ES 2.0 甚至 1.1——`eglCreateContext` ES3 失败时要有明确报错。

## 自测

1. Context 和 Surface 的区别是什么？销毁 Surface 时 Context 里的纹理还在吗？
2. 为什么 GL 对象不能在 UI 线程创建？
3. `surfaceDestroyed` 回调里如果不阻塞等待 GL 线程，会发生什么？
4. GLSurfaceView 与自建方案最大的能力差异在哪？

<details><summary>查看答案</summary>
1. Context 是 GL 状态机（纹理/VBO/program 都挂在它下面），Surface 是渲染目标画布。销毁 Surface 不影响 Context——纹理/VBO 全部保留，新 Surface makeCurrent 后继续可用。
2. GL 命令只对"当前接线的线程"生效。UI 线程没有 makeCurrent 过任何 Context，调用非法；且两个线程并发操作同一 Context 会破坏状态机。
3. 系统可能在回调返回后立即回收/复用 Surface，而 GL 线程可能还在对它 swapBuffers → 崩溃或产生残影。
4. GLSurfaceView 的 EGLContext/Config 是私有的：无法要求 stencil、无法对接 SurfaceTexture/离屏渲染、无法自己控制帧调度。自建才有相機滤镜、视频处理这些玩法。
</details>

◀ 返回 [docs/README.md](README.md)
