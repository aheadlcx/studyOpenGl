# 08 · Varying 插值：smooth / flat

> 对应 App 第 08 项 · 难度：基础动手

## 你将搞懂

- 插值发生的阶段与原理（透视校正）
- `smooth` 与 `flat` 两个可用限定符的区别
- `noperspective` 为什么在 ES 3.0 不能用
- flat 的"激起顶点"规则与实际用途

## 先讲人话

VS 输出的 `out` 变量（varying）在图元内部会经过光栅化**插值**后交给 FS。默认方式 `smooth` 是**透视校正插值**：先除以 w 在屏幕空间插值，再乘回 w——保证三维透视下依然正确（贴在斜面上的纹理不扭曲的根本原因）。

## 两种可用限定符

```glsl
// 顶点着色器（FS 对称地写 in）
smooth out vec3 v_color;   // 默认，可省略：透视校正插值
flat   out vec3 v_mode;    // 不插值：整个图元取同一个值
```

- **smooth**：渐变。三个顶点不同色 → 三角形内部平滑过渡。
- **flat**：不插值。整个三角形显示**激起顶点（provoking vertex）**的值——OpenGL 约定为**图元最后一个顶点**。用途：传"每图元常量"，比如材质 ID、朝向标记，让整个三角形统一。
- ⚠ **`noperspective`**：桌面 GLSL 支持"屏幕空间线性插值"，但 **GLSL ES 3.00 里它是保留字却未实现**，写了直接编译错误 `Illegal use of reserved word`——从桌面移植 shader 的高频坑！

## 核心代码（App 内实现）

```java
// 同一份顶点数据，三种编译变体里保留两种合法的
String[] interps = {"smooth", "flat"};
for (int i = 0; i < 2; i++) {
    String vs = "#version 300 es\n"
        + "layout(location=0) in vec3 a_pos;\n"
        + "layout(location=1) in vec3 a_color;\n"
        + "uniform mat4 u_mvp;\n"
        + interps[i] + " out vec3 v_color;\n"      // 这里切换限定符
        + "void main() {\n"
        + "    v_color = a_color;\n"
        + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
        + "}";
    programs[i] = new ShaderProgram(vs, fsWithInterp);
}
```

顶点数据故意让三个顶点的 **z 深度不同**，旋转时对比更明显：

```java
-0.9f, -0.55f, -0.4f,  1f, 0.1f, 0.1f,    // v0 红（远）
 0.9f, -0.55f, -0.4f,  0.1f, 1f, 0.1f,    // v1 绿（远）
 0f,   0.85f,  0.6f,  0.15f, 0.3f, 1f     // v2 蓝（近）
```

## 在 App 里怎么玩（第 08 项）

- **插值限定符** 切 smooth/flat：
  - smooth：三色沿 3D 面平滑流动；
  - flat：整块纯色——是**第 3 个顶点的蓝色**（激起顶点规则）；
- **旋转速度** 拉起来，透视校正插值的效果更明显；
- 【代码】页签可看两种限定符的完整 shader。

## 常见坑

- **flat 模式拿错顶点的值**：以为是第 1 个顶点，实际是**最后一个**（激起顶点）。
- **varying 太多**：每个插值变量都占带宽，能合并的 vec 合并。
- **从桌面 GL 抄 shader 带 `noperspective`**：ES3 编译不过，改 smooth 或手写插值。

## API 速查

| GLSL 语法 | 作用 |
|---|---|
| `smooth out/in` | 透视校正插值（默认） |
| `flat out/in` | 不插值，取激起顶点值 |

## 自测

1. flat 模式下三角形显示的是哪个顶点的颜色？
2. 为什么插值要做"透视校正"？不做会怎样？
3. ES 3.0 想要"屏幕空间线性插值"怎么办？

<details><summary>查看答案</summary>
1. 激起顶点 = 图元最后一个顶点（三角形第 3 个顶点）。
2. 不校正的话，屏幕上线性插出的颜色/纹理与 3D 空间真实位置不对应，斜面上的纹理会歪。
3. ES 3.0 做不了（noperspective 保留字未实现）；要么接受 smooth，要么在 FS 用 gl_FragCoord 手工计算。
</details>

➡️ 下一章：[09 · 图元类型与索引绘制](09-primitive-types.md)
