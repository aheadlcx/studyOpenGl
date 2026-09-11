# 38 · 阴影贴图 Shadow Mapping

> 对应平台：Android + win32（ES3.0 核心）· 难度：进阶补充 · 前置：第 14/21/29 章

## 你将搞懂

- 阴影贴图的完整两 pass 流程
- 深度纹理（第 29 章已铺垫）在阴影里的角色
- Shadow Acne 与 bias
- PCF 软化阴影边缘

## 先讲人话

一句话：**"从光源的视角再渲染一遍场景，把深度存下来；主渲染时每个片元问一句——'在光源眼里，我前面有没有别人挡着？'"**

> **Android 类比**：逆光拍照时的剪影——判断"你和太阳之间有没有东西"，就是比较"你到太阳的距离"与"遮挡物到太阳的距离"。

## 原理图解

```text
 Pass1 光源视角                    Pass2 相机视角
      ☀                                👁
       ╲                                ╲
        ╲  把深度写进 depthTex            ╲  片元 P：
         ╲                                ║ 计算它在光源空间的深度 d
          ▼                               ║ 采样 depthTex 得"最近遮挡深度" d*
      depthTex                            比较：d > d* + bias → 在阴影里！
```

## 核心代码

**Pass1：光源视角渲染深度**（第 29 章的深度纹理 FBO 直接复用）

```java
// 光源位置当"眼睛"，注意 near/far 要罩住整个场景
Mat4 lightProj = ..., lightView = ...;
Matrix.setLookAtM(lightView, 0, lightX, lightY, lightZ, 0, 0, 0, 0, 1, 0);
float[] lightMVP = new float[16];                       // 保存下来给 Pass2 用
matMul(lightMVP, lightProj, lightView);

glBindFramebuffer(GL_FRAMEBUFFER, shadowFbo);
glViewport(0, 0, SHADOW_SIZE, SHADOW_SIZE);
glClear(GL_DEPTH_BUFFER_BIT);
glCullFace(GL_FRONT);              // 减轻阴影粉刺：先剔正面
drawSceneWithProgram(depthOnlyProg);                     // 只输出深度，无颜色
```

**Pass2：主渲染，片元里比较**

```glsl
// 顶点着色器：把片元变换到"光源裁剪空间"，传给 FS
out vec4 v_shadowCoord;
v_shadowCoord = u_lightMVP * u_model * vec4(a_pos, 1.0);
// 纹理采样需要 [0,1]，矩阵里预乘偏移：
// biasMatrix = 0.5,0,0,0.5 / 0,0.5,0,0.5 / 0,0,0.5,0 / 0,0,0,1
v_shadowCoord = u_biasMatrix * v_shadowCoord;
```

```glsl
// 片元着色器
float shadow = 0.0;
float bias = max(0.05 * (1.0 - dot(N, L)), 0.005);   // 斜面角度越大 bias 越大
vec3 proj = v_shadowCoord.xyz / v_shadowCoord.w;      // 透视除法！
if (proj.z > 1.0) shadow = 0.0;                       // 光锥外不受阴影
else {
    float nearest = texture(u_shadowMap, proj.xy).r;  // 光源视角最近深度
    shadow = (proj.z - bias > nearest) ? 1.0 : 0.0;   // 被挡 = 阴影
}
vec3 c = lighting * (1.0 - shadow * 0.6);             // 阴影处压暗
```

**PCF（百分比渐近过滤）**：把一次比较换成 3×3 共 9 次采样平均，硬边变成柔和渐变：

```glsl
for (int x = -1; x <= 1; x++)
for (int y = -1; y <= 1; y++)
    shadow += step(nearest(x,y) + bias, proj.z) * (1.0/9.0);
```

## 常见坑

| 症状 | 原因 | 解法 |
|---|---|---|
| 表面全是黑色条纹（Shadow Acne） | 自身深度自比较的精度误差 | bias + 光源 pass 剔正面 |
| 阴影和物体之间有缝隙（Peter Panning） | bias 太大 | 调小 bias，配合剔正面 |
| 阴影边缘闪烁 | 光源矩阵/深度精度 | 光源 near/far 收紧，阴影图 1024+ |
| 光锥外出现大片阴影 | proj.z 未判断 | `proj.z>1` 时不计算阴影 |

## 自测

1. Pass1 为什么用"光源当眼睛"渲染？颜色输出需要吗？
2. `bias` 解决什么问题？为什么朝光角度越大 bias 要越大？
3. PCF 的原理是什么？

<details><summary>查看答案</summary>
1. 需要记录"光源能看到的位置中，每个方向最近的遮挡深度"——这就是 depthTex；颜色输出不需要（可丢弃光栅化输出）。
2. 深度存储有精度误差，斜着照向表面时误差被放大，导致表面"自己挡自己"产生条纹；与法线夹角越大误差越大，所以 bias 随 dot(N,L) 减小而增大。
3. 把单次深度比较扩展为邻域多次比较求平均——比较结果 0/1 平均后得到 0~1 的半影，硬边变软。
</details>

◀ 返回 [docs/README.md](README.md)
