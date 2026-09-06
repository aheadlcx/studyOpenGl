# 26 · Uniform Buffer Object（UBO）

> 对应 App 第 26 项 · 难度：高级·GPU 管线

## 你将搞懂

- UBO 解决什么问题（多 program 共享 uniform）
- 建立绑定的三步流程
- std140 布局规则（对齐与 padding）
- 与传统 uniform 的取舍

## 先讲人话

传统 uniform 属于单个 program：10 个 shader 共享一份光照参数，就要 `glUseProgram` ×10 + `glUniform` ×10。UBO 把一块 uniform 放进 **Buffer Object**，多个 program 通过 binding 槽位指向同一块显存——**一次更新，全体生效**。

> **Android 类比**：传统 uniform 像 Activity 各自持有的私有配置；UBO 像 `SharedPreferences`——一处写入，多处读取。

## 三步建立连接

```java
// 1) GLSL 里声明块（std140 是 ES3 唯一支持的布局）
// layout(std140) uniform LightBlock {
//     vec4  lightColor;    // offset 0
//     float intensity;     // offset 16
// };                       // 总大小 32 字节（16+4+12填充）

// 2) program 侧：拿到块索引并绑定到槽位 0
int index = GLES30.glGetUniformBlockIndex(prog, "LightBlock");
GLES30.glUniformBlockBinding(prog, index, 0);
// 注意：GLSL ES 3.00 不支持 layout(binding=N)，必须运行时绑定！

// 3) 缓冲侧：把 UBO 挂到槽位 0
GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, ubo);
GLES30.glBufferData(GLES30.GL_UNIFORM_BUFFER, 32, data, GLES30.GL_DYNAMIC_DRAW);
GLES30.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, 0, ubo);  // 关键！
```

**更新只需一次**：

```java
GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, mUBO);
GLES30.glBufferSubData(GLES30.GL_UNIFORM_BUFFER, 0, 32, newData);
GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, 0);
// 所有绑定到槽位 0 的 program 立刻看到新值
```

## std140 布局规则（重点！）

| 类型 | 对齐/大小 |
|---|---|
| float / int | 4 字节 |
| vec2 | 8 字节 |
| vec3 / vec4 / 数组元素 | **16 字节** |
| mat4 | 4 列 × 16 字节 |

> **经典坑**：`vec3` 也按 16 字节对齐！所以 `vec3 + float` 常写成 `vec4 + float`（本例正是如此），避免编译器插入你算不到的 padding。

## 在 App 里怎么玩（第 26 项）

- 两个立方体 = **两个 program**（左：平滑光照；右：卡通三档光照），共享同一个 `LightBlock`；
- 拖 **光照颜色** 色相滑条：一次 `glBufferSubData`，两个立方体同时变色；
- 拖 **强度**：同时增亮；
- 对比右侧卡通风格：同一份 uniform，不同的 FS 逻辑。

## 常见坑

- **忘了 glUniformBlockBinding**：块默认 binding=0，撞槽或读不到。
- **std140 算错 padding**：数据错位，光照参数"鬼畜"——用 vec4 对齐最稳。
- **在 link 前调 glUniformBlockBinding**：必须在链接成功之后。
- **每帧重复 glBindBufferBase**：绑定是全局状态，设一次即可，换 program 不用重绑。

## 自测

1. `vec3 lightDir; float power;` 在 std140 里的偏移分别是多少？
2. 为什么用 `glGetUniformBlockIndex` 而不能在 GLSL 里写 binding？
3. UBO 相比多个独立 uniform 的主要收益？

<details><summary>查看答案</summary>
1. lightDir 对齐 16（vec3 按 16 对齐），power 在 offset 16；整块 32 字节。
2. GLSL ES 3.00 不支持 layout(binding=N)（3.1 起才有），ES3.0 必须运行时 glUniformBlockBinding。
3. 一次更新多 program 共享、减少 glUniform 调用次数、显存集中管理（适合相机/光照/雾等全局常量）。
</details>

➡️ 下一章：[27 · 缓冲映射](27-buffer-mapping.md)
