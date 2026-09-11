# 14 · 深度测试与 z-fighting

> 对应 App 第 14 项 · 难度：进阶状态
> 对应 App 第 14 项 · 难度：进阶状态

```text
管线定位：①数据 ─ ②VS ─ ③装配 ─ ④裁剪 ─ ⑤光栅 ─ ⑥FS ─ 【⑦测试】
 ▲【】内为本章聚焦环节
```

## 你将搞懂

- 深度缓冲的工作方式与 8 种 `glDepthFunc`
- 深度掩码 `glDepthMask` 的用途
- z-fighting 成因与多边形偏移解法
- Early-Z 与绘制顺序

## 先讲人话

每个片元带着插值出的深度值 [0,1]，`glDepthFunc` 设定的**比较**通过才能写颜色：默认 `GL_LESS` = 更近者胜。比较失败只丢颜色；`glDepthMask(GL_FALSE)` 则连深度也不写。

> **Android 类比**：像发牌——每张牌（片元）要盖在桌面上，得先和桌面上最上面的牌比"谁更靠前"；`DepthMask(false)` 就是"只比较、不把牌留在桌上"（透明贴纸）。

## 8 种深度函数

| 函数 | 通过条件 | 典型用途 |
|---|---|---|
| GL_NEVER | 永不 | 调试 |
| **GL_LESS** | 更近 | **默认/常规** |
| GL_EQUAL | 深度相等 | 同几何体二次渲染（贴花） |
| GL_LEQUAL | ≤ | 天空盒（z=w → 深度恰为 1.0，第 20 章） |
| GL_GREATER | 更远 | 反转深度（少见） |
| GL_NOTEQUAL | 不等 | 特殊遮罩 |
| GL_GEQUAL | ≥ | 同上 |
| GL_ALWAYS | 恒过 | 强制覆盖 |

## z-fighting 与多边形偏移

两个**共面**几何体（如地板+地板上的网格线）深度几乎相同，光栅化舍入导致颜色随机闪烁。解法：

```java
// 实心面往后推，让线框稳定浮在上面
GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL);
GLES30.glPolygonOffset(factor, units);   // offset = m×factor + r×units
drawSolidFloor();                        // m=深度斜率, r=硬件最小可分辨差
GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL);
drawGridLines();                          // 线不偏移 → 稳定露在面外
```

其它招：手动抬高 0.001f；拉大 near / 拉近 far 提高深度精度（第 13 章）；避免 truly 共面。

## 深度掩码：画半透明的标配

```java
GLES30.glDepthMask(false);   // 半透明物体：参与比较但不写深度
drawGlassPanels();           //   → 它们不遮挡彼此
GLES30.glDepthMask(true);    // 恢复
```

## Early-Z 与绘制顺序

现代 GPU 在片元着色器**之前**先测深度。不透明物体**从近到远**画：近的先占深度，远的在 early-z 被拦下，连 FS 都不执行——大场景提速显著。失效条件：FS 用 `discard` 或写深度。

## 在 App 里怎么玩（第 14 项）

- **深度函数** 下拉 8 种全试：GL_GREATER 出"远者胜"幽灵画面；GL_ALWAYS 一切通过只剩绘制顺序；
- **深度写入** 开关：关掉后再画的立方体不写深度——观察"穿透"效果；
- **多边形偏移** 开关 + factor/units 滑条：地板网格线从闪烁到稳定；
- 把 factor 调 0：闪烁回归。

## API 速查

| API | 作用 |
|---|---|
| `glEnable(GL_DEPTH_TEST)` | 开深度测试（**默认关闭！**） |
| `glDepthFunc` | 比较函数 |
| `glDepthMask(boolean)` | 深度写入开关 |
| `glPolygonOffset(factor, units)` + `GL_POLYGON_OFFSET_FILL` | 深度偏移 |
| `glClear(DEPTH_BUFFER_BIT)` | 清深度缓冲（每帧开头） |

## 常见坑

- **忘了开深度测试**：后画的永远盖住先画的（画家算法乱序）——`GL_DEPTH_TEST` 默认是关的！
- **每帧忘了清深度**：上一帧深度残留，物体时隐时现。
- **半透明开了深度写入**：透明面后的物体消失。
- **near/far 比值过大**：远处 z-fighting，第 13 章的深度非线性所致。

## 原理图解与代码逐步拆解

```text
 深度比较（GL_LESS 默认）：每一帧

  清深度=1.0
     │
     ▼  画红色立方体      画蓝色立方体
  帧缓冲:  红z=0.5 ✓写入   蓝z=0.3 ✓更近·覆盖红
           ─────────────────────────────
  若深度掩码关闭：红z写入✓   蓝z=0.3 通过但【不写】
                  → 红色立方体反而"挡住"蓝色（鬼影）

 z-fighting：两个共面片元 z 差 < 精度 → 谁赢随机 → 闪烁
   解法：polygonOffset 把填充面整体推远 → 线框稳定浮出
```

逐步拆解：

1. 每帧开头 `glClear(GL_DEPTH_BUFFER_BIT)`：深度全部重置为 1.0（最远）；
2. 每个片元的深度与缓冲中已存值按 `glDepthFunc` 比较，通过才写颜色+深度；
3. 多边形偏移：`offset = m×factor + r×units`（m=表面倾斜斜率，r=硬件最小精度），只推**填充**面，网格线不偏移所以稳定浮在上面；
4. 切深度函数到 GL_GREATER：世界反转——先画的（近的）被后画的（远的）覆盖；
5. 关深度掩码：比较仍进行但赢家不留深度记录 → 后画的总是盖上来。

## 自测

1. `glDepthMask(false)` 后深度测试还生效吗？
2. 多边形偏移公式里 m 和 r 是什么？
3. 为什么天空盒要 `glDepthFunc(GL_LEQUAL)`？

<details><summary>查看答案</summary>
1. 生效。Mask 只控制"是否把通过者的深度写入缓冲"，比较照常进行。
2. m=该表面的深度斜率（多边形相对视线的倾斜程度），r=深度缓冲的最小可分辨差（硬件常量）。
3. 天空盒故意把 gl_Position 设为 z=w，深度恰为 1.0；默认 GL_LESS 下 1.0 不小于已有的 1.0 会被剔掉，LEQUAL 才能通过。
</details>

➡️ 下一章：[15 · 混合 Blending](15-blending.md)
