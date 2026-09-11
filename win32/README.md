# Win32 版：在 Windows 上直接运行的 C++ 教程代码

这是 Android App 中 32 个章节的 **C++ 移植版**。每一章的绘制逻辑与 App 内的 Demo 一一对应，
但**不依赖任何第三方库**：窗口用 Win32 API，GL 上下文用原生 WGL，GL 2.0/3.0 函数通过
`wglGetProcAddress` 手动加载（见 `src/common/glfuncs.h`）——和 Android 端"自己写 EglCore"
是同一个教学思路。

## 编译

### 方式 A：MinGW-w64 g++（最简单）

1. 安装 [MinGW-w64](https://winlibs.com/)（解压即用），把 `bin` 加入 PATH；
2. 双击或运行 `build.bat`；
3. 产物：`build\study_gl.exe`。

也可以手动一条命令：

```bat
g++ -O2 -std=c++14 src/main.cpp src/common/glfuncs.cpp src/chapters/ch*.cpp ^
    -o build/study_gl.exe -lopengl32 -luser32 -lgdi32
```

### 方式 B：Visual Studio (MSVC)

1. 打开 "x64 Native Tools Command Prompt for VS"；
2. 运行 `build.bat`；或用 CMake：

```bat
cmake -B build -S . && cmake --build build --config Release
```

产物同样是 `build\study_gl.exe`。

## 运行

```bat
build\study_gl.exe        :: 弹出章节菜单，输入编号回车
build\study_gl.exe 14     :: 直接运行第 14 章（深度测试）
```

每章窗口内按 `ESC` 退出。按键说明会打印在控制台（`printf`），例如第 14 章：
`D` 切换深度函数、`M` 深度掩码、`O` 多边形偏移。

## 章节对照（与 App / docs 编号一致）

| # | 源文件 | 内容 |
|---|---|---|
| 01 | ch01.cpp | 管线全景：渲染三角形 + 控制台滚动打印 7 道工序 |
| 02 | ch02.cpp | 交错 vs 分离顶点布局（V 切换） |
| 03 | ch03.cpp | 局部/世界/相机/裁剪四空间四视口 |
| 04 | ch04.cpp | 光栅化放大镜：GPU 平滑 vs CPU 片元方格 |
| 05 | ch05.cpp | 片元着色器 6 种输入（自动轮播） |
| 06 | ch06.cpp | Scissor/深度/混合三态演示 |
| 07 | ch07.cpp | 第一个三角形（T 第二三角形、H 色调） |
| 08 | ch08.cpp | smooth/flat 插值（F 切换） |
| 09 | ch09.cpp | 全部图元类型 + 重启索引（1-8 切换） |
| 10 | ch10.cpp | 3×3 视口 + Scissor（S 开关） |
| 11 | ch11.cpp | 面剔除（C 剔除面、W 绕序） |
| 12 | ch12.cpp | T·R·S 变换（方向键/+/-/Q/E） |
| 13 | ch13.cpp | 轨道相机 + fov/near（方向键、F、N） |
| 14 | ch14.cpp | 深度函数/掩码/多边形偏移（D/M/O） |
| 15 | ch15.cpp | 混合因子/方程/排序（B/R/D） |
| 16 | ch16.cpp | 模板描边（O 开关、[ ] 缩放） |
| 17 | ch17.cpp | 纹理 wrap/filter/翻转（W/F/+/Y） |
| 18 | ch18.cpp | Mipmap 与 bias（F 过滤、+/− 偏移） |
| 19 | ch19.cpp | 纹理数组（+/− 层偏移、A 轮播） |
| 20 | ch20.cpp | 天空盒 + 环境反射（O 反射强度） |
| 21 | ch21.cpp | Phong 光照（A/D/S/F 调参） |
| 22 | ch22.cpp | 光照贴图（S/E 强度） |
| 23 | ch23.cpp | 法线贴图（S 强度） |
| 24 | ch24.cpp | 雾效（T 类型、+/− 密度） |
| 25 | ch25.cpp | 实例化渲染（+/− 数量、W 波浪） |
| 26 | ch26.cpp | UBO 共享（H 色相、I 强度） |
| 27 | ch27.cpp | glMapBufferRange 波浪（M 切换、+/− 频率） |
| 28 | ch28.cpp | Transform Feedback 粒子（G 重力、+/− 数量） |
| 29 | ch29.cpp | FBO 附件可视化：颜色/深度纹理/模板绿洞（1/2/3 切换）|
| 30 | ch30.cpp | 后处理卷积（1-7 核、+/− 步长、M 混合） |
| 31 | ch31.cpp | MSAA（S 切采样数，W 线框） |
| 32 | ch32.cpp | MRT 双输出（V 切换查看） |

## 与 Android 版的对应关系

| Android 端 | 本目录 |
|---|---|
| `EglCore`（EGLDisplay/Config/Context） | `wglwin.cpp`（ChoosePixelFormat / wglCreateContext） |
| `GLThread`（Choreographer 帧循环） | `main.cpp` + `wglwin.cpp` 的消息泵 while 循环 |
| `RenderSurface`（Surface 事件） | Win32 消息（WM_SIZE / WM_CLOSE） |
| `ShaderProgram`（编译/链接/查错） | `glutil.h::makeProgram` |
| `Mesh`（VAO/VBO/EBO 封装） | `glutil.h::Mesh / makeMesh` |
| `GLES30.glXxx` | `glfuncs.h` 运行时加载的同名函数 |

## 环境要求

- Windows 7+，任何 ~2010 年后的独立/集成显卡驱动（需要 GL 3.0+ 兼容上下文）；
- 远程桌面（RDP）环境下 GL 可能降级为 1.1 软件实现，程序会提示并退出——请在物理控制台会话运行。
