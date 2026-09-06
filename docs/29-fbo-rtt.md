# 29 · 帧缓冲与渲染到纹理（RTT）

> 对应 App 第 29 项 · 难度：高级·帧缓冲

## 你将搞懂

- 默认帧缓冲与 FBO 的关系
- 创建 FBO 的四步流程与完整性检查
- 颜色附件（纹理）与深度附件（renderbuffer）
- RTT 的经典应用：后处理/镜子/阴影图

## 先讲人话

`eglCreateWindowSurface` 得到的渲染目标是**默认帧缓冲**（屏幕）。FBO 让我们**自建**渲染目标：把颜色画进一张纹理、把深度画进一个 renderbuffer——渲染结果直接变成可采样的纹理。

> **Android 类比**：默认帧缓冲 = 直接画在屏幕的 SurfaceView；FBO = 先画进一张离屏 `Bitmap`，想怎么再加工都行。后处理、镜子、传送门、阴影图、延迟渲染，地基全是它。

## 创建四步

```java
// 1. 颜色附件：一张普通纹理
GLES30.glGenTextures(1, tex, 0);
GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0]);
GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
        size, size, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
// RTT 惯例：CLAMP_TO_EDGE + 无 mipmap

// 2. 深度附件：renderbuffer（不能采样的专用存储）
GLES30.glGenRenderbuffers(1, rbo, 0);
GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, rbo[0]);
GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER,
        GLES30.GL_DEPTH_COMPONENT24, size, size);

// 3. 组装 FBO：绑定附件
GLES30.glGenFramebuffers(1, fbo, 0);
GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0]);
GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,
        GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, tex[0], 0);
GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER,
        GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, rbo[0]);

// 4. 完整性检查（必做！）
int status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) throw ...;
```

## 两 pass 渲染骨架

```java
// Pass1：场景 → FBO
GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mFBO);
GLES30.glViewport(0, 0, RT_SIZE, RT_SIZE);     // 视口切到 RTT 尺寸！
glClear(...); drawScene();

// Pass2：RTT 当纹理 → 屏幕
GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);  // 切回默认帧缓冲
GLES30.glViewport(0, 0, screenW, screenH);            // 视口切回屏幕！
GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0]);
drawFullScreenQuad();                                  // 采样它贴满屏幕
```

## 完整性规则

- 所有启用的 draw buffer 都必须有兼容附件（或显式 GL_NONE）；
- 附件尺寸一致、颜色可渲染格式；
- 失败码 `GL_FRAMEBUFFER_INCOMPLETE_*` 会告诉你缺什么。

## 在 App 里怎么玩（第 29 项）

- 4 个彩色立方体画进 1024² 的 FBO，再作为全屏纹理贴回屏幕；
- **RTT 采样缩放** 拉小：只采中心区域 → 内容"放大"，同时看到放大采样的模糊；
- **镜像采样**：uv.x 翻转；
- **亮度伪深度**：把画面转 HSV 彩虹色，近似观察明暗（真正深度可视化需采样深度纹理）。

## 常见坑

- **Pass2 忘了切回 glBindFramebuffer(0) 和 glViewport**：画面只占屏幕一角或全黑。
- **FBO 没检查 COMPLETE**：未定义行为，真机才暴露。
- **RTT 纹理用 REPEAT**：采样超界把对边绕进来 → 用 CLAMP_TO_EDGE。
- **深度没挂**：场景内遮挡失效（物体顺序错乱）。
- **每帧重建 FBO**：附件创建很贵，尺寸变化才重建。

## 自测

1. 为什么深度附件用 renderbuffer 而不用纹理？
2. `glFramebufferTexture2D` 和 `glFramebufferRenderbuffer` 的区别？
3. Pass1 和 Pass2 之间必须重设哪两个状态？

<details><summary>查看答案</summary>
1. ES3.0 的深度纹理采样受限（需 sampler2DShadow 或扩展），且深度只需要测试不需要读取——renderbuffer 是专用的高效存储。
2. 前者把纹理挂为颜色附件（可采样），后者把 renderbuffer 挂为深度/模板附件（仅测试用）。
3. 绑定的帧缓冲对象（FBO↔0）和 glViewport。
</details>

➡️ 下一章：[30 · 后处理卷积](30-post-processing.md)
