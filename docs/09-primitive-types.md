# 09 · 图元类型与索引绘制

> 对应 App 第 09 项 · 难度：基础动手

## 你将搞懂

- 全部 7 种 draw mode 的顶点组合规则
- 索引绘制为什么省顶点
- ES3 原生重启索引：一次 draw 画多段图元
- glPointSize / glLineWidth 的硬件限制

## 七种图元类型速查

| mode | 规则 | n 个顶点产出 |
|---|---|---|
| `GL_POINTS` | 每顶点一个点 | n 个点 |
| `GL_LINES` | 两两成对 | n/2 条线段 |
| `GL_LINE_STRIP` | 连折线 | n-1 条线段 |
| `GL_LINE_LOOP` | 闭合折线 | n 条线段 |
| `GL_TRIANGLES` | 三三成组 | n/3 个三角形 |
| `GL_TRIANGLE_STRIP` | 锯齿共享边 | n-2 个三角形 |
| `GL_TRIANGLE_FAN` | 扇形共享首顶点 | n-2 个三角形 |

strip/fan 的意义：**n+2 个顶点画 n 个三角形**，比 TRIANGLES 省近 3 倍顶点。

## 核心代码

```java
// 同一份圆环顶点（中心点 + 36 边形），不同索引缓冲切换 mode
int[] fanIndices = new int[segments + 2];      // 三角扇：中心+环
fanIndices[0] = 0;                             // 中心点
for (int i = 0; i <= segments; i++) {
    fanIndices[i + 1] = i % segments + 1;
}
GLES30.glDrawElements(GLES30.GL_TRIANGLE_FAN,
        fanIndices.length, GLES30.GL_UNSIGNED_INT, 0);

// 点模式下点的大小在顶点着色器里设置：
//     gl_PointSize = u_pointSize;
// 线宽在 CPU 侧设置（多数设备只保证 1）：
GLES30.glLineWidth(3f);   // 超过 1 通常被驱动钳制！
```

## 重启索引（ES3 新增，核心功能）

一次 draw call 画**多段不相连**的图元：

```java
GLES30.glEnable(GLES30.GL_PRIMITIVE_RESTART_FIXED_INDEX);
// 索引缓冲：[上半弧顶点..., 0xFFFFFFFF, 下半弧顶点...]
//            └─ strip 1                └ 重启符  └─ strip 2
GLES30.glDrawElements(GLES30.GL_TRIANGLE_STRIP, count,
        GLES30.GL_UNSIGNED_INT, 0);
GLES30.glDisable(GLES30.GL_PRIMITIVE_RESTART_FIXED_INDEX);
```

- 固定重启值：32 位索引用 `0xFFFFFFFF`，16 位用 `0xFFFF`；
- 对比 ES2 需要多次 draw call——省掉的是 **CPU 提交开销**（驱动校验、状态切换），不是 GPU 绘制本身。

## 在 App 里怎么玩（第 09 项）

- **绘制模式** 下拉：同一份"中心点+36 边形"顶点，切 8 种画法——重点看 POINTS（方形点）、LINE_LOOP（圆环）、TRIANGLE_FAN（实心饼）、重启索引（两条独立弧线一次画完）；
- **点大小滑条**：`gl_PointSize` 生效（点被光栅化成方块）；
- **线宽滑条**：多数模拟器/真机 >1 被钳制——亲眼验证硬件限制。

## 常见坑

- **`glLineWidth(5)` 没效果**：移动 GPU 普遍只支持 1；粗线用细长四边形画。
- **strip 顶点顺序错**：三角形出现"翻转"的叉叉——strip 按 (v0v1v2)(v2v1v3)(v2v3v4)… 锯齿连接，顺序敏感。
- **LINE_LOOP 自动闭合**：不需要自己补最后一条回到起点的线。
- **重启索引忘开 enable**：0xFFFFFFFF 被当成普通索引 → 顶点越界或乱画。

## 自测

1. 画一个五边形轮廓，最少用什么 mode、几个顶点？
2. TRIANGLE_STRIP 画 10 个三角形需要几个顶点？
3. 重启索引省的是 GPU 时间还是 CPU 时间？

<details><summary>查看答案</summary>
1. GL_LINE_LOOP，5 个顶点。
2. 12 个顶点（n+2）。
3. CPU 时间（减少 draw call 提交开销），GPU 绘制量不变。
</details>

➡️ 下一章：[10 · 视口与裁剪](10-viewport-scissor.md)
