# OpenGL ES 3.0 实验室（studyOpenGL）

一个**零第三方依赖**的 Android 学习工程：用 **Java 7** 语法 + **OpenGL ES 3.0**，
**自建 GL 线程**（EGL 直连，不用 GLSurfaceView），以 32 个独立界面逐个演示
OpenGL ES 3.0 的核心技术点。每个界面都带**可实时调节的参数面板**和**成体系的技术讲解**，
主界面为**可上下滑动浏览的列表**。

## 📚 文字版教程

全部 32 章的完整教程文档在 [docs/](docs/README.md)——按难度从入门概念到高级进阶排列，每章包含：学习目标、人话概念（配 Android 类比）、核心代码、App 实操指南、API 速查、常见坑、自测题。**建议先读 [docs/README.md](docs/README.md) 的学习路线，再在 App 里逐章验证。**

```
minSdk 21 · targetSdk 33 · AGP 7.4.2 · Gradle 8.5 · Java sourceCompatibility 1.7 · 无任何依赖
```

## 构建 & 运行

```bash
# 1) 配置 local.properties 中的 sdk.dir
# 2) 命令行构建
./gradlew assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk

# 或直接用 Android Studio 打开本目录 Sync 运行
```

设备要求：Android 5.0+，且支持 OpenGL ES 3.0（Manifest 已声明 `glEsVersion="0x00030000"`）。

## 需求对照

| 需求 | 实现位置 |
|---|---|
| 1. Java 7 语言 | `app/build.gradle` `sourceCompatibility VERSION_1_7`，全部代码无 lambda/无 API 24+ 语言特性 |
| 2. OpenGL ES 3.0 | 全部使用 `android.opengl.GLES30` + GLSL `#version 300 es` |
| 3. 自建 GL 线程 | `gl/EglCore`（EGLDisplay/Config/Context/Surface 全手动）+ `gl/GLThread`（自有 Looper + Choreographer 帧调度）+ `gl/RenderSurface`（普通 SurfaceView 转发表面事件） |
| 4. 尽可能全的 ES3 技术细节 | 26 个 Demo，见下表 |
| 5. 每个技术点单独界面 | 每个 Demo 独立引擎类 + `DemoActivity` 统一承载 |
| 6. 动态调参 | `param/ParamSpec` 声明式参数 + DemoActivity 动态生成滑条/开关/下拉框，UI → `GLThread.postGLTask` → GL 线程生效 |
| 7. 技术讲解 | 每个 Demo 类内有 `DESCRIPTION`（管线原理、API 表、坑点、公式），显示在底部面板 |
| 8. 列表界面 | `MainActivity` + ListView，上下滑动选择 |

## 自建 GL 线程要点（需求 3 的核心代码）

- `EglCore`：`eglGetDisplay → eglInitialize → eglChooseConfig(RGBA8888+Depth24+Stencil8+ES3位) → eglCreateContext(CLIENT_VERSION=3) → eglCreateWindowSurface → eglMakeCurrent`，每步检查 `eglGetError`；
- `GLThread`：自带 Looper 的渲染线程；Choreographer（vsync 对齐）驱动帧循环；表面销毁时**同步等待** GL 线程释放 EGLSurface 后 UI 线程才返回；Activity 退后台停帧省电；Surface 重建复用 EGLContext（纹理/VBO 不丢）；
- 线程模型：GL 对象只在 GL 线程创建/使用；UI 改参数 → `ConcurrentHashMap` 存值 + 任务队列，GL 帧首 drain 执行。

## 32 个技术点目录

| # | 阶段 | 技术点 | 演示要点 | 可调参数示例 |
|---|---|---|---|---|
| 01 | 入门·概念 | 渲染管线全景（零基础） | 7 道工序流水线图+数据包流动画，建立整体认知 | 流速、跟随高亮 |
| 02 | 入门·概念 | 第1站·顶点数据 VBO/VAO | 内存条可视化、交错/分离布局 | 布局切换、选中顶点 |
| 03 | 入门·概念 | 第2站·顶点着色器 MVP | 局部→世界→相机→裁剪四空间 | 自转、环绕、fov |
| 04 | 入门·概念 | 第3站·光栅化放大镜 | GPU 连续 vs CPU 离散片元对比 | 放大部位、网格密度 |
| 05 | 入门·概念 | 第4站·片元着色器输入 | 插值色/FragCoord/uv/discard 六视角 | 视角切换、密度 |
| 06 | 入门·概念 | 第5站·测试混合与上屏 | 片元闯四关+双缓冲 SWAP 动画 | 各关卡开关、速度 |
| 07 | 基础·动手 | 三角形与着色器管线 | VAO/VBO/编译链接/绘制全流程 | 色相、旋转、第二个三角形 |
| 08 | 基础·动手 | Varying 插值 | smooth/flat 插值限定符对比 | 插值限定符、旋转 |
| 09 | 基础·动手 | 图元类型与索引绘制 | 全部 draw mode、32 位索引、**重启索引** | 模式切换、点大小、线宽 |
| 10 | 基础·状态 | 视口与裁剪 | 多视口分屏、glClear 受 scissor 影响 | 网格 N、scissor 开关 |
| 11 | 基础·状态 | 面剔除 | 绕序、glFrontFace/glCullFace | 剔除面、绕序 |
| 12 | 进阶·矩阵 | 变换矩阵 | T·R·S 顺序陷阱、坐标轴 | 平移/旋转/缩放 |
| 13 | 进阶·矩阵 | 相机与透视投影 | fovy/near/far、轨道相机、触摸拖动 | fov、near/far |
| 14 | 进阶·状态 | 深度测试 | 8 种 glDepthFunc、深度掩码、**多边形偏移治 z-fight** | 深度函数、factor/units |
| 15 | 进阶·状态 | 混合 Blending | 因子矩阵、混合方程、常量颜色、绘制顺序问题 | src/dst 因子、方程、alpha |
| 16 | 进阶·状态 | 模板测试 | glStencilFunc/Op、**描边三段式** | 描边开关/缩放/颜色 |
| 17 | 纹理 | 2D 纹理与采样 | wrap/filter 全状态、纹理单元、Y 翻转 | wrap S/T、min/mag、uv 缩放 |
| 18 | 纹理 | Mipmap 与 LOD | mip 链、三线性、texture(s,uv,bias) | min filter、LOD bias |
| 19 | 纹理 | 2D 纹理数组 | glTexImage3D、sampler2DArray 逐实例选层 | 层偏移、轮播 |
| 20 | 纹理 | 立方体贴图天空盒 | samplerCube 方向采样、z=w 技巧 | 天空转速、反射强度 |
| 21 | 光照与材质 | Phong 光照 | ADS 逐片元、reflect()、shininess | 环境/漫反射/镜面/反光度 |
| 22 | 光照与材质 | 材质与光照贴图 | 三张贴图、GLSL struct、多纹理单元 | shininess、自发光强度 |
| 23 | 光照与材质 | 法线贴图与 TBN | 切线空间、程序化法线图 | 法线强度、光源距离 |
| 24 | 光照与材质 | 雾效 | 线性/EXP/EXP2 公式 | 雾类型、密度 |
| 25 | 高级·GPU 管线 | 实例化渲染 | DrawElementsInstanced、attribDivisor、gl_InstanceID | 网格 N（1~1600 实例） |
| 26 | 高级·GPU 管线 | Uniform Buffer | std140 布局、多 program 共享 UBO | 光色/强度（一次更新两对象） |
| 27 | 高级·GPU 管线 | 缓冲映射 | glMapBufferRange vs glBufferSubData FPS 对比 | 网格密度、更新方式 |
| 28 | 高级·GPU 管线 | Transform Feedback | GPU 粒子、乒乓缓冲、RASTERIZER_DISCARD | 粒子数、重力、点大小 |
| 29 | 高级·帧缓冲 | FBO 与 RTT | 附件、完整性检查、两 pass 渲染 | 采样缩放、镜像 |
| 30 | 高级·帧缓冲 | 后处理卷积 | 3×3 核（模糊/锐化/边缘/浮雕） | 核类型、步长、混合 |
| 31 | 高级·帧缓冲 | 多重采样 MSAA | multisample RBO + blit 解析 | 2x/4x/8x 开关 |
| 32 | 高级·帧缓冲 | 多渲染目标 MRT | glDrawBuffers、G-Buffer 雏形 | 显示附件切换/分屏 |

## 工程结构

```
app/src/main/java/com/example/studyopengl/
├── ui/            MainActivity(列表) · DemoActivity(演示+面板) · DemoCatalog · DemoAdapter · DemoInfo
├── gl/            EglCore · GLThread · RenderSurface · ShaderProgram · Mesh(VAO) · GeoGen · TextureHelper
├── engine/        DemoEngine(接口) · BaseDemoEngine(参数仓库+GL线程调度)
├── param/         ParamSpec(滑条/开关/枚举声明)
└── demos/         D01~D32 共 32 个技术点引擎（内嵌 GLSL 3.00 着色器 + 讲解文本；D27~D32 为零基础渲染管线教程系列）
```

## 常见问题

- **设备不支持 ES3？** `EglCore` 会抛出异常并闪退（Manifest 已声明要求，安装时即过滤）。
- **模拟器上 MSAA/模板行为异常？** 部分 SwiftShader/主机 GPU 驱动对 `GL_MAX_SAMPLES`、模板支持有限，真机表现为准。
- **如何加新 Demo？** 写一个类继承 `BaseDemoEngine`（实现 `onSurfaceCreated/onDrawFrame/getParamSpecs` 等），
  在 `DemoCatalog.all()` 里加一条 `DemoInfo` 即可，UI 自动生成列表项和参数面板。
