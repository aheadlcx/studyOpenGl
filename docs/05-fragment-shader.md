# 05 · 片元着色器：每个像素的小程序

> 对应 App 第 05 项「第4站·片元着色器输入」· 难度：入门概念

## 你将搞懂

- 片元着色器（FS）的职责与执行量级
- FS 能拿到哪些输入（v_color / gl_FragCoord / v_uv / 纹理 / uniform）
- 6 个内建"招式"：插值色、FragCoord、uv、数学函数、fract 技巧、discard
- VS 与 FS 的成本差异

## 先讲人话

片元着色器是一个 mini 程序，GPU 对**每个片元执行一次**，唯一任务：**算出一个 RGBA 颜色**。

三角形覆盖 1 万个像素，它就跑 1 万次——但 GPU 有几千个核心**并行**跑，瞬间完成。

> **Android 类比**：把 `onDraw()` 里逐像素的 for 循环（比如操作 Bitmap）拆给几千个线程同时执行——Bitmap 逐像素处理正是该搬进 FS 的典型场景。

## FS 能拿到哪些输入？

| 输入 | 来源 | 典型用途 |
|---|---|---|
| `in vec3 v_color` 等 varying | 光栅化插值（白送） | 顶点色渐变、传 uv/法线 |
| `gl_FragCoord` | 内建（屏幕像素坐标） | 屏幕空间效果、条纹 |
| `gl_FrontFacing` | 内建（正面？） | 双面光照 |
| `sampler2D` 纹理 | `glBindTexture` + uv 采样 | 贴图 |
| `uniform` | CPU 每帧传入 | 矩阵、时间、灯位置 |

## 原理图：FS 的输入从哪来

```text
   顶点着色器(VS)                光栅化(白送)              片元着色器(FS)
┌──────────────────┐        ┌──────────────────┐     ┌──────────────────┐
│ out vec3 v_color  │ ─────▶ │  按重心坐标插值    │ ──▶ │ in vec3 v_color   │
│ out vec2 v_uv     │ ─────▶ │  (透视校正)        │ ──▶ │ in vec2 v_uv      │
└──────────────────┘        └──────────────────┘     │                   │
                                                      │  你的算法          │
   CPU ──uniform──▶ ═════════════════════════════════▶│                   │
                                                      └────────┬─────────┘
   屏幕坐标 ──────gl_FragCoord(内建)──────────────────────────▶│
                                                               ▼
                                                        out vec4 颜色
```

## 六个招式（App 可切换的真实代码）

```glsl
#version 300 es
precision mediump float;        // FS 必须声明浮点精度
in vec3 v_color;                // ① 插值色
in vec2 v_uv;                   // ③ uv
uniform int u_mode;
uniform float u_density, u_time;
out vec4 fragColor;

void main() {
    vec3 c = v_color;                                        // 【模式0】渐变白送
    if (u_mode == 1) {                                       // 【模式1】屏幕坐标条纹
        float s = step(0.5, fract(gl_FragCoord.x / u_density));
        c = v_color * (0.55 + 0.45 * s);
    } else if (u_mode == 2) {                                // 【模式2】uv 可视化
        c = vec3(v_uv.x, v_uv.y, 0.35);                      // R=水平 G=垂直
    } else if (u_mode == 3) {                                // 【模式3】程序化圆环
        float d = length(v_uv - 0.5);
        float ring = 0.5 + 0.5 * sin(d * 40.0 - u_time * 3.0);
        c = mix(vec3(0.95,0.55,0.20), vec3(0.20,0.55,0.95), ring);
    } else if (u_mode == 4) {                                // 【模式4】棋盘
        vec2 g = floor(v_uv * u_density * 0.25);
        c = mix(vec3(0.92), vec3(0.16,0.20,0.32), mod(g.x+g.y, 2.0));
    } else if (u_mode == 5) {                                // 【模式5】镂空
        if (length(v_uv - vec2(0.5,0.45)) < 0.22) discard;   // 直接否决自己
        c = vec3(0.30, 0.85, 0.60);
    }
    fragColor = vec4(c, 1.0);
}
```

要点解读：

- **`gl_FragCoord`**：屏幕像素坐标（中心在 x.5）——与三角形位置无关，适合屏幕空间效果；
- **`v_uv`**：贴图定位坐标 0~1，`texture(u_tex, v_uv)` 用它查贴图（第 17 章）；
- **数学函数**：`length/fract/step/mix/sin` 组合可以"无中生有"画任何花纹——Shadertoy 整个网站都靠这个；
- **`discard`**：FS 唯一能"否决自己"的手段（镂空、圆形头像不画方形黑边）。⚠ 会破坏 early-z 优化，少用。

## VS vs FS 成本

| | 顶点着色器 | 片元着色器 |
|---|---|---|
| 执行次数 | 每顶点一次（三角形=3 次） | 每片元一次（全屏=百万次） |
| 定位 | "顶点在哪" | "像素什么颜色" |
| 优化原则 | 能预乘的矩阵在 CPU 预乘 | 能放 VS 的计算别放 FS；但逐像素精细效果（法线贴图）只能在这 |

## 在 App 里怎么玩（第 05 项）

- **点屏幕左/右半边**：直接切换 6 种模式（比下拉框快），同时底部自动跳到【代码】页签对应片段；
- **密度滑条**：同时控制条纹/棋盘疏密——改一下立刻看到"FS 是逐像素执行的"；
- 观察【模式1】：条纹固定在屏幕上，旋转三角形条纹不动——因为 `gl_FragCoord` 是屏幕坐标不是模型坐标。

## API 速查

| GLSL 内建 | 作用 |
|---|---|
| `gl_FragCoord` | 片元屏幕坐标（vec3，z=深度） |
| `discard` | 丢弃当前片元 |
| `texture(sampler, uv)` | 纹理采样 |
| `length / fract / step / mix / clamp` | 常用数学函数 |

## 常见坑

- **忘写 `precision mediump float;`**：FS 编译直接失败（VS 有默认精度）。
- **in/out 名字不匹配**：VS 的 `out v_color` 和 FS 的 `in v_color` 名字、类型必须一致。
- **滥用 discard**：破坏 early-z，透明遮罩优先考虑 alpha 混合。
- **把逐物体常量放 FS 每像素算**：光照方向等 uniform 能算一次的别算百万次。

## 自测

1. FS 的执行次数由什么决定？
2. `discard` 的副作用是什么？
3. 想画一个"随时间转动的圆环花纹"，最少需要哪些输入？

<details><summary>查看答案</summary>
1. 片元数量——由图元覆盖的像素范围决定，与顶点数无关。
2. 破坏 early-z 深度提前优化，被 discard 的片元之前做的深度预判作废，性能下降。
3. `v_uv`（或 gl_FragCoord）加一个 `u_time` uniform 即可，纯数学可画（见模式 3）。
</details>

➡️ 下一章：[06 · 测试混合与上屏](06-tests-and-swap.md)
