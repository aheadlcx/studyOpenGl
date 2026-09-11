# 19 · 2D 纹理数组

> 对应 App 第 19 项 · 难度：纹理 · ES3 新增
> 对应 App 第 19 项 · 难度：纹理 · ES3 新增

```text
管线定位：【①数据】 ─ ②VS ─ ③装配 ─ ④裁剪 ─ ⑤光栅 ─ 【⑥FS】 ─ ⑦测试
 ▲【】内为本章聚焦环节
```

## 你将搞懂

- GL_TEXTURE_2D_ARRAY 与 glTexImage3D
- sampler2DArray 的三层采样 vec3(u, v, layer)
- layer 可以来自顶点属性 → 逐实例贴不同图
- 纹理数组 vs 图集 vs 立方体贴图

## 先讲人话

把 N 张**等尺寸** 2D 纹理叠成一摞（第三个维度是层号），一次上传一次绑定：

> **Android 类比**：`ViewFlipper` 里预放好的多页图片——翻页（换 layer）不需要重新加载。

## 核心代码

```java
// 上传：第三维就是层数
int[] ids = new int[1];
GLES30.glGenTextures(1, ids, 0);
GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, ids[0]);
GLES30.glTexImage3D(GLES30.GL_TEXTURE_2D_ARRAY, 0, GLES30.GL_RGBA8,
        size, size, layers,          // w, h, 层数
        0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE,
        combinedBuffer);              // 所有层的数据首尾相接
GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D_ARRAY);  // 每层独立生成 mip

// 状态与 2D 纹理一致
GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY,
        GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR);
```

```glsl
// 片元：第三分量是层号（浮点，就近取整）
vec4 tex = texture(u_array, vec3(v_uv, v_layer));
```

**layer 可以是顶点属性**（插值后仍是常量，因为一个三角形三个顶点层号相同）——甚至做成实例化属性（divisor=1），8 个实例一次 draw call 各贴各的图，App 里正是这么做的。

## 为什么不用图集（atlas）

| | 图集（大图拼小图） | 纹理数组 |
|---|---|---|
| mipmap | 相邻图块互相"渗色"，要 padding+技巧 | 每层独立 mip，无接缝 |
| uv 计算 | 手动换算子区域 | uv 就是 0~1，层号另传 |
| 运行时换层 | 不行（uv 固定） | layer 一改即换图 |
| 尺寸要求 | 无 | 所有层同尺寸同格式 |

## 三者对比

| | 图集 | 数组 | 立方体 |
|---|---|---|---|
| 采样方式 | vec2 + 手动映射 | vec3(u,v,layer) | 方向向量 |
| 层/面数 | 任意拼 | GL_MAX_ARRAY_TEXTURE_LAYERS（≥256） | 固定 6 面 |
| 典型用途 | UI 雪碧图 | 角色换装、瓦片地形 | 天空盒、环境反射 |

## 在 App 里怎么玩（第 19 项）

- 4 层纹理（棋盘/砖墙/噪点/色环）× 环上 8 个实例四边形，**一次 draw call**；
- **层偏移** 滑条 0~4：所有实例的层号整体轮换（mod 4）；
- **自动轮播** 开关：层号随时间循环，看每个实例独立换图；
- 【代码】页签：VS 里 `v_layer = mod(i_meta.z + u_layerOffset, 4.0)` 的实现。

## 常见坑

- **忘了精度声明**：`precision lowp sampler2DArray;`——它没有默认精度，编译报 `No precision specified`（本工程踩过的真实坑）。
- **层号插值**：跨层号不同的三角形会插出中间值（如 1.5）→ 取整跳变，确保一个三角形同层。
- **层数超限**：查询 `GL_MAX_ARRAY_TEXTURE_LAYERS`（ES3 下限 256）。
- **ES2 直接用**：需要 `EXT_texture_array` 扩展，ES3 是核心功能。

## 原理图解与代码逐步拆解

```text
 纹理数组（第三维 = 层号）：

   layer 3  ┌──────┐      VS 传层号属性（每实例一份）
   layer 2  │ 色环  │  ──▶ FS: texture(u_array, vec3(uv, layer))
   layer 1  │ 砖墙  │            └ uv ┘  └ 整数层号
   layer 0  └棋盘──┘
   一次 glTexImage3D 全部上传；一次 draw 各实例贴各的层
```

逐步拆解：

1. 四张过程图（棋盘/砖墙/噪点/色环）字节首尾相接成一个大 buffer；
2. `glTexImage3D(target, 0, RGBA8, w, h, layers=4, ...)` 一次上传 4 层——注意第三维；
3. `glGenerateMipmap(GL_TEXTURE_2D_ARRAY)` 每层独立生成 mip，不互相渗色（对比图集的优势）；
4. VS 里 `v_layer = mod(a_layer + u_offset + u_time, 4.0)`：层号是顶点属性，滑条/时间可以整体轮换；
5. FS 里 `texture(u_array, vec3(v_uv, v_layer))` 按层采样——8 个四边形一次 draw、各贴各的图。

## 自测

1. 一张 256×256、4 层的纹理数组，不生成 mipmap 占多少字节？
2. 数组的 layer 能否在 FS 里动态计算（比如随 uv 变化）？
3. 数组和 cubemap 最本质的区别？

<details><summary>查看答案</summary>
1. 256×256×4B×4 层 = 1 MB。
2. 可以（sampler2DArray 的第三分量是任意表达式），但会经过 2×2 的"层间视图"，常用做法仍是顶点属性传整型层号。
3. cubemap 按方向向量选面（法线球概念，用于环境反射）；数组按显式层号选层（列表概念，用于批量同尺寸贴图）。
</details>

➡️ 下一章：[20 · 立方体贴图天空盒](20-cubemap-skybox.md)
