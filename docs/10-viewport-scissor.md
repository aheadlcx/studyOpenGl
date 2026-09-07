# 10 · 视口与裁剪框

> 对应 App 第 10 项 · 难度：基础状态

## 你将搞懂

- `glViewport` 做的坐标变换（以及它**不**裁剪）
- `glScissor` 做的像素级裁剪（以及 `glClear` 也受它管）
- 多视口分屏渲染的套路

## 先讲人话

| | glViewport | glScissor |
|---|---|---|
| 干什么 | NDC[-1,1] **映射**到窗口矩形 | 像素级**裁剪**片元 |
| 超出会怎样 | **不裁剪**！图元照画，溢出视口 | 直接丢弃 |
| 影响 glClear | 否 | **是** |
| 典型用途 | 全屏渲染基座、分屏 | 小地图、限定清屏、部分重绘 |

**视口变换公式**（y 从底部起算）：

```text
x_win = (x_ndc + 1) / 2 × w + ox
y_win = (y_ndc + 1) / 2 × h + oy
```

**Android 类比**：Viewport ≈ View 的 layout 尺寸（内容往里放）；Scissor ≈ `canvas.clipRect()`（超出的画不出来）。

## 核心代码：N×N 分屏

```java
int grid = 3;
float cellW = width / (float) grid;
float cellH = height / (float) grid;

for (int gy = 0; gy < grid; gy++) {
    for (int gx = 0; gx < grid; gx++) {
        int ox = Math.round(gx * cellW), oy = Math.round(gy * cellH);
        int cw = Math.round(cellW), ch = Math.round(cellH);

        // ① scissor 限定清屏区域 → 每格刷不同底色
        GLES30.glEnable(GLES30.GL_SCISSOR_TEST);
        GLES30.glScissor(ox, oy, cw, ch);
        GLES30.glClearColor(r, g, b, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        // ② 视口故意比格子小 20%：不裁剪时立方体会溢出格子——教学点！
        GLES30.glViewport(ox + inset, oy + inset, cw - inset*2, ch - inset*2);
        drawRotatingCube();
    }
}
GLES30.glDisable(GLES30.GL_SCISSOR_TEST);
GLES30.glViewport(0, 0, width, height);   // 用完恢复
```

## 在 App 里怎么玩（第 10 项）

- **网格 N** 拉到 3 或 4：每格一个旋转立方体、各自底色；
- **Scissor 开关**来回切：关掉后立方体"越界"画到相邻格子——直观证明 **glViewport 不裁剪**；
- 注意 Scissor 开启时每格底色是逐格 clear 出来的。

## 常见坑

- **以为 glViewport 会裁剪**：溢出内容画到视口外，通常再叠一层 Scissor 才是"分屏"。
- **切 FBO 后忘改 viewport**：渲染到 1024 的 RTT 后回到屏幕，viewport 还是 1024 → 画面只占 1/4。
- **glClear 被残留的 Scissor 限制**：清屏只清了一角，画面"缺一块"。
- **GL 坐标 y 向上**：第 0 行视口的 y = height - rowH，和 Android View 相反。

## 原理图解与代码逐步拆解

```text
  NDC 空间（数学坐标）              窗口（像素坐标）
   -1,1 ┌────┬────┬────┐ 1,1      ┌────┬────┬────┐
        │    │    │    │          │ 格1 │ 格2 │ 格3 │   glViewport 决定
      ──┼────┼────┼────┼──        ├────┼────┼────┤   NDC 落到哪个矩形
        │    │    │    │          │ 格4 │ 格5 │ 格6 │
     -1,└────┴────┴────┘ 1,-1     └────┴────┴────┘
                                     ▲▲
              Scissor 再叠一层"像素栅栏"：栅栏外的片元直接丢弃
              （溢出视口的立方体就是这么被拦住的）
```

逐步拆解：

1. 每格先 `glScissor` + `glClear`：把这一格刷成自己的底色（glClear 受 scissor 管——只清格子内）；
2. 再 `glViewport` 设成比格子小 20%：立方体的 NDC 坐标被映射到这个小矩形；
3. NDC 里 ±1 之外的三角形**不会**被 viewport 挡住，会画出格子——只有打开 scissor 栅栏才能拦住（开关对比的核心）；
4. 每格的 aspect 用格子自己的宽高比，否则立方体被拉伸；
5. 用完恢复 `glViewport(0,0,fullW,fullH)` + 关 scissor，防止影响下一帧。

## 自测

1. 只调用 `glViewport(0,0,100,100)` 画一个超出范围的大三角形，会看到什么？
2. 想让 `glClear` 只清屏幕右半边，怎么做？
3. 多视口渲染时投影矩阵的 aspect 应该用什么值？

<details><summary>查看答案</summary>
1. 三角形完整画出，超出 (0,0,100,100) 的部分照样显示——viewport 只做映射不裁剪。
2. `glEnable(GL_SCISSOR_TEST); glScissor(width/2, 0, width/2, height);` 再 clear。
3. 每个视口自己的宽高比（视口宽/视口高），否则每格内容被拉伸。
</details>

➡️ 下一章：[11 · 面剔除](11-face-culling.md)
