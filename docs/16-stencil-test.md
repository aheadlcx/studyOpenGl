# 16 · 模板测试与描边

> 对应 App 第 16 项 · 难度：进阶状态
> 对应 App 第 16 项 · 难度：进阶状态

```text
管线定位：①数据 ─ ②VS ─ ③装配 ─ ④裁剪 ─ ⑤光栅 ─ ⑥FS ─ 【⑦测试】
 ▲【】内为本章聚焦环节
```

## 你将搞懂

- 模板缓冲是什么、为什么需要 EGL 配置请求模板位
- `glStencilFunc` / `glStencilOp` 两个函数的参数含义
- 经典"物体描边三段式"的完整流程
- 模板的其它应用：镜面、传送门、阴影体

## 先讲人话

模板缓冲是每像素一个 **8 位整数**（本 App 的 EGL 配置请求了 `EGL_STENCIL_SIZE=8`）。它像一张"用几何写入、用几何读取"的掩码——先画个形状往模板里写标记，之后再画的东西只在标记允许的地方出现。

> **Android 类比**：镂空喷漆模板——第一遍喷出图案固化（写模板），第二遍换颜色从镂空处喷（读模板限制绘制区域）。

## 两个核心函数

```java
// 比较规则：片元的模板值 (s & mask) 与 (ref & mask) 按 func 比较
GLES30.glStencilFunc(func, ref, mask);
// 回写规则：三种结果各自怎么改模板值
GLES30.glStencilOp(sfail, dfail, dpass);
```

`glStencilOp` 的取值：`GL_KEEP / GL_ZERO / GL_REPLACE / GL_INCR / GL_DECR / GL_INVERT`（含 WRAP 变体）。还有 `glStencilFuncSeparate / glStencilOpSeparate` 对正反面用不同规则（阴影体必需）。

## 经典应用：物体描边三段式

```java
// Pass1：正常画物体，同时把覆盖区模板值写成 1
GLES30.glEnable(GLES30.GL_STENCIL_TEST);
GLES30.glStencilFunc(GLES30.GL_ALWAYS, 1, 0xFF);            // 永远通过
GLES30.glStencilOp(GLES30.GL_KEEP, GLES30.GL_KEEP, GLES30.GL_REPLACE); // 通过→写1
drawCube();

// Pass2：只允许模板值 ≠ 1 的片元通过（物体轮廓外）
GLES30.glStencilFunc(GLES30.GL_NOTEQUAL, 1, 0xFF);
GLES30.glStencilMask(0x00);      // 禁止再改模板
GLES30.glDepthMask(false);       // 不污染深度
Matrix.scaleM(outlineModel, 0, 1.12f, 1.12f, 1.12f);  // 放大 12%
drawCubeWithColor(orange);       // 只有"轮廓环带"能画出来 → 描边！

// 恢复
GLES30.glStencilMask(0xFF);
GLES30.glDisable(GLES30.GL_STENCIL_TEST);
GLES30.glDepthMask(true);
```

## 其它应用场景

| 场景 | 套路 |
|---|---|
| 镜面/传送门 | 镜面区域写模板 → 场景镜像绘制限制在该区域 |
| 阴影体 | 模板 INCR/DECR 计数进出，奇偶判定阴影（Separate 版本） |
| 共面绘制优先级 | 小地图边框、贴花 |

## 在 App 里怎么玩（第 16 项）

- 绿色立方体悬浮旋转，描边默认开启；
- **描边缩放** 滑条 1.02~1.5：环带宽度实时变化；
- **描边颜色** 色相滑条；
- 关掉"显示描边"再打开，观察 Pass2 的来去；
- 【代码】页签有完整三段式实现。

## 常见坑

- **EGL 配置没有模板位**：永远不通过——创建 context 时必须请求 `EGL_STENCIL_SIZE`（见 `EglCore`）。
- **glClear 忘带 `GL_STENCIL_BUFFER_BIT`**：上一帧模板残留，描边/镜面越描越乱。
- **Pass2 忘了 `glStencilMask(0x00)`**：描边又往模板里写值，越描越厚。
- **放大中心不在物体中心**：描边粗细不均——先 translate 到原点再 scale。

## 原理图解与代码逐步拆解

```text
 描边三段式（俯视截面）：

 Pass1   ██████          模板缓冲:  000111000
         ██████  ←画物体            └物体区=1

 Pass2   ██████          画放大物体  只允许"模板≠1"的片元
        ████████          ┌──────┐
 Pass3  ░░░░░░░░          │██████│  ░=只有环带被描边色覆盖 → 描边！
        ▓▓▓▓▓▓▓▓          └──────┘
```

逐步拆解：

1. 地板绘制前 `glStencilMask(0x00)`：地板不许改模板；
2. Pass1 画物体：`glStencilFunc(ALWAYS, 1)` 恒通过 + `glStencilOp(KEEP,KEEP,REPLACE)` 通过时把模板写为 1；
3. Pass2 `glStencilFunc(NOTEQUAL, 1)`：只放行模板值≠1 的片元；同时 `glStencilMask(0)`（不许改）+ `glDepthMask(false)`（不许写深度）；
4. Pass2 画**放大 1.12 倍**的同款物体：环带区（模板=0）被画成橙色，其余被模板挡掉 → 描边；
5. 恢复所有状态，否则下一帧全乱。

## 自测

1. `glStencilOp(KEEP, KEEP, REPLACE)` 三个参数分别对应什么结果？
2. 描边第二遍为什么用 `NOTEQUAL`？
3. 想实现"只在圆镜子里显示另一个场景"，流程是什么？

<details><summary>查看答案</summary>
1. 依次是：模板测试失败时、深度测试失败时、深度测试通过时对模板值的操作（这里只有通过才 REPLACE 写入 ref）。
2. 物体本身覆盖区模板值=1 被排除，放行的只有轮廓外的环带，配合放大几何正好形成描边。
3. 画圆形区域时向模板写入 1 → 绑定镜面相机渲染目标场景（限制在模板==1 区域）→ 恢复正常渲染。
</details>

➡️ 下一章：[17 · 2D 纹理与采样](17-texture-2d.md)
