# 30 · 后处理卷积

> 对应 App 第 30 项 · 难度：高级·帧缓冲

## 你将搞懂

- 后处理的管线骨架：场景→FBO→全屏 pass
- 3×3 卷积核：锐化/盒模糊/高斯/边缘/浮雕
- 采样步长（一个纹素）的概念
- 可分离模糊的性能优化方向

## 先讲人话

后处理 = 两 pass：Pass1 把场景画进 FBO 纹理；Pass2 用一个**全屏四边形**把纹理"贴"回屏幕，采样时逐像素做**卷积**（对周围 9 个采样点加权求和）。所有滤镜——景深、Bloom、FXAA、调色——都是这个骨架。

> **Android 类比**：`ColorMatrixColorFilter` 只能做逐像素线性变换；卷积还要"看邻居"（邻域操作），所以必须先有离屏纹理才能访问周围像素。

## 核心代码

**片元着色器（通用 3×3 卷积）**：

```glsl
uniform sampler2D u_scene;
uniform float u_offset;       // 一个"纹素"= 1.0/纹理宽
uniform float u_kernel[9];    // 卷积核权重
uniform float u_mix;          // 原图与效果混合

void main() {
    vec2 off = vec2(u_offset);
    vec3 sum =
        texture(u_scene, v_uv + off * vec2(-1.0, 1.0)).rgb * u_kernel[0] +
        texture(u_scene, v_uv + off * vec2( 0.0, 1.0)).rgb * u_kernel[1] +
        texture(u_scene, v_uv + off * vec2( 1.0, 1.0)).rgb * u_kernel[2] +
        texture(u_scene, v_uv + off * vec2(-1.0, 0.0)).rgb * u_kernel[3] +
        texture(u_scene, v_uv).rgb                          * u_kernel[4] +
        texture(u_scene, v_uv + off * vec2( 1.0, 0.0)).rgb * u_kernel[5] +
        texture(u_scene, v_uv + off * vec2(-1.0,-1.0)).rgb * u_kernel[6] +
        texture(u_scene, v_uv + off * vec2( 0.0,-1.0)).rgb * u_kernel[7] +
        texture(u_scene, v_uv + off * vec2( 1.0,-1.0)).rgb * u_kernel[8];
    fragColor = vec4(mix(texture(u_scene, v_uv).rgb, sum, u_mix), 1.0);
}
```

**Java 侧切核**（`glUniform1fv` 一次传 9 个 float）：

```java
float[] sharpen = {0, -1, 0, -1, 5, -1, 0, -1, 0};
mPostProgram.setFloatArray("u_kernel", KERNELS[kernelIndex]);
mPostProgram.set("u_offset", step / (float) RT_SIZE);   // 一个纹素
```

## 经典卷积核

| 效果 | 核 | 直觉 |
|---|---|---|
| 原图 | 中心 1 其余 0 | 恒等 |
| 锐化 | 中心 5 四邻 -1 | 突出与邻域的差异 |
| 盒模糊 | 全 1/9 | 邻域平均 |
| 高斯 | 1,2,1,2,4,2,1,2,1 ÷16 | 按距离加权，更自然 |
| 边缘检测 | 全 1 中心 -8 | 平坦处≈0，边缘残留 |
| 浮雕 | -2,-1,0,-1,1,1,0,1,2 | 方向性明暗错位 |

## 在 App 里怎么玩（第 30 项）

- 场景（旋转彩色立方体）画进 FBO，全屏 pass 做卷积；
- **卷积核** 下拉 6 种：锐化看轮廓加重；边缘检测出"线稿"；浮雕出金属刻痕感；
- **采样步长** 0.5→6：模糊半径放大（超出 1 纹素=跨像素采样）；
- **混合强度**：原图与效果 0~1 渐变（完美的过渡动画素材）。

## 常见坑

- **RTT 纹理 wrap 用 REPEAT**：屏幕边缘采样把对面像素"绕"进来 → 必须 CLAMP_TO_EDGE。
- **多效果串联没 ping-pong**：读写的同一纹理未定义 → 两个 FBO 轮流用。
- **offset 忘除以纹理宽**：步长 1 像素变成 1 NDC，整个画面错位。
- **一上来做全屏模糊**：可分离核（高斯拆横竖两个 1D pass）O(9)→O(6)，大半径差距更大。

## 自测

1. 为什么后处理必须先把场景渲染到 FBO？
2. 边缘检测核为什么能"找边缘"？
3. 高斯模糊为什么比盒模糊更自然？

<details><summary>查看答案</summary>
1. 卷积需要读"周围像素"，而绘制过程中帧缓冲内容不可随机读取——只有整个场景画完后它才变成一张可采样的纹理。
2. 平坦区域 9 个采样颜色几乎相同，加权和≈0（黑）；边缘两侧颜色差异大，加权和显著偏离 0——残留的就是轮廓。
3. 盒模糊对所有邻居等权，产生方块感振铃；高斯按距离加权（中心 4 角 1 边 2），过渡符合人眼感知。
</details>

➡️ 下一章：[31 · 多重采样 MSAA](31-msaa.md)
