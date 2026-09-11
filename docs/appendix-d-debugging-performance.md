# 附录 D · 调试方法与性能优化清单

> GL 是"提交命令后异步执行"的，出错往往不崩溃而是黑屏/花屏——调试方法本身就是必修课。

## 一、黑屏排查决策树（按顺序问）

```text
 屏幕全黑？
 ├─ clearColor 设置的颜色能对上吗？ → glClearColor 是否被调用、alpha 是否为 1
 ├─ glClear 被调用了吗？            → 每帧开头必须清（残留深度会吞掉物体）
 ├─ 深度测试开了吗？                → GL_DEPTH_TEST 默认是【关】的！
 ├─ 程序链接成功了吗？              → 必查 GL_LINK_STATUS（看 logcat 的 shader log）
 ├─ MVP 是单位矩阵吗？             → 先用单位矩阵画，排除矩阵错误
 ├─ viewport 对吗？                → 尺寸为 0 或不在屏幕区域
 ├─ 顶点在视锥内吗？               → 先 ortho 画 2D 排除投影问题
 ├─ 遮挡了？                       → 关深度/剔除/混合再试（状态残留是惯犯）
 └─ GL 错误？                      → glGetError 逐调用插桩（见下）
```

## 二、glGetError 插桩

```java
// 发布版别用（有性能成本），调试期插在可疑调用后：
public static void checkError(String tag) {
    int e;
    while ((e = GLES30.glGetError()) != GLES30.GL_NO_ERROR) {
        Log.e(tag, "GL error 0x" + Integer.toHexString(e));
    }
}
// 0x0500 INVALID_ENUM  枚举值非法
// 0x0501 INVALID_VALUE 数值越界
// 0x0502 INVALID_OPERATION 状态不对（如没绑定纹理就采样配置）
// 0x0505 OUT_OF_MEMORY
```

## 三、调试工具

| 工具 | 用途 |
|---|---|
| `adb logcat -s ShaderProgram GLThread` | 本工程的编译错误/生命周期日志 |
| Android GPU Inspector (AGI) | 帧捕获：看每次 draw 的状态/纹理/耗时（强烈推荐） |
| GPU 厂商工具 | Snapdragon Profiler / Mali Graphics Debugger / RenderDoc(桌面) |
| `glGet*` 查询 | GL_MAX_TEXTURE_SIZE、MAX_SAMPLES 等能力上限 |

## 四、性能优化清单（按收益排序）

### CPU 侧
1. **减少 draw call**：实例化（第 25 章）、合并 mesh、重启索引（第 09 章）；
2. **每帧零分配**：顶点数组/uniform 缓冲预分配复用（GC 抖动=掉帧）；
3. **数据一次性进显存**：`glBufferData` 静态数据别每帧重传；
4. **动态数据用映射+INVALIDATE**（第 27 章）避免隐式同步。

### GPU 侧（Fill Rate 片元是手机的第一瓶颈）
5. **不透明物体从近到远画**：early-z 拦掉被遮挡片元（第 14 章）；
6. **开背面剔除**：省一半片元（第 11 章）；
7. **overdraw 控制**：UI/场景避免大面积半透明叠加；
8. **降低片元着色器成本**：能放 VS 的别放 FS；mediump 优先（除关键量）；
9. **少用 discard**：破坏 early-z（第 05 章）。

### 纹理与显存
10. **mipmap 全开**：缩小闪烁且缓存更友好（第 18 章）；
11. **ETC2 压缩纹理**：ES3 核心保证，显存降为 1/4~1/8（`glCompressedTexImage2D`）；
12. **纹理尺寸不超需**：一张 4096 贴图 = 64MB 显存。

### 帧节奏
13. **对齐 vsync**：Choreographer / eglSwapBuffers（第 06 章）；
14. **复杂效果降帧或降分辨率**：后处理可渲染到半分辨率 FBO 再放大（第 29 章）。

## 五、常见性能症状 → 对症

| 症状 | 大概率原因 | 先试 |
|---|---|---|
| 一动就卡、不动流畅 | CPU 瓶颈（draw call/每帧分配） | 减 draw call、查 GC |
| 整体稳定 30~40fps | GPU fill rate 瓶颈 | 降分辨率/overdraw/FS 成本 |
| 近处物体边缘闪 | z-fighting | 多边形偏移/调 near（第 14 章） |
| 远处纹理闪烁摩尔纹 | 没有 mipmap | 第 18 章 |
| 后台回来黑屏 | 上下文丢失未重建 | 附录 B 第三节 |

## 自测

1. `glGetError` 返回 `0x0502` 大概率是什么问题？
2. 为什么"不透明物体从近到远画"能提速？
3. 一个全屏 FS 里放了 `if (texture(...).a < 0.5) discard;`，会带来什么性能影响？

<details><summary>查看答案</summary>
1. GL_INVALID_OPERATION——状态非法，最常见的是"没绑定纹理就配置采样参数"或"当前没有 program/use"。
2. 先画近的物体占住深度缓冲，后面画的远物体片元在 early-z 阶段就被丢弃，不执行昂贵的片元着色器。
3. discard 使 early-z 失效：所有被它覆盖的片元都必须完整执行 FS 才知道要不要丢，且遮挡剔除失效——大范围使用会显著掉帧，应改用 alpha blend 或 alpha-to-coverage。
</details>

◀ 返回 [docs/README.md](README.md)
