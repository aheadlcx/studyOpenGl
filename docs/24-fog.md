# 24 · 雾效

> 对应 App 第 24 项 · 难度：光照与材质

## 你将搞懂

- 三种经典距离雾公式：线性 / EXP / EXP2
- 雾色必须等于天空清屏色的原因
- 雾的三个工程作用
- 高度雾/体积雾的扩展方向

## 先讲人话

雾 = **按距离把物体颜色混向雾色**。设 d = 片元到相机距离：

| 类型 | 公式 | 特点 |
|---|---|---|
| 线性 Linear | `factor = (end − d) / (end − start)` | start 前全清晰、end 后全雾，有硬边界 |
| 指数 EXP | `factor = 1 − exp(−density·d)` | 无边界自然衰减 |
| 平方指数 EXP2 | `factor = 1 − exp(−(density·d)²)` | 衰减更陡，"雾墙"感强 |

最终：`color = mix(物体色, 雾色, factor)`。

> **Android 类比**：像给照片加"远景蒙版"图层——蒙版透明度由距离驱动。

## 核心代码（片元着色器）

```glsl
uniform vec3 u_cameraPos;
uniform vec3 u_fogColor;
uniform int  u_fogType;      // 0无 1线性 2exp 3exp2
uniform float u_density, u_fogEnd;

float d = length(v_worldPos - u_cameraPos);   // 片元到相机距离
float factor = 0.0;
if (u_fogType == 1) {
    factor = clamp((u_fogEnd - d) / (u_fogEnd * 0.6), 0.0, 1.0);
} else if (u_fogType == 2) {
    factor = 1.0 - exp(-u_density * d);
} else if (u_fogType == 3) {
    factor = 1.0 - exp(-u_density * u_density * d * d);
}
fragColor = vec4(mix(objectColor, u_fogColor, factor), 1.0);
```

## 三个工程作用

1. **距离感知**：大气透视让场景有纵深感；
2. **掩盖远裁剪**：远处物体在雾里淡出，被 far 平面裁掉也不突兀；
3. **性能工具**：雾吞掉远处细节 → far 可以拉近、LOD 提前切换、阴影距离减半。

## 铁律：雾色 = 天空清屏色

远处的地面和天空必须"融"成同一种颜色，否则地平线出现生硬接缝。App 里 clear 色与 u_fogColor 用同一个 vec3。

## 在 App 里怎么玩（第 24 项）

- 场景：棋盘地面 + 两排立柱向远处延伸，相机缓慢前进；
- **雾类型** 四选一：无雾时立柱清晰锐利；线性雾有明确的雾墙位置；EXP/EXP2 平滑衰减；
- **密度** 滑条（EXP/EXP2 生效）：0.3 时十米外一片白；
- **end 距离**（线性雾）：雾墙推近拉远；
- 注意观察：不管什么雾，地平线永远和天空无缝。

## 常见坑

- **雾色 ≠ 清屏色**：地平线出现分界线（最常见）。
- **雾算在光照之前**：先雾后光 = 雾里的东西又被照亮，顺序应该是"光照 → 雾 → 输出"。
- **距离用错空间**：世界空间距离要与 u_cameraPos 配套；相机空间则直接用 `length(v_viewPos)`。
- **线性雾的 start/end 没按场景尺度调**：室内场景用室外参数，全屏白或全屏清晰。

## 原理图解与代码逐步拆解

```text
 三种雾因子曲线（横轴=距离 d，纵轴=雾浓度 0~1）：

 1.0 ┤            ┌────── 线性（有明确雾墙 end）
     │        ╱
     │      ╱     ⣀⣀⣀⣀⣀  EXP2（先缓后陡）
 0.5 │    ╱ ⣀⣀⣀
     │ ⣀⣀⣀            EXP（均匀衰减）
   0 └──────────────▶ d

 混合：final = mix(物体色, 雾色, factor)   雾色必须=清屏色！
```

逐步拆解：

1. FS 里先算片元颜色（贴图/光照），最后一步才混雾——顺序错了雾里的东西会被"重新照亮"；
2. `d = length(v_worldPos - u_cameraPos)`：世界空间距离（注意与 cameraPos 同空间）；
3. 三种公式只是"因子随距离增长的速度"不同：线性最可控，EXP2 雾墙感最强；
4. 清屏色 = 雾色 → 地平线处地面自然融入天空，无接缝；
5. 工程价值：雾可以掩盖远裁剪突兀感 + 允许拉近 far 省性能。

## 自测

1. EXP2 相比 EXP 的曲线有什么不同？
2. 雾应该放在光照前还是后？为什么？
3. 场景想"近处也有薄雾"，三种雾选哪个、调什么？

<details><summary>查看答案</summary>
1. EXP2 对距离平方敏感，近处衰减慢、远处衰减更陡（雾墙感更强）。
2. 后。雾是大气对光线的遮挡效果，发生在光照完成之后；先雾后光会把雾中的东西重新照亮。
3. 线性雾，把 start 设为接近 0（或减小 end），让近距离就有雾因子。
</details>

➡️ 下一章：[25 · 实例化渲染](25-instancing.md)
