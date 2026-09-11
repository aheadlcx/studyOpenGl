# 32 · 多渲染目标 MRT

> 对应 App 第 32 项 · 难度：高级·帧缓冲
> 对应 App 第 32 项 · 难度：高级·帧缓冲

```text
管线定位：①数据 ─ ②VS ─ ③装配 ─ ④裁剪 ─ ⑤光栅 ─ 【⑥FS】 ─ 【⑦测试】
 ▲【】内为本章聚焦环节
```

## 你将搞懂

- MRT：一次 draw call 同时写多张纹理
- `glDrawBuffers` 与 `layout(location=N) out` 的对应关系
- G-Buffer 与延迟渲染的概念
- 完整性与数量上限

## 先讲人话

普通渲染一个片元只输出 1 个颜色。MRT 让片元着色器**同时向 FBO 的多个颜色附件输出**——渲染一遍，同时得到"颜色图、法线图、位置图"等多张中间结果。

> **Android 类比**：像一次 `findViewById` 返回一组控件的引用集——一遍遍历，多处赋值。延迟渲染正是用这些"分门别类的中间结果"做第二次计算。

## 核心代码

**GLSL 侧：多个 out**

```glsl
layout(location=0) out vec4 o_albedo;   // 反照率（棋盘格）
layout(location=1) out vec4 o_normal;   // 世界法线伪彩

void main() {
    float checker = mod(floor(v_worldPos.x*2.0) + floor(v_worldPos.y*2.0)
                      + floor(v_worldPos.z*2.0), 2.0);
    o_albedo = vec4(mix(vec3(0.85,0.4,0.25), vec3(0.95,0.9,0.8), checker), 1.0);
    o_normal = vec4(normalize(v_normal) * 0.5 + 0.5, 1.0);   // 法线编码进 0~1
}
```

**GL 侧：启用多个 draw buffer（与 location 对应）**

```java
GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mFBO);
for (int i = 0; i < 2; i++) {
    GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0 + i, GLES30.GL_TEXTURE_2D, colorTex[i], 0);
}
// 关键：声明 FS 的 location i 写进第 i 个附件
GLES30.glDrawBuffers(2, new int[]{
        GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_COLOR_ATTACHMENT1}, 0);
```

**显示 pass**：两张纹理分别绑定采样，切换/分屏查看。

## 用途：延迟渲染 G-Buffer

传统前向渲染：每个物体"画一遍算一遍光照"，复杂光源下大量被遮挡的像素白算。延迟渲染拆成：

```text
Pass1（几何 pass）：只写 G-Buffer——albedo / 法线 / 位置（MRT）
Pass2（光照 pass）：全屏四边形逐像素读 G-Buffer 算光照
                 → 只对"屏幕上可见的像素"执行光照
```

复杂多光源场景下省掉海量被遮挡着色；点选拾取（pick-by-color）、PBR 材质输出同样基于 MRT。

## 数量与完整性

- 数量上限查询 `GL_MAX_DRAW_BUFFERS`（ES3 下限 4）；
- 所有 draw buffer 都必须有有效附件（或显式设 GL_NONE），否则 FBO 不完整；
- 附件尺寸/格式需一致；
- 切回默认帧缓冲时记得恢复 `glDrawBuffers(1, {GL_BACK})`。

## 在 App 里怎么玩（第 32 项）

- Pass1 同时写两张 1024² 纹理：附件0=反照率棋盘、附件1=法线伪彩（n×0.5+0.5 的经典配色）；
- **显示哪个附件** 下拉：附件0 / 附件1 / 左右分屏——同一帧的两个产物；
- 法线伪彩里：绿=(0,1,0) 朝上、粉=(1,0,1) 朝右下……学看法线图是调延迟渲染的基本功。

## 常见坑

- **忘了 glDrawBuffers**：FS 的 location=1 输出无去处（默认只有附件0）。
- **切回屏幕忘恢复 draw buffer**：后续渲染写错目标。
- **附件格式不兼容**：FBO incomplete。
- **以为 MRT 能写深度**：深度仍然只有一份（DEPTH_ATTACHMENT），多张的是颜色。

## 原理图解与代码逐步拆解

```text
 一次 draw，两份输出（延迟渲染的 G-Buffer 雏形）：

   FS                                       FBO
 ┌─────────────────────┐    location=0 ──▶ ┌──────────────┐
 │ o_albedo = 棋盘颜色  │                   │ 附件0: 颜色图 │
 │ o_normal = 法线伪彩  │    location=1 ──▶ ┌──────────────┐
 └─────────────────────┘                   │ 附件1: 法线图 │
                                            └──────────────┘
 glDrawBuffers(2, {ATTACHMENT0, ATTACHMENT1})  ← 对应关系声明
 显示 pass 可切换查看 / 分屏对比两张"中间产物"
```

逐步拆解：

1. FS 声明两个 `layout(location=N) out`：location0 写反照率、location1 写法线伪彩（n×0.5+0.5 的经典配色）；
2. FBO 上挂两张颜色纹理（附件0/1）+ 一张深度 RBO；
3. `glDrawBuffers(2, {C0, C1})` 声明两个绘制目标——顺序与 location 对应；
4. 法线伪彩读法：绿色=朝上、粉色=朝右下……学看法线图是做延迟渲染的基本功；
5. 显示 pass 切换 u_mode：附件0 / 附件1 / 左右分屏——同一帧的两份产物随意查看。

## 自测

1. `glDrawBuffers(2, {C0, C1})` 中 FS 的 location=1 写到哪里？
2. 延迟渲染为什么能省光照计算？
3. MRT 的附件数量下限是多少？怎么查？

<details><summary>查看答案</summary>
1. 写进绑定到 GL_COLOR_ATTACHMENT1 的纹理——location 下标与 drawBuffers 数组下标一一对应。
2. Pass1 只记录属性不算法线；Pass2 用全屏 pass 逐像素光照——被深度挡住的像素天然不参与（深度测试已把它们排除在 G-Buffer 外）。
3. 下限 4，用 glGetIntegerv(GL_MAX_DRAW_BUFFERS) 查询。
</details>

🎉 **32 章完结**。恭喜你走完了从"GPU 是什么"到"延迟渲染雏形"的全程。回到 App 里，把任意两章的知识组合玩一玩（比如：实例化 + 光照 + FBO 后处理）——融会贯通才是真正的掌握。
