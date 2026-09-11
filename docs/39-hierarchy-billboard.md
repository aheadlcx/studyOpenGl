# 39 · 层次变换与 Billboard 公告板

> 对应平台：Android + win32（ES3.0 核心）· 难度：进阶补充

## 你将搞懂

- 父子变换：矩阵从根节点一路"传播"到叶子
- 世界矩阵的正确构建：`world = parentWorld × local`
- Billboard 两兄弟：屏幕对齐（球形）与竖直对齐（圆柱形）
- 常见应用：机械臂、太阳系、血条、草与树

## 一、层次变换：机械臂/太阳系模型

真实场景里物体是**挂**在别的物体上的：手指挂在手掌上，手掌挂在手臂上。父节点动，子节点跟着动——实现就是**矩阵连乘**：

```text
 手指世界矩阵 = 肩矩阵 × 大臂R × 小臂T·R × 手掌T·R × 手指T·R
              （每个关节只存自己的"局部变换"）
```

> **Android 类比**：View 树——`ViewGroup` 的 `translation/rotation` 会影响所有子 View；子 View 的坐标永远是"相对父 View"的。GL 里每个节点存局部矩阵，绘制时从根累乘。

## 核心代码：三节机械臂

```java
// 每个关节的局部变换（随时间摆动）
float a1 = swing1, a2 = swing2, a3 = swing3;

// 从根开始逐级累乘，每一级都得到一个"世界矩阵"
Mat4 shoulderWorld;                          // 关节1（肩）
matIdentity(shoulderWorld);
matTranslate(shoulderWorld, 0, 1.5f, 0);     // 肩的位置
matRotate(shoulderWorld, a1, 0, 0, 1);
drawSegment(shoulderWorld, len1);            // 画大臂

Mat4 elbowWorld = new Mat4();                // 关节2（肘）= 肩 × 局部
matMul(elbowWorld, shoulderWorld, localT(0, len1, 0));
matMul2(elbowWorld, rotZ(a2));
drawSegment(elbowWorld, len2);

Mat4 handWorld = new Mat4();                 // 关节3（手）
matMul(handWorld, elbowWorld, localT(0, len2, 0));
matMul2(handWorld, rotZ(a3));
drawHand(handWorld);

// 关键：draw 时传的是"世界矩阵"，几何体只管自己的局部坐标
```

**通用化**就是场景图（Scene Graph）：每个节点存 localMatrix + 父指针，绘制时递归 `world = parentWorld * local`。

## 二、Billboard：永远面向相机的 Quad

| 类型 | 旋转方式 | 用途 |
|---|---|---|
| 球形（屏幕对齐） | 完全面向相机 | 粒子、光晕、血条 |
| 圆柱形（Y 轴对齐） | 只绕 Y 转向相机 | 树木、路灯、角色标记（不歪头） |

**CPU 侧实现**（简单直接）：

```java
// 相机的 right / up 向量（从 view 矩阵取行）
float[] right  = { view[0], view[4], view[8]  };
float[] up     = { view[1], view[5], view[9]  };
// 每个公告板：以中心点 pos、半宽 w、半高 h 展开四个角
corner[i] = pos ± right*w ± up*h
```

**VS 侧实现**（一次 draw 画 N 个，配合实例化第 25 章）：

```glsl
uniform vec3 u_right, u_up;             // 相机基向量
in vec3 i_center; in vec2 i_size;       // 逐实例
vec3 world = i_center
           + u_right * a_corner.x * i_size.x
           + u_up    * a_corner.y * i_size.y;
gl_Position = u_vp * vec4(world, 1.0);
```

## 三、文字渲染（应用最广的 Billboard）

GL 没有文字，三种方案按质量递增：

| 方案 | 做法 | 适用 |
|---|---|---|
| 位图字体图集 | 把字符画进一张纹理，FS 采样字符格 | FPS 数字、简单 HUD |
| Android 侧画到 Bitmap 再上纹理 | `Canvas.drawText` → `GLUtils.texImage2D` | 动态文本（分数/昵称），实现最快 |
| SDF（有向距离场） | 特殊纹理+FS 判距离，任意缩放锐利 | 聊天气泡、可缩放文字（正规做法） |

## 常见坑

- **世界矩阵构造顺序**：`parentWorld × local`，local 内部仍是 T·R·S——两级都要注意顺序；
- **球形 billboard 在 VR/大 fov 下穿帮**：需按视线重算 up；
- **文字纹理 min filter 无 mipmap**：缩小后文字闪烁——文字图集要生成 mip 或限制缩放；
- **Billboard 的深度**：中心点参与深度测试即可，四角别乱写 z。

## 自测

1. 太阳系里"地球公转+自转"，地球的世界矩阵怎么构建？
2. 圆柱形 billboard 为什么比球形更适合"树"？
3. 位图字体缩小时文字闪烁，怎么解决？

<details><summary>查看答案</summary>
1. earthWorld = sunWorld × R_y(公转角) × T(轨道半径) × R_y(自转角)——公转在里、自转在外。
2. 树应该"转头看相机"但保持竖直（树干不歪）；球形 billboard 会随相机俯仰倾斜，树像"躺下"。
3. 给字体图集生成 mipmap 并用 LINEAR_MIPMAP_LINEAR，或改用 SDF 字体（缩放锐利）。
</details>

◀ 返回 [docs/README.md](README.md)
