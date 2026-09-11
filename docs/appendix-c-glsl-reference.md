# 附录 C · GLSL ES 3.00 语言速查

> 把散落在各章的 GLSL 语法汇总成一张参考。建议用到时回来查。

## 着色器骨架（每个 shader 必有）

```glsl
#version 300 es                    // 必须第一行，前面不能有任何字符
precision mediump float;           // FS 必须声明浮点精度（VS 默认 highp）

// 输入/输出/uniform 见下表
out vec4 fragColor;                // FS 必须自声明输出
void main() { fragColor = ...; }
```

## 类型系统

| 类别 | 类型 | 说明 |
|---|---|---|
| 标量 | `float int bool uint` | |
| 向量 | `vec2 vec3 vec4` | float 向量；`ivec/uvec/bvec` 同维 |
| 矩阵 | `mat2 mat3 mat4` | 列主序 |
| 采样器 | `sampler2D` `samplerCube` `sampler2DArray` `sampler3D` | 只能在 FS 里采样 |

## 向量操作（GLSL 最好用的部分）

```glsl
vec3 v = vec3(1.0, 2.0, 3.0);
vec3 a = v.xyz;            // swizzle：任意重排/复制分量 xyzw/rgba 皆可
vec2 b = v.xx;             // 甚至重复：结果 (1,1)
v.xy *= 2.0;               // 分量级运算：+-*/ 与标量或同维向量
float d = dot(a, b);       // 点积
vec3 c = cross(a, b);      // 叉积
float len = length(a);     // 模长
vec3 n = normalize(v);     // 归一化
vec3 m = mix(a, b, 0.5);   // 线性插值（第三参可为标量或向量）
```

> **swizzle 合法性**：`v.xyz` ✓、`v.xxx` ✓、`v.xyzx` ✗（超过 4 分量）、`v.wz` 读取 ✓、`v.xy.x` ✗（链式）。

## 常用内建函数（按用途）

| 用途 | 函数 |
|---|---|
| 三角 | `sin cos tan asin acos atan` |
| 幂/绝对 | `pow exp log sqrt inversesqrt abs sign floor ceil fract mod` |
| 插值/钳制 | `mix(a,b,t) clamp(x,lo,hi) step(edge,x) smoothstep(e0,e1,x)` |
| 几何 | `dot cross length distance normalize reflect(I,N) refract` |
| 纹理 | `texture(s,uv[,bias]) textureLod(s,uv,lod) texelFetch` |
| 导数（FS） | `dFdx dFdy fwidth` |
| 比较 | `min max equal lessThan ...` |

`step(edge, x)`：x<edge→0 否则 1；`smoothstep`：平滑过渡（画图形不锯齿必用）。

## 变量限定符

| 限定符 | 位置 | 含义 |
|---|---|---|
| `in` / `out` | VS | 顶点属性输入 / 传给 FS（插值） |
| `in` / `out` | FS | 收自 VS / 最终颜色 |
| `uniform` | 两处 | CPU 每批次传入的常量 |
| `layout(location=N)` | VS 属性 / FS 输出 | 显式槽位 |
| `flat` | out/in | 不插值（第 08 章） |
| `const` | 任意 | 编译期常量 |

## 精度

```glsl
precision mediump float;         // FS 必须声明（或对每个变量单独声明）
precision lowp sampler2DArray;   // 某些采样器（如 sampler2DArray）无默认精度，必须声明
```

| 精度 | 范围 | 用途 |
|---|---|---|
| highp | 32 位 float | 顶点位置、大世界坐标 |
| mediump | 通常 16 位 | 大多数颜色/光照计算 |
| lowp | 通常 8 位 | 颜色、采样器 |

## 常见编译错误速查

| 报错 | 原因 |
|---|---|
| `#version` expected first | 版本行前有空行/注释 |
| `Illegal use of reserved word: noperspective` | ES3 保留字未实现（第 08 章） |
| `No precision specified` | 该类型无默认精度，需 `precision lowp sampler2DArray;` |
| undeclared identifier | 变量名拼错 / FS 用了 VS 没输出的变量 |
| `in/out` 不匹配 | VS out 与 FS in 名字或类型不一致 |

## 自测

1. `vec4 v;` 写 `v.xyzx` 合法吗？
2. `sampler2DArray` 缺精度声明会怎样？
3. `smoothstep(0.4, 0.6, x)` 在 x=0.5 时约等于多少？

<details><summary>查看答案</summary>
1. 不合法——swizzle 分量总数不能超过 4。
2. 编译失败：`'sampler2DArray' : No precision specified`（本 App ch19 踩过的真实坑）。
3. 约 0.5——smoothstep 在 0.4~0.6 之间做平滑过渡，中点正好 0.5。
</details>

◀ 返回 [docs/README.md](README.md)
