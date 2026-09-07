# 20 · 立方体贴图与天空盒

> 对应 App 第 20 项 · 难度：纹理

## 你将搞懂

- GL_TEXTURE_CUBE_MAP 的 6 个面与方向采样
- 天空盒三板斧：去平移、z=w、LEQUAL
- `GL_TEXTURE_CUBE_MAP_SEAMLESS`
- 环境反射：R=reflect(I, N) 采 cubemap

## 先讲人话

立方体贴图把 6 张等尺寸正方形装进一个对象（+X −X +Y −Y +Z −Z）。`samplerCube` **不用 uv**——用一个 3D 方向向量采样：GL 按向量的主轴选面，其余两轴做面内 uv（方向 = 从立方体中心射出）。

> **Android 类比**：站在房间中央，往任意方向看，看到的都是墙面/地面/天花板上的画——你的"视线方向"决定了看到哪面。

## 核心代码

```java
// 上传：6 个面依次 glTexImage2D
GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, id);
for (int i = 0; i < 6; i++) {
    GLUtils.texImage2D(GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, faceBmp[i], 0);
}
// wrap 必须 S/T/R 三个方向都 CLAMP_TO_EDGE（跨面接缝才正确）
GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE);
GLES30.glEnable(0x884F);   // GL_TEXTURE_CUBE_MAP_SEAMLESS：消除 mip 跨面接缝
```

```glsl
// 天空盒 VS 三板斧
vec4 p = u_proj * u_viewRot * vec4(a_pos, 1.0);  // u_viewRot：视图矩阵去掉平移
gl_Position = p.xyww;                            // z=w → 深度恒 1.0（最远）
// 片元
fragColor = texture(u_sky, v_dir);               // 方向采样
```

## 天空盒三板斧（原理）

1. **视图矩阵去掉平移**（只留旋转）：天空永远"无限远"，不跟着相机走；
2. **`gl_Position = pos.xyww`**：让 z 恒等于 w → 深度恒 1.0（远平面）；
3. **`glDepthFunc(GL_LEQUAL)`**：默认 GL_LESS 下深度 1.0 不小于已清空的 1.0 会被剔掉；LEQUAL 允许"等于"通过。

## 环境反射（同一张 cubemap 的第二个用途）

```glsl
vec3 I = normalize(worldPos - cameraPos);   // 入射方向
vec3 R = reflect(I, N);                     // 反射方向
vec3 envColor = texture(u_sky, R).rgb;      // 查环境色
fragColor = mix(baseColor, envColor, u_reflect);   // 金属感就出来了
```

PBR 的环境光照（IBL）也是在此基础上扩展。

## 在 App 里怎么玩（第 20 项）

- 程序化星空 cubemap 包裹全场，**手指拖动**环视；
- **天空旋转速度**：整个星空绕你转（改的是采样方向，不是相机）；
- **反射强度** 滑条：中央立方体从哑光塑料渐变成镜面金属；
- **fov** 滑条：广角下天空盒畸变更明显。

## 常见坑

- **View 忘了去平移**：天空跟着相机平移，走两步就"走出天空"。
- **忘改 `glDepthFunc(LEQUAL)`**：天空被默认 GL_LESS 剔掉，只剩物体。
- **wrap 用 REPEAT**：跨面采样把对面内容绕进来，接缝处花掉。
- **忘开 SEAMLESS**：mipmap 跨面采样出现明显接缝线。

## 原理图解与代码逐步拆解

```text
 方向采样：从立方体中心射出的向量决定看哪面

        +Y
         │    方向向量 ──▶ 主轴选面
         │      ↖        例 (0,1,0.2) → +Y 面
 -X ─── +O ─── +X        面内 uv 由另两轴决定
         │
        -Z        六面上传顺序: +X,-X,+Y,-Y,+Z,-Z

 天空盒三板斧：
 1) u_viewRot = View 去掉平移列 → 天空"无限远"
 2) gl_Position = pos.xyww     → z/w = 1.0 永远最远
 3) glDepthFunc(GL_LEQUAL)     → 深度==1.0 也能通过
```

逐步拆解：

1. 星空贴图 6 张程序生成，逐面 `glTexImage2D(POSITIVE_X + f, ...)` 上传；
2. skybox 就是一个放大 60 倍的立方体 mesh，只有位置属性；
3. 反射：FS 里 `R = reflect(入射方向, 法线)`，`texture(u_sky, R)` 查环境色——中央立方体的"金属感"来源；
4. 拖反射滑条：`mix(漫反射色, 环境色, u_reflect)` 从塑料渐变到镜面。

## 自测

1. `samplerCube` 的采样参数是 vec2 还是 vec3？语义是什么？
2. `gl_Position = pos.xyww` 之后深度值是多少？为什么？
3. 环境反射的采样方向 R 怎么算？

<details><summary>查看答案</summary>
1. vec3(u,v,dir 的第三分量)——整体是 3D 方向向量，GL 按主轴选面、其余两分量做面内 uv。
2. 深度 = z/w = 1.0，恰好是远平面，配合 LEQUAL 永远只在没有更近物体的地方显示。
3. R = reflect(I, N)，I 是"片元指向相机"的单位向量取反后的入射方向，N 是法线。
</details>

➡️ 下一章：[21 · Phong 光照](21-phong-lighting.md)
