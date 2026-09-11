# 36 · PBO：纹理上传与读取的异步化

> 对应平台：Android + win32（ES3.0 核心）· 难度：进阶补充 · 前置：第 35 章 Fence

## 你将搞懂

- PBO（Pixel Buffer Object）是什么、和普通 VBO 的区别
- 纹理上传如何异步：CPU 写 PBO → GPU 从 PBO 拷到纹理
- 读回（截图/录像）如何异步：GPU 写 PBO → CPU 延迟取
- 双/三 PBO 环的套路

## 先讲人话

`glTexImage2D`/`glReadPixels` 直接操作 CPU 内存时，驱动要做"CPU↔GPU 之间的搬运"，而且往往**同步阻塞**（第 35 章的问题）。PBO 是一块 **GPU 可见的缓冲**，把搬运拆成两段异步：

```text
 上传（UI 图片→纹理）：
   CPU: glMapBufferRange(PBO) 写像素（随时写，不打扰 GPU）
   GL : glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pbo)
        glTexImage2D(..., 0)   ← 源指针传 0：从"当前绑定的 PBO"取数据

 下载（截图→内存）：
   GL : glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo)
        glReadPixels(..., 0)   ← 目标传 0：结果写进 PBO（异步！）
   CPU: 之后用 fence 确认完成，再 map 读取
```

> **Android 类比**：直接 read/write 是"当场等外卖"；PBO 是"取餐柜"——你把单子放进柜子，好了再来拿，两边都不用等对方。

## 核心代码：双 PBO 截图环

```java
int[] pbos = new int[2];
GLES30.glGenBuffers(2, pbos, 0);
long fence = 0;
int cur = 0;
int size = w * h * 4;

// 初始化：两个 PBO 各分配 size
for (int p : pbos) {
    glBindBuffer(GL_PIXEL_PACK_BUFFER, p);
    glBufferData(GL_PIXEL_PACK_BUFFER, size, null, GL_STREAM_READ);
}

// 每帧截图：
// 1) 若上一个 PBO 的 fence 已就绪 → 取出数据（隔了一帧，几乎不等待）
if (fence != 0 && 已就绪(fence)) {
    glBindBuffer(GL_PIXEL_PACK_BUFFER, pbos[prev]);
    ByteBuffer data = glMapBufferRange(GL_PIXEL_PACK_BUFFER, 0, size, GL_MAP_READ_BIT);
    saveBitmap(data);                        // 处理上一帧的像素
    glUnmapBuffer(GL_PIXEL_PACK_BUFFER);
}

// 2) 本帧读进另一个 PBO（异步，立即返回）
glBindBuffer(GL_PIXEL_PACK_BUFFER, pbos[cur]);
glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, 0);
fence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
prev = cur;
cur = 1 - cur;
```

上传纹理同理：把 `glTexImage2D` 的像素指针换成 `0` 并绑定 `GL_PIXEL_UNPACK_BUFFER`，CPU 侧用 `glMapBufferRange` 写 PBO。

## 双缓冲/三缓冲环

```text
 PBO0: GPU 正在读（上一帧的下载）      ← 配 fence
 PBO1: CPU 刚写入（本帧的上传/下载）
 轮转使用 → CPU 写 PBO[k] 的同时 GPU 读 PBO[k-1]，互不等待
```

## 常见坑

- **map 一个"GPU 正在用"的 PBO**：退化为同步等待——必须配 fence（第 35 章）；
- **PBO 太少**：单 PBO 每帧读写必然互相阻塞，至少双缓冲；
- **下载格式选 GL_RGBA + UNSIGNED_BYTE**：与屏幕格式一致可避免驱动格式转换；
- **对半透明窗口截图 alpha 不为 255**：帧缓冲 alpha 通道内容如此，导出前手动置 255。

## 自测

1. PBO 上传纹理时，`glTexImage2D` 的最后一个参数（像素指针）传什么？
2. 为什么截图环要"隔一帧再取数据"？
3. PBO 和 VBO 的区别？

<details><summary>查看答案</summary>
1. 传 0（null）——表示数据源是"当前绑定到 GL_PIXEL_UNPACK_BUFFER 的 PBO"，偏移由 size 前的参数语义隐含为从 0 开始。
2. GPU 写 PBO 是异步的；隔一帧（配合 fence）时几乎必然已完成，CPU 读取零等待，同时 GPU 不被拖慢。
3. 都是一块显存缓冲；区别在挂点语义：PIXEL_UNPACK/PACK 是像素传输命令的数据源/目标，ARRAY_BUFFER/ELEMENT_ARRAY_BUFFER 是顶点/索引数据。
</details>

◀ 返回 [docs/README.md](README.md)
