# 34 · 采样器对象：纹理与采样状态分离

> 对应平台：Android + win32（ES3.0 核心）· 难度：进阶补充

## 你将搞懂

- 采样状态一直藏在纹理对象里的问题
- Sampler Object：把"过滤/环绕"状态从纹理中拆出来
- 一套采样状态配 N 张纹理的工程价值

## 先讲人话

第 17 章里 `glTexParameteri` 设置的 wrap/filter 其实**存在纹理对象内部**。后果：

```text
 同一张贴图，UI 要 CLAMP+LINEAR，地面要 REPEAT+MIPMAP
 → 必须为两种用法准备两份纹理拷贝（显存×2）
```

ES3.0 的 **Sampler Object** 把采样状态拆出来独立成对象：纹理只存像素，采样器只存"怎么读"。绘制时用 `glBindSampler(unit, sampler)` 把两者**在绑定点上组合**。

> **Android 类比**：以前是"图片自带缩放方式"；现在是"图片归图片，`ScaleType` 归 ImageView"——同一张图配不同 ScaleType，不用复制图片。

## 核心代码

```java
// 创建两个采样器（一次配置，长期使用）
int[] samplers = new int[2];
GLES30.glGenSamplers(2, samplers, 0);

// 采样器0：UI 风格
glBindSampler(0, samplers[0]);   // 配置时先绑到一个单元
GLES30.glSamplerParameteri(samplers[0], GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
GLES30.glSamplerParameteri(samplers[0], GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
GLES30.glSamplerParameteri(samplers[0], GL_TEXTURE_MIN_FILTER, GL_LINEAR);
GLES30.glSamplerParameteri(samplers[0], GL_TEXTURE_MAG_FILTER, GL_LINEAR);

// 采样器1：3D 场景风格
glBindSampler(1, samplers[1]);
glSamplerParameteri(samplers[1], GL_TEXTURE_WRAP_S, GL_REPEAT);
glSamplerParameteri(samplers[1], GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
glSamplerParameteri(samplers[1], GL_TEXTURE_MAG_FILTER, GL_LINEAR);
glSamplerParameterf(samplers[1], GL_TEXTURE_MAX_ANISOTROPY_EXT, 8.0f); // 各向异性(扩展)

// 绘制：同一张纹理，两个采样器任意组合
glActiveTexture(GL_TEXTURE0);
glBindTexture(GL_TEXTURE_2D, texId);
glBindSampler(0, samplers[ui ? 0 : 1]);   // 采样器优先级高于纹理内部参数！
```

**优先级规则**：绑定了 sampler 对象时，纹理自身的 `glTexParameteri` 采样状态**被完全忽略**——这是最容易踩的坑（改了纹理参数没反应，就是有 sampler 在覆盖）。

## 工程价值

| 场景 | 收益 |
|---|---|
| 一张贴图既当 UI 又当 3D 贴图 | 一份数据两套读法，显存减半 |
| 材质系统 | "贴图 × 采样风格"自由组合，配置化 |
| 减少 GL 状态切换 | 切 sampler 比重传/重设纹理参数便宜 |

## 常见坑

- **忘了 `glBindSampler(unit, 0)` 解绑**：sampler 一直覆盖纹理自己的参数，"明明改了参数没效果"。
- **绑错单元**：sampler 绑定在**单元**上不是纹理上，`glBindSampler(unit, s)` 的 unit 要和 `glActiveTexture` 的号一致。
- **sampler 上配 mipmap 但纹理没生成 mip**：不完整采样 → 黑色（和第 17 章同款后果）。

## 自测

1. 绑定 sampler 后，纹理对象里的 `GL_TEXTURE_WRAP_S` 还起作用吗？
2. 一张 2048 贴图要"UI 用 CLAMP、地面用 REPEAT"，分离前后的显存对比？
3. `glBindSampler` 的第二个参数是纹理 id 吗？

<details><summary>查看答案</summary>
1. 不起作用。sampler 对象存在时完全覆盖纹理内部的采样状态。
2. 分离前需要两份纹理拷贝（2×显存）；分离后一份纹理数据 + 两个只有几十字节的 sampler 对象。
3. 不是，是纹理单元号；纹理仍用 glBindTexture 绑到同一单元。
</details>

◀ 返回 [docs/README.md](README.md)
