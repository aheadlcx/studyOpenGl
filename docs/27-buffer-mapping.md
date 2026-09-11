# 27 · 缓冲映射：glMapBufferRange

> 对应 App 第 27 项 · 难度：高级·GPU 管线
> 对应 App 第 27 项 · 难度：高级·GPU 管线

```text
管线定位：【①数据】 ─ ②VS ─ ③装配 ─ ④裁剪 ─ ⑤光栅 ─ ⑥FS ─ ⑦测试
 ▲【】内为本章聚焦环节
```

## 你将搞懂

- 动态更新顶点数据的三种方式与各自代价
- glMapBufferRange 的标志位语义
- 孤儿化（orphan）与免同步更新
- 映射 vs glBufferSubData 的实测性能差异

## 先讲人话

每帧都要改的顶点数据（波浪面、布料、粒子轨迹）有三种更新姿势：

| 方式 | 机制 | 代价 |
|---|---|---|
| `glBufferData(新数据)` | 整块重新分配 | 大数据拷贝浪费 |
| `glBufferSubData` | 原地更新 | 可能被"正在读的 GPU"卡住（隐式同步） |
| `glMapBufferRange` | 映射显存成 CPU 指针直接写 | 配合标志位可完全免同步 |

> **Android 类比**：subData 像"改共享文档要等别人关掉"；映射+INVALIDATE 像"直接复制一份新文档改，旧文档留给还在看的人"。

## 核心代码

```java
// 每帧：把整个顶点缓冲标记作废并映射成可写内存
GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mPosVBO);
ByteBuffer mapped = (ByteBuffer) GLES30.glMapBufferRange(
        GLES30.GL_ARRAY_BUFFER,
        0, vertCount * 3 * 4,
        GLES30.GL_MAP_WRITE_BIT                     // 写意图
        | GLES30.GL_MAP_INVALIDATE_BUFFER_BIT);     // 旧数据全不要→驱动免同步
if (mapped != null) {
    FloatBuffer view = mapped.order(ByteOrder.nativeOrder()).asFloatBuffer();
    // ……CPU 算波浪，直接写进 view……
    GLES30.glUnmapBuffer(GLES30.GL_ARRAY_BUFFER);   // 修改回传驱动
}
```

## 标志位语义（ES3 核心）

| 标志 | 含义 |
|---|---|
| GL_MAP_WRITE_BIT / READ_BIT | 读写意图 |
| GL_MAP_INVALIDATE_RANGE_BIT | 声明该区间旧数据不要 → 免同步 |
| GL_MAP_INVALIDATE_BUFFER_BIT | 整个缓冲都不要（等效"孤儿化"但保留名字） |
| GL_MAP_FLUSH_EXPLICIT_BIT | 配合 `glFlushMappedBufferRange` 只回传改过的子区间 |
| GL_MAP_UNSYNCHRONIZED_BIT | 完全不等 GPU（风险自担） |

**配套建议**：创建缓冲时用 `GL_DYNAMIC_DRAW`，提示驱动放"对 CPU 写友好"的位置。

## 在 App 里怎么玩（第 27 项）

- 64×64 波浪海面，CPU 每帧重算全部顶点 Y 值；
- **更新方式** 在 `glMapBufferRange` / `glBufferSubData` 之间切换：盯住 FPS——
  - 映射+INVALIDATE：驱动跳过同步等待，帧率稳；
  - subData：隐式同步偶发卡顿（数据量越大越明显）；
- **网格密度** 拉到 128×128（4900 顶点）：两种方式的差距被放大；
- **波频/波速**：纯粹的视觉效果。

## 常见坑

- **映射期间对该 buffer 发 GL 命令**：未定义行为——映射后写完立刻 unmap。
- **unmap 返回 false**：数据丢失（驱动异常），要有降级路径。
- **映射时没绑定目标缓冲**：`glMapBufferRange` 作用于"当前绑定到 target 的缓冲"。
- **把 STATIC_DRAW 的缓冲每帧映射**：放错堆，性能反而差——常改的用 DYNAMIC_DRAW。

## 原理图解与代码逐步拆解

```text
 glBufferSubData（可能卡）          glMapBufferRange + INVALIDATE（不卡）

 GPU: 正在读旧块 ▓▓▓▓              GPU: 正在读旧块 ▓▓▓▓
 CPU: 想改同一块 ▓▓▓▓              CPU: 拿到新块 ░░░░ 直接写
      └── 等GPU读完…(卡顿)              └ 互不干扰！unmap 后交换

 INVALIDATE_BUFFER_BIT 的含义 = "旧数据全不要了"
 → 驱动直接给 CPU 一块新内存（孤儿化），GPU 继续用旧的
```

逐步拆解：

1. 顶点缓冲创建用 `GL_DYNAMIC_DRAW`（提示驱动：每帧改）；
2. 每帧 `glMapBufferRange(0, size, WRITE|INVALIDATE_BUFFER)` 拿到 CPU 指针；
3. 直接把 4900 个顶点的波浪高度写进这块内存（就是普通内存写入）；
4. `glUnmapBuffer` 通知驱动"写完了"——数据对 GPU 可见；
5. 按 M 切换 subData 模式对比 FPS：数据量越大差距越明显。

## 自测

1. `INVALIDATE_BUFFER_BIT` 为什么能免同步？
2. glBufferSubData 什么时候会卡？
3. 孤儿化（orphaning）指什么？

<details><summary>查看答案</summary>
1. 它向驱动声明"旧数据我完全不要"，驱动可以把 GPU 正在读的旧块留在原地，把映射指针指向一块新内存，无需等待 GPU 用完。
2. 该缓冲还有未完成的 GPU 读取命令时（上上帧还在用它），驱动必须等 GPU 读完才能原地改写。
3. 用 glBufferData 重新分配一块新显存替换旧块——旧块引用计数留给 GPU，新块归 CPU 写；效果等同 INVALIDATE。
</details>

➡️ 下一章：[28 · Transform Feedback](28-transform-feedback.md)
