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

## 原理图解与代码逐步拆解

```text
 Pass1（离屏）                       Pass2（上屏）
 ┌────────────────┐                 ┌────────────────┐
 │ glBindFBO(fbo)  │                │ glBindFBO(0)    │  ← 切回屏幕
 │ viewport(1024)  │                │ viewport(屏幕)   │
 │ 画立方体场景     │──颜色进纹理──▶ │ 采样它贴满屏幕    │
 │ 深度进 RBO      │                │ (后处理从这开始)  │
 └────────────────┘                 └────────────────┘

 FBO 组装: fbo + [颜色=纹理] + [深度=RBO] → glCheckFramebufferStatus 必须 COMPLETE
```

逐步拆解：

1. 颜色附件是一张**普通纹理**（以后要被采样做后处理）；深度附件是 **renderbuffer**（只参与测试，从不被读取——这是本例的选型依据，并非"深度不能用纹理"）；
2. `glFramebufferTexture2D / glFramebufferRenderbuffer` 把两者挂到 FBO 的挂点上；
3. `glCheckFramebufferStatus == GL_FRAMEBUFFER_COMPLETE` 不通过就不能用（缺深度/尺寸不一致都会失败）；
4. Pass1 画场景前把 viewport 切到 1024²；Pass2 切回屏幕尺寸——**两个状态每次都要重设**；
5. Pass2 用全屏四边形采样这张纹理：缩放 uv 就是放大缩小画面，翻转 uv.x 就是镜像。

## 原理图解：帧缓冲的三种附件全家福

一个 FBO（包括屏幕那个默认帧缓冲）的**每个像素**同时挂着三份记录：

```text
 每个像素 = 三层记录

 ┌────────────────────────────────────────────┐
 │ ① 颜色附件 COLOR_ATTACHMENT0..N             │  RGBA —— 片元着色器的输出写这里
 ├────────────────────────────────────────────┤
 │ ② 深度附件 DEPTH_ATTACHMENT                 │  数值 —— 离相机多近，遮挡判决用
 ├────────────────────────────────────────────┤
 │ ③ 模板附件 STENCIL_ATTACHMENT               │  8位整数 —— 手工掩码，镂空判决用
 └────────────────────────────────────────────┘

 FS 算出颜色 ─▶ ②模板测试(读/写模板) ─▶ ③深度测试(读/写深度)
                    │失败丢弃               │失败丢弃
                    ▼                      ▼
              ④混合 ──▶ 只有走完全程的片元才改写【颜色附件】
```

| 附件 | 存什么 | 载体选择 | 能否被 FS 采样 | 杀手级应用 |
|---|---|---|---|---|
| 颜色 | RGBA 颜色 | 纹理 或 RBO | ✅（RTT 核心） | 画面、后处理、MRT 多输出 |
| 深度 | 深度值 | RBO 或**深度纹理** | ✅ ES3 可采样深度纹理（阴影图） | 遮挡、阴影贴图 |
| 模板 | 8位掩码 | RBO（或 DEPTH24_STENCIL8 打包） | ❌ 只能靠测试间接用 | 描边、镜面/传送门、共面优先级 |

**深度纹理**：把 `GL_DEPTH_COMPONENT24` 纹理挂为深度附件，就能在 FS 里采样它（阴影贴图 = 从光源视角存深度，主渲染比较"该点在光源下的深度"）。若永远不需要读，选 renderbuffer——驱动可放得更优，且 ES3.0 的 MSAA 内容只能存 RBO。

**关系一句话**：颜色是"结果"，深度/模板是"判决依据"；判决不过，结果不落榜。

## 自测

1. 颜色、深度、模板三种附件分别存什么？各自被谁读写？
2. `glFramebufferTexture2D` 和 `glFramebufferRenderbuffer` 的区别？
3. Pass1 和 Pass2 之间必须重设哪两个状态？
4. 想做阴影贴图，深度附件应该选 renderbuffer 还是纹理？为什么？

<details><summary>查看答案</summary>
1. 颜色附件存片元输出的 RGBA（FS 写、屏幕/FS 读）；深度附件存深度值（光栅化写、深度测试读）；模板附件存 8 位掩码（glStencilOp 写、模板测试读）。
2. 前者把纹理挂为颜色附件（内容可被 FS 采样）；后者把 renderbuffer 挂为深度/模板附件（只参与测试，不可采样）。
3. 绑定的帧缓冲对象（FBO↔0）和 glViewport。
4. 用纹理（GL_DEPTH_COMPONENT24）：阴影贴图需要主渲染 pass 采样光源视角的深度做比较——renderbuffer 无法被采样。若深度永远只用于测试不读取，renderbuffer 是更优选择。
</details>

➡️ 下一章：[30 · 后处理卷积](30-post-processing.md)
