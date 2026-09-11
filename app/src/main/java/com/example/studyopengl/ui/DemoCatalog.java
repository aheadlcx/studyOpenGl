package com.example.studyopengl.ui;

import com.example.studyopengl.demos.D01HelloTriangle;
import com.example.studyopengl.demos.D02PrimitiveTypes;
import com.example.studyopengl.demos.D03Interpolation;
import com.example.studyopengl.demos.D04Texture2D;
import com.example.studyopengl.demos.D05Mipmap;
import com.example.studyopengl.demos.D06Transform;
import com.example.studyopengl.demos.D07Camera;
import com.example.studyopengl.demos.D08DepthTest;
import com.example.studyopengl.demos.D09Blending;
import com.example.studyopengl.demos.D10StencilTest;
import com.example.studyopengl.demos.D11FaceCulling;
import com.example.studyopengl.demos.D12ViewportScissor;
import com.example.studyopengl.demos.D13BasicLighting;
import com.example.studyopengl.demos.D14LightMaps;
import com.example.studyopengl.demos.D15NormalMapping;
import com.example.studyopengl.demos.D16Fog;
import com.example.studyopengl.demos.D17Instancing;
import com.example.studyopengl.demos.D18UniformBlock;
import com.example.studyopengl.demos.D19TransformFeedback;
import com.example.studyopengl.demos.D20FboRtt;
import com.example.studyopengl.demos.D21PostProcess;
import com.example.studyopengl.demos.D22CubemapSkybox;
import com.example.studyopengl.demos.D23Msaa;
import com.example.studyopengl.demos.D24Mrt;
import com.example.studyopengl.demos.D25BufferMapping;
import com.example.studyopengl.demos.D26TextureArray;
import com.example.studyopengl.demos.D27PipelineJourney;
import com.example.studyopengl.demos.D28VertexData;
import com.example.studyopengl.demos.D29VertexShaderSpaces;
import com.example.studyopengl.demos.D30Rasterizer;
import com.example.studyopengl.demos.D31FragmentShader;
import com.example.studyopengl.demos.D32TestsAndSwap;

import java.util.ArrayList;

/**
 * 全部技术点目录：按难度从简单到难排序，学习路线即列表顺序。
 *
 * 难度阶梯：
 *   1~6   零基础概念（渲染管线教程系列，先建立整体认知，不写代码）
 *   7~9   基础动手（一个三角形起步）
 *   10~11 简单渲染状态（一两个开关就有效果）
 *   12~14 矩阵与相机（数学介入）+ 深度
 *   15~16 混合与模板（状态组合）
 *   17~20 纹理（2D → mipmap → 数组 → 立方体）
 *   21~24 光照与材质（多概念叠加）+ 雾
 *   25~28 GPU 管线进阶（实例化/UBO/缓冲映射/TF）
 *   29~32 帧缓冲与后处理（RTT/卷积/MSAA/MRT）
 */
public final class DemoCatalog {

    private DemoCatalog() {
    }

    /** 构建章节的小节列表。 */
    private static java.util.ArrayList<SubDemo> subs(SubDemo... s) {
        java.util.ArrayList<SubDemo> l = new java.util.ArrayList<SubDemo>();
        for (SubDemo x : s) l.add(x);
        return l;
    }

    /** 便捷工厂：键值对形式的锁定参数。 */
    private static SubDemo sub(String id, String title, String brief, String detail,
                               Object... kv) {
        String[] keys = new String[kv.length / 2];
        Object[] vals = new Object[kv.length / 2];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = (String) kv[i * 2];
            vals[i] = kv[i * 2 + 1];
        }
        return new SubDemo(id, title, brief, detail, keys, vals);
    }

    public static ArrayList<DemoInfo> all() {
        ArrayList<DemoInfo> list = new ArrayList<DemoInfo>();

        // ═══ 第 1 阶段：零基础概念（先看懂发生了什么，还不用写代码）═══
        list.add(new DemoInfo(1, "pipeline_journey", "渲染管线全景（零基础）", "入门·概念",
                "7 道工序流水线图 + 数据包流动画，建立整体认知",
                D27PipelineJourney.DESCRIPTION, D27PipelineJourney.CODE, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D27PipelineJourney();
            }
        }));
        list.add(new DemoInfo(2, "vertex_data", "第1站·顶点数据 VBO/VAO", "入门·概念",
                "float 数组如何进显存：内存条可视化 + 交错/分离布局",
                D28VertexData.DESCRIPTION, D28VertexData.CODE,
                subs(
                    sub("vd_inter", "小节1 · 交错布局", "位置和颜色挤在一起，GPU 一次读取一个顶点",
                        "上半屏：每个顶点的 6 个数字连成一个块（stride=24 字节）。\n用【选中顶点】切换 v0/v1/v2，观察内存块与 3D 顶点的对应。",
                        "layout", 0),
                    sub("vd_separate", "小节2 · 分离布局", "位置、颜色各一条独立数组",
                        "位置一条 positions[]、颜色一条 colors[]。\n对比交错布局：读顶点要跨两条缓冲，但单独更新某类属性更方便。",
                        "layout", 1)),
                new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D28VertexData();
            }
        }));
        list.add(new DemoInfo(3, "vertex_shader_spaces", "第2站·顶点着色器 MVP", "入门·概念",
                "局部→世界→相机→裁剪 四空间变换四视口",
                D29VertexShaderSpaces.DESCRIPTION, D29VertexShaderSpaces.CODE, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D29VertexShaderSpaces();
            }
        }));
        list.add(new DemoInfo(4, "rasterizer", "第3站·光栅化放大镜", "入门·概念",
                "GPU 连续画面 vs CPU 离散片元方格，看穿插值与采样",
                D30Rasterizer.DESCRIPTION, D30Rasterizer.CODE, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D30Rasterizer();
            }
        }));
        list.add(new DemoInfo(5, "fragment_shader", "第4站·片元着色器输入", "入门·概念",
                "插值色/FragCoord/uv/程序化花纹/discard 六种视角",
                D31FragmentShader.DESCRIPTION, D31FragmentShader.CODE,
                subs(
                    sub("fs_vcolor", "视角1 · 插值色 v_color", "渐变是光栅化白送的",
                        "顶点着色器输出 v_color，光栅化按重心坐标插值后交给每个像素。\n三个顶点不同色，三角形内部自然渐变——零代码成本。",
                        "mode", 0),
                    sub("fs_fragcoord", "视角2 · gl_FragCoord", "屏幕像素坐标（内建变量）",
                        "gl_FragCoord 是片元在屏幕上的位置。\n除以 u_density 取小数再 step，画出固定在屏幕上的竖条纹。",
                        "mode", 1),
                    sub("fs_uv", "视角3 · v_uv 坐标", "贴图定位坐标 0~1",
                        "把 uv 当颜色显示：红=水平方向，绿=垂直方向。\n真实用法：texture(u_tex, v_uv) 用它查贴图（第 17 章）。",
                        "mode", 2),
                    sub("fs_rings", "视角4 · 程序化圆环", "纯数学画花纹，不用任何贴图",
                        "length(v_uv-0.5) 算出到中心的距离，sin 让距离变环。\n时间项让圆环向外流动。",
                        "mode", 3),
                    sub("fs_checker", "视角5 · 棋盘格", "floor 取整经典技巧",
                        "uv 乘密度取整 → 格子坐标；x+y 的奇偶决定黑白。",
                        "mode", 4),
                    sub("fs_discard", "视角6 · discard 镂空", "片元自我否决",
                        "圆孔区域内 discard 直接丢弃片元（什么都没画）。\n注意：discard 会让 early-z 失效，能不用尽量少用。",
                        "mode", 5)),
                new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D31FragmentShader();
            }
        }));
        list.add(new DemoInfo(6, "tests_and_swap", "第5站·测试混合与上屏", "入门·概念",
                "片元闯四关动画 + 双缓冲 SWAP 演示",
                D32TestsAndSwap.DESCRIPTION, D32TestsAndSwap.CODE, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D32TestsAndSwap();
            }
        }));

        // ═══ 第 2 阶段：基础动手（一个三角形起步）═══
        list.add(new DemoInfo(7, "hello_triangle", "三角形与着色器管线", "基础·动手",
                "VAO/VBO、着色器编译链接、glDrawArrays 完整流程",
                D01HelloTriangle.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D01HelloTriangle();
            }
        }));
        list.add(new DemoInfo(8, "interpolation", "Varying 插值", "基础·动手",
                "smooth / flat 插值限定符对比",
                D03Interpolation.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D03Interpolation();
            }
        }));
        list.add(new DemoInfo(9, "primitives", "图元类型与索引绘制", "基础·动手",
                "全部 glDrawElements mode + ES3 重启索引",
                D02PrimitiveTypes.DESCRIPTION,
                null,
                subs(
                    sub("prim_points", "GL_POINTS 点", "每个顶点画一个方块点",
                        "点大小在顶点着色器里用 gl_PointSize 设置。", "mode", 0),
                    sub("prim_lines", "GL_LINES 线段", "两两成对",
                        "顶点 0-1 一条、2-3 一条……奇数顶点会被忽略。", "mode", 1),
                    sub("prim_lstrip", "GL_LINE_STRIP 连折线", "依次连接，不闭合", "mode", 2),
                    sub("prim_lloop", "GL_LINE_LOOP 闭合环", "首尾自动相连", "mode", 3),
                    sub("prim_tris", "GL_TRIANGLES 三角形", "每 3 个顶点一个三角形", "mode", 4),
                    sub("prim_tstrip", "GL_TRIANGLE_STRIP 三角带", "共享边，n+2 顶点画 n 个三角形",
                        "顶点顺序呈锯齿：0,1,2 → 2,1,3 → 2,3,4……顺序敏感。", "mode", 5),
                    sub("prim_tfan", "GL_TRIANGLE_FAN 三角扇", "全部与首顶点组队", "mode", 6),
                    sub("prim_restart", "重启索引（ES3 新增）", "0xFFFFFFFF 断开，一次 draw 画两段",
                        "启用 GL_PRIMITIVE_RESTART_FIXED_INDEX 后，\n索引流中的 0xFFFFFFFF 表示\"从这里重新开始\"。", "mode", 7)),
                new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D02PrimitiveTypes();
            }
        }));

        // ═══ 第 3 阶段：最简单的渲染状态（一两个开关就有效果）═══
        list.add(new DemoInfo(10, "viewport_scissor", "视口与裁剪", "基础·状态",
                "glViewport/glScissor 多视口分屏",
                D12ViewportScissor.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D12ViewportScissor();
            }
        }));
        list.add(new DemoInfo(11, "culling", "面剔除", "基础·状态",
                "绕序、glFrontFace/glCullFace",
                D11FaceCulling.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D11FaceCulling();
            }
        }));

        // ═══ 第 4 阶段：矩阵与相机（数学介入）═══
        list.add(new DemoInfo(12, "transform", "变换矩阵", "进阶·矩阵",
                "模型矩阵 T·R·S 组合与顺序陷阱",
                D06Transform.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D06Transform();
            }
        }));
        list.add(new DemoInfo(13, "camera", "相机与透视投影", "进阶·矩阵",
                "fovy/near/far、轨道相机、触摸交互",
                D07Camera.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D07Camera();
            }
        }));
        list.add(new DemoInfo(14, "depth_test", "深度测试", "进阶·状态",
                "glDepthFunc 全家族、深度掩码、多边形偏移",
                D08DepthTest.DESCRIPTION,
                null,
                subs(
                    sub("dep_func", "小节1 · 深度函数家族", "8 种 glDepthFunc 逐个试",
                        "默认 GL_LESS：更近者胜。\n切到 GL_GREATER 看\"远者胜\"的幽灵世界；GL_ALWAYS 则完全不测试。",
                        "mask", true, "offset", false),
                    sub("dep_mask", "小节2 · 深度掩码", "glDepthMask：参与测试但不留记录",
                        "关掉后蓝色半透明面仍然会被红色面正确遮挡，\n但它自己不会在深度缓冲里\"立牌子\"挡住后面画的东西。",
                        "func", 1, "offset", false),
                    sub("dep_offset", "小节3 · 多边形偏移治 z-fighting", "共面闪烁的解法",
                        "地面填充面被往后推，网格线稳定浮在上面。\n关掉开关对比：网格线开始随机闪烁（z-fighting）。",
                        "func", 1, "mask", true)),
                new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D08DepthTest();
            }
        }));

        // ═══ 第 5 阶段：状态组合（混合/模板）═══
        list.add(new DemoInfo(15, "blending", "混合 Blending", "进阶·状态",
                "glBlendFunc/glBlendEquation 因子矩阵",
                D09Blending.DESCRIPTION,
                null,
                subs(
                    sub("blend_std", "小节1 · 标准半透明", "SRC_ALPHA, ONE_MINUS_SRC_ALPHA",
                        "最终 = 新色×α + 旧色×(1-α)。\n玻璃、贴纸等常规半透明都用它（等价 Canvas 的 SRC_OVER）。"),
                    sub("blend_premult", "小节2 · 预乘 Alpha", "ONE, ONE_MINUS_SRC_ALPHA",
                        "贴图颜色已预先乘过 alpha 时用这套，边缘无黑边。\nAndroid UI 合成默认就是预乘。"),
                    sub("blend_glow", "小节3 · 加色发光", "SRC_ALPHA, ONE",
                        "越叠越亮，黑色区域完全\"消失\"。\n粒子、火焰、光晕的标准做法。"),
                    sub("blend_eq", "小节4 · 混合方程", "ADD/SUB/MIN/MAX",
                        "切方程看不同合成效果：MIN/MAX 是 ES3 新增。",
                        "eq", 0)),
                new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D09Blending();
            }
        }));
        list.add(new DemoInfo(16, "stencil", "模板测试", "进阶·状态",
                "glStencilFunc/Op 与物体描边三段式",
                D10StencilTest.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D10StencilTest();
            }
        }));

        // ═══ 第 6 阶段：纹理（2D → mipmap → 数组 → 立方体）═══
        list.add(new DemoInfo(17, "texture_2d", "2D 纹理与采样", "纹理",
                "wrap/filter 状态机、纹理单元绑定、坐标翻转",
                D04Texture2D.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D04Texture2D();
            }
        }));
        list.add(new DemoInfo(18, "mipmap", "Mipmap 与 LOD", "纹理",
                "mip 链、三线性过滤、texture bias",
                D05Mipmap.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D05Mipmap();
            }
        }));
        list.add(new DemoInfo(19, "texture_array", "2D 纹理数组", "纹理",
                "glTexImage3D、sampler2DArray 逐实例选层",
                D26TextureArray.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D26TextureArray();
            }
        }));
        list.add(new DemoInfo(20, "cubemap_skybox", "立方体贴图天空盒", "纹理",
                "samplerCube、方向采样、天空盒技巧",
                D22CubemapSkybox.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D22CubemapSkybox();
            }
        }));

        // ═══ 第 7 阶段：光照与材质（多概念叠加）═══
        list.add(new DemoInfo(21, "basic_lighting", "Phong 光照", "光照与材质",
                "环境/漫反射/镜面 ADS 逐片元光照",
                D13BasicLighting.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D13BasicLighting();
            }
        }));
        list.add(new DemoInfo(22, "light_maps", "材质与光照贴图", "光照与材质",
                "漫反射/镜面/自发光贴图、多纹理单元",
                D14LightMaps.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D14LightMaps();
            }
        }));
        list.add(new DemoInfo(23, "normal_mapping", "法线贴图与 TBN", "光照与材质",
                "切线空间、法线扰动、程序化法线图",
                D15NormalMapping.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D15NormalMapping();
            }
        }));
        list.add(new DemoInfo(24, "fog", "雾效", "光照与材质",
                "线性/EXP/EXP2 距离雾公式",
                D16Fog.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D16Fog();
            }
        }));

        // ═══ 第 8 阶段：GPU 管线进阶 ═══
        list.add(new DemoInfo(25, "instancing", "实例化渲染", "高级·GPU 管线",
                "glDrawElementsInstanced + attribDivisor",
                D17Instancing.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D17Instancing();
            }
        }));
        list.add(new DemoInfo(26, "uniform_block", "Uniform Buffer", "高级·GPU 管线",
                "std140 布局、多 program 共享 uniform",
                D18UniformBlock.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D18UniformBlock();
            }
        }));
        list.add(new DemoInfo(27, "buffer_mapping", "缓冲映射", "高级·GPU 管线",
                "glMapBufferRange 流式顶点更新对比",
                D25BufferMapping.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D25BufferMapping();
            }
        }));
        list.add(new DemoInfo(28, "transform_feedback", "Transform Feedback", "高级·GPU 管线",
                "GPU 粒子、乒乓缓冲、RASTERIZER_DISCARD",
                D19TransformFeedback.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D19TransformFeedback();
            }
        }));

        // ═══ 第 9 阶段：帧缓冲与后处理（最难）═══
        list.add(new DemoInfo(29, "fbo_rtt", "帧缓冲与 RTT", "高级·帧缓冲",
                "FBO 附件、完整性检查、渲染到纹理",
                D20FboRtt.DESCRIPTION,
                null,
                subs(
                    sub("fbo_color", "小节1 · 颜色附件", "片元着色器的输出写在这里",
                        "场景被渲染进一张颜色纹理，再贴回屏幕——后处理的地基。",
                        "view", 0),
                    sub("fbo_depth", "小节2 · 深度附件", "灰度图 = 每像素离相机的距离",
                        "深度纹理可被采样：越近越亮、越远越暗（做了 pow 增强对比）。\n阴影贴图（第 38 章）正是基于它。",
                        "view", 1)),
                new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D20FboRtt();
            }
        }));
        list.add(new DemoInfo(30, "post_process", "后处理卷积", "高级·帧缓冲",
                "3×3 核：模糊/锐化/边缘/浮雕",
                D21PostProcess.DESCRIPTION,
                null,
                subs(
                    sub("post_sharpen", "小节1 · 锐化", "中心 5 邻域 -1：突出差异",
                        "轮廓变清晰。把【步长】调大，锐化会出现\"重影\"——采样跨格了。"),
                    sub("post_box", "小节2 · 盒式模糊", "全 1/9 邻域平均",
                        "最简单的模糊：所有邻居等权平均。步长调大模糊更强。"),
                    sub("post_gauss", "小节3 · 高斯模糊", "1,2,1,2,4,2,1,2,1 ÷16",
                        "按距离加权，比盒式更自然。正规引擎会拆成横竖两趟省算力。"),
                    sub("post_edge", "小节4 · 边缘检测", "邻域和 - 8×中心",
                        "平坦处≈0（黑），边缘处差异大（亮线）——\"线稿\"效果。")),
                new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D21PostProcess();
            }
        }));
        list.add(new DemoInfo(31, "msaa", "多重采样 MSAA", "高级·帧缓冲",
                "multisample renderbuffer + blit 解析",
                D23Msaa.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D23Msaa();
            }
        }));
        list.add(new DemoInfo(32, "mrt", "多渲染目标 MRT", "高级·帧缓冲",
                "glDrawBuffers、G-Buffer 延迟渲染雏形",
                D24Mrt.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D24Mrt();
            }
        }));

        return list;
    }

    public static DemoInfo byId(String id) {
        ArrayList<DemoInfo> list = all();
        for (DemoInfo info : list) {
            if (info.id.equals(id)) {
                return info;
            }
        }
        return null;
    }
}
