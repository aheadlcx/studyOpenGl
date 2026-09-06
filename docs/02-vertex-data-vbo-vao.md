# 02 · 顶点数据：VBO / VAO / 索引

> 对应 App 第 02 项「第1站·顶点数据 VBO/VAO」· 难度：入门概念

## 你将搞懂

- GPU 不认识 Java 对象，只认什么
- VBO、VAO、EBO（索引）三件套分别是什么
- stride / offset 的精确含义（初学者第一大懵点）
- 交错布局和分离布局的区别与取舍

## 先讲人话

GPU 不认识你的 `Point`、`Vertex` Java 对象，它只认**连续的二进制数字流**。所以渲染第一步：把顶点数据从 Java 数组拷进 GPU 显存，并附一份"说明书"告诉它怎么读。三件套：

| 名称 | 全称 | 一句话 | Android 类比 |
|---|---|---|---|
| **VBO** | Vertex Buffer Object | 显存里的一块数组，数据本体 | `List<Item>` 数据列表 |
| **VAO** | Vertex Array Object | 说明书：每行哪几列是位置、哪几列是颜色 | ViewHolder（一次绑定反复用） |
| **EBO** | Element Buffer Object | 顶点复用表（索引） | 稀疏引用下标 |

## 核心代码：从 float 数组到可绘制状态

```java
// ── 1. 定义数据：交错布局（一个顶点的所有属性挤在一起）──
float[] vertices = {
    // x      y      z      r     g     b
    -0.75f, -0.55f, 0f,  1.00f, 0.30f, 0.25f,   // v0
     0.85f, -0.45f, 0f,  0.25f, 1.00f, 0.40f,   // v1
     0.05f,  0.80f, 0f,  0.30f, 0.55f, 1.00f    // v2
};  // 共 18 个 float，一个顶点占 6×4 = 24 字节

// ── 2. 拷进显存：一次 glBufferData 整批搬运 ──
int[] vbo = new int[1];
GLES30.glGenBuffers(1, vbo, 0);                     // 领一块显存
GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0]);// 之后的操作作用在它身上
GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,
        vertices.length * 4,                        // 字节数：18 float × 4
        FloatBuffer.wrap(vertices),                 // CPU 内存 → 显存
        GLES30.GL_STATIC_DRAW);                     // 提示：写一次、读 N 帧

// ── 3. 登记"怎么读"——stride/offset 就在这一步！──
// stride = 相邻两个顶点同一属性的跨度（字节）
// offset = 本属性在一个顶点块内的起点（字节）
GLES30.glVertexAttribPointer(
        0,                    // location=0（对应 GLSL 里的 a_pos）
        3,                    // 3 个分量 (x,y,z)
        GLES30.GL_FLOAT,      // 数据类型
        false,                // 不做归一化
        24,                   // stride：每 24 字节一组
        0);                   // offset：位置从组内第 0 字节开始
GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT,
        false, 24, 12);       // location=1 颜色：同样步长，偏移 12 字节
GLES30.glEnableVertexAttribArray(0);
GLES30.glEnableVertexAttribArray(1);

// ── 4. VAO：把上面第 3 步的"读法"打包存档 ──
int[] vao = new int[1];
GLES30.glGenVertexArrays(1, vao, 0);
GLES30.glBindVertexArray(vao[0]);   // 绑定后做的设置都会被它记住
//   ……做第 2、3 步……
GLES30.glBindVertexArray(0);

// ── 5. 之后每一帧绘制只需两行 ──
GLES30.glBindVertexArray(vao[0]);   // 说明书一次性恢复
GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
```

**内存布局图**（交错布局，App 里上半屏的可视化就是这个）：

```text
       ┌────── v0 (24字节) ──────┐┌────── v1 (24字节) ──────┐
byte:  [x][y][z][r][g][b]          [x][y][z][r][g][b]        ...
        ▲位置 offset=0        ▲颜色 offset=12
        ◀──────── stride=24 ────────▶
```

## 交错 vs 分离布局

```java
// 分离布局：位置、颜色各自一条数组（切 App 里"内存布局"对比）
float[] positions = {-0.75f,-0.55f,0f,  0.85f,-0.45f,0f,  0.05f,0.80f,0f};
float[] colors    = {1.00f,0.30f,0.25f, 0.25f,1.00f,0.40f, 0.30f,0.55f,1.00f};
// 各自一条 VBO，stride=0（让 GL 自动算紧凑步长），offset=0
```

| | 交错 interleaved | 分离 separate |
|---|---|---|
| 读一个顶点 | 一次搞定（缓存友好） | 跨两条缓冲 |
| 单独更新某类属性 | 不便 | 方便（只动一条） |
| 使用建议 | **静态几何默认选它** | 只动态更新单一属性时用 |

## 索引（EBO）：顶点复用

一个正方形只需要 4 个顶点，靠索引拼出 2 个三角形：

```java
int[] indices = {0, 1, 2,  2, 1, 3};   // 用索引"引用"顶点
// 上传到 GL_ELEMENT_ARRAY_BUFFER 后：
GLES30.glDrawElements(GLES30.GL_TRIANGLES, 6,
        GLES30.GL_UNSIGNED_INT, 0);     // ES3 原生支持 32 位索引
```

模型越大省得越多：一个 1 万顶点的人物模型，三顶点间平均复用 6 次。

## 在 App 里怎么玩（第 02 项）

- 上半屏"内存条"：每个方块 = 一个 float，蓝=位置、彩色=颜色分量数值；
- 切换 **内存布局** 参数：对比交错/分离两种排法；
- 切换 **选中顶点** 参数：v0/v1/v2 对应的内存块高亮，同时下半屏 3D 三角形上的对应顶点脉冲放大——建立"一段数字 ↔ 空间中一个点"的直觉；
- 底部【代码】页签：本页全部代码的完整版。

## API 速查

| API | 作用 |
|---|---|
| `glGenBuffers` / `glBindBuffer` | 创建/绑定缓冲对象 |
| `glBufferData` | 整批拷贝数据进显存（可传 `null` 先只分配） |
| `glVertexAttribPointer` | 登记属性位置：location、分量数、类型、stride、offset |
| `glEnableVertexAttribArray` | 启用该属性槽 |
| `glGenVertexArrays` / `glBindVertexArray` | 创建/绑定 VAO |
| `glDrawElements` | 按索引绘制 |

## 常见坑

- **stride/offset 单位是字节不是 float 个数**：3 个 float 的偏移 = 12。
- **`glBufferData` 传 `FloatBuffer.wrap()` 的堆缓冲是允许的**，但高频更新建议 `allocateDirect`。
- **忘记 `glEnableVertexAttribArray`**：属性读到恒 0，物体位置奇怪或纯黑。
- **location 想当然**：GLSL 里 `layout(location=N)` 必须和 `glVertexAttribPointer` 的第一个参数一致。
- **每帧重复 `glBufferData` 大数组**：考虑"孤儿化"（先传 null 再传数据）或映射（第 27 章）。

## 自测

1. 交错布局中每个顶点 8 个 float（pos3+normal3+uv2），颜色属性的 stride 和 offset 是多少？
2. VAO 存的是数据本身吗？
3. 一个四边形用 `GL_TRIANGLES` 画，最少需要几个顶点、几个索引？

<details><summary>查看答案</summary>
1. stride = 8×4 = 32 字节；offset = 3×4 = 12 字节（颜色排在 pos3+normal3 之后）。
2. 不是。VAO 只存"怎么读"的配置（属性指针、启用位、EBO 绑定），数据在 VBO 里。
3. 4 个顶点 + 6 个索引（0,1,2, 2,1,3）。
</details>

➡️ 下一章：[03 · 顶点着色器 MVP](03-vertex-shader-mvp.md)
