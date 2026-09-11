# 12 · 变换矩阵：T·R·S 组合

> 对应 App 第 12 项 · 难度：进阶矩阵
> 对应 App 第 12 项 · 难度：进阶矩阵

```text
管线定位：①数据 ─ 【②VS】 ─ ③装配 ─ ④裁剪 ─ ⑤光栅 ─ ⑥FS ─ ⑦测试
 ▲【】内为本章聚焦环节
```

## 你将搞懂

- MVP 三矩阵中 Model 矩阵的构成：平移×旋转×缩放
- 组合顺序为什么不可交换
- 法线矩阵问题（非等比缩放）
- `android.opengl.Matrix` 的实用要点

## 先讲人话

Model 矩阵把物体从局部空间"摆放"进世界空间。习惯写法 **T·R·S**（作用到顶点上：先缩放 S → 再旋转 R → 最后平移 T），因为矩阵乘法作用在列向量上**右边先算**，所以代码调用顺序是**反的**：

```java
// 想要"先缩放、再旋转、最后平移"：
Matrix.setIdentityM(m, 0);
Matrix.translateM(m, 0, tx, ty, tz);   // ① 代码先写 T（最后作用）
Matrix.rotateM(m, 0, angle, 0, 1, 0);  // ② 再写 R
Matrix.scaleM(m, 0, s, s, s);          // ③ 最后写 S（最先作用）
```

**顺序错了会怎样**：
- `R·T`（先平移后旋转）→ 物体绕世界原点"公转"而不是原地自转；
- `S` 放在旋转后 → 斜向拉伸（剪切变形）。

> **Android 类比**：等价于 `View.setTranslationX()` + `setRotation()` + `setScaleX()` 的组合——View 内部恰恰也是合成一个矩阵处理的。

## 核心代码：立方体 + 坐标轴

```java
// 每帧构建 Model
Matrix.setIdentityM(model, 0);
Matrix.translateM(model, 0, px, py, pz);
Matrix.rotateM(model, 0, rotX, 1, 0, 0);
Matrix.rotateM(model, 0, rotY, 0, 1, 0);
float s = scale;
Matrix.scaleM(model, 0, s, s, s);

// MVP = P×V×Model
Matrix.multiplyMM(pv, 0, proj, 0, view, 0);
Matrix.multiplyMM(mvp, 0, pv, 0, model, 0);
```

## 法线矩阵：非等比缩放的坑

光照用的法线是方向向量，被非等比缩放后会指错方向。严格做法是给 shader 传 **model 的逆转置 mat3**：

```glsl
out vec3 v_normal;
v_normal = mat3(u_normalMatrix) * a_normal;   // normalMatrix = inverse(transpose(model))
```

只有**旋转 + 等比缩放**时，直接 `mat3(u_model)` 才是安全的（本 App 多数 Demo 属于这种简化情形）。

## 4×4 矩阵为什么是 4×4

平移不是线性变换（加法），把 3D 点升成 4 维齐次坐标 (x,y,z,1)，平移就能并入矩阵乘法——顺便让 w 分量承载透视（第 03 章）。

## 在 App 里怎么玩（第 12 项）

- 平移 X/Y/Z 三个滑条 + 旋转 X/Y 速度 + 缩放滑条；
- **实验一**：缩放拉到 2，平移 X 拉到 ±2 —— 物体原地变大再移动（T·S 顺序正确）；
- **实验二**：打开坐标轴（红X绿Y蓝Z），观察旋转轴；
- 有意把缩放和旋转叠加，观察等比缩放不变形。

## API 速查

| API | 作用 |
|---|---|
| `Matrix.setIdentityM` | 重置为单位矩阵（每帧重建前必做） |
| `Matrix.translateM / rotateM / scaleM` | 在现有矩阵右侧叠加变换 |
| `Matrix.rotateM(m, o, angle, x, y, z)` | 绕任意轴旋转（罗德里格斯） |
| `Matrix.multiplyMM` | 矩阵乘法 |

## 常见坑

- **忘了 `setIdentityM`**：在上一帧的矩阵上继续累加，物体飞出屏幕。
- **rotateM 角度用弧度**：`android.opengl.Matrix` 用**度**。
- **非等比缩放 + 光照**：法线歪了，明暗不对——用法线矩阵。
- **在多物体场景复用矩阵**：每个物体独立 Model，P·V 可复用。

## 原理图解与代码逐步拆解

```text
 顺序陷阱图解（列向量约定：矩阵从右往左作用）

 T·R·S  ✅   顶点 ──×S──▶ 缩好 ──×R──▶ 转好 ──×T──▶ 搬到目标位置
             （先变胖）    （原地转）     （再搬家）      = 立方体原地转动 ✓

 R·T    ❌   顶点 ──×T──▶ 搬到 +2 ──×R──▶ 绕世界原点转 2 格
             （先搬家）    （再转身）   = "公转"而不是自转！
```

逐步拆解：

1. 每帧先 `setIdentityM` 清零重建——矩阵是累积的，忘了清就"飞出屏幕"；
2. `translateM(m,...)` 内部是 `m = m × T`：把平移"接"在现有变换后面；
3. 调用顺序 translate→rotate→scale 对应数学 T×R×S，作用到顶点上右边的 S 先执行；
4. 坐标轴（X红Y绿Z蓝）用单位矩阵绘制，为旋转提供参照物；
5. 法线：等比缩放只改长度不改方向，FS 里 normalize 一次即可；非等比缩放必须用逆转置矩阵。

## 自测

1. 想让立方体"先绕 Y 转 90 度，再沿自身方向平移"，代码顺序怎么写？
2. 为什么平移要 4×4 矩阵才能表示？
3. 等比缩放下能否直接用 mat3(model) 变换法线？

<details><summary>查看答案</summary>
1. 先 `rotateM` 再 `translateM`（矩阵 T×R：先转后移）。若先 translate 再 rotate，旋转会带着平移分量把物体甩到别处。
2. 平移是加法不是线性变换；升维到齐次坐标 (x,y,z,1) 后平移并入矩阵乘法。
3. 可以。等比缩放只改变法线长度不改变方向，FS 里 normalize() 一次即可。
</details>

➡️ 下一章：[13 · 相机与透视投影](13-camera-projection.md)
