# 13 · 相机与透视投影

> 对应 App 第 13 项 · 难度：进阶矩阵
> 对应 App 第 13 项 · 难度：进阶矩阵

```text
管线定位：①数据 ─ 【②VS】 ─ ③装配 ─ ④裁剪 ─ ⑤光栅 ─ ⑥FS ─ ⑦测试
 ▲【】内为本章聚焦环节
```

## 你将搞懂

- 视锥（frustum）六要素：fovy / aspect / near / far
- fov 对画面的影响（广角 vs 长焦）
- 深度非线性（1/z）与 near 的选择
- 触摸轨道相机的实现套路

## 先讲人话

透视投影矩阵定义一个**四棱台视锥**：只有锥内的物体可见。四个参数：

```text
        fovy：垂直视场角（广角大/长焦小）
       ┌────── far 远切面
      /│
     / │
    eye───▶ near 近切面（不能为 0/负）
   aspect：宽高比
```

| 参数 | 调大 | 调小 |
|---|---|---|
| fovy | 广角：看得宽、边缘拉伸畸变 | 长焦：看得窄、有"压缩感" |
| near | 场景近处被切 | 深度精度变差（见下） |
| far | 远处深度精度浪费 | 远处物体被裁掉 |

> **Android 类比**：和相机 App 的"广角/长焦"滑条同一个概念；`near` 相当于最近对焦距离。

## 核心代码：轨道相机

```java
// 触摸拖动 → yaw（水平角）/ pitch（俯仰角）
// 每帧把角度换算成相机位置：
float yaw = (float) Math.toRadians(mYaw + autoYaw);
float pitch = (float) Math.toRadians(mPitch);
float radius = 14f;
float ex = radius * (float) Math.cos(pitch) * (float) Math.sin(yaw);
float ey = radius * (float) Math.sin(pitch);
float ez = radius * (float) Math.cos(pitch) * (float) Math.cos(yaw);

Matrix.perspectiveM(proj, 0, fov, aspect, near, far);
Matrix.setLookAtM(view, 0, ex, ey, ez, 0, 0, 0, 0, 1, 0);
```

相机始终看向原点，eye 在球面上滑动——这就是"轨道相机"（Blender/3D 查看器同款交互）。

## 深度非线性（高频面试点）

透视投影后，设备深度值 = f(z) 是 **1/z 型曲线**：

```text
深度分布（near=0.5, far=100 示意）：
z=0.5  → depth 0.0     ┐ 近处：一半深度值
z=1    → depth 0.5     │ 分给了眼前
z=2    → depth 0.75    ┘ 一小段距离
z=100  → depth 1.0     远处：挤在一点点深度里
```

结论：**near 越小 / far 越大，远处深度精度越差** → z-fighting（第 14 章）。修 z-fight 的第一招就是"拉大 near、拉近 far"。

## 在 App 里怎么玩（第 13 项）

- **视场角 fovy** 滑条 15°~120°：20° 长焦压缩感 / 110° 广角边缘拉伸；
- **近平面 near** 拉到 0.05 再把相机贴近立方体：直接"看穿"近处三角形（被近切面裁掉）；
- **远平面 far** 拉小：远处地面被裁掉出现"断层"；
- **手指拖动屏幕**：轨道相机环绕 5×5 立方体阵；拉"自动旋转"挂机观察。

## API 速查

| API | 作用 |
|---|---|
| `Matrix.perspectiveM(m, o, fovy, aspect, near, far)` | 构建透视投影 |
| `Matrix.setLookAtM(m, o, eye, center, up)` | 构建视图矩阵 |
| `GLES30.glDepthRangef(n, f)` | 深度映射范围（默认 0~1） |

## 常见坑

- **aspect 用死值**：surface 尺寸变化（旋转/分屏）后不更新 → 画面拉伸。必须在 `onSurfaceChanged`/每帧按实际比例设置。
- **near=0**：除以 0，投影矩阵无效。
- **fov 理解成水平角**：`perspectiveM` 的 fovy 是**垂直**视场角。
- **天空盒用带平移的 View**：天空跟着相机"飘"——View 去掉平移列（第 20 章）。

## 原理图解与代码逐步拆解

```text
 视锥（相机能看到的空间）：

          far ═══════════
             ╱          ╲
            ╱   ← 场景    ╲
           ╱               ╲
   eye ── ▐━━━ near ━━━━━━━▌
              fov 决定张角

 深度缓冲的 1/z 非线性（near=0.5 far=100）：
 depth 0.0      0.5      0.75     ~1.0
   ─────|────────|────────|───────▶
   z=near       z=1       z=2    z=100
   （一半深度分给了眼前一小段 → 远处精度极差）
```

逐步拆解：

1. `perspectiveM(fovy, aspect, near, far)` 造出视锥：fovy 是**垂直**张角，aspect=宽/高，near/far 是两个切面距离；
2. `setLookAtM(eye, center, up)` 内部计算"相机变换的逆矩阵"——你指定相机在哪看哪，GPU 拿到的是"世界反向动"的矩阵；
3. 轨道相机：eye 在以目标为球心的球面上滑动（yaw 绕竖直轴、pitch 上下）；
4. 拖 fov：张角大→同样的世界投到更宽的视野→物体显得小（广角）；张角小→长焦压缩；
5. 把 near 拉大到 2：近处立方体被近切面直接裁掉——"看穿"现象。

## 自测

1. fovy=120° 的广角下远处物体显得更小还是更大？为什么？
2. near 从 0.1 改成 2.0，远处两个重叠平面的闪烁（z-fight）会好转还是恶化？
3. 相机绕场景旋转时，世界坐标轴会动吗？

<details><summary>查看答案</summary>
1. 更小。广角把更大的世界塞进同样的屏幕，单位物体的投影像素变少。
2. 好转。near 变大使 1/z 曲线平缓，远处深度分辨精度提高。
3. 世界轴（物体）不动；动的是 View 矩阵。App 视口③里可见轴是静止的。
</details>

➡️ 下一章：[14 · 深度测试](14-depth-test.md)
