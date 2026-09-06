# 06 · 片元测试与双缓冲上屏

> 对应 App 第 06 项「第5站·测试混合与上屏」· 难度：入门概念

## 你将搞懂

- 片元写进屏幕前的四道固定顺序测试
- 每道测试的用途和对应 API
- Early-Z 为什么能省性能、什么时候失效
- 双缓冲 + vsync：为什么看不到"画一半"的画面

## 先讲人话：四道关

片元着色器算完颜色后**不是直接上屏**，而是按**固定顺序**（硬件决定，不可换）过四道测试，全部通过才能写入帧缓冲：

| 关 | 名称 | 一句话 | Android 类比 |
|---|---|---|---|
| ① | Scissor 裁剪框 | 只许画进指定矩形 | 给屏幕贴遮罩胶带 |
| ② | Stencil 模板 | 用之前画好的形状当"镂空纸" | 镂空喷漆模板 |
| ③ | Depth 深度 | 更近者胜（遮挡机制） | 前面的卡片挡住后面的 |
| ④ | Blend 混合 | 与已有颜色按公式混合 | 透明贴纸叠上去 |

任何一道失败 = 片元被丢弃，**后面的测试统统跳过**。

## 核心代码

```java
// ① 裁剪框：像素级矩形遮罩（glClear 也受它管！）
GLES30.glEnable(GLES30.GL_SCISSOR_TEST);
GLES30.glScissor(0, 0, width / 2, height);   // 只许画左半屏

// ② 模板：模板值==1 才放行（值从之前某次绘制写入）
GLES30.glEnable(GLES30.GL_STENCIL_TEST);
GLES30.glStencilFunc(GLES30.GL_EQUAL, 1, 0xFF);

// ③ 深度："更近者胜"
GLES30.glEnable(GLES30.GL_DEPTH_TEST);
GLES30.glDepthFunc(GLES30.GL_LESS);   // 新片元更近才通过（默认）
GLES30.glDepthMask(true);             // 深度写入开关（画半透明时关）

// ④ 混合：半透明公式
GLES30.glEnable(GLES30.GL_BLEND);
GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,           // 新色 × 它的 alpha
        GLES30.GL_ONE_MINUS_SRC_ALPHA);           // 旧色 × (1-alpha)
// 最终色 = 新色×a + 旧色×(1-a)；a=0.5 即一半透明
```

## Early-Z：聪明的深度测试

现代 GPU 会在片元着色器**之前**先做深度测试（early-z）：被挡住的片元连 shader 都不用跑，省掉大量计算。

前提：**不透明物体从近到远画**（先画近的占住深度，远的直接被拦）。失效条件：FS 里用了 `discard` 或手动写深度——硬件不敢提前判死刑。

## 双缓冲：防撕裂

屏幕 60 次/秒刷新，GPU 画一帧需要时间。如果直接画在"正在显示"的缓冲上，刷新瞬间可能拿到半新半旧画面（**撕裂**）。

解法：两块缓冲轮流用——

```text
后缓冲(back)  ：GPU 正在画的
前缓冲(front) ：屏幕正在显示的
一帧画完 → eglSwapBuffers() 两块交换（对齐 vsync 信号）
```

所以你**永远看不到画了一半的画面**。这套机制和 Android 的 Choreographer / SurfaceFlinger 同源：App 画离屏 buffer，合成器在 vsync 上屏。

## 每帧渲染循环（完整骨架）

```java
// 每帧三步（GLThread 里由 Choreographer 驱动）：
GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT); // 1.清屏
glDrawScene();                                    // 2. 所有物体依次绘制
EGL14.eglSwapBuffers(display, surface);           // 3. 交换上屏
```

## 在 App 里怎么玩（第 06 项）

- **上半屏闯关动画**：黄色片元从左往右跑，依次过 4 道关卡，全过则右侧帧缓冲格子点亮一个；
- **点关卡柱**：切换该道测试通过/失败（柱子绿变红）——看到片元在那一关变红坠落重来；比如关掉"深度"并选"失败"，片元永远倒在第 3 关；
- **下半屏双缓冲**：左"后缓冲"逐格填充（GPU 正在画），填满瞬间 SWAP 闪动、右"前缓冲"整屏刷新；
- 拖 **动画速度** 观察交换节奏。

## API 速查

| API | 作用 |
|---|---|
| `glScissor` + `GL_SCISSOR_TEST` | 像素矩形裁剪 |
| `glStencilFunc / glStencilOp` | 模板比较与回写规则 |
| `glDepthFunc / glDepthMask` | 深度比较 / 深度写入 |
| `glBlendFunc / glBlendEquation` | 混合因子与方程 |
| `eglSwapBuffers` | 交换前后缓冲 |

## 常见坑

- **画半透明忘了 `glDepthMask(false)`**：半透明面写深度 → 后面的半透明面被挡掉。
- **半透明没排序**：混合不满足交换律，从远到近画才正确（第 15 章细讲）。
- **Scissor 开了忘关**：之后所有 glClear/glDraw 都被限制在矩形里，画面"缺角"。
- **不开深度测试物体穿插**：默认没有深度测试！`glEnable(GL_DEPTH_TEST)` 要自己开。

## 自测

1. 四道测试的顺序是什么？可以调换吗？
2. 为什么"不透明物体从近到远画"更快？
3. 半透明物体为什么要关深度**写入**但保留深度**测试**？

<details><summary>查看答案</summary>
1. Scissor → Stencil → Depth → Blend；固定顺序，硬件流水线决定，不可换。
2. 先画近的占住深度缓冲，远处物体的片元在 early-z 阶段就被丢弃，省掉片元着色器执行。
3. 关写入：半透明面不该挡住它后面的物体（否则后面全部消失）；保留测试：它自己仍要被更近的不透明面正确遮挡。
</details>

➡️ 至此入门概念篇完结。下一章进入动手：[07 · 画第一个三角形](07-hello-triangle.md)
