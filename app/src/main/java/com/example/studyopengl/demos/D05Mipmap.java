package com.example.studyopengl.demos;

import android.opengl.GLES30;
import android.opengl.Matrix;

import com.example.studyopengl.engine.BaseDemoEngine;
import com.example.studyopengl.gl.Mesh;
import com.example.studyopengl.gl.ShaderProgram;
import com.example.studyopengl.gl.TextureHelper;
import com.example.studyopengl.param.ParamSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 05 · Mipmap 与 LOD：缩小过滤组合 + bias。
 */
public class D05Mipmap extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍为什么需要 mipmap\n"
            + "缩小时多个纹素映射进一个像素，只采 1 个纹素会闪现高频噪点（摩尔纹）。"
            + "mipmap 把纹理按 1/2 逐级降采样成完整链条，显存多花约 1/3；"
            + "采样时按屏幕像素的 uv 变化率（相邻像素差分）自动选层级，远处用模糊的低层级。\n\n"
            + "▍缩小过滤组合（大纹理映射到小屏幕时生效）\n"
            + "· GL_NEAREST / GL_LINEAR：不用 mipmap，高频细节直接闪烁；\n"
            + "· GL_x_MIPMAP_NEAREST：取最近 1 个 mip 层（块状过渡）；\n"
            + "· GL_x_MIPMAP_LINEAR：相邻两层各采一次再线性混合（三线性），层级过渡平滑。\n\n"
            + "▍手动控制层级\n"
            + "· texture(s, uv, bias)：在自动 LOD 上加偏移（仅片元着色器可用），"
            + "bias>0 更糊、<0 更锐；\n"
            + "· textureLod(s, uv, lod)：完全使用指定层级，不做自动差分（常用于 raymarching）。\n\n"
            + "▍创建方式\n"
            + "本例用 ES3 的 glTexStorage2D 一次性分配不可变 mip 链（levels = log2(size)+1），"
            + "glTexSubImage2D 填 level0 后 glGenerateMipmap 派生其余层；"
            + "注意 glGenerateMipmap 对完全黑色的输入会得到全黑链（降采样无信息）。\n\n"
            + "▍观察点\n"
            + "把 MIN 切到 GL_NEAREST 再对比 GL_LINEAR_MIPMAP_LINEAR，远处地面从\"雪花闪烁\"变成稳定灰色；"
            + "拖动 bias 滑条还能看到\"该糊不糊/不该糊先糊\"的中间状态。";

    private static final String VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec2 a_uv;\n"
            + "uniform mat4 u_mvp;\n"
            + "uniform float u_scroll;\n"
            + "out vec2 v_uv;\n"
            + "void main() {\n"
            + "    v_uv = vec2(a_uv.x, a_uv.y + u_scroll);\n" // 沿 z 向滚动，强化缩小感
            + "    gl_Position = u_mvp * vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec2 v_uv;\n"
            + "uniform sampler2D u_tex;\n"
            + "uniform float u_bias;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    // 带 bias 的采样：GLSL ES 3.00 片元着色器专用重载\n"
            + "    fragColor = texture(u_tex, v_uv, u_bias);\n"
            + "}\n";

    private static final String KEY_MIN = "min";
    private static final String KEY_BIAS = "bias";
    private static final String KEY_REPEAT = "repeat";
    private static final String KEY_SCROLL = "scroll";

    private static final String[] MIN_LABELS = {
            "GL_NEAREST", "GL_LINEAR",
            "GL_NEAREST_MIPMAP_NEAREST", "GL_NEAREST_MIPMAP_LINEAR",
            "GL_LINEAR_MIPMAP_NEAREST", "GL_LINEAR_MIPMAP_LINEAR（三线性）"
    };
    private static final int[] MIN_FILTERS = {
            GLES30.GL_NEAREST, GLES30.GL_LINEAR,
            GLES30.GL_NEAREST_MIPMAP_NEAREST, GLES30.GL_NEAREST_MIPMAP_LINEAR,
            GLES30.GL_LINEAR_MIPMAP_NEAREST, GLES30.GL_LINEAR_MIPMAP_LINEAR
    };

    private ShaderProgram mProgram;
    private Mesh mGround;
    private int mTexture;
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mMvp = new float[16];
    private float mScroll;
    private float mCurRepeat = 24f;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProgram = new ShaderProgram(VS, FS);
        rebuildGround(getFloat(KEY_REPEAT));
        mTexture = TextureHelper.createCheckerTexture(256);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mTexture);
        TextureHelper.setParams(GLES30.GL_REPEAT, GLES30.GL_REPEAT,
                MIN_FILTERS[getOptionIndex(KEY_MIN)], GLES30.GL_LINEAR);
    }

    private void rebuildGround(float repeat) {
        if (mGround != null) mGround.dispose();
        mCurRepeat = repeat;
        // 地面纵深 60 单位，远处剧烈缩小 -> mip 层级清晰可见
        com.example.studyopengl.gl.GeoGen.GeoData data =
                com.example.studyopengl.gl.GeoGen.gridPlane(96, 60f, repeat);
        int vertCount = data.positions.length / 3;
        float[] interleaved = new float[vertCount * 8];
        for (int i = 0; i < vertCount; i++) {
            interleaved[i * 8] = data.positions[i * 3];
            interleaved[i * 8 + 1] = data.positions[i * 3 + 1];
            interleaved[i * 8 + 2] = data.positions[i * 3 + 2];
            interleaved[i * 8 + 3] = data.normals[i * 3];
            interleaved[i * 8 + 4] = data.normals[i * 3 + 1];
            interleaved[i * 8 + 5] = data.normals[i * 3 + 2];
            interleaved[i * 8 + 6] = data.uvs[i * 2];
            interleaved[i * 8 + 7] = data.uvs[i * 2 + 1];
        }
        mGround = new Mesh.Builder()
                .addBuffer(interleaved, new Mesh.Attrib(0, 3),
                        new Mesh.Attrib(1, 3), new Mesh.Attrib(2, 2))
                .setIndices(data.indices)
                .build();
    }

    @Override
    public void onParamChanged(ParamSpec spec) {
        if (spec.key.equals(KEY_MIN)) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mTexture);
            TextureHelper.setParams(GLES30.GL_REPEAT, GLES30.GL_REPEAT,
                    MIN_FILTERS[getOptionIndex(KEY_MIN)], GLES30.GL_LINEAR);
        } else if (spec.key.equals(KEY_REPEAT)) {
            float r = getFloat(KEY_REPEAT);
            if (Math.abs(r - mCurRepeat) > 0.5f) {
                rebuildGround(r); // uv 布局变了要重建缓冲（GL 线程内）
            }
        }
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        clearFrame(0.35f, 0.55f, 0.75f); // 天空色
        mScroll += getFloat(KEY_SCROLL) * deltaTime;

        Matrix.setIdentityM(mProj, 0);
        Matrix.perspectiveM(mProj, 0, 55f, aspect(), 0.1f, 120f);
        // 低机位平视远方，地平线自然出现
        Matrix.setLookAtM(mView, 0,
                0f, 1.4f, 6f,
                0f, 0.6f, -10f,
                0f, 1f, 0f);
        float[] model = new float[16];
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, 0, 0, -24f);
        Matrix.multiplyMM(mMvp, 0, mProj, 0, mView, 0);
        float[] mvp = new float[16];
        Matrix.multiplyMM(mvp, 0, mMvp, 0, model, 0);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mTexture);
        mProgram.use();
        mProgram.setMat4("u_mvp", mvp);
        mProgram.set("u_tex", 0);
        mProgram.set("u_bias", getFloat(KEY_BIAS));
        mProgram.set("u_scroll", mScroll);
        mGround.draw(GLES30.GL_TRIANGLES);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.optionSpec(KEY_MIN, "缩小过滤", MIN_LABELS, 5));
        specs.add(ParamSpec.floatSpec(KEY_BIAS, "LOD bias texture(s,uv,bias)", -3f, 3f, 0f, "%.1f"));
        specs.add(ParamSpec.intSpec(KEY_REPEAT, "UV 重复次数", 4, 64, 24));
        specs.add(ParamSpec.floatSpec(KEY_SCROLL, "滚动速度", 0f, 2f, 0.3f));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mGround.dispose();
        TextureHelper.deleteTexture(mTexture);
        mProgram.release();
    }
}
