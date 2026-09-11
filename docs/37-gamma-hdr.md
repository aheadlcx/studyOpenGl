# 37 · γ 校正、sRGB 与 HDR：颜色为什么会"不对"

> 对应平台：Android + win32（ES3.0 核心）· 难度：进阶补充

## 你将搞懂

- 显示器的 γ=2.2 是什么、为什么"直接线性渲染"会偏暗
- sRGB 纹理：`GL_SRGB8_ALPHA8` 让采样自动线性化
- 输出端的 γ 编码：`pow(color, 1/2.2)`
- HDR：RGBA16F 渲染目标 + Tone Mapping 的管线位置

## 先讲人话

两个"颜色空间"在作怪：

```text
 物理线性空间（光照计算在这里才正确）
        ↑ 采样 sRGB 纹理时自动解码（^2.2）
 [sRGB 纹理]
        ↓ 上传时是人眼感知编码（显示器直接显示的格式）
 [显示器的 γ=2.2]
        ↑ 输出前必须再编码（^1/2.2），否则发出去的线性值被显示器再压一遍
 FS 输出
```

**现象**：不做 γ 校正，光照渲染的暗部会**明显偏暗发闷**，两盏灯叠加"不亮"。原因：你在 FS 里做线性加法，但结果被显示器按 γ=2.2 又压了一次。

> **Android 类比**：`Bitmap.Config` 里 sRGB 与线性 FP16（`RGBA_F16`，Android 8+ 硬件支持）的关系——系统把"显示编码"和"计算精度"分开了，GL 里同理。

## 核心代码

```java
// ① sRGB 纹理：上传 UI/照片类贴图时用 sRGB 内部格式
GLES30.glTexImage2D(GL_TEXTURE_2D, 0, GLES30.GL_SRGB8_ALPHA8,
        w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
// 之后 FS 里 texture() 采样会【自动解码到线性】——代码不用变！

// ② 输出附件用 RGBA16F（HDR）：灯光叠加可以超过 1.0 而不被截断
GLES30.glTexImage2D(GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, w, h,
        0, GL_RGBA, GL_HALF_FLOAT, null);
```

```glsl
// ③ 后处理最后一站的输出前：γ 编码（回到 sRGB 给显示器）
vec3 c = texture(u_scene, v_uv).rgb;      // HDR 线性值（可能 >1）
c = c / (c + 1.0);                        // 可选：简单 Tone Mapping 压亮部
fragColor = vec4(pow(c, vec3(1.0 / 2.2)), 1.0);   // γ 编码
```

## 管线位置（重要！）

```text
 sRGB贴图 ──自动线性──▶ FS（线性光照计算）──▶ RGBA16F FBO（线性HDR）
                                                    │
                                        Tone Mapping + pow(1/2.2)
                                                    ▼
                                              屏幕（sRGB）
```

**只有"最后一站"做一次 γ 编码**；中间所有 pass 都保持线性。多次 pow 是新手常见错误。

## 在 App 里怎么玩

- 打开第 21 章 Phong：关掉 sRGB 路径的暗部偏暗就是"没做 γ 校正"的典型症状；
- 把光照强度调到 >1 的范围、再手动 `pow(c, 1/2.2)`：对比开/关校正的暗部细节差异。

## 常见坑

- **双重 γ**：sRGB 纹理采样（自动线性化）+ FS 里再手动 pow(2.2) —— 解码两次，画面发灰；
- **中间 pass 输出也 pow**：每过一道后处理就编一次码，画面越来越亮；
- **HDR 后处理在 8bit 目标上做**：亮部截断、暗部条带——先 16F 中转，最后再编码输出；
- **所有贴图都上 sRGB**：法线图/遮罩是数据不是颜色，sRGB 化会破坏数值。

## 自测

1. FS 采样 `GL_SRGB8_ALPHA8` 纹理得到的是 sRGB 值还是线性值？
2. γ 编码应该在管线哪个位置做？几次？
3. 为什么 Bloom 的亮部阈值要在 HDR/线性空间做？

<details><summary>查看答案</summary>
1. 线性值——硬件采样时自动做 sRGB→线性解码（^2.2），你拿到的可以直接参与光照。
2. 在"最终写到 sRGB 显示目标之前"做一次；中间 pass 全程保持线性。
3. sRGB 编码后的亮度被人眼感知非线性放大，阈值判断会在视觉上忽亮忽暗；线性空间的亮度值才与物理光强成正比。
</details>

◀ 返回 [docs/README.md](README.md)
