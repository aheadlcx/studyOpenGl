# 25 · 实例化渲染

> 对应 App 第 25 项 · 难度：高级·GPU 管线
> 对应 App 第 25 项 · 难度：高级·GPU 管线

```text
管线定位：①数据 ─ 【②VS】 ─ 【③装配】 ─ ④裁剪 ─ ⑤光栅 ─ ⑥FS ─ ⑦测试
 ▲【】内为本章聚焦环节
```

## 你将搞懂

- draw call 的 CPU 提交开销与实例化的动机
- `glVertexAttribDivisor`：逐实例属性
- `gl_InstanceID` 内建变量
- 什么时候实例化能赢、什么时候不合适

## 先讲人话

每次 draw call 都有 CPU 侧固定开销（驱动校验、状态切换、命令入队）。画 1000 棵同样的树 = 1000 次 draw call，手机 CPU 直接爆。

实例化把"同一几何体 × N 份"压成 **1 次 draw**：

> **Android 类比**：`RecyclerView` 不为每个 item 新建 View 类型，而是同一 ViewHolder 模板 + 每项自己的数据——实例化是"同一几何体模板 + 每实例一份位置/颜色数据"。

## 核心代码

```java
// VBO0：单位立方体（每顶点，divisor=0 默认）
// VBO1：实例数据 [offset.xyz, scale, color.rgb]（divisor=1，每实例步进一次）
mesh.addInstancedBuffer(instanceData, 1,
        new Mesh.Attrib(2, 4),   // i_offset: xyz 偏移 + w 缩放
        new Mesh.Attrib(3, 3));  // i_color: 逐实例颜色

// 一条命令画出全部实例：
mesh.drawInstanced(GLES30.GL_TRIANGLES, instanceCount);
// 内部 = glDrawElementsInstanced(GL_TRIANGLES, 36, GL_UNSIGNED_INT, 0, N)
```

```glsl
// 顶点着色器：逐实例数据 + 内建实例 ID
layout(location=2) in vec4 i_offset;   // divisor=1 的属性
layout(location=3) in vec3 i_color;

uniform mat4 u_vp;
uniform float u_time;

void main() {
    float id = float(gl_InstanceID);           // 0,1,2,...N-1
    float a = u_time + id * 0.7;               // 每实例相位错开
    // 绕自身 Y 轴转（罗德里格斯简化版）
    float c = cos(a), s = sin(a);
    vec3 p = a_pos * i_offset.w;
    p = vec3(p.x*c - p.z*s, p.y, p.x*s + p.z*c);
    p.y += sin(u_time*2.0 + i_offset.x*2.0) * u_wave;   // 波浪起伏
    vec3 world = p + i_offset.xyz;
    // 逐实例颜色：要么来自实例缓冲，要么用 ID 生成渐变
    v_color = mix(i_color, idColor(id), u_useIdColor);
    gl_Position = u_vp * vec4(world, 1.0);
}
```

`glVertexAttribDivisor(loc, n)`：该属性每 n 个实例步进一次。n=0 普通（每顶点），n=1 每实例一份。

## 在 App 里怎么玩（第 25 项）

- **网格 N** 滑条 1→40：实例数 1 → 1600，观察 FPS——1600 个彩色方块仍然满帧；
- 心算对比：同样 1600 个方块用传统方式 = 1600 次 draw call；
- **波浪幅度**：所有实例按位置产生波浪（数据全部在 VS 里算，CPU 零参与）；
- **ID 着色开关**：切换"实例缓冲颜色"与"gl_InstanceID 生成的渐变色"。

## 适用性判断

| 适合实例化 | 不适合 |
|---|---|
| 大量相同几何（草、树、子弹、砖块） | 每个实例几何都不同 |
| 每实例差异小（位置/颜色/缩放） | 每实例需要不同纹理复杂混合 |
| 差异能用矩阵/小属性表达 | 需要逐实例改顶点数量 |

## 常见坑

- **实例缓冲忘设 divisor**：所有实例挤在同一位置（属性被当成逐顶点插值）。
- **gl_InstanceID 是 int**：做浮点运算要 `float(gl_InstanceID)`。
- **实例数太大一次提交卡顿**：几万实例考虑分批或 LOD。
- **ES2 直接用**：需要扩展 `EXT_instanced_arrays`，ES3 是核心。

## 原理图解与代码逐步拆解

```text
 传统方式（1600 次 draw）            实例化（1 次 draw）

 for i in 0..1599:                  ┌ VBO0: 立方体模板（每顶点）
   setInstanceData(i)               └ VBO1: [偏移xyz,缩放,颜色rgb]×1600
   glDraw(...)                                │ divisor=1
 CPU 爆炸                          glDrawElementsInstanced(1次)
                                    每实例: GPU 自动步进一次 VBO1
```

逐步拆解：

1. 模板立方体 VBO 正常配置（location 0/1，divisor 默认 0=每顶点）；
2. 实例缓冲 VBO 装 1600 份 `[offset.xyz, scale] + [color.rgb]`，`glVertexAttribDivisor(loc, 1)` 让它**每实例只步进一次**；
3. VS 里 `gl_InstanceID`（0~N-1）生成自转相位和渐变色——连实例缓冲都可以省一部分；
4. `meshAddInstanced`（App 版 drawInstanced）一条命令画完全部实例；
5. 拖 N 滑条从 1 到 40（1600 实例）观察 FPS：瓶颈几乎不出现——省的就是 1599 次 CPU 提交。

## 自测

1. `glVertexAttribDivisor(loc, 2)` 是什么效果？
2. 实例化节省的主要是 GPU 时间还是 CPU 时间？
3. 想让每个实例有不同贴图，有哪些方案？

<details><summary>查看答案</summary>
1. 该属性每 2 个实例步进一次（每两个实例共用一份值）。
2. 主要是 CPU 时间（draw call 提交开销）；GPU 着色量与 N 成正比不变，但省了状态切换。
3. 纹理数组 + 逐实例 layer 属性（第 19 章）；或图集 + 实例传 uv 偏移；或按 ID 分批 draw。
</details>

➡️ 下一章：[26 · Uniform Buffer](26-uniform-buffer.md)
