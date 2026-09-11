# OpenGL ES 3.0 教程文档（配套 StudyOpenGL App）

本目录是 App 内 32 个技术章节的**完整文字版教程**，目标读者：**有一定 Android 应用开发经验、但几乎没接触过 OpenGL ES 的开发者**。

每一章的结构统一：

1. **你将搞懂** —— 本章学习目标
2. **先讲人话** —— 概念原理，全部配 Android 开发者熟悉的类比
3. **核心代码** —— 真实可用的 GLES30/GLSL 代码，带逐行注释
4. **在 App 里怎么玩** —— 对应演示界面的交互与实验建议
5. **API 速查** —— 本章涉及的 API 一览表
6. **常见坑** —— 初学者最容易踩的雷
7. **自测** —— 2~3 道题检验是否真的懂了

## 学习路线（与 App 内章节顺序一致）

### 第 1 阶段 · 入门概念（01~06）
不写代码，先建立"GPU 是怎么画出一帧画面"的整体认知。

| 章 | 文档 | 内容 |
|---|---|---|
| 01 | [pipeline-overview.md](01-pipeline-overview.md) | 渲染管线全景：7 道工序总览 |
| 02 | [vertex-data-vbo-vao.md](02-vertex-data-vbo-vao.md) | 顶点数据：float 数组怎么进显存 |
| 03 | [vertex-shader-mvp.md](03-vertex-shader-mvp.md) | 顶点着色器：MVP 四空间变换 |
| 04 | [rasterization.md](04-rasterization.md) | 光栅化：三角形如何变成像素 |
| 05 | [fragment-shader.md](05-fragment-shader.md) | 片元着色器：每个像素的小程序 |
| 06 | [tests-and-swap.md](06-tests-and-swap.md) | 测试/混合与双缓冲上屏 |

### 第 2 阶段 · 基础动手（07~09）
把概念落地成第一行代码。

| 章 | 文档 | 内容 |
|---|---|---|
| 07 | [hello-triangle.md](07-hello-triangle.md) | 画第一个三角形：VAO/VBO/着色器全流程 |
| 08 | [varying-interpolation.md](08-varying-interpolation.md) | Varying 插值：smooth/flat |
| 09 | [primitive-types.md](09-primitive-types.md) | 图元类型与索引绘制、重启索引 |

### 第 3 阶段 · 渲染状态与矩阵（10~16）

| 章 | 文档 | 内容 |
|---|---|---|
| 10 | [viewport-scissor.md](10-viewport-scissor.md) | 视口与裁剪框 |
| 11 | [face-culling.md](11-face-culling.md) | 面剔除与绕序 |
| 12 | [transform-matrix.md](12-transform-matrix.md) | 变换矩阵 T·R·S |
| 13 | [camera-projection.md](13-camera-projection.md) | 相机与透视投影 |
| 14 | [depth-test.md](14-depth-test.md) | 深度测试与 z-fighting |
| 15 | [blending.md](15-blending.md) | 混合（半透明） |
| 16 | [stencil-test.md](16-stencil-test.md) | 模板测试与描边 |

### 第 4 阶段 · 纹理（17~20）

| 章 | 文档 | 内容 |
|---|---|---|
| 17 | [texture-2d.md](17-texture-2d.md) | 2D 纹理与采样状态 |
| 18 | [mipmap-lod.md](18-mipmap-lod.md) | Mipmap 与 LOD |
| 19 | [texture-array.md](19-texture-array.md) | 2D 纹理数组 |
| 20 | [cubemap-skybox.md](20-cubemap-skybox.md) | 立方体贴图与天空盒 |

### 第 5 阶段 · 光照与材质（21~24）

| 章 | 文档 | 内容 |
|---|---|---|
| 21 | [phong-lighting.md](21-phong-lighting.md) | Phong 光照模型 |
| 22 | [light-maps.md](22-light-maps.md) | 材质与光照贴图 |
| 23 | [normal-mapping.md](23-normal-mapping.md) | 法线贴图与切线空间 |
| 24 | [fog.md](24-fog.md) | 雾效 |

### 第 6 阶段 · GPU 管线进阶（25~28）

| 章 | 文档 | 内容 |
|---|---|---|
| 25 | [instancing.md](25-instancing.md) | 实例化渲染 |
| 26 | [uniform-buffer.md](26-uniform-buffer.md) | Uniform Buffer Object |
| 27 | [buffer-mapping.md](27-buffer-mapping.md) | 缓冲映射 glMapBufferRange |
| 28 | [transform-feedback.md](28-transform-feedback.md) | Transform Feedback |

### 第 7 阶段 · 帧缓冲与后处理（29~32）

| 章 | 文档 | 内容 |
|---|---|---|
| 29 | [fbo-rtt.md](29-fbo-rtt.md) | 帧缓冲与渲染到纹理 |
| 30 | [post-processing.md](30-post-processing.md) | 后处理卷积 |
| 31 | [msaa.md](31-msaa.md) | 多重采样 MSAA |
| 32 | [mrt.md](32-mrt.md) | 多渲染目标 MRT |

### 第 8 阶段 · 进阶补充篇（33~40，常用但常被教程遗漏的知识）

| 章 | 文档 | 内容 |
|---|---|---|
| 33 | [compressed-textures.md](33-compressed-textures.md) | ETC2 压缩纹理：显存/带宽省 4~8 倍（Android 刚需） |
| 34 | [sampler-objects.md](34-sampler-objects.md) | 采样器对象：纹理与采样状态分离 |
| 35 | [fence-sync.md](35-fence-sync.md) | Fence 同步：CPU/GPU 异步协作 |
| 36 | [pbo.md](36-pbo.md) | PBO：纹理上传/下载异步化（双缓冲环） |
| 37 | [gamma-hdr.md](37-gamma-hdr.md) | γ 校正、sRGB 与 HDR（RGBA16F） |
| 38 | [shadow-mapping.md](38-shadow-mapping.md) | 阴影贴图：两 pass + bias + PCF |
| 39 | [hierarchy-billboard.md](39-hierarchy-billboard.md) | 层次变换（父子）+ Billboard + 文字渲染 |
| 40 | [advanced-roadmap.md](40-advanced-roadmap.md) | OBJ 模型加载、Program Binary、遮挡查询、进阶路线图 |

### 附录（学完 32 章后按需查阅）

| 文档 | 内容 |
|---|---|
| [appendix-a-egl-glthread.md](appendix-a-egl-glthread.md) | EGL 四大对象 + 自建 GL 线程源码逐行解读（App 第 01 章之前的"地基"） |
| [appendix-b-android-integration.md](appendix-b-android-integration.md) | Android 实战：相机滤镜（SurfaceTexture/OES）、Bitmap 加载、截图、上下文丢失 |
| [appendix-c-glsl-reference.md](appendix-c-glsl-reference.md) | GLSL ES 3.00 语言速查：类型/swizzle/内建函数/精度/编译错误 |
| [appendix-d-debugging-performance.md](appendix-d-debugging-performance.md) | 黑屏排查决策树、glGetError、调试工具、性能优化清单 |

> **推荐学习方式**：01~06 先建立概念 → 07~09 动手写 → 10~32 按顺序推进 → 遇到平台问题查附录 A/B，写 shader 查附录 C，优化查附录 D。

## 配套 App

所有概念都可以在配套 App（本仓库源码编译）中交互验证：每个界面 = 一个章节，含可交互演示、技术讲解、语法高亮的代码示例和实时参数调节。

## Windows C++ 版

每章的 C++ 移植代码在仓库 [win32/](../win32/README.md) 目录，可在 Windows 上直接编译运行（零第三方依赖），与 App 内 Demo 一一对应。
