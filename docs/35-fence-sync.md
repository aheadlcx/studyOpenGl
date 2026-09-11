# 35 · Fence 同步：CPU 与 GPU 的异步协作

> 对应平台：Android + win32（ES3.0 核心）· 难度：进阶补充

## 你将搞懂

- GL 命令是异步的：`glReadPixels` 为什么会"卡"
- Fence Sync：给 GPU 命令流插一面旗子
- CPU 何时该等、何时可以干别的

## 先讲人话

你的 `glDraw*`/`glReadPixels` 只是**把命令排进队列**，GPU 在后面慢慢消化。所以：

```text
 frame N:   提交 draw A → draw B → readPixels(读 A 画的结果)
                                  └─▶ A/B 还在 GPU 队列里没执行！
                                  CPU 只能停下来等 GPU 追上 → 卡顿
```

**Fence Sync** 就是在命令流里**插一面旗子**："GPU 执行到这里时把旗子升起"。CPU 之后可以：
- `glClientWaitSync`：阻塞等旗子（必要时的正确等待方式）；
- `glGetSynciv`：轮询旗子状态（不阻塞，等的时候先干别的，比如准备下一帧资源）。

> **Android 类比**：`Handler.post` 是异步的；fence 相当于往消息队列里插一个"执行到我这时的回调"，CPU 可以选择同步 `get()` 等它，或轮询。

## 核心代码

```java
// 提交命令流，并在末尾插旗
GLES30.glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, buf);   // 例：截图读回
long fence = GLES30.glFenceSync(GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);

// 稍后（比如下一帧）再等结果——而不是立刻阻塞
int wait = GLES30.glClientWaitSync(fence, GLES30.GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000_000);
if (wait == GLES30.GL_ALREADY_SIGNALED || wait == GLES30.GL_CONDITION_SATISFIED) {
    // GPU 已完成，buf 里的数据有效，可以安全处理/保存
}
GLES30.glDeleteSync(fence);
```

| 返回值 | 含义 |
|---|---|
| GL_ALREADY_SIGNALED | 旗子早已升起（GPU 早执行完了） |
| GL_CONDITION_SATISFIED | 这次等待期间升起来了 |
| GL_TIMEOUT_EXPIRED | 超时还没完成（继续等或放弃） |

## 典型应用

| 场景 | 套路 |
|---|---|
| 截图/录像读取 | `glReadPixels` 到 PBO → 插 fence → 下一帧再取数据（第 33/34 章的异步思想闭环） |
| Transform Feedback 结果统计 | TF 绘制后插 fence，稍后安全 `glGetBufferSubData` |
| 双/三缓冲 PBO 环 | 每个 PBO 配一个 fence，只有"旗子已升起"的 PBO 才复用 |

## 常见坑

- **提交后立刻 `glClientWaitSync`（timeout 巨大）**：等于强同步，帧率直接掉——能晚等就晚等（下下帧再取）；
- **忘传 `GL_SYNC_FLUSH_COMMANDS_BIT`**：命令还留在 CPU 队列没推给 GPU，等待可能永远超时；
- **fence 不删**：`glDeleteSync` 泄漏同步对象；
- **以为 fence 能同步两个 Context**：它是单命令流的；跨 Context 用 `eglWaitSync/GL_SYNC` 或 EGLImage。

## 自测

1. `glReadPixels` 之后立刻处理数据，为什么会卡？
2. `GL_ALREADY_SIGNALED` 说明什么？
3. 为什么"截图"推荐用"PBO + fence + 下帧取"而不是当帧直接读？

<details><summary>查看答案</summary>
1. 读取会强制 CPU 等 GPU 把队列里所有绘制命令执行完（流水线被清空），打破 CPU/GPU 并行。
2. 在你调用 glClientWaitSync 之前 GPU 就已经执行到 fence 位置了——这次等待零耗时。
3. 当帧读取把渲染流水线清空（同步点）；PBO 方案让读取目标也是异步的，fence 提示"就绪"后下一帧再取，CPU/GPU 重叠工作。
</details>

◀ 返回 [docs/README.md](README.md)
