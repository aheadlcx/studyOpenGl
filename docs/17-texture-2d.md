# 17 · 2D 纹理与采样

> 对应 App 第 17 项 · 难度：纹理

## 你将搞懂

- 纹理对象 = 纹素数组 + 一组采样状态
- 纹理单元（texture unit）与 sampler 绑定流程
- wrap 环绕模式与 filter 过滤模式
- 图像坐标系与 GL 纹理坐标系的方向差

## 先讲人话

纹理就是**一张记录颜色的数组**，`v_uv` 是查表坐标。GLSL 里 `sampler2D` 是"采样器句柄"，它的值是**纹理单元号**：

```text
glActiveTexture(GL_TEXTURE0) → glBindTexture(我的图)   // 把图放进0号单元
glUniform1i(loc, 0)                                    // 告诉 sampler 读0号单元
```

> **Android 类比**：纹理单元 = 抽屉柜的抽屉编号；sampler uniform = "去几号抽屉拿图"的字条。忘了写字条，sampler 默认读 0 号——多张贴图全采成同一张的经典 bug。

## 核心代码

```java
// 创建：把 Bitmap 上传成纹理（GLUtils 自动处理行对齐）
int[] ids = new int[1];
GLES30.glGenTextures(1, ids, 0);
GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0]);
GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0);
// 参数：环绕 + 过滤（见下表）
GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT);
GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT);
GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR);
GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);
```

```glsl
// 片元里采样一行搞定
vec4 tex = texture(u_tex, v_uv);
```

## wrap 环绕模式（uv 超出 [0,1] 时）

| 模式 | 效果 |
|---|---|
| GL_REPEAT | 重复平铺 |
| GL_MIRRORED_REPEAT | 镜像重复 |
| GL_CLAMP_TO_EDGE | 边缘像素延伸（ES 常用默认） |
| GL_CLAMP_TO_BORDER | ❌ **ES 没有**（桌面独有） |

## filter 过滤模式（纹素与像素不对齐时）

| 时机 | 选项 | 效果 |
|---|---|---|
| 放大 MAG | NEAREST / LINEAR | 块状 / 平滑 |
| 缩小 MIN | NEAREST / LINEAR | 不用 mipmap：闪烁 |
| 缩小 MIN | *_MIPMAP_* | 用 mip 链（第 18 章） |

⚠ 用了 mipmap 类 min filter 却没 `glGenerateMipmap` → 纹理"不完整"→ 采到黑色。

## 坐标系方向差（经典翻转问题）

图像文件第 0 行在**顶部**，上传后 v=0 对应第 0 行；GL 空间 v 向上。于是直接采样图片是**上下颠倒**的。三种解法：shader 里 `v_uv.y = 1.0 - v_uv.y`；上传前翻转 Bitmap；或 uv 数据里就写好。App 里"翻转Y"开关现场演示。

## 在 App 里怎么玩（第 17 项）

- "GL"标志纹理 + 网格底：文字方向是否颠倒一目了然；
- **WRAP_S/T** 切三种环绕 + **UV 缩放**拉大 → 看平铺/镜像/钳制的差异；
- **MIN/MAG 过滤** 切换：NEAREST 像素风 vs LINEAR 平滑；
- **色调混合 mix()** 滑条：shader 里 `mix(tex.rgb, tint, u_mix)`。

## 常见坑

- **多纹理忘 glUniform1i**：全部采成 0 号单元的图。
- **用了 mipmap min filter 忘生成 mipmap**：整张贴图变黑。
- **RTT/天空盒纹理用 REPEAT**：边缘把对面"绕"进来 → 必须 CLAMP_TO_EDGE。
- **NPOT（非2次幂）纹理**：ES3 已完全支持 + mipmap，ES2 时代限制不再。

## 原理图解与代码逐步拆解

```text
 采样管线：uv ──wrap──▶ [0,1] ──filter──▶ 最终颜色

 uv=(0.25,0.25)                纹素网格 (4×4 示意)
      │                        ┌───┬───┬───┬───┐
      ▼                        │ A │ B │ C │ D │
 [0,1]内直接用 ────────▶      ├───┼───┼───┼───┤
 uv=(1.5,0.2)                 │ E │ F │ G │ H │
      │ REPEAT → (0.5,0.2)    ├───┼───┼───┼───┤
      │ CLAMP  → (1.0,0.2)    │ I │ J │ K │ L │
      ▼                        └───┴───┴───┴───┘
 NEAREST → 取最近1个纹素（块状）
 LINEAR  → 取周围4个加权（平滑）
```

逐步拆解：

1. `glGenTextures` + `glBindTexture` 创建纹理对象；
2. `GLUtils.texImage2D`（或 `glTexImage2D` + 手动像素）把 Bitmap 像素上传到显存；
3. `glTexParameteri` ×4：wrap S/T、min filter、mag filter——每个都是独立旋钮；
4. 绘制时三步：`glActiveTexture(GL_TEXTURE0)` → `glBindTexture(图)` → `glUniform1i(loc, 0)`；
5. FS 里 `texture(u_tex, v_uv)` 完成采样；uv 超界走 wrap，尺寸不匹配走 filter。

## 自测

1. 三张贴图分别绑在单元 0/1/2，shader 里 sampler 要怎么对应？
2. uv=(1.5, -0.2) 在 REPEAT 与 CLAMP_TO_EDGE 下分别采到哪？
3. 为什么 ES3 的 GL_CLAMP_TO_BORDER 用不了？

<details><summary>查看答案</summary>
1. 各自 glUniform1i 传对应单元号 0/1/2，且上传时分别 ActiveTexture(0/1/2) 后绑定。
2. REPEAT：x 取 0.5、y 取 0.8（小数部分循环）；CLAMP：钳到 (1.0, 0.0) 边缘像素。
3. 该模式属于桌面 GL / ES/NV 扩展，GLSL ES 3.0 核心不提供，用 CLAMP_TO_EDGE 替代。
</details>

➡️ 下一章：[18 · Mipmap 与 LOD](18-mipmap-lod.md)
