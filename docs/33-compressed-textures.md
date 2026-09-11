# 33 · 压缩纹理 ETC2：显存与带宽的降维打击

> 对应平台：Android（ES3.0 核心保证支持）；难度：进阶补充

## 你将搞懂

- 为什么未压缩纹理是手机上的"显存/带宽杀手"
- ETC2/EAC：ES3.0 核心保证的压缩格式家族
- `glCompressedTexImage2D` 的用法与 KTX 容器
- 压缩纹理的适用边界（哪些内容不适合压缩）

## 先讲人话

一张 2048×2048 的 RGBA 纹理：`2048×2048×4B = 16MB` 显存；每帧采样还要消耗对应带宽——手机上带宽是最贵的资源。**ETC2 压缩**把显存压到 1/4（RGB8）甚至 1/8（R11），且 GPU 可以**保持压缩状态直接采样**（渲染时硬件解压单个"块"），不像 ZIP 那样要先整体解压。

> **Android 类比**：Bitmap 加载图片用 `inSampleSize`/`RGB_565` 省内存——ETC2 是 GPU 侧的同类操作，而且 ES3.0 起**所有 Android 设备都硬件支持**（ES2.0 时代各厂商格式分裂的痛点被终结了）。

## 原理图解

```text
 未压缩 RGBA8（每纹素 32bit）           ETC2 RGB8（每 4×4 块 64bit = 4bit/纹素）

 ┌──┬──┬──┬──┐                        ┌───────────┐
 │▒▒│▒▒│▒▒│▒▒│  4×4=16 个纹素          │ base色 + 3bit修正 │  一块只存
 ├──┼──┼──┼──┤  = 64 字节              │ + 每纹素 2bit 索引 │  "基色调色板+选择索引"
 └──┴──┴──┴──┘                        └───────────┘
 采样时：按索引从调色板取色（有损，但视觉上通常可接受）
```

## ETC2/EAC 格式家族

| 格式 | 每纹素 | 内容 |
|---|---|---|
| `COMPRESSED_RGB8_ETC2` | 4bit | RGB（无 alpha） |
| `COMPRESSED_RGBA8_ETC2_EAC` | 8bit | RGBA（alpha 走 EAC） |
| `COMPRESSED_R11_EAC` / `RG11_EAC` | 4/8bit | 单/双通道数据（法线、高度、遮罩） |
| SIGNED 变体 | 同上 | 存有符号数据（如法线贴图 -1~1） |

## 核心代码

```java
// 离线用工具（etcpack / astcenc / Android Studio）把 PNG 转成 ETC2 的 .pkm/.ktx
// 运行时直接把压缩字节喂给 GPU：
GLES30.glCompressedTexImage2D(
        GLES30.GL_TEXTURE_2D,
        0,                                      // level
        GLES30.COMPRESSED_RGB8_ETC2,            // 压缩内部格式
        width, height,
        0,                                      // border 必须 0
        data.length,                            // 压缩数据字节数
        ByteBuffer.wrap(data));                 // .pkm 去掉 16 字节头后的数据

// mipmap 每层都要单独压缩上传（或 glGenerateMipmap 让驱动对压缩纹理再压缩）
```

**KTX 容器**建议：`.ktx` 文件头里就有格式、尺寸、mip 层数，可以几乎原样把 buffer 交给 `glCompressedTexImage2D`——工程上推荐直接加载 KTX。

## 适用边界

| 适合 ETC2 | 不适合 |
|---|---|
| 照片、砖墙、角色贴图（颜色渐变类） | UI 纯色/锐利文字（压缩会出块状噪点） |
| 法线/高度（用 EAC R11 SIGNED） | 需要逐像素精确写入的 RenderTarget |
| 大量贴图的 3D 场景（显存/加载时间双赢） | 需要频繁 CPU 读回的图 |

## 常见坑

- **压缩后 alpha 硬边**：ETC2 的 alpha 是 EAC 压缩的，锐利 UI 边缘会毛糙——UI 贴图保持 RGBA8；
- **忘传 level**：mipmap 每层尺寸减半，字节数也要按 4bit/纹素 重新算；
- **不支持 `glTexSubImage2D`**：压缩纹理只能整体 `glCompressedTexSubImage2D` 且必须按块对齐；
- **桌面 GL**：ETC2 需要 GL 4.3 / `ARB_ES3_compatibility`——win32 版跑不了本章属正常（对应桌面用 BC 压缩）。

## 自测

1. 2048² 的 ETC2 RGB8 纹理占多少显存？
2. 为什么 ETC2 可以"保持压缩状态采样"？
3. UI 小图标适合 ETC2 吗？

<details><summary>查看答案</summary>
1. 2048×2048×4bit ÷ 8 = 2MB（未压缩 16MB 的 1/8）。
2. GPU 采样时按需解压单个 4×4 块（块内是"基色调色板+每纹素索引"），无需整图解压。
3. 不适合。ETC2 是有损压缩，纯色/锐边会出块状噪点，UI 用 RGBA8。
</details>

◀ 返回 [docs/README.md](README.md)
