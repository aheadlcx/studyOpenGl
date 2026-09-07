# 31 · 多重采样 MSAA

> 对应 App 第 31 项 · 难度：高级·帧缓冲

## 你将搞懂

- MSAA 的原理：多采样点 + 单次着色
- ES3 的完整流程：multisample renderbuffer → blit 解析 → 采样
- GL_MAX_SAMPLES 查询与钳制
- MSAA 治不了什么

## 先讲人话

锯齿来自"连续边界落在离散像素网格"。MSAA 的思路：**每个像素放 N 个采样点**，片元着色器只算一次，"被覆盖的采样点比例"决定写入颜色的混合比例——几何边缘一次变柔和，成本远低于 4 倍分辨率超采样。

> **Android 类比**：超采样=把整张画放大 4 倍画完再缩小（贵）；MSAA=只把边缘的每个像素分成 4 小格记录覆盖率（便宜且只优化边缘）。

## ES3 完整流程（三步）

```java
// 1) MSAA FBO：颜色/深度都是多重采样 renderbuffer
int samples = Math.min(wantSamples, maxSamples);   // 查 GL_MAX_SAMPLES 钳制
GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, colorRbo);
GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples,
        GLES30.GL_RGBA8, w, h);
GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER,
        GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_RENDERBUFFER, colorRbo);
// 深度同理：glRenderbufferStorageMultisample(DEPTH_COMPONENT24)

// 2) 场景画进 MSAA FBO
GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, msaaFbo);
drawScene();

// 3) blit 解析到普通 FBO（颜色位必须 GL_NEAREST）
GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, msaaFbo);
GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, resolveFbo);
GLES30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h,
        GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST);

// 4) 解析后的纹理作为全屏四边形输出到屏幕
```

为什么中间要有 resolve FBO：**ES 3.0 的 FBO 颜色附件不支持多重采样纹理**（那是 ES3.1 的 `glTexStorage2DMultisample`），所以必须用 renderbuffer 承载多重采样内容，再 blit 到普通纹理才能被 shader 采样。

## 在 App 里怎么玩（第 31 项）

- 场景刻意设计成"高对比细白十字线"——锯齿最容易被看见；
- **多重采样** 下拉：关闭 → 2x → 4x → 8x（超过设备 GL_MAX_SAMPLES 自动钳制）；
- 关闭时细线边缘呈明显台阶；开启后柔和平滑——同一管线、只多了一个 resolve 步骤；
- 注意 App 用半分辨率渲染目标，在真机上分辨率的差异会更明显。

## 常见坑

- **blit 颜色位用 GL_LINEAR**：带多重采样的 blit 只接受 GL_NEAREST，否则 `GL_INVALID_OPERATION`。
- **采样数超过 GL_MAX_SAMPLES**：renderbuffer 创建失败/FBO 不完整——先查询再钳制。
- **以为 MSAA 省事直接开窗口级 MSAA（EGL_SAMPLES）**：可行，但拿不到 resolve 后的纹理做后处理；FBO 方案才兼容后处理管线。
- **期待 MSAA 平滑纹理**：它只平滑几何边缘；纹理摩尔纹靠 mipmap（第 18 章），alpha 镂空边缘靠 alpha-to-coverage 或 softer clip。

## 原理图解与代码逐步拆解

```text
 无 MSAA（中心1点）                 4x MSAA（每像素4点）

 ┌───┬───┬───┐                    ┌───┬───┬───┐
 │ ▒ │   │   │                    │▪▒▪│   │   │   颜色只算一次
 ├───┼───┼───┤                    ├───┼───┼───┤   覆盖率 2/4=50%
 │   │ ▒ │   │                    │   │▪▒▪│   │   → 边缘柔和
 ├───┼───┼───┤                    ├───┼───┼───┤
 │   │   │ ▒ │                    │   │   │▪▒▪│
 └───┴───┴───┘                    └───┴───┴───┘
 二值硬边（锯齿）                   平滑渐变边

 ES3 流程: 场景→多重采样RBO(FBO) ─blit解析─▶ 普通纹理 → 采样上屏
```

逐步拆解：

1. `glRenderbufferStorageMultisample(target, samples, ...)` 创建 4x/8x 的颜色+深度存储（查询 GL_MAX_SAMPLES 先钳制）；
2. 场景画进 MSAA FBO——注意 ES3.0 多重采样只能配 renderbuffer（不能配纹理）；
3. `glBlitFramebuffer(..., GL_COLOR_BUFFER_BIT, GL_NEAREST)` 把 MSAA 内容**解析**（对采样点取平均）到普通 FBO 的纹理上；
4. 解析后的纹理作为全屏四边形采样显示；
5. 按 S 在 0/2/4/8（≤MAX_SAMPLES）间切换：细白线的边缘从硬台阶变柔和。

## 自测

1. MSAA 和 4× 超采样（SSAA）的本质区别？
2. 为什么 ES3.0 的 MSAA FBO 用 renderbuffer 而不用纹理？
3. `glBlitFramebuffer` 对带多重采样的颜色位有什么限制？

<details><summary>查看答案</summary>
1. SSAA 每个采样点都完整执行一遍渲染（4 倍成本）；MSAA 只在片元中心算一次颜色，采样点只记录"覆盖与否"，仅边缘付出解析成本。
2. ES3.0 不支持多重采样纹理附件（ES3.1 才有 glTexStorage2DMultisample），多重采样内容只能存 renderbuffer，读出来必须 blit 解析。
3. 颜色缓冲位必须用 GL_NEAREST；深度/模板位用 GL_NEAREST 或 GL_NONE。
</details>

➡️ 下一章：[32 · 多渲染目标 MRT](32-mrt.md)
