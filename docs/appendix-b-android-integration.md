# 附录 B · Android 实战集成：相机、Bitmap 与生命周期

> 面向场景：学完基础后，Android 开发者在真实项目里最常见的三件事——
> **渲染相机/视频（滤镜）**、**加载图片**、**截图导出**。

## 一、相机预览：SurfaceTexture 与 OES 纹理

### 为什么需要它

Android 相机/视频的帧不由你产生，而是系统推给你的。`SurfaceTexture` 就是一块"系统往里写帧、GL 往外采样"的桥梁——它是把 **CameraX/Camera2/MediaCodec 接入 GL 的唯一正规入口**。

### 原理图

```text
 CameraX/MediaCodec
       │ 输出帧（YUV）
       ▼
 SurfaceTexture (系统写入, 自动做 YUV→RGB)
       │ onFrameAvailable() 通知
       ▼
 updateTexImage()                    ← 把最新帧绑到"外部纹理"
       │
       ▼
 GL_TEXTURE_EXTERNAL_OES 纹理        ← 特殊纹理类型！
       │ FS: #extension GL_OES_EGL_image_external
       │     samplerExternalOES ...
       ▼
 正常管线绘制（滤镜=改 FS）→ 屏幕 / FBO / 录制
```

### 核心代码

```java
// 1. 创建外部纹理 + SurfaceTexture
int[] tex = new int[1];
GLES30.glGenTextures(1, tex, 0);
GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0]);
GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
        GL_TEXTURE_MIN_FILTER, GL_LINEAR);   // OES 不支持 mipmap！
GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
        GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);

SurfaceTexture surfaceTexture = new SurfaceTexture(tex[0]);
surfaceTexture.setOnFrameAvailableListener(st -> hasNewFrame = true);
Surface surface = new Surface(surfaceTexture);
camera.bindToLifecycle(lifecycleOwner, selector, camera.bindToLifecycle(...,
        new Preview.Builder().setTargetSurface(surface).build()));

// 2. 每帧：有新帧才 update（updateTexImage 要求纹理当前绑定）
if (hasNewFrame) {
    hasNewFrame = false;
    surfaceTexture.updateTexImage();      // 内部完成 YUV→RGB 绑定
    surfaceTexture.getTransformMatrix(mtx);  // 裁剪/方向修正矩阵！
}

// 3. FS 里的关键差异
#extension GL_OES_EGL_image_external : require
uniform samplerExternalOES u_camera;      // 不是 sampler2D！
...
vec4 c = texture(u_camera, v_uv);
```

### 三个必知的坑

1. **方向**：相机帧自带旋转变换（前后摄/横竖屏各不同），必须用 `getTransformMatrix()` 的矩阵乘到 uv 上，否则画面转 90°或镜像；
2. **OES 限制**：`GL_TEXTURE_EXTERNAL_OES` 不支持 mipmap、部分 wrap 模式，shader 里必须写 `#extension` 且用 `samplerExternalOES` 类型；
3. **updateTexImage 必须在 GL 线程**，且只在有新帧时调用（否则丢帧/卡顿）。

滤镜 = 在你的 FS 里对采到的 `c` 做任意处理（灰度/美颜/LUT），这就是相机滤镜 App 的全部基础。

## 二、加载 Bitmap / 保存截图

### Bitmap → 纹理

```java
Bitmap bmp = BitmapFactory.decodeResource(res, R.drawable.photo);
GLES30.glBindTexture(GL_TEXTURE_2D, texId);
GLUtils.texImage2D(GL_TEXTURE_2D, 0, bmp, 0);   // GLUtils 直接搞定对齐问题
bmp.recycle();
```

注意：GL 对象必须在 GL 线程创建。先在 UI 线程 decode，把 Bitmap 对象传给 GL 线程再上传。

### glReadPixels：截图（GL 帧缓冲 → Bitmap）

```java
// 必须在 GL 线程、且在 glSwapBuffers 之前读取（读的是当前帧缓冲）
int w = surfaceWidth, h = surfaceHeight;
ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4);
GLES30.glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, buf);

// GL 的第 0 行在底部，Bitmap 第 0 行在顶部 → 要垂直翻转
Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
buf.rewind();
bmp.copyPixelsFromBuffer(buf);
Matrix flip = new Matrix(); flip.setScale(1, -1, h / 2f, h / 2f);
Bitmap out = Bitmap.createBitmap(bmp, 0, 0, w, h, flip, false);
// 保存到相册：ImageSaver / MediaStore...
```

## 三、生命周期与上下文丢失

```text
 App 退后台 ──▶ 系统内存紧张 ──▶ GL 上下文可能被系统回收！
                                （SurfaceView 场景经常发生）
 回前台 ──▶ onSurfaceCreated 再次触发
```

- **原则**：所有 GL 对象（纹理/VBO/program）在 `onSurfaceCreated` 里创建——把它当"可重复进入"的函数写，本工程所有 Demo 均如此；
- 需要"跨 survive"的数据（用户编辑状态等）放 Java 层保存，重建时重新上传；
- 检测上下文丢失：`eglQueryContext` / 绘制后 `glGetError() == GL_CONTEXT_LOST`（部分实现）。

## 四、GLSurfaceView vs 自建：选型速查

| 需求 | 建议 |
|---|---|
| 简单 3D 展示、学习练手 | GLSurfaceView 也行 |
| 相机/视频滤镜 | **必须自建**（要对接 SurfaceTexture + 控制 EGL 配置） |
| 与 UI 混合、透明叠加 | 自建（GLSurfaceView 默认不透明） |
| 多 Surface 输出（预览+录制） | 自建（多 EGLSurface 共享 Context） |
| 想真正搞懂 EGL/GLThread | 自建（本工程） |

## 自测

1. 相机帧接入 GL 必须用哪种纹理类型？shader 里要加什么？
2. `updateTexImage()` 为什么必须在 GL 线程、且每帧最多调一次？
3. `glReadPixels` 读出来的图为什么是上下颠倒的？
4. 系统回收了 GL 上下文，你的 App 应该怎么恢复？

<details><summary>查看答案</summary>
1. `GL_TEXTURE_EXTERNAL_OES`；FS 顶部加 `#extension GL_OES_EGL_image_external : require` 并用 `samplerExternalOES` 类型。
2. SurfaceTexture 内部绑定的正是当前线程的 OES 纹理，跨线程调用非法；重复调用会跳帧或触发未定义行为（官方约定每帧一次）。
3. GL 帧缓冲第 0 行在左下角，Bitmap 第 0 行在左上角——坐标系相反，需垂直翻转。
4. 不试图"恢复"，而是等 `onSurfaceCreated` 重新触发时重建全部 GL 对象；用户数据保存在 Java 层重新上传。
</details>

◀ 返回 [docs/README.md](README.md)
