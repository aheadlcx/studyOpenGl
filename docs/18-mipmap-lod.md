# 18 · Mipmap 与 LOD

> 对应 App 第 18 项 · 难度：纹理

## 你将搞懂

- 为什么缩小必须用 mipmap（频率混叠）
- 4 种 mipmap min filter 的差别
- `texture(s, uv, bias)` 手动偏移层级
- `glTexStorage2D`（ES3 不可变存储）的用法

## 先讲人话

大纹理映射到小屏幕时，**多个纹素挤进一个像素**，只采 1 个纹素 → 高频细节闪烁（摩尔纹）。Mipmap 把纹理按 1/2 逐级降采样成链条，采样时按"像素覆盖多大区域"自动选层级：

```text
level0 256×256（原图）
level1 128×128
level2  64×64
...
level8    1×1      显存多花约 1/3
```

> **Android 类比**：`BitmapFactory` 的 `inSampleSize` 缩略图链——远处的图根本不需要原图精度。

## 四种缩小过滤

| min filter | 行为 |
|---|---|
| GL_NEAREST / GL_LINEAR | 不用 mipmap（高频闪烁） |
| NEAREST_MIPMAP_NEAREST | 取最近 1 层（块状过渡） |
| NEAREST_MIPMAP_LINEAR | 两层各采（层间线性） |
| **LINEAR_MIPMAP_LINEAR** | 每层双线性+层间混合 = **三线性**（最平滑） |

## 核心代码：ES3 不可变存储

```java
// glTexStorage2D：一次性分配完整 mip 链（不可变！格式尺寸不能再改）
int levels = 32 - Integer.numberOfLeadingZeros(size);   // log2(size)+1
GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, levels, GLES30.GL_RGBA8, size, size);
GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, size, size,
        GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels); // 只填 level0
GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);            // 派生其余层
```

对比 `glTexImage2D`（每次可重新指定格式尺寸，"可变"存储）：`texStorage` 分配后不可改，驱动可预优化内存布局，且不会因中途改格式引发重新分配——ES3 推荐风格。

## 手动控制层级

```glsl
// 带 bias：在自动 LOD 上加偏移（仅片元着色器可用）
vec4 c = texture(u_tex, v_uv, u_bias);   // bias>0 更糊，<0 更锐
// 完全指定层级（不做自动差分，常用于 raymarching/调试）
vec4 c2 = textureLod(u_tex, v_uv, 3.0);  // 强制用第 3 层
```

## 在 App 里怎么玩（第 18 项）

- 地面向地平线延伸 60 单位，远处纹理剧烈缩小——mipmap 的天然演示场；
- **缩小过滤** 先切 `GL_NEAREST`：远处雪花闪烁刺眼；切 `GL_LINEAR_MIPMAP_LINEAR`：立刻稳定成灰色渐变；
- **LOD bias** 拉到 +3：整屏糊；拉到 -3：远处重新闪烁；
- **UV 重复次数** 拉大，远处层级更密集。

## 常见坑

- **min filter 用了 mipmap 组合但没生成 mip**：纹理不完整，采样得黑色。
- **bias 是片段级差异**：`texture()` 带 bias 的重载只在片元着色器有效。
- **全黑纹理生成 mip 全黑**：降采样不会产生信息。
- **线性过滤的 RTT 没 mip**：后处理采样缩小会闪——链式 RTT 时要 `glGenerateMipmap` 或接受锯齿。

## 原理图解与代码逐步拆解

```text
 mip 链（256 原图 → 显存 +1/3）：

 level0 ████████████████ 256²   ← 近处采样这层（细节全）
 level1 ████████ 128²
 level2 ████ 64²
 level3 ██ 32²                  ← 远处采样这层（平均色，不闪）
 level4 ▌ 16²
  ...     1²

 一个像素的"视野"越小（物体越远）→ 自动选越糊的层
 bias>0 强制选更糊层 / bias<0 强制选更锐层（本App滑条）
```

逐步拆解：

1. `glTexStorage2D(levels, RGBA8, w, h)`：一次性把整条链的显存**全部**分配好（不可变）；
2. `glTexSubImage2D(..., 0, ...)` 只填 level0；
3. `glGenerateMipmap` 从 level0 逐级降采样派生其余层；
4. FS 里 `texture(u_tex, uv, bias)` 的第三个参数在自动选择的层级上**加偏移**；
5. 观察 App 地面：切 `GL_NEAREST` 时远处雪花闪烁 = 一个像素采到一个高频纹素；切三线性后 = 取了平均色。

## 自测

1. mipmap 链额外占用多少显存？
2. 三线性过滤对应哪个枚举？
3. `textureLod(s, uv, 0.0)` 采样哪一层？

<details><summary>查看答案</summary>
1. 约 1/3：256+128+64+...+1 = 511 ≈ 256×2 的和，比原图多出 1/3。
2. GL_LINEAR_MIPMAP_LINEAR。
3. 第 0 层——也就是原图本身。
</details>

➡️ 下一章：[19 · 2D 纹理数组](19-texture-array.md)
