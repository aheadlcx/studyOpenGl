# 22 · 材质与光照贴图

> 对应 App 第 22 项 · 难度：光照与材质

## 你将搞懂

- 材质 = GLSL struct：一组系数 + 一组贴图
- 漫反射/镜面/自发光三种光照贴图的意义
- 多纹理单元绑定的完整流程
- sampler uniform 的经典坑

## 先讲人话

第 21 章的材质是"一组数字"（整个物体统一反光）。把数字升级为**贴图**，就能逐纹素控制：

| 贴图 | 控制 | 直觉 |
|---|---|---|
| diffuse map | 基础颜色 | "这里画的是什么"（砖是砖色、缝是灰色） |
| specular map | 逐点反光强度 | "哪里抛光了哪里粗糙"（砖面反光、灰浆不反光） |
| emission map | 自发光 | "哪里自己在亮"（霓虹灯、窗户） |

> **Android 类比**：diffuse=照片本体，specular=一张灰度"反光蒙版"，emission=一张"夜光贴片"——三者叠加才是完整材质。

## GLSL 材质结构

```glsl
struct Material {
    sampler2D diffuse;     // 单元 0
    sampler2D specular;    // 单元 1
    sampler2D emission;    // 单元 2
    float shininess;
};
uniform Material u_material;

// 片元着色器里：
vec3 diffuseTex = texture(u_material.diffuse, v_uv).rgb;
float specMask  = texture(u_material.specular, v_uv).r;   // 灰度图取 R
vec3 emission   = texture(u_material.emission, v_uv).rgb;
vec3 color = ambient + diffuse + specular + emission * u_emisStrength;
```

## 多纹理单元绑定流程（必背）

```java
// 1. 各自激活单元并绑纹理
GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mDiffuseTex);
GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mSpecularTex);
GLES30.glActiveTexture(GLES30.GL_TEXTURE2);
GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mEmissionTex);

// 2. 告诉 sampler 各读哪个单元（名字带 struct 前缀）
mProgram.set("u_material.diffuse", 0);
mProgram.set("u_material.specular", 1);
mProgram.set("u_material.emission", 2);
```

单元数量查询 `GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS`（ES3 下限 32）。**用完记得 `glActiveTexture(GL_TEXTURE0)` 切回默认**。

## 在 App 里怎么玩（第 22 项）

- 砖墙立方体：砖面有镜面高光、灰浆缝没有（specular 贴图在起作用）；
- 横向亮条随**自发光强度**亮起（emission 贴图，不受光照影响）；
- **shininess** 滑条：高光斑大小；
- **UV 缩放**：贴图平铺密度——注意三张贴图同步缩放。

## 常见坑

- **忘 glUniform1i 绑单元**：三个 sampler 全默认读单元 0，三张图采成同一张。
- **切换单元后不切回 GL_TEXTURE0**：下一帧绑定错乱（隐蔽 bug）。
- **emission 叠加在光照之后**：自发光应独立于光照直接加上（它"自己在亮"）。
- **贴图颜色空间**：diffuse 是 sRGB、specular/emission 是数据——严格管线要区分（简单场景可忽略）。

## 原理图解与代码逐步拆解

```text
 三张贴图各自"接管"一个通道：

  diffuse 贴图   ──▶ 替换物体基色        （砖=红 灰浆=灰）
  specular 贴图  ──▶ 乘到高光上(灰度蒙版)  （砖面白=反光 缝=黑=不反光）
  emission 贴图  ──▶ 直接加到最终色       （横条亮起=自发光）

 绑定流程（三个抽屉）：
   ActiveTexture(0) Bind(diffuse) ─┐
   ActiveTexture(1) Bind(specular)─┼─▶ glUniform1i 各自指向抽屉号
   ActiveTexture(2) Bind(emission)─┘
```

逐步拆解：

1. 程序化生成三张贴图：砖墙 Canvas、灰度噪点（specular 蒙版）、条纹（emission）；
2. 三个纹理分别绑到单元 0/1/2（`glActiveTexture` + `glBindTexture`）；
3. FS 里 `struct Material { sampler2D diffuse, specular, emission; float shininess; }` 组织参数；
4. 高光计算乘上 `specMask`：灰浆缝是黑色（0）→ 永远无高光；
5. 自发光直接加到最终色——它与光照无关（"自己在亮"）。

## 自测

1. specular map 采样后通常取哪个分量？为什么灰度图就够？
2. 自发光贴图的光要不要乘 diffuse 的 dot(N,L)？
3. `uniform Material` 里的 sampler 在 GPU 侧占什么？

<details><summary>查看答案</summary>
1. 取 R（灰度图 RGB 相同）；反光强度只有一维，灰度足够表达。
2. 不要。自发光是物体自己发出的光，与外部光源方向无关，直接加到最终色。
3. 只是纹理单元号（int），真正的纹理数据通过绑定关系间接引用。
</details>

➡️ 下一章：[23 · 法线贴图与 TBN](23-normal-mapping.md)
