# 15 · 混合 Blending：半透明的实现

> 对应 App 第 15 项 · 难度：进阶状态

## 你将搞懂

- 混合公式 C = F_src×C_src [EQ] F_dst×C_dst
- 常用因子组合（标准半透明/预乘/加色发光）
- 混合方程 ADD/SUB/MIN/MAX
- 半透明的三条铁律（深度写入、排序、时机）

## 先讲人话

启用 `GL_BLEND` 后，片元颜色不再直接覆盖帧缓冲，而是：

```text
C = F_src × C_src  [EQ]  F_dst × C_dst
    └源因子×片元色     [方程]  └目标因子×旧色
```

C_src = 片元着色器输出，C_dst = 帧缓冲已有颜色。`[EQ]` 默认 `GL_FUNC_ADD`，另有 SUBTRACT / REVERSE_SUBTRACT / **MIN / MAX**（ES3 新增）。

**Android 类比**：就是 Paint 的 alpha 合成（Porter-Duff）。`(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)` 正是 `SRC_OVER` 规则。

## 常用因子配方

| 配方 | 公式效果 | 用途 |
|---|---|---|
| `(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)` | 标准半透明 | 玻璃、贴纸 |
| `(ONE, ONE_MINUS_SRC_ALPHA)` | 预乘 alpha | 高质量合成（UI 贴图） |
| `(SRC_ALPHA, ONE)` | 加色发光 | 粒子、火焰、光晕（第 28 章粒子用它） |
| `(ONE, ONE)` + MAX | 取亮 | 光效叠加 |

## 核心代码

```java
// 半透明三连：开混合 + 设因子 + 关深度写入
GLES30.glEnable(GLES30.GL_BLEND);
GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
GLES30.glBlendEquation(GLES30.GL_FUNC_ADD);   // 也可 SUBTRACT/MIN/MAX
GLES30.glDepthMask(false);                    // 铁律：不写深度
// ……按"从远到近"顺序画所有半透明物体……
GLES30.glDepthMask(true);
GLES30.glDisable(GLES30.GL_BLEND);

// 常量颜色因子：不写 alpha 也能调透明度
GLES30.glBlendColor(0.3f, 0.6f, 0.9f, 0.5f);  // 供 GL_CONSTANT_ALPHA 使用

// RGB 与 A 分离（渲染到带 alpha 的离屏纹理时必用）
GLES30.glBlendFuncSeparate(
        GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA,   // RGB
        GLES30.GL_ONE,      GLES30.GL_ONE_MINUS_SRC_ALPHA);    // A
```

## 三条铁律

1. **画不透明物体时关 GL_BLEND**（混合有带宽成本，且不透明不需要）；
2. **混合时 `glDepthMask(false)`** 但保留深度测试——半透明面不遮挡别人，自己仍被不透明面正确遮挡；
3. **半透明物体从远到近排序绘制**——混合不满足交换律，顺序错了颜色就错（App 里"反转绘制顺序"开关专门演示）。

## 在 App 里怎么玩（第 15 项）

- **源/目标因子** 下拉全家族试一遍：`(ZERO, SRC_COLOR)` 会得到"乘法变暗"；
- **混合方程** 切 SUBTRACT/MIN/MAX：色彩艺术效果立刻出现；
- **反转绘制顺序** 开关：三个半透明板的叠加颜色前后互换——混合不可交换的铁证；
- **常量 alpha** 滑条 + 目标因子选 `GL_CONSTANT_ALPHA`：不用改 shader 就能调透明度。

## 常见坑

- **半透明写深度**：先画的透明面挡住后面的透明面（"透明面互相消失"）。
- **忘了排序**：多层玻璃顺序错误颜色明显不对——引擎里透明队列必排序。
- **预乘/非预乘混用**：边缘出现黑边/白边（Premultiplied Alpha 问题，Android Bitmap 也有同款坑）。
- **RTT 上做透明再叠回屏幕**：必须 `BlendFuncSeparate`，否则 alpha 通道被污染。

## 原理图解与代码逐步拆解

```text
 标准半透明公式（SRC_ALPHA, ONE_MINUS_SRC_ALPHA）：

   最终 = 新色×α + 旧色×(1-α)

   α=1.0 ──▶ 完全不透明（旧色权重 0）
   α=0.5 ──▶ 各占一半（玻璃）
   α=0.0 ──▶ 完全消失

 排序问题：三块玻璃 A(前) B(中) C(后)
   正确顺序 C→B→A：B 混合时"透过它看到 C" ✓
   错误顺序 A→B→C：C 最后画，直接盖在 A、B 上 ✗
   （混合不满足交换律：A混B ≠ B混A）
```

逐步拆解：

1. 先画**不透明**背景：混合关闭、深度写入开启——它们奠定"旧色"基底；
2. 切换到半透明阶段：开混合、**关深度写入**（透明面不该挡人）但保留深度测试（仍要被墙挡住）；
3. 三个旋转半透明板从远到近依次绘制；
4. 按下 R 反转顺序：混合顺序错乱，颜色明显不同——排序的重要性一目了然；
5. 换 blend 预设 `(SRC_ALPHA, ONE)`：加色发光，暗背景上颜色越叠越亮。

## 自测

1. `(SRC_ALPHA, ONE)` 的效果是什么？适合什么场景？
2. 为什么半透明要按从远到近排序，而不透明物体推荐从近到远？
3. `glBlendEquation(GL_MIN)` 的作用？

<details><summary>查看答案</summary>
1. C = src×srcA + dst×1——加色叠加，越叠越亮；适合粒子、火焰、光晕等发光效果（黑色背景完美消失）。
2. 半透明：混合依赖"已有的旧色"，必须后画近的（远→近）；不透明：early-z 按"先画近的占深度"（近→远）拦掉被挡片元，方向恰好相反。
3. 逐分量取新旧颜色的较小值，常用于光效抑制/特殊后处理。
</details>

➡️ 下一章：[16 · 模板测试](16-stencil-test.md)
