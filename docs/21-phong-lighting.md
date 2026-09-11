# 21 · Phong 光照模型

> 对应 App 第 21 项 · 难度：光照与材质
> 对应 App 第 21 项 · 难度：光照与材质

```text
管线定位：①数据 ─ ②VS ─ ③装配 ─ ④裁剪 ─ ⑤光栅 ─ 【⑥FS】 ─ ⑦测试
 ▲【】内为本章聚焦环节
```

## 你将搞懂

- ADS 三分量：环境光 / 漫反射 / 镜面高光的公式与直觉
- 逐顶点（Gouraud）与逐片元（Phong）的区别
- 法线变换的正确姿势
- 点光源衰减

## 先讲人话

Phong 模型把"一个像素的亮度"拆成三份相加：

```text
最终色 = 环境光 + 漫反射 + 镜面高光

ambient  = 环境系数 × 光色                 —— 常数打底，避免全黑
diffuse  = max(dot(N, L), 0) × 光色        —— 面朝光源越正越亮（立体感）
specular = pow(max(dot(R, V), 0), shininess) × 光色  —— 高光点
```

三个向量（都在同一空间、都要 normalize）：

```text
N = 法线        L = 片元指向光源
V = 片元指向相机  R = reflect(-L, N) = 反射光方向
```

> **直觉**：漫反射像"哑光纸"（角度决定亮度）；镜面像"抛光金属"（只在特定角度亮，shininess 越大光斑越小越锐）。

## 核心代码（逐片元 Phong）

```glsl
// 片元着色器
vec3 N = normalize(v_normal);                  // 插值后的法线必须重新归一化！
vec3 L = normalize(u_lightPos - v_worldPos);   // 指向光源
vec3 V = normalize(u_viewPos - v_worldPos);    // 指向相机
vec3 R = reflect(-L, N);                       // 反射方向

vec3 ambient  = u_ambient * u_lightColor;
vec3 diffuse  = u_diffuse * max(dot(N, L), 0.0) * u_lightColor;
vec3 specular = u_specular * pow(max(dot(R, V), 0.0), u_shininess) * u_lightColor;

fragColor = vec4((ambient + diffuse + specular) * objectColor, 1.0);
```

**顶点着色器**负责把法线和位置带到世界空间：

```glsl
v_normal = mat3(u_model) * a_normal;              // 法线矩阵（见下）
v_worldPos = (u_model * vec4(a_pos, 1.0)).xyz;    // 世界坐标给 FS 用
```

## 法线矩阵：非等比缩放的坑

法线被非等比缩放后会指错方向，正确做法是传 **model 的逆转置 mat3**（`u_normalMatrix`）。只有旋转+等比缩放时可偷懒用 `mat3(u_model)`（本 App 的简化）。

## 逐顶点 vs 逐片元

| | Gouraud（VS 里算） | Phong（FS 里算） |
|---|---|---|
| 执行次数 | 3 次 | 每像素一次 |
| 高光质量 | 差（插值把高光糊掉） | 好 |
| 成本 | 极低 | 中（移动端标配可接受） |

## 光源衰减（可选第四通道）

```glsl
float dist = length(u_lightPos - v_worldPos);
float attenuation = 1.0 / (1.0 + 0.09*dist + 0.032*dist*dist);  // 二次衰减
diffuse *= attenuation;
```

## 在 App 里怎么玩（第 21 项）

- 橙色立方体 + 地面，光源小方块环绕飞行；
- **镜面强度** 拉到 0：纯漫反射；恢复后立方体棱边出现高光；
- **shininess** 8→128：高光从大片光斑收敛成锐利亮点；
- **环境光** 拉满：画面"洗白"失去立体感——真实项目 0.05~0.2；
- **光源颜色/环绕速度**：换暖光冷光、加速飞行。

## 常见坑

- **插值后法线忘 normalize**：光源方向稍偏就出现"阴阳面"。
- **非等比缩放没用法线矩阵**：明暗方向错误。
- **dot 结果为负还参与计算**：背面被"假照亮"，必须 `max(dot, 0.0)`。
- **光源位置在相机空间、法线在世界空间**：空间不统一，光照乱——所有向量统一到同一空间。

## 原理图解与代码逐步拆解

```text
 ADS 三向量的几何意义（逐片元）：

        L ↖       ↗ V
          ╲  ▲N  ╱
           ╲ │ ╱            N=法线  L=指向光源
            ╲│╱             V=指向相机
   ─────────●─────────      R = reflect(-L, N)
    片元表面        R↘

  ambient = 常数            （整个面一样亮）
  diffuse ∝ dot(N,L)        （朝光的面亮，背光的面黑）
  specular ∝ pow(dot(R,V), shininess)
                            （R 与 V 对齐时出现白斑；shininess 越大斑越小）
```

逐步拆解：

1. VS 把法线（`mat3(u_model) * a_normal`）和世界坐标传给 FS；
2. FS 把三个向量都 `normalize`——插值后的向量长度不保证是 1；
3. `max(dot(N,L), 0)`：背光面负数截断为 0（不会被假照亮）；
4. `pow(dot(R,V), shininess)`：指数越大曲线越尖，高光越小越锐；
5. 衰减（可选）：`1/(kc + kl·d + kq·d²)` 让远处光变暗。

## 自测

1. `shininess` 调大会发生什么？
2. 为什么 diffuse 要 `max(dot(N,L), 0)`？
3. 逐顶点光照的高光为什么容易"糊"？

<details><summary>查看答案</summary>
1. 高光指数越大，只有 R 和 V 非常接近时才有明显值 → 高光斑更小更锐利。
2. 光在表面背后时 dot 为负，负数会叠加出错误亮度；截断为 0 表示"背光面无漫反射"。
3. 高光是逐像素的尖峰函数，在 3 个顶点上采样后线性插值，峰值被抹平——必须逐片元计算。
</details>

➡️ 下一章：[22 · 材质与光照贴图](22-light-maps.md)
