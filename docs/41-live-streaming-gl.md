# 41 · 直播场景的 OpenGL ES 知识地图

> 对应 App 第 34 项「直播预览实战」· 难度：综合实战
> 前置：17（纹理）、29（FBO/RTT）、33 章 App 内「调试工坊」（MVP / Viewport 探针）

```text
管线定位：直播 = 把这条渲染链的【输出】交给编码器，而不是交给屏幕
 【①相机OES纹理】→ 【②FBO 美颜/磨皮】→ 【③FBO 调色】→ 【④合成水印/画中画】→ 编码器(MediaCodec) → 推流
                     └────────── 全部发生在 GPU，一次充电跑 30/60 帧 ──────────┘
```

## 你将搞懂

- 直播前处理的**完整链路**：每一环的输入输出都是一张 GL 纹理
- 相机帧为什么必须用 **OES 外部纹理**，YUV→RGB 为什么在着色器里做
- **美颜/滤镜/磨皮/调色**对应的 GL 原语（FBO 多 pass、卷积、luma）
- **绿幕抠像**的判定式与羽化、**水印/画中画**的合成顺序
- 推流分辨率适配 = **viewport + 投影 aspect**（App 33 章节点8 的实战用法）
- 与 MediaCodec 硬编码的对接点：**EGLSurface 记录整个 EGL 环境**

## 先讲人话：直播画面的一生

把直播前处理想象成一条**照片冲洗流水线**，每道工序拿一张"底片"（纹理），洗出一张新"底片"（FBO 纹理）交给下一道：

```text
相机帧(OES)   →  美颜(FBO-A)  →  调色(FBO-B)  →  合成(最终FBO)  →  编码器
  毛坯底片        磨皮/滤镜        色温/饱和         水印/小窗        H.264 码流
```

> **Android 类比**：FBO 之于纹理，就像 `Canvas` 之于 `Bitmap`——你可以把画面画进一张离屏 Bitmap（FBO attach 的纹理），下一道工序再把它当输入画进另一张。编导只有最后一张会"播出"。

## 第一环：相机帧进 GL —— OES 外部纹理

相机给的每一帧是 **YUV(NV21)** 且由相机服务直接写入一块 Buffer，GL 没法用 `glTexImage2D` 手动上传（每帧 30 次拷贝会拖垮性能）。正确姿势是"零拷贝接管"：

```java
// 1. 创建 OES 纹理 + SurfaceTexture，把 Surface 交给相机
int[] tex = new int[1];
GLES30.glGenTextures(1, tex, 0);
GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0]);
GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
        GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
        GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
SurfaceTexture st = new SurfaceTexture(tex[0]);
st.setOnFrameAvailableListener(...);        // 有新帧的回调
camera.setOutputSurface(new Surface(st));   // CameraX/Camera2 同理

// 2. 每帧渲染前：确认有新帧 → 更新纹理
if (st.updateTimestamp()) st.updateTexImage(); // 把最新帧"发布"成 OES 纹理内容
```

```glsl
// 3. 片元着色器：采样类型换成 externalOES，一行矩阵 YUV→RGB
#extension GL_EXT_YUV_TARGET           // 部分vendor需要；多数驱动不写也能用
uniform samplerExternalOES u_cam;      // ⚠️ 不是 sampler2D
uniform mat4 u_yuv2rgb;                // 标准BT.601/706转换矩阵，官方demo可抄
...
vec4 yuv = texture(u_cam, v_uv);
vec3 rgb = (u_yuv2rgb * yuv).rgb;
```

| 坑 | 原因 |
|---|---|
| 采出来全黑 | sampler 类型没写 `samplerExternalOES`，或没 `updateTexImage()` |
| 画面绿紫相间 | YUV→RGB 矩阵用错（601 vs 709，全范围 vs 有限范围） |
| 画面倒了/镜像了 | 相机帧方向与屏幕方向不一致，用 uv 翻转矩阵修正（见第二环） |
| `sampler2D` 与 `samplerExternalOES` 绑同一单元 | 与 App 33 章同款坑：不同类型采样器不能指同一纹理单元 |

## 第二环：前处理链 —— 每一环都是一次 FBO pass

### 镜像 / 旋转 / 适配（uv 变换）

前置摄像头"照镜子"习惯、竖屏推流、分辨率适配，全部只是**采样坐标变换**：

```glsl
uv.x = 1.0 - uv.x;                       // 水平镜像
uv = mat2(0,-1,1,0) * (uv - 0.5) + 0.5;  // 旋转90°（竖屏推流）
```

分辨率适配（App 33 章节点8 的实战）：

```java
// 输入 16:9，输出 9:16：viewport 只切中间，投影 aspect 同步改
GLES30.glViewport(0, (fullH - cutH) / 2, fullW, cutH);
Matrix.perspectiveM(mProj, 0, fov, (float) fullW / cutH, near, far);
```

### 磨皮（模糊类卷积）

磨皮 = **保边的低通滤波**。教学版是"邻域平均混回原图"；产品级用双边滤波（按颜色差加权，保住边缘）：

```glsl
vec3 blur = vec3(0.0);
for (int i = 0; i < 8; i++) {
    blur += texture(u_tex, v_uv + u_dir[i] * u_radius).rgb;
}
blur /= 8.0;
vec3 beauty = mix(original, blur, 0.7);   // 混回原图，避免"糊脸"
```

> 大眼/瘦脸**不是像素操作**：用 ML Kit / 商汤等拿到人脸关键点，把人脸区域的**顶点网格**往外/往内顶——是顶点阶段（mesh warp）的活。

### 调色（色温 / 饱和度 / 亮度）

```glsl
float luma = dot(c, vec3(0.299, 0.587, 0.114));
c = mix(vec3(luma), c, u_saturation);        // 饱和度
c += vec3(u_warm*0.10, u_warm*0.02, -u_warm*0.10);  // 色温：R/B 反向偏移
c *= u_brightness;                            // 亮度（乘法式）
```

## 第三环：绿幕抠像与虚拟背景

```glsl
float greenness = clamp((c.g - max(c.r, c.b)) * 4.0, 0.0, 1.0);
float mask = smoothstep(u_thresh, u_thresh + 0.18, greenness); // 容差+羽化
c = mix(c, virtualBg, mask);
// 产品级还要做 spill suppression：把前景边缘残留的绿压回去
c.g = min(c.g, max(c.r, c.b) + 0.05);
```

| 细节 | 说明 |
|---|---|
| 容差 u_thresh | 太小：抠不干净；太大：偏绿肤色一起被抠 |
| 羽化 smoothstep | 阈值附近做平滑过渡，避免"锯齿蓝边" |
| 溢出抑制 | 头发丝上的绿色反光单独压通道 |
| 实际抠像 | 多在 HSV/UV 空间算色度距离，比 RGB 差值更稳 |

## 第四环：水印 / 画中画 / 连麦 —— 合成永远放最后

- **水印**：半透明纹理 + `glBlendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)`，在**最终帧缓冲上最后画**。画早了会被后续内容盖住；纹理记得用 `CLAMP_TO_EDGE` + premultiplied 注意事项。
- **画中画**：片元里判断"uv 落在小窗矩形内 → 用放大坐标再采样一路"；工程上多路视频是每路先渲染进自己的 FBO，最后在一个帧缓冲里用**多个 viewport** 各画一次（连麦布局=改几个矩形）。
- **推流适配**：小窗模式/连麦的布局变化，本质都是 viewport 矩形表——**编码器只认最终那份帧缓冲**，所以合成是流水线最后一站。

## 与 MediaCodec 对接：GPU 画完直接喂编码器

```java
MediaCodec codec = MediaCodec.createEncoderByType("video/avc");
codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
Surface encInput = codec.createInputSurface();   // 关键：编码器的"画布"
// 用 EGL 创建指向 encInput 的 EGLSurface（EGL_RECORDABLE_ANDROID）
EGLSurface encSurface = egl.createWindowSurface(encInput);
// 每帧：makeCurrent(encSurface) → 跑完渲染链 → eglSwapBuffers → 编码器出码流
```

> **为什么要懂手写 EGL**（本项目附录 A / GLThread）：`GLSurfaceView` 的输出只能上屏，不能给编码器。直播必须自己建 EGL 环境，让"渲染输出表面"指向编码器的 InputSurface。

## 性能要点（直播是实时战场）

| 要点 | 说明 |
|---|---|
| 能在 GPU 就不回 CPU | 禁止每帧 `glReadPixels`（会强制同步，掉帧元凶） |
| FBO 数量最小化 | pass 越多带宽越贵；小效果合进同一个 shader |
| 纹理尺寸 | 前处理纹理用推流分辨率（如 720x1280），别用相机原始 4K |
| float 精度 | 片元里 `precision mediump` 够用；坐标计算留 highp |
| 上传下载异步 | 大图用 PBO / fence（docs 35/36），避免 CPU 等 GPU |
| 帧率对齐 vsync | Choreographer 驱动（本项目 GLThread 的做法） |

## 常见坑

1. **OES 纹理用 `GL_TEXTURE_2D` 绑定** → 采出全黑；绑定目标必须是 `GL_TEXTURE_EXTERNAL_OES`。
2. **两个不同类型采样器指同一纹理单元**（sampler2D + samplerExternalOES）→ draw 直接 INVALID_OPERATION（App 33 章踩过）。
3. **画面绿紫马赛克** → YUV 矩阵/色彩范围错误，换 BT.601 full-range 矩阵试试。
4. **前置画面没镜像** → 用户预期是"照镜子"，uv.x 翻转一次。
5. **美颜把边缘糊掉** → 纯高斯模糊的锅，换双边滤波或降低混合比例。
6. **水印画完被盖住** → 合成顺序错了，水印必须最后画。
7. **编码出绿花屏** → `swapBuffers` 前 `glFinish` 乱用/没用对 EGLSurface，确保当前表面是编码器表面。

## 自测

1. 相机帧为什么不能每帧 `glTexImage2D` 上传？OES + SurfaceTexture 解决了什么？
2. 磨皮和美颜"大眼瘦脸"分别作用在渲染管线的哪个阶段？
3. 16:9 相机帧推 9:16 流，viewport 和投影 aspect 各要怎么改？
4. 绿幕抠像的判定式是什么？容差滑杆在调什么？
5. 为什么水印必须最后画？连麦两路视频最后怎么合成？

<details><summary>查看答案</summary>
1. 每帧 30 次大块内存拷贝会占满带宽；OES 让相机服务直写 GL 可采样的纹理，`updateTexImage()` 零拷贝发布新帧。
2. 磨皮/调色在片元阶段（像素卷积）；大眼瘦脸在顶点阶段（人脸网格 warp）。
3. viewport 裁掉左右（或上下）留出目标比例矩形；perspectiveM 的 aspect 改成"输出宽/输出高"，否则画面被拉扁。
4. `greenness = G - max(R,B)`，超过阈值判为绿幕；容差滑杆控制 smoothstep 的起始阈值（决定抠多狠/羽化多宽）。
5. 编码器只认最终帧缓冲，后画的内容会盖住先画的；连麦 = 每路先 FBO，最后在一个帧缓冲里用多个 viewport 各画一次。
</details>

➡️ 下一站：[附录 A · EGL 与自建 GL 线程](appendix-a-egl-glthread.md)（直播里手写 EGL 是刚需）
