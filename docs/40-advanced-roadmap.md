# 40 · 模型加载、程序缓存与进阶路线图

> 对应平台：Android + win32（ES3.0 核心）· 难度：进阶补充

## 你将搞懂

- OBJ 模型文件的最小解析器（v/vn/vt/f 四类行）
- Program Binary：着色器编译缓存，加速启动
- 遮挡查询：GPU 告诉你"画没画出来"
- 学完 ES 3.0 之后的进阶路线图

## 一、OBJ 模型加载（最小可用解析器）

OBJ 是纯文本，常见行只有四种：

```text
v  0.1 2.0 3.0          # 顶点位置
vn 0.0 1.0 0.0          # 法线
vt 0.25 0.5             # uv
f  1/1/1 2/2/2 3/3/3    # 面：顶点/uv/法线 的索引（1 起始！且可能负数）
```

**关键难点**：OBJ 的 f 行是"位置/uv/法线"三个**独立索引**，而 GPU 需要"每顶点一组交错数据"——要把它们重新拼装（去重）：

```java
// 伪代码：解析 f 行时查重拼装
int key = posIdx * 1_000_000 + uvIdx * 1_000 + nIdx;
Integer known = dedupMap.get(key);
if (known == null) {
    // 新顶点：把 pos/uv/normal 三份数据按索引取出，拼成一个顶点加入数组
    dedupMap.put(key, nextIndex++);
} else {
    indices.add(known);           // 复用已有顶点
}
```

工程上更常用 **glTF**（二进制、自带材质/层级，Android 有官方加载库）——但手写一遍 OBJ 解析器能让你彻底理解"顶点数据布局"这一课。

## 二、Program Binary：着色器编译缓存

GLSL 编译链接在启动时可能耗时几十到几百毫秒。ES3.0 可把编译好的 program 存成二进制：

```java
// 首次：编译链接后导出
glGetProgramBinary(prog, bufSize, &len, &format, binary);   // 存文件
// 之后启动：直接加载
glProgramBinary(prog, format, binary, len);
glGetProgramiv(prog, GL_LINK_STATUS, &ok);                  // 仍要检查！驱动不认就回退编译
```

注意：二进制**不跨设备/驱动版本**——做缓存要带设备指纹校验，失败自动回退源码编译。

## 三、遮挡查询：GPU 告诉你"画没画出来"

```java
glEnable(GL_RASTERIZER_DISCARD 之外的正常管线);
glGenQueries(1, &query);
glBeginQuery(GL_ANY_SAMPLES_PASSED, query);
drawBoundingBox(obj);          // 画一个包围盒（便宜）
glEndQuery(GL_ANY_SAMPLES_PASSED);

GLuint passed = 0;
glGetQueryObjectuiv(query, GL_QUERY_RESULT, &passed);
// passed==0：包围盒都没片元通过 → 物体完全被挡住 → 这一帧可以跳过精细绘制
```

配合"上一帧的结果用于下一帧的剔除"策略，复杂场景可省大量绘制。

## 四、进阶路线图（ES 3.0 之后）

| 方向 | 内容 |
|---|---|
| **GLES 3.1** | Compute Shader（GPU 通用计算）、SSBO、program interface、多重采样纹理 |
| **GLES 3.2** | 几何着色器、Tessellation、混合处理器、KHR_debug |
| **PBR 材质** | 金属度/粗糙度工作流 + IBL（环境贴图预滤波，基于第 20 章 cubemap） |
| **Vulkan** | 显式管线/内存/同步管理——GL 的所有"隐式魔法"（第 35 章同步、附录 A 状态机）在 Vulkan 里都要手动做，GL 基础越牢学得越快 |
| 实战方向 | 相机滤镜（附录 B）、视频编辑引擎、3D 模型查看器、轻量游戏引擎 |

## 常见坑

- **OBJ 索引从 1 开始且可为负**（负数=从末尾倒数）——直接当数组下标会崩；
- **Program Binary 跨设备复用**：驱动不认时 LINK_STATUS 为 0，必须回退；
- **遮挡查询当帧读取结果**：又是第 35 章的同步问题——用上一帧的结果；
- **跳过基础直接学 PBR**：法线/切线/深度/混合不牢，PBR 调不出效果也无从排查。

## 自测

1. OBJ 的 f 行索引为什么不能直接用？
2. Program Binary 加载后还要检查什么？失败怎么办？
3. 遮挡查询的"上一帧结果"策略为什么是安全的？

<details><summary>查看答案</summary>
1. 它是"位置/uv/法线"三个独立索引（1 起始、可负），GPU 需要"每顶点交错"的数据——必须重组装去重。
2. 检查 GL_LINK_STATUS；驱动/设备不匹配会加载失败，此时回退到源码编译路径。
3. 物体可见性在相邻帧之间高度连续（相机连续移动），用上帧结果做本帧剔除最坏情况是偶尔多画一次，不会出错。
</details>

◀ 返回 [docs/README.md](README.md)
