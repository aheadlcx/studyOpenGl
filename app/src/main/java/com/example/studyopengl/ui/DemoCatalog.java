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
                D28VertexData.DESCRIPTION, D28VertexData.CODE, new DemoInfo.Factory() {
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
                D31FragmentShader.DESCRIPTION, D31FragmentShader.CODE, new DemoInfo.Factory() {
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
                D02PrimitiveTypes.DESCRIPTION, new DemoInfo.Factory() {
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
                D08DepthTest.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D08DepthTest();
            }
        }));

        // ═══ 第 5 阶段：状态组合（混合/模板）═══
        list.add(new DemoInfo(15, "blending", "混合 Blending", "进阶·状态",
                "glBlendFunc/glBlendEquation 因子矩阵",
                D09Blending.DESCRIPTION, new DemoInfo.Factory() {
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
                D20FboRtt.DESCRIPTION, new DemoInfo.Factory() {
            public com.example.studyopengl.engine.DemoEngine create() {
                return new D20FboRtt();
            }
        }));
        list.add(new DemoInfo(30, "post_process", "后处理卷积", "高级·帧缓冲",
                "3×3 核：模糊/锐化/边缘/浮雕",
                D21PostProcess.DESCRIPTION, new DemoInfo.Factory() {
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
