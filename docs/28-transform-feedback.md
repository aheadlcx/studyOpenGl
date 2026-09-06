# 28 · Transform Feedback：GPU 上的粒子

> 对应 App 第 28 项 · 难度：高级·GPU 管线

## 你将搞懂

- Transform Feedback（XFB）把 VS 输出捕获回缓冲的原理
- 四要素：varyings 注册、XFB 对象、光栅化丢弃、开始/结束
- 乒乓双缓冲为什么是必须的
- 用它实现纯 GPU 粒子物理

## 先讲人话

普通管线里 VS 的输出送去光栅化就"消失"了。Transform Feedback 把 VS 输出**捕获回 Buffer Object**——粒子位置/速度从不回读 CPU，物理全部在 GPU 上演化，没有回读同步停顿。

> **Android 类比**：普通绘制是"流水线产出的成品直接装箱出货"；XFB 是"产出的半成品重新放回原料传送带，下一帧继续加工"——GPU 自循环。

## 四要素

```java
// ① 链接前注册要捕获的 varyings（必须在 glLinkProgram 之前！）
GLES30.glTransformFeedbackVaryings(prog,
        new String[]{"v_posLife", "v_velSeed"},   // VS 里的 out
        GLES30.GL_INTERLEAVED_ATTRIBS);           // 交错写入（或 SEPARATE）

// ② XFB 对象 + 写入目标
GLES30.glGenTransformFeedbacks(1, xfb, 0);
GLES30.glBindTransformFeedback(GLES30.GL_TRANSFORM_FEEDBACK, xfb);
GLES30.glBindBufferBase(GLES30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, dstVBO);

// ③ 丢弃光栅化：只跑 VS，不生成片元
GLES30.glEnable(GLES30.GL_RASTERIZER_DISCARD);

// ④ 捕获开始/结束
GLES30.glBeginTransformFeedback(GLES30.GL_POINTS);
GLES30.glDrawArrays(GLES30.GL_POINTS, 0, particleCount);
GLES30.glEndTransformFeedback();
```

**更新着色器示例**（物理全在 VS 里）：

```glsl
in vec4 a_posLife;      // xyz=位置 w=寿命
in vec4 a_velSeed;      // xyz=速度 w=随机种子
out vec4 v_posLife;     // 会被 XFB 捕获
out vec4 v_velSeed;

void main() {
    vec3 vel = a_velSeed.xyz * u_speed;
    vel.y -= u_gravity * u_dt * 4.0;      // 重力积分
    vec3 pos = a_posLife.xyz + vel * u_dt;
    // ……盒子边界反弹……
    v_posLife = vec4(pos, a_posLife.w - u_dt);
    v_velSeed = vec4(vel / u_speed, a_velSeed.w);
}
```

## 乒乓双缓冲（必须！）

同一帧"读 A 写 A"在 GL 里是未定义行为（读写并发），所以要两份 VBO：

```text
偶数帧：VAO(A) 读 → XFB 写 B
奇数帧：VAO(B) 读 → XFB 写 A
渲染永远画"最新写入"的那份
```

## 渲染 pass（点精灵）

```glsl
// VS
gl_PointSize = u_pointSize;
// FS：gl_PointCoord 是点精灵内部 0~1 坐标（左上原点）
vec4 tex = texture(u_sprite, gl_PointCoord);   // 软圆贴图
fragColor = vec4(color, tex.a * life);         // 按寿命淡出
```

## 在 App 里怎么玩（第 28 项）

- 2000 个粒子在盒子里弹跳、坠落、重生，全部 GPU 自循环，CPU 每帧只传 3 个 uniform；
- **粒子数** 拉到 6000：仍然满帧（对比 CPU 粒子的掉帧）；
- **重力** 开关：失重漂浮 vs 雪崩下落；
- **点大小**：拉大后看软圆贴图（gl_PointCoord 采样的径向渐变）。

## 常见坑

- **glTransformFeedbackVaryings 放在 link 之后**：不生效，捕获不到（最常见错误）。
- **同一帧读写同一 VBO**：未定义行为，必须乒乓。
- **忘开 RASTERIZER_DISCARD**：更新 pass 画出一屏乱点（VS 输出没写 gl_Position 时的未定义值）。
- **XFB 绑定的 buffer 同时又被顶点属性引用**：同上，读写分离。

## 自测

1. `glTransformFeedbackVaryings` 必须在哪个调用之前？为什么？
2. `GL_INTERLEAVED_ATTRIBS` 和 `GL_SEPARATE_ATTRIBS` 的区别？
3. `gl_PointCoord` 的原点在哪？范围？

<details><summary>查看答案</summary>
1. glLinkProgram 之前——链接时驱动要按捕获列表重排 VS 输出布局，链接后再注册无效。
2. INTERLEAVED：所有 varying 交错写入同一个缓冲（本例 posLife+velSeed 挨着）；SEPARATE：每个 varying 写到 XFB 的不同绑定槽。
3. 原点在左上角，范围 [0,1]²——与纹理图像方向一致，与 GL 常规 uv 相反。
</details>

➡️ 下一章：[29 · 帧缓冲与 RTT](29-fbo-rtt.md)
