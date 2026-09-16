package com.example.studyopengl.demos;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.opengl.GLES30;
import android.opengl.GLUtils;

import com.example.studyopengl.engine.BaseDemoEngine;
import com.example.studyopengl.gl.Mesh;
import com.example.studyopengl.gl.ShaderProgram;
import com.example.studyopengl.param.ParamSpec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * 第 34 章 · 直播预览实战：把"直播前处理"的常用 GL 知识点做成可玩的工坊。
 *
 * 真实直播的画面链路（本项目的对照实现写在每个小节的讲解里）：
 *   相机(OES 外部纹理) → FBO 美颜/磨皮 → FBO 调色 → 合成水印/画中画 → 送编码器推流
 * 模拟器上没有相机，用一个"程序化视频帧"(着色器实时生成的运动画面)代替 OES 纹理，
 * 效果链的每个环节保持和真实场景一一对应：
 *   节点1 视频源：uv/采样/程序化纹理（对应 OES 采样那一环）
 *   节点2 前处理：镜像翻转 / 磨皮(邻域平均) / 色温 / 饱和度（对应美颜调色 pass）
 *   节点3 绿幕抠像：色度抠绿 + 虚拟背景合成（对应虚拟直播间）
 *   节点4 水印+画中画：半透明叠加与子矩形二次采样（对应直播合图）
 */
public class D42LivePreview extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍直播画面的 GL 链路（本章所有节点的地图）\n"
            + "真实推流前，画面在 GPU 里要走一条固定流水线：\n"
            + "  相机 OES 纹理 → FBO 美颜/磨皮 → FBO 调色 → 合成水印/画中画 → 送编码器\n"
            + "模拟器没有相机，本章用\"程序化视频帧\"(着色器实时生成的运动画面)代替第一环，"
            + "后面每一环都和真实直播一一对应、可以单独玩。\n\n"
            + "▍四个节点\n"
            + "· 节点1 视频源：一张\"会动的纹理\"。真实场景是 GL_TEXTURE_EXTERNAL_OES "
            + "直接接收相机 YUV 帧，片元着色器一个矩阵转 RGB；\n"
            + "· 节点2 前处理链：镜像翻转(前置摄像头习惯)、磨皮(邻域平均近似)、"
            + "色温、饱和度——美颜相机的主干就是这几个 pass 的组合；\n"
            + "· 节点3 绿幕抠像：G 通道显著高于 R/B 判定为绿幕，抠掉换虚拟背景；"
            + "拖容差滑杆看边缘羽化；\n"
            + "· 节点4 水印+画中画：半透明角标叠加(注意画在最后)、子矩形二次采样做小窗"
            + "——连麦/小窗模式就是再开一个 viewport 画第二路；\n"
            + "· 节点5 连麦本地混画：主播+观众两路画面按\"矩形表\"本地合成一帧再推流，"
            + "画中画/左右分屏/上下分屏/一大两小四种布局随时切——切布局只是换一张矩形表。\n\n"
            + "▍为什么每环都用 FBO\n"
            + "效果链要\"上一环的输出当下一环的输入\"，FBO(离屏画布)就是中间寄存地；"
            + "编码器只认最终那份帧缓冲——所以合成永远放最后一步。详见 docs/41 直播知识地图。";

    /** 节点2 小节讲解。 */
    public static final String DETAIL_N2 = ""
            + "▍真实美颜/滤镜是一串 FBO pass\n"
            + "相机帧(OES) 先渲染进 FBO-A，美颜 shader 读 A 写 B；调色 shader 读 B 写 C……"
            + "每一环\"上一环的纹理\"就是\"这一环的输入\"。本项目为了在单屏演示，"
            + "把几个效果合并进一个片元着色器，顺序和真实链路一致。\n\n"
            + "▍每个效果的原理解剖\n"
            + "· 镜像翻转：uv.x = 1-uv.x——采样坐标左右翻一下，前置摄像头\"照镜子\"习惯就是这样来的；\n"
            + "· 磨皮：对邻域多次采样取平均，再按滑杆比例混回原图——平滑了高频(毛孔/噪点)。"
            + "真实美颜会用双边滤波(保边缘)+人脸关键点(大眼瘦脸是 mesh 顶点位移，不是像素操作)；\n"
            + "· 色温：R/B 通道反向偏移，暖橙冷蓝；\n"
            + "· 饱和度：先算亮度 luma(R·0.299+G·0.587+B·0.114)，再在\"灰色和原色\"之间插值。";

    /** 节点3 小节讲解。 */
    public static final String DETAIL_N3 = ""
            + "▍抠像的判定式\n"
            + "画面里\"G 明显比 R 和 B 都亮\"的像素判为绿幕：\n"
            + "  greenness = G - max(R, B)\n"
            + "greenness 超过容差(滑杆)就替换成虚拟背景，接近容差的区域做平滑过渡(羽化)——"
            + "所以拖\"容差\"滑杆会看到边缘一圈渐变。\n\n"
            + "▍工程里的三个细节\n"
            + "· 溢出抑制(spill suppression)：头发边缘常有绿色反光，要把 G 压回 R/B 的水平；\n"
            + "· 背景不透明：虚拟背景是另一路纹理/渐变，合成顺序=先抠前景 alpha 再 mix 背景；\n"
            + "· 肤色安全：容差太大会把偏绿的肤色一起抠掉，真实产品会限制判定区域。";

    /** 节点4 小节讲解。 */
    public static final String DETAIL_N4 = ""
            + "▍水印：最后一次半透明叠加\n"
            + "水印是\"贴在最终画面上\"的半透明纹理(本项目用 Canvas 生成【● LIVE】角标)，"
            + "开混合(glBlendFunc SRC_ALPHA, ONE_MINUS_SRC_ALPHA)画在最后——"
            + "画早了会被后面的内容盖住；编码器只认最终帧缓冲，所以合成永远是最后一步。\n\n"
            + "▍画中画：一个矩形区域\"再采样一次\"\n"
            + "小窗 = 片元着色器里判断\"uv 落在小窗矩形内\"，把这一小块用放大坐标再采样一路视频"
            + "并描一圈白边。多路视频(连麦)则是先给每路一个 FBO 纹理，"
            + "最后在同一个帧缓冲里用多个 viewport/偏移各画一次。\n\n"
            + "▍参数对应\n"
            + "小窗大小/位置滑杆改变的就是那个矩形——拖到右下角就是常见的\"连麦小窗\"布局。\n\n"
            + "▍下一节\n"
            + "真正的连麦混画（多路画面按布局表合成一帧）看节点5。";

    /** 节点5 小节讲解：连麦本地混画。 */
    public static final String DETAIL_N5 = ""
            + "▍连麦是什么\n"
            + "主播和观众实时互相看到听到。画面有两种合成方式：\n"
            + "· 本地混流：主播端把\"自己 + 对方\"两路画面合成一帧再推给所有人（省服务器算力、"
            + "主播耗电；本节演示的就是它）；\n"
            + "· 云端混流：各路上行，服务器合成后下发（省终端电、但要付转码钱、多一跳延迟）。\n\n"
            + "▍混画怎么做\n"
            + "每一路 = 一张独立纹理（解码器输出或相机 OES，各自走完前处理链进自己的 FBO）。\n"
            + "混画 = 在同一个帧缓冲里按\"矩形表\"各画一次：每个矩形一次 uv 重映射采样"
            + "（本节做法），工程里也常用 glViewport 切矩形逐个画。"
            + "布局切换 = 换一张矩形表——分屏/宫格/画中画本质上都只是数据，不是代码。\n\n"
            + "▍四个细节\n"
            + "· 名字标签：每个矩形一次半透明叠加，画在混画之后（\"画在最后\"原则 Again）；\n"
            + "· 分割线：矩形之间的间隙露出 clear 底色，工程里也常给小窗描彩边；\n"
            + "· 大小流：大窗用高分辨率纹理、小窗用低分辨率的\"订阅流\"，下行带宽立省一半；\n"
            + "· 音频是另一条链：多路 PCM 混音（叠加+限幅）后与混画帧一起打包推流，和 GL 无关。";

    private static final String KEY_SPEED = "src_speed";
    private static final String K_MIRROR = "fx_mirror";
    private static final String K_BEAUTY = "fx_beauty";
    private static final String K_WARM = "fx_warm";
    private static final String K_SAT = "fx_sat";
    private static final String K_CHROMA = "key_chroma";
    private static final String K_THRESH = "key_thresh";
    private static final String K_BADGE = "fx_badge";
    private static final String K_PIP = "pip_on";
    private static final String K_PIP_SCALE = "pip_scale";
    private static final String K_PIP_X = "pip_x";
    private static final String K_PIP_Y = "pip_y";
    // 节点5：连麦本地混画
    private static final String K_CO_ON = "co_on";
    private static final String K_CO_LAYOUT = "co_layout";
    private static final String K_CO_GAP = "co_gap";
    private static final String K_CO_LABEL = "co_label";

    /** 全屏合成着色器：程序化视频 + 前处理 + 抠像 + 画中画，一个 FS 走完整条链。 */
    private static final String VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec2 a_pos;\n"
            + "out vec2 v_uv;\n"
            + "void main() {\n"
            + "    v_uv = a_pos * 0.5 + 0.5;\n"
            + "    gl_Position = vec4(a_pos, 0.0, 1.0);\n"
            + "}\n";

    private static final String FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec2 v_uv;\n"
            + "uniform float u_time;\n"
            + "uniform float u_speed;\n"
            + "uniform float u_mirror;\n"
            + "uniform float u_beauty;\n"
            + "uniform float u_warm;\n"
            + "uniform float u_sat;\n"
            + "uniform float u_chroma;\n"
            + "uniform float u_thresh;\n"
            + "uniform float u_pip;\n"
            + "uniform vec4 u_pipRect;\n"     // xy=左下角 uv, zw=宽高
            + "uniform float u_co;\n"         // 连麦混画开关（节点5）
            + "uniform vec4 u_rA;\n"          // 主播画面矩形表（uv 空间，原点左下）
            + "uniform vec4 u_rB;\n"
            + "uniform vec4 u_rB2;\n"
            + "uniform float u_hasB2;\n"
            + "uniform float u_aspect;\n"     // 屏幕宽/高，cover 裁剪要用像素比例
            + "out vec4 fragColor;\n"
            + "\n"
            + "// 程序化\"视频帧\"：运动色条 + 扫描高光 + 底部字幕条 + 中央绿幕区。\n"
            + "// 真实直播里这一环是 sampler2DExternal 的相机帧。\n"
            + "vec3 video(vec2 uv) {\n"
            + "    uv = clamp(uv, 0.0, 1.0);\n"
            + "    float band = floor(uv.x * 6.0);\n"
            + "    vec3 col = 0.32 + 0.24 * cos(6.2831 * band / 6.0 + vec3(0.0, 2.1, 4.2));\n"
            + "    col *= 0.72 + 0.18 * sin(u_time * u_speed + uv.y * 4.0);\n"
            + "    float sweep = 1.0 - smoothstep(0.0, 0.03, abs(uv.x - fract(u_time * 0.13 * u_speed)));\n"
            + "    col += sweep * 0.30;\n"
            + "    if (uv.y < 0.14) col = mix(col, vec3(0.04, 0.05, 0.08), 0.85);\n"
            + "    vec2 d = (uv - vec2(0.5, 0.44)) / vec2(0.16, 0.26);\n"
            + "    float subj = 1.0 - smoothstep(0.85, 1.0, length(d));\n"
            + "    vec3 green = vec3(0.12, 0.82, 0.22) + 0.05 * sin(vec3(uv.yx * 60.0, 0.5));\n"
            + "    return mix(col, green, subj);\n"
            + "}\n"
            + "\n"
            + "// 第二路画面（连麦观众）：冷色调背景 + 圆脸 + 蓝衣——和主播一路一眼可辨。\n"
            + "vec3 guest(vec2 uv) {\n"
            + "    uv = clamp(uv, 0.0, 1.0);\n"
            + "    float band = floor(uv.x * 5.0);\n"
            + "    vec3 col = 0.26 + 0.20 * cos(6.2831 * band / 5.0 + vec3(4.0, 3.0, 1.5));\n"
            + "    col *= 0.72 + 0.16 * sin(u_time * u_speed * 1.3 + uv.x * 6.0);\n"
            + "    vec2 face = (uv - vec2(0.5, 0.64)) / vec2(0.08, 0.08);\n"
            + "    col = mix(col, vec3(0.90, 0.74, 0.60), 1.0 - smoothstep(0.88, 1.0, length(face)));\n"
            + "    vec2 body = (uv - vec2(0.5, 0.26)) / vec2(0.14, 0.14);\n"
            + "    col = mix(col, vec3(0.18, 0.34, 0.72), 1.0 - smoothstep(0.9, 1.0, length(body)));\n"
            + "    return col;\n"
            + "}\n"
            + "\n"
            + "// 连麦混画：判断 uv 落在哪个矩形里，用重映射坐标采对应那一路\n"
            + "bool inR(vec2 uv, vec4 r) {\n"
            + "    return uv.x > r.x && uv.y > r.y && uv.x < r.x + r.z && uv.y < r.y + r.w;\n"
            + "}\n"
            + "vec3 sampleA(vec2 uv, vec4 r) {\n"
            + "    return video((uv - r.xy) / r.zw);\n"
            + "}\n"
            + "vec3 sampleB(vec2 uv, vec4 r) {\n"
            + "    vec2 rel = (uv - r.xy) / r.zw;\n"
            + "    // cover 裁剪：按矩形的\"像素宽高比\"裁采源画布中央，人物不因矩形瘦长而变形\n"
            + "    float a = clamp(r.z * u_aspect / max(r.w, 0.0001), 0.25, 2.0);\n"
            + "    rel = vec2(0.5 + (rel.x - 0.5) * min(1.0, a),\n"
            + "               0.5 + (rel.y - 0.5) * min(1.0, 1.0 / a));\n"
            + "    return guest(rel);\n"
            + "}\n"
            + "vec3 sampleB2(vec2 uv, vec4 r) {\n"
            + "    vec2 rel = (uv - r.xy) / r.zw;\n"
            + "    float a = clamp(r.z * u_aspect / max(r.w, 0.0001), 0.25, 2.0);\n"
            + "    rel = vec2(0.5 + (rel.x - 0.5) * min(1.0, a),\n"
            + "               0.5 + (rel.y - 0.5) * min(1.0, 1.0 / a));\n"
            + "    return guest(vec2(1.0 - rel.x, rel.y));  // 第三路=第二路镜像，画面可辨\n"
            + "}\n"
            + "\n"
            + "// 调色：饱和度 + 色温（美颜链路的最后一环）\n"
            + "vec3 grade(vec3 c) {\n"
            + "    float luma = dot(c, vec3(0.299, 0.587, 0.114));\n"
            + "    c = mix(vec3(luma), c, u_sat);\n"
            + "    c += vec3(u_warm * 0.10, u_warm * 0.02, -u_warm * 0.10);\n"
            + "    return clamp(c, 0.0, 1.0);\n"
            + "}\n"
            + "\n"
            + "// 虚拟背景：深色演播厅 + 聚光灯（抠像的\"垫底\"）\n"
            + "vec3 studioBg(vec2 uv) {\n"
            + "    vec3 c = mix(vec3(0.07, 0.08, 0.12), vec3(0.16, 0.18, 0.26), uv.y);\n"
            + "    float spot = 1.0 - smoothstep(0.2, 0.62, length(uv - vec2(0.5, 0.55)));\n"
            + "    return c + spot * 0.10;\n"
            + "}\n"
            + "\n"
            + "void main() {\n"
            + "    vec2 uv = v_uv;\n"
            + "    if (u_mirror > 0.5) uv.x = 1.0 - uv.x;\n"
            + "\n"
            + "    if (u_co > 0.5) {\n"
            + "        vec3 c2 = vec3(0.015, 0.015, 0.02);\n"
            + "        if (u_hasB2 > 0.5 && inR(uv, u_rB2)) c2 = sampleB2(uv, u_rB2);\n"
            + "        if (inR(uv, u_rB)) c2 = sampleB(uv, u_rB);\n"
            + "        else if (inR(uv, u_rA)) c2 = sampleA(uv, u_rA);\n"
            + "        fragColor = vec4(c2, 1.0);\n"
            + "        return;\n"
            + "    }\n"
            + "\n"
            + "    vec3 c = video(uv);\n"
            + "    if (u_beauty > 0.001) {\n"
            + "        vec2 px = vec2(0.0035);\n"
            + "        vec3 blur = ( video(uv + vec2(px.x, 0.0)) + video(uv - vec2(px.x, 0.0))\n"
            + "                    + video(uv + vec2(0.0, px.y)) + video(uv - vec2(0.0, px.y)) ) * 0.25;\n"
            + "        c = mix(c, blur, u_beauty * 0.9);   // 磨皮=按比例混入低频\n"
            + "    }\n"
            + "    c = grade(c);\n"
            + "\n"
            + "    if (u_chroma > 0.5) {\n"
            + "        float greenness = clamp((c.g - max(c.r, c.b)) * 4.0, 0.0, 1.0);\n"
            + "        float mask = smoothstep(u_thresh, u_thresh + 0.18, greenness);\n"
            + "        c = mix(c, studioBg(uv), mask);     // 抠掉绿幕 → 虚拟背景（边缘即羽化）\n"
            + "    }\n"
            + "\n"
            + "    if (u_pip > 0.5) {\n"
            + "        vec2 rel = (uv - u_pipRect.xy) / u_pipRect.zw;\n"
            + "        if (rel.x > 0.0 && rel.y > 0.0 && rel.x < 1.0 && rel.y < 1.0) {\n"
            + "            vec3 pip = video(0.5 + (rel - 0.5) * 0.45);  // 小窗=放大采样(假分镜)\n"
            + "            float edge = min(min(rel.x, 1.0 - rel.x), min(rel.y, 1.0 - rel.y));\n"
            + "            pip = mix(pip, vec3(1.0), smoothstep(0.02, 0.015, edge));\n"
            + "            c = pip;\n"
            + "        }\n"
            + "    }\n"
            + "    fragColor = vec4(c, 1.0);\n"
            + "}\n";

    /** 水印角标叠加：Canvas 生成【● LIVE】贴图，混合画在最终画面上。 */
    private static final String OVER_VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec2 a_pos;\n"
            + "layout(location=1) in vec2 a_uv;\n"
            + "uniform vec4 u_rect;\n"          // NDC: x, y, w, h
            + "out vec2 v_uv;\n"
            + "void main() {\n"
            + "    v_uv = a_uv;\n"
            + "    gl_Position = vec4(u_rect.xy + a_pos * u_rect.zw, 0.0, 1.0);\n"
            + "}\n";

    private static final String OVER_FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec2 v_uv;\n"
            + "uniform sampler2D u_tex;\n"
            + "out vec4 fragColor;\n"
            + "void main() { fragColor = texture(u_tex, v_uv); }\n";

    private ShaderProgram mProg;
    private ShaderProgram mOverProg;
    private Mesh mQuad;
    private Mesh mOverQuad;
    private int mBadgeTex;
    private int mLabelHost;      // 「主播」名牌
    private int mLabelGuest;     // 「连麦」名牌
    private int mLabelGuest2;    // 「连麦2」名牌（宫格布局用）
    private float mTime;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProg = new ShaderProgram(VS, FS);
        mOverProg = new ShaderProgram(OVER_VS, OVER_FS);

        // 全屏两三角形：pos2
        float[] quad = {-1, -1, 1, -1, -1, 1, 1, 1};
        mQuad = new Mesh.Builder()
                .addBuffer(quad, new Mesh.Attrib(0, 2))
                .build();

        // 水印叠加用小四边形：pos2 + uv2（0..1）
        float[] over = {
                -1, -1, 0, 1,   1, -1, 1, 1,
                -1, 1, 0, 0,    1, 1, 1, 0,
        };
        mOverQuad = new Mesh.Builder()
                .addBuffer(over, new Mesh.Attrib(0, 2), new Mesh.Attrib(1, 2))
                .build();

        mBadgeTex = makeBadgeTexture();
        mLabelHost = makeLabelTexture("主播", new int[]{190, 60, 60});
        mLabelGuest = makeLabelTexture("连麦", new int[]{60, 100, 200});
        mLabelGuest2 = makeLabelTexture("连麦2", new int[]{50, 140, 150});
        mOverProg.use();
        mOverProg.set("u_tex", 0);
    }

    /** 【● LIVE】角标：直播软件水印位（右上角、半透明底、红点闪烁靠 u_time 亦可，这里用静态图）。 */
    private static Bitmap badgeBitmap() {
        Bitmap bmp = Bitmap.createBitmap(220, 80, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.argb(150, 20, 20, 24));
        cv.drawRoundRect(new RectF(4, 8, 216, 72), 18, 18, p);
        p.setColor(Color.rgb(235, 60, 60));
        cv.drawCircle(34, 40, 12, p);
        p.setColor(Color.WHITE);
        p.setTextSize(30);
        p.setFakeBoldText(true);
        cv.drawText("LIVE", 58, 51, p);
        return bmp;
    }

    private int makeBadgeTexture() {
        Bitmap bmp = badgeBitmap();
        int[] ids = new int[1];
        GLES30.glGenTextures(1, ids, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0]);
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        bmp.recycle();
        return ids[0];
    }

    /** 名字标签贴图：半透明圆角底 + 白字（直播画面里的"主播/连麦"角标）。 */
    private int makeLabelTexture(String text, int[] rgb) {
        Bitmap bmp = Bitmap.createBitmap(160, 64, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.argb(170, rgb[0], rgb[1], rgb[2]));
        cv.drawRoundRect(new RectF(2, 4, 158, 60), 14, 14, p);
        p.setColor(Color.WHITE);
        p.setTextSize(34);
        p.setFakeBoldText(true);
        p.setTextAlign(Paint.Align.CENTER);
        cv.drawText(text, 80, 44, p);
        int[] ids = new int[1];
        GLES30.glGenTextures(1, ids, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0]);
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        bmp.recycle();
        return ids[0];
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        mTime += deltaTime;
        clearFrame(0.02f, 0.02f, 0.03f);

        glUseProgramSafe(mProg);
        mProg.set("u_time", mTime);
        mProg.set("u_speed", Math.max(0f, getFloat(KEY_SPEED)));
        mProg.set("u_mirror", bool01(K_MIRROR));
        mProg.set("u_beauty", clamp01(getFloat(K_BEAUTY)));
        mProg.set("u_warm", clamp(getFloat(K_WARM), -1f, 1f));
        mProg.set("u_sat", clamp(getFloat(K_SAT), 0f, 2f));
        mProg.set("u_chroma", bool01(K_CHROMA));
        mProg.set("u_thresh", clamp01(getFloat(K_THRESH)));
        boolean pipOn = getBool(K_PIP);
        mProg.set("u_pip", pipOn ? 1f : 0f);
        float ps = clamp(getFloat(K_PIP_SCALE), 0.08f, 0.6f);
        float px = clamp01(getFloat(K_PIP_X));
        float py = clamp01(getFloat(K_PIP_Y));
        mProg.set("u_pipRect", px, py, ps, ps * 9f / 16f * (float) mWidth / (float) mHeight);

        // ── 节点5 连麦本地混画：Java 算"矩形表"，FS 按矩形各采各路 ──
        boolean coOn = getBool(K_CO_ON);
        mProg.set("u_co", coOn ? 1f : 0f);
        float[] coA = {0f, 0f, 1f, 1f};
        float[] coB = {0f, 0f, 0f, 0f};
        float[] coB2 = {0f, 0f, 0f, 0f};
        boolean hasB2 = false;
        if (coOn) {
            float g = clamp01(getFloat(K_CO_GAP));
            int layout = getOptionIndex(K_CO_LAYOUT);
            if (layout == 0) { // 画中画：主播全屏 + 观众小窗
                float pw = 0.34f;
                float ph = Math.min(0.6f, pw * (float) mWidth / mHeight);
                coB = new float[]{0.97f - pw, 0.03f, pw, ph};
            } else if (layout == 1) { // 左右分屏
                float w = (1f - g) / 2f;
                coA = new float[]{0f, 0f, w, 1f};
                coB = new float[]{1f - w, 0f, w, 1f};
            } else if (layout == 2) { // 上下分屏
                float h = (1f - g) / 2f;
                coA = new float[]{0f, 1f - h, 1f, h};
                coB = new float[]{0f, 0f, 1f, h};
            } else { // 一大两小宫格
                float top = 0.6f;
                coA = new float[]{0f, top + g, 1f, 1f - top - g};
                float w2 = (1f - 3f * g) / 2f;
                float h2 = top - g;
                coB = new float[]{g, g, w2, h2};
                coB2 = new float[]{2f * g + w2, g, w2, h2};
                hasB2 = true;
            }
        }
        mProg.set("u_rA", coA[0], coA[1], coA[2], coA[3]);
        mProg.set("u_rB", coB[0], coB[1], coB[2], coB[3]);
        mProg.set("u_rB2", coB2[0], coB2[1], coB2[2], coB2[3]);
        mProg.set("u_hasB2", hasB2 ? 1f : 0f);
        mProg.set("u_aspect", mHeight == 0 ? 1f : (float) mWidth / mHeight);
        mQuad.draw(GLES30.GL_TRIANGLE_STRIP);

        // ── 2D 叠加层（名字标签 / LIVE 角标）：半透明混合、"画在最后"原则 ──
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        glUseProgramSafe(mOverProg);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        if (coOn && getBool(K_CO_LABEL)) {
            drawLabel(mLabelHost, coA);
            drawLabel(mLabelGuest, coB);
            if (hasB2) drawLabel(mLabelGuest2, coB2);
        }
        if (getBool(K_BADGE)) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mBadgeTex);
            // 右上角角标（中心点语义）：宽 0.62 NDC ≈ 屏宽 31%，高按贴图 220:80 比例
            float bw = 0.62f;
            float bh = bw * (float) mWidth / mHeight * (80f / 220f);
            mOverProg.set("u_rect", 1f - bw / 2f - 0.05f, 1f - bh / 2f - 0.05f, bw, bh);
            mOverQuad.draw(GLES30.GL_TRIANGLE_STRIP);
        }
        GLES30.glDisable(GLES30.GL_BLEND);
    }

    /** 在混画矩形内叠一个名字标签（u_rect.xy 是中心点、zw 是半宽半高）。 */
    private void drawLabel(int tex, float[] r) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex);
        float hw = 0.075f;
        float hh = 0.025f;
        float cx = (r[0] + r[2] / 2f) * 2f - 1f + 0.06f;
        // 贴矩形顶部；全高矩形再往下压，避开演示 UI 的标题栏
        float cy = Math.min((r[1] + r[3]) * 2f - 1f - hh - 0.05f, 0.72f);
        mOverProg.set("u_rect", cx, cy, hw, hh);
        mOverQuad.draw(GLES30.GL_TRIANGLE_STRIP);
    }

    private static float clamp01(float v) {
        return clamp(v, 0f, 1f);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** 布尔参数转着色器 float 开关。 */
    private float bool01(String key) {
        return getBool(key) ? 1f : 0f;
    }

    private void glUseProgramSafe(ShaderProgram p) {
        GLES30.glUseProgram(p.getProgramId());
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        List<ParamSpec> specs = new ArrayList<>();
        specs.add(ParamSpec.floatSpec(KEY_SPEED, "画面运动速度", 0f, 2f, 1f));
        specs.add(ParamSpec.boolSpec(K_MIRROR, "镜像翻转(前置习惯)", true));
        specs.add(ParamSpec.floatSpec(K_BEAUTY, "磨皮强度", 0f, 1f, 0f));
        specs.add(ParamSpec.floatSpec(K_WARM, "色温(冷→暖)", -1f, 1f, 0f));
        specs.add(ParamSpec.floatSpec(K_SAT, "饱和度", 0f, 2f, 1f));
        specs.add(ParamSpec.boolSpec(K_CHROMA, "绿幕抠像→虚拟背景", false));
        specs.add(ParamSpec.floatSpec(K_THRESH, "抠像容差", 0.05f, 0.9f, 0.3f));
        specs.add(ParamSpec.boolSpec(K_BADGE, "LIVE 水印角标", false));
        specs.add(ParamSpec.boolSpec(K_PIP, "画中画小窗", false));
        specs.add(ParamSpec.floatSpec(K_PIP_SCALE, "小窗大小", 0.1f, 0.5f, 0.24f));
        specs.add(ParamSpec.floatSpec(K_PIP_X, "小窗左下角X", 0f, 0.95f, 0.68f));
        specs.add(ParamSpec.floatSpec(K_PIP_Y, "小窗左下角Y", 0f, 0.95f, 0.72f));
        // ---- 节点5：连麦本地混画 ----
        specs.add(ParamSpec.boolSpec(K_CO_ON, "连麦混画", false));
        specs.add(ParamSpec.optionSpec(K_CO_LAYOUT, "连麦布局", new String[]{
                "画中画（右下）", "左右分屏", "上下分屏", "一大两小宫格"}, 1));
        specs.add(ParamSpec.floatSpec(K_CO_GAP, "分割间隙", 0f, 0.05f, 0.012f));
        specs.add(ParamSpec.boolSpec(K_CO_LABEL, "显示名字标签", true));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mQuad.dispose();
        mOverQuad.dispose();
        GLES30.glDeleteTextures(1, new int[]{mBadgeTex}, 0);
        GLES30.glDeleteTextures(1, new int[]{mLabelHost}, 0);
        GLES30.glDeleteTextures(1, new int[]{mLabelGuest}, 0);
        GLES30.glDeleteTextures(1, new int[]{mLabelGuest2}, 0);
        mProg.release();
        mOverProg.release();
    }
}
