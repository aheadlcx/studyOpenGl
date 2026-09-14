package com.example.studyopengl.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.example.studyopengl.R;

/**
 * 术语速查：新手学 GL 最大的拦路虎是满屏英文缩写。
 * 这里把高频术语收进一个词典：一句人话解释 + 该去哪一章看活例子。
 * 数据就在本文件里（纯静态），想加词条照着 {术语, 解释, 看哪章} 追加即可。
 */
public class GlossaryActivity extends Activity {

    /** {词条, 人话解释, 去哪看} */
    private static final String[][] TERMS = {
        // ---------- 管线与整体 ----------
        {"GPU", "显卡里的并行计算器。它和 CPU 的分工：CPU 发号施令，GPU 成千上万个「小工人」同时算每个顶点、每个像素。", "01 渲染管线全景"},
        {"渲染管线 (Pipeline)", "一帧画面在 GPU 里流过的固定流水线：顶点数据 → 顶点着色器 → 光栅化 → 片元着色器 → 各种测试 → 上屏。", "01 / 06"},
        {"上下文 (Context)", "GL 的「工作台」：所有 GL 状态、GL 对象都记在它名下。所以渲染代码必须在「持有上下文」的线程里跑。", "附录·EGL 与 GLThread"},
        {"EGL", "连接「窗口系统」和 GL 的桥梁：负责创建画布(Surface)、上下文、交换缓冲。GLSurfaceView 帮你做了，本项目手写一遍。", "附录·EGL 与 GLThread"},
        {"双缓冲 / SwapBuffers", "GPU 画在「后台缓冲」，画完一帧和「前台缓冲」交换一下再上屏——避免看到画到一半的画面。", "06 / 附录"},
        {"VSync 垂直同步", "屏幕的刷新节拍（通常 60Hz）。每帧等一次 vsync 再交换，帧率就锁定 60，Choreographer 就是干这个的。", "附录·GLThread"},
        // ---------- 数据与缓冲 ----------
        {"顶点 (Vertex)", "几何图形的「角点」。三角形 3 个、立方体 24 个（每面 4 个角）。数据就是一个 float 数组。", "02"},
        {"VBO (Vertex Buffer Object)", "顶点缓冲对象：显存里的一块内存，把顶点数据从 CPU 内存一次性拷进去，之后每帧直接用。", "02"},
        {"VAO (Vertex Array Object)", "顶点数组对象：一张「配置清单」，记录用了哪个 VBO、每项数据怎么解读。ES3 必备，一次配置终身受用。", "02 / 07"},
        {"EBO / 索引 (Index)", "顶点索引缓冲：多个三角形共享顶点时只存一份顶点，用编号复用，省显存。", "09"},
        {"交错布局 (Interleaved)", "一个顶点的 位置+颜色+uv 连着放，再放下一个顶点。 opposed 分离布局是所有位置一摞、所有颜色一摞。", "02"},
        {"stride / offset", "读交错数据的口诀：stride=每个顶点占多少字节，offset=这一项从第几字节开始。", "02"},
        {"glBufferData vs glBufferSubData", "前者「分配+一次性灌入」，后者「只更新一部分」。流式更新数据用后者或 glMapBufferRange。", "27"},
        {"缓冲映射 (MapBuffer)", "把显存映射成 Java 可写的内存直接改，省一次拷贝；改完 unmap 归还。", "27"},
        // ---------- 着色器 ----------
        {"着色器 (Shader)", "跑在 GPU 上的小程序。只有两种必须会写：顶点着色器（每个顶点跑一次）+ 片元着色器（每个像素跑一次）。", "07"},
        {"GLSL", "写着色器的语言（类 C）。ES 3.0 对应 #version 300 es，in/out 传值，片元着色器必须声明精度。", "附录·GLSL 速查"},
        {"顶点着色器 (VS)", "顶点流水线工人：输入一个顶点，输出它在屏幕上的最终位置 gl_Position。想移动/变形几何体就在这里写。", "03 / 07"},
        {"片元着色器 (FS)", "像素流水线工人：输入插值好的数据，输出这个像素的颜色。想要什么花纹、光照就在这里算。", "05 / 07"},
        {"uniform", "全局常量：一次设置、整个 draw 所有顶点/像素共用（比如 MVP 矩阵、时间、灯光位置）。", "03"},
        {"in / out（旧称 attribute/varying）", "着色器之间的传值通道：VS 的 out 插值后变成 FS 的 in。顶点数据则通过 layout(location=N) in 进 VS。", "07 / 08"},
        {"插值 (Interpolation)", "三角形三个顶点颜色不同，中间像素的颜色 GPU 自动按距离加权混合——渐变就是这么来的。", "08"},
        {"MVP 矩阵", "三个 4x4 矩阵连乘：Model(物体摆放) × View(相机位置) × Projection(透视压扁)。顶点乘它就落到屏幕正确位置。", "03 / 12"},
        {"局部/世界/相机/裁剪空间", "顶点的四段旅程：建模坐标 → 放进场景 → 相机视角 → 投影到「裁剪立方体」，再除以 w 变成 NDC。", "03"},
        {"NDC 归一化设备坐标", "裁剪空间除以 w 后的坐标，x/y/z 都在 -1..1，(0,0) 是屏幕正中。", "03"},
        {"法线 (Normal)", "垂直于表面的方向向量，长度 1。光照全靠它：N·L 决定这面有多亮。", "21 / 23"},
        // ---------- 纹理 ----------
        {"纹理 (Texture)", "贴在模型表面的图片（显存里的 2D 数组）。也可以当「数据表」用。", "17"},
        {"UV 坐标", "纹理上的地址：u/v 都在 0..1，(0,0) 在左上（GL 传统在左下，注意方向）。每个顶点带一个 uv，像素的 uv 靠插值。", "17"},
        {"采样 (Sampler)", "按 uv 去「取」纹理颜色这个动作。sampler2D 就是 FS 里代表「一张 2D 纹理」的变量类型。", "17"},
        {"纹理单元 (Texture Unit)", "同时贴多张图时用的「插槽编号」：glActiveTexture 选插槽、绑纹理、uniform 里填插槽号。", "22 / 33"},
        {"过滤 (Filtering)", "纹理被放大/缩小时怎么取色：GL_NEAREST 锐利马赛克、GL_LINEAR 平滑。", "17"},
        {"Mipmap", "预先生成一串减半的小图，远处用小图，又快又抗闪烁。LOD 就是「该用第几张小图」。", "18"},
        {"纹理数组 (TEXTURE_2D_ARRAY)", "一堆同尺寸纹理打包成一摞，一次 draw 按「层号」取——六面不同贴图的性能方案。", "19 / 33"},
        {"立方体贴图 (Cubemap)", "6 张图组成一个「盒子」，按方向采样。天空盒、环境反射都用它。", "20"},
        // ---------- 状态与测试 ----------
        {"深度缓冲 (Depth Buffer)", "每像素记录「离相机多远」。新片元更近才通过——这就是近处挡住远处的原理。", "14"},
        {"模板缓冲 (Stencil Buffer)", "每像素一个整数「记号本」，配合测试实现「只在某些区域画」：镜子、小地图、描边。", "16"},
        {"混合 (Blending)", "新片元和已有颜色按透明度混出的技术，半透明玻璃的核心。注意要关闭深度写入。", "15 / 06"},
        {"面剔除 (Face Culling)", "背对相机的三角形直接不画（按顶点绕序判断），性能白赚一半。绕序错了图形会「消失」。", "11 / 33"},
        {"视口 (Viewport) / 裁剪 (Scissor)", "viewport=3D 画面映射到窗口哪块矩形；scissor=只允许在窗口某块矩形里画。", "10"},
        // ---------- 光照 ----------
        {"Phong 光照", "三件套：环境光(恒亮) + 漫反射(N·L) + 高光(reflect·V)。一个公式让物体有立体感。", "21"},
        {"光照贴图 / 材质", "用多张纹理分别控制 漫反射色/高光强度 等，让同一光照下物体质感不同。", "22"},
        {"法线贴图 (Normal Mapping)", "把凹凸信息存进一张「法线纹理」，低模也能照出高模的细节感。需要 TBN 矩阵换坐标系。", "23"},
        // ---------- 进阶 ----------
        {"实例化渲染 (Instancing)", "一次 draw 画几千份几何体（草地、粒子），每份的差异用「实例属性」区分。", "25"},
        {"UBO (Uniform Buffer)", "把一批 uniform 打包进缓冲，多个着色器程序共享，改一处全体生效。", "26"},
        {"Transform Feedback", "把顶点着色器的输出直接写回缓冲：粒子模拟全程 GPU 闭环，配「乒乓缓冲」交替读写。", "28"},
        {"FBO / RTT", "帧缓冲对象 = 自己组装的「画布」（颜色/深度/模板附件）。渲染到纹理 (RTT) 是后处理和镜子/监控画面的基础。", "29"},
        {"后处理 (Post-Processing)", "把整帧先画进 FBO，再套一个全屏四边形用卷积核二次加工：模糊/锐化/边缘。", "30"},
        {"MSAA 多重采样", "每个像素采样多次取平均，把三角形边缘的锯齿磨平。", "31"},
        {"MRT (Multiple Render Targets)", "一次 draw 同时写多张颜色纹理，延迟渲染(G-Buffer)的地基。", "32"},
        {"glGetError / 调试", "GL 出错不崩溃、只「画错」。排查三板斧：铺梯度色、线框叠加、glReadPixels 探针——本章综合实战全用上。", "33"},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_glossary);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        ListView list = (ListView) findViewById(R.id.glossary_list);
        list.setAdapter(new GlossaryAdapter(getLayoutInflater()));
    }

    private static class GlossaryAdapter extends BaseAdapter {
        private final LayoutInflater mInflater;

        GlossaryAdapter(LayoutInflater inflater) {
            mInflater = inflater;
        }

        @Override
        public int getCount() {
            return TERMS.length;
        }

        @Override
        public String[] getItem(int position) {
            return TERMS[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = mInflater.inflate(R.layout.item_glossary, parent, false);
            }
            String[] term = getItem(position);
            ((TextView) v.findViewById(R.id.tv_term)).setText(term[0]);
            ((TextView) v.findViewById(R.id.tv_meaning)).setText(term[1]);
            ((TextView) v.findViewById(R.id.tv_see)).setText("📍 去看：" + term[2]);
            return v;
        }
    }
}
