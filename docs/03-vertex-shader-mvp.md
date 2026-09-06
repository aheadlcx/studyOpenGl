# 03 · 顶点着色器：MVP 四空间变换

> 对应 App 第 03 项「第2站·顶点着色器 MVP」· 难度：入门概念

## 你将搞懂

- 顶点着色器（VS）执行几次、输入输出是什么
- 局部 → 世界 → 相机 → 裁剪四个空间的含义
- Model / View / Projection 三个矩阵各自干了什么
- 为什么顺序必须是 P×V×M 且不可交换

## 先讲人话

顶点着色器是 GPU 对**每个顶点执行一次**的小程序。它的核心通常只有一行：

```glsl
gl_Position = u_proj * u_view * u_model * vec4(a_pos, 1.0);
```

三个矩阵把顶点从"模型自己的坐标"一步步搬到"屏幕坐标"：

| 空间 | 乘的矩阵 | 干什么 | Android 类比 |
|---|---|---|---|
| ① 局部空间 Local | —（起点） | 模型自身坐标系，美术导出的坐标 | View 内部画自己内容时的坐标 |
| ② 世界空间 World | × **Model** | 把模型平移/旋转/缩放"摆进"场景 | `translationX` / `rotationY` / `scaleX` |
| ③ 相机空间 View | × **View** | 相机变换的逆：相机环绕=世界反向动 | 相机永远在原点，世界反着动 |
| ④ 裁剪空间 Clip | × **Projection** | 定义视锥（fov/near/far），把 z 编进 w | 产生透视的源头 |

> **顺序类比**：先穿衣服(S)→再转身(R)→最后出门(T)。矩阵乘法"右边的先作用"，所以写 `P·V·M`，且**顺序不可交换**——先出门再穿衣服就乱套了。

## 核心代码

**顶点着色器全文**（就干一件事）：

```glsl
#version 300 es
layout(location=0) in vec3 a_pos;      // 输入：顶点位置（来自 VBO）
uniform mat4 u_mvp;                    // CPU 预乘好的 P·V·M
void main() {
    gl_Position = u_mvp * vec4(a_pos, 1.0);   // w 分量=1 表示"点"
}
```

**CPU 侧构建三个矩阵**（`android.opengl.Matrix`，列主序）：

```java
float[] model = new float[16];
Matrix.setIdentityM(model, 0);
Matrix.translateM(model, 0, 0.65f, 0, 0);   // T：摆到场景右侧（先调用）
Matrix.rotateM(model, 0, angle, 0, 1, 0);   // R：后调用的先作用 → 等效 T×R

float[] view = new float[16];
Matrix.setLookAtM(view, 0,
        eyeX, eyeY, eyeZ,    // 相机位置（环绕时 eye 沿圆周动）
        0f, 0f, 0f,          // 看向哪
        0f, 1f, 0f);         // up 向量：哪边是"上"

float[] proj = new float[16];
Matrix.perspectiveM(proj, 0,
        fovY,      // 垂直视场角（度）
        aspect,    // 宽高比 = surface宽/高
        near, far);// 近/远切面距离

// 合体：顺序固定 P×V×M
float[] pv = new float[16], mvp = new float[16];
Matrix.multiplyMM(pv, 0, proj, 0, view, 0);
Matrix.multiplyMM(mvp, 0, pv, 0, model, 0);
GLES30.glUniformMatrix4fv(loc, 1, false, mvp, 0); // false=已按列主序
```

## 四个关键观念

1. **Model 顺序陷阱**：矩阵"后调用的先作用"。`translateM` 后 `rotateM` = 先转再平移（绕自身转）；反过来就是"绕远处公转"。
2. **View 是相机变换的逆**：相机没有"自己的矩阵"——相机向右转 = 整个世界向左转。所以"天空盒无限远"要手动去掉 View 的平移。
3. **w 分量是透视的种子**：`vec4(a_pos, 1.0)` 的 1.0 经投影矩阵变成 w，下一站"除以 w"就产生近大远小。
4. **VS 不能增删顶点**：它能移动顶点、能一次算好光照传给 FS，但不能改变顶点数量。

## 性能思维

`P·V·M` 在 **CPU 每帧预乘一次**，GPU 每个顶点只需一次矩阵乘法（4 个点积）——百万顶点也便宜。"能预计算就预计算、能放 VS 就别放 FS"是 GL 性能优化的第一原则。

## 在 App 里怎么玩（第 03 项）

- 四个视口自上而下 = **同一个三角形**在四个空间的样子：
  - 视口1 局部：三角形原地自转，坐标轴跟着转；
  - 视口2 世界：三角形平移到右侧，参照方块和世界轴不动；
  - 视口3 相机：相机环绕——注意世界轴没动，是"你"在绕；
  - 视口4 裁剪：从侧上方看视锥线框 + 相机小方块。
- 拖 **fov 滑条**：视锥张角变化——张角越大（广角），同距离三角形显得越小；
- 拖 **自转/环绕速度**：对比视口 1 与 2，体会"Model 动"与"View 动"的差别。

## API 速查

| API | 作用 |
|---|---|
| `Matrix.setIdentityM` | 单位矩阵 |
| `Matrix.translateM / rotateM / scaleM` | 平移/旋转/缩放（后调用先作用） |
| `Matrix.setLookAtM` | 构建视图矩阵（eye/center/up） |
| `Matrix.perspectiveM` | 构建透视投影矩阵 |
| `Matrix.multiplyMM` | 4×4 矩阵相乘 |
| `glUniformMatrix4fv` | 矩阵传给 uniform（列主序传 `false`） |

## 常见坑

- **MVP 顺序写错**：写成 `M·V·P` 画面直接乱掉；`multiplyMM(result, lhs, rhs)` 是 `lhs×rhs`。
- **宽高比没跟随 surface**：`aspect` 用错 → 画面被拉伸。
- **w 分量写 0**：`vec4(pos, 0)` 表示方向向量，不能当点做投影。
- **非等比缩放后光照错**：法线要用"逆转置矩阵"（第 21 章细讲）。

## 自测

1. 想让物体"绕自身 Y 轴转"，`translateM` 和 `rotateM` 应该谁先调用？
2. 为什么说"相机没有矩阵"？
3. `perspectiveM` 的 `near` 取 0.001 会有什么问题？

<details><summary>查看答案</summary>
1. `translateM` 先调用（矩阵层面 T×R，先自转再平移）。若 `rotateM` 先调用会变成绕世界原点公转。
2. View 矩阵是相机世界变换的逆矩阵；GPU 固定"相机在原点朝 -Z"，动的永远是世界。
3. near 太小 → 深度的 1/z 曲线极度陡峭 → 远处物体 z-fighting（深度冲突）。
</details>

➡️ 下一章：[04 · 光栅化](04-rasterization.md)
