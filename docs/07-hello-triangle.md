# 07 · 画第一个三角形：全流程

> 对应 App 第 07 项 · 难度：基础动手 · 前置：01~06 概念篇

## 你将搞懂

- 把概念串成可运行代码：数据 → 编译着色器 → 绘制
- GLSL ES 3.00 着色器的必须元素
- 编译/链接失败的排查方法

## 先讲人话

前 6 章的 7 道工序，落地成代码就是**四步**：

1. 数据进显存（VBO/VAO）
2. 写两个 shader 并编译链接成 program
3. 每帧：use program → 设 uniform → draw
4. 交换上屏

**Android 类比**：第 2 步相当于"编译并安装一个自定义 View"；第 3 步相当于每帧 `onDraw()`。

## 完整代码（可运行的最小集）

```java
// ── 第 1 步：数据（第 02 章详解）──
float[] vertices = {            // 交错：pos3 + color3
    -0.6f, -0.5f, 0f,  1f, 0.2f, 0.2f,
     0.6f, -0.5f, 0f,  0.2f, 1f, 0.2f,
     0f,  0.62f, 0f,  0.25f, 0.4f, 1f
};
Mesh mesh = new Mesh.Builder()
        .addBuffer(vertices, new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 3))
        .build();

// ── 第 2 步：着色器（GLSL ES 3.00）──
String vs = "#version 300 es\n"
    + "layout(location=0) in vec3 a_pos;\n"
    + "layout(location=1) in vec3 a_color;\n"
    + "uniform mat4 u_mvp;\n"
    + "out vec3 v_color;\n"                 // out：给片元插值
    + "void main() {\n"
    + "    v_color = a_color;\n"
    + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
    + "}\n";
String fs = "#version 300 es\n"
    + "precision mediump float;\n"          // FS 必须声明精度
    + "in vec3 v_color;\n"
    + "uniform vec3 u_tint;\n"
    + "out vec4 fragColor;\n"               // ES3 自声明输出
    + "void main() { fragColor = vec4(v_color * u_tint, 1.0); }\n";
ShaderProgram prog = new ShaderProgram(vs, fs);   // 编译+链接+查错

// ── 第 3 步：每帧 ──
GLES30.glClearColor(0.06f, 0.08f, 0.12f, 1f);
GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
prog.use();
prog.setMat4("u_mvp", mvp);               // 每帧可变
prog.set("u_tint", r, g, b);              // 参数面板控制的色调
mesh.draw(GLES30.GL_TRIANGLES);

// ── 第 4 步：交换（GLThread 统一处理）──
eglSwapBuffers(...);
```

## GLSL ES 3.00 必须元素清单

| 元素 | 说明 |
|---|---|
| `#version 300 es` | **第一行**，前面不能有空行/注释 |
| `in` / `out` | 替代 ES2 的 attribute/varying |
| `out vec4`（FS） | 自声明输出（替代 gl_FragColor） |
| `precision mediump float;`（FS） | FS 必须声明浮点精度 |
| `layout(location=N)` | 显式属性槽位（配合 VAO） |

## 编译/链接失败排查

驱动不会替你检查，必须手动查状态：

```java
GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0);
if (status[0] == 0) {
    String log = GLES30.glGetShaderInfoLog(shader);   // 行号+原因都在这
    throw new RuntimeException(log);
}
```

常见错误：`#version` 不在第一行；FS 缺精度声明；`in/out` 名字不匹配；用了 `noperspective` 这类 ES3 保留字（第 08 章）。

## 在 App 里怎么玩（第 07 项）

- **色调滑条**：改 uniform `u_tint`——体验"每帧上传 uniform"；
- **旋转速度**：改模型矩阵角度；
- **第二个三角形开关**：控制第二个 draw call 是否执行——注意两个三角形共用同一个 program。

## 常见坑

- **着色器编译错误只在运行时暴露**：换机型 GLSL 编译器不同，务必真机回归。
- **uniform 被优化掉**：没用的 uniform `glGetUniformLocation` 返回 -1，`glUniform*` 传 -1 是静默无操作，不算错误但也不生效。
- **黑屏排查顺序**：clear 颜色对不对 → program 链接了吗 → mvp 是不是单位矩阵 → 顶点数据 location 对不对。

## 原理图解与代码逐步拆解

```text
 步骤① 数据           步骤② 程序              步骤③ 每帧
 ┌──────────────┐    ┌──────────────┐      ┌──────────────────┐
 │ v0 -0.6,-0.5 │    │ VS源码 ──编译─┐│      │ glUseProgram(p)  │
 │ v1  0.6,-0.5 │──▶ │ FS源码 ──编译─┤│─▶链接─▶│ 设 u_mvp/u_tint  │
 │ v2  0.0,0.62 │    │        └─────┘│      │ draw(TRIANGLES)  │
 └──────────────┘    └──────────────┘      └──────────────────┘
      VBO+VAO              program              每帧重复
```

逐步拆解（对照【代码】页签）：

1. **建数据**：18 个 float 两两成组——每个顶点 6 个数，前 3 个是"在哪"（x,y,z），后 3 个是"什么颜色"（r,g,b）。这就是**交错布局**。
2. **进显存**：`glGenBuffers` 领一块显存 → `glBindBuffer` 把它设为"当前操作对象" → `glBufferData` 一次性把 18 个 float 拷进去。`GL_STATIC_DRAW` 是给驱动的提示："写一次读很多次"。
3. **登记读法**：`glVertexAttribPointer(0, 3, ...)` 告诉 GPU"location 0 的属性，每 24 字节一组，从第 0 字节读 3 个 float"；颜色同理但从第 12 字节开始。
4. **编译 shader**：VS 计算 `gl_Position`（顶点在哪），FS 计算 `fragColor`（像素什么颜色）。编译失败会打印行号和原因——GLSL 是运行时编译的语言。
5. **绘制**：`glDrawArrays(GL_TRIANGLES, 0, 3)` = "从第 0 个顶点开始，拿 3 个顶点，绑成 1 个三角形"。之后 ②→⑦ 站全自动完成。

## 自测

1. FS 里少了 `precision` 声明会怎样？
2. `in/out` 在 ES 2.0 里叫什么？
3. 一个 program 由什么组成？

<details><summary>查看答案</summary>
1. 编译失败，FS 必须显式声明浮点精度（VS 默认 highp）。
2. attribute（VS 的 in）/ varying（VS 的 out、FS 的 in）。
3. 一个编译过的 VS + 一个编译过的 FS 链接（glLinkProgram）成的整体。
</details>

➡️ 下一章：[08 · Varying 插值](08-varying-interpolation.md)
