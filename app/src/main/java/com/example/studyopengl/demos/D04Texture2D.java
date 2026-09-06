package com.example.studyopengl.demos;

import android.opengl.GLES30;

import com.example.studyopengl.engine.BaseDemoEngine;
import com.example.studyopengl.gl.Mesh;
import com.example.studyopengl.gl.ShaderProgram;
import com.example.studyopengl.gl.TextureHelper;
import com.example.studyopengl.param.ParamSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 04 · 2D 纹理与采样：wrap/filter/混合 等全部采样状态。
 */
public class D04Texture2D extends BaseDemoEngine {

    public static final String DESCRIPTION = ""
            + "▍纹理对象与采样器\n"
            + "纹理 = 纹素数组 + 一组采样状态。GLSL 里 sampler2D 是\"采样器句柄\"，"
            + "它的值是纹理单元号：glActiveTexture(GL_TEXTURE0) 后 glBindTexture，"
            + "再 glUniform1i(loc, 0) 把单元 0 绑到 sampler 上（默认就是 0）。\n\n"
            + "▍环绕模式 wrap（uv 超出 [0,1] 的行为）\n"
            + "· GL_REPEAT 重复；GL_MIRRORED_REPEAT 镜像重复；GL_CLAMP_TO_EDGE 边缘延伸。"
            + "注意 ES3 没有 GL_CLAMP_TO_BORDER（桌面独有）。\n\n"
            + "▍过滤 filter（纹素与像素不对齐时怎么取）\n"
            + "放大 GL_NEAREST（块状）/GL_LINEAR（平滑）；缩小还有 4 种 mipmap 组合"
            + "（见 05 课）。使用 mipmap 的 min filter 必须先 glGenerateMipmap，否则纹理\"不完整\"采到黑色。\n\n"
            + "▍本例细节\n"
            + "· 纹理用 Canvas 程序化生成，GLUtils.texImage2D 直传 Bitmap；\n"
            + "· uv 缩放滑条让 uv 超出 0..1，直观展示三种 wrap；\n"
            + "· 混合滑条演示 shader 里 texture(u_tex, v_uv) 与纯色的 mix()；\n"
            + "· \"翻转Y\"开关展示图像坐标系(原点左上)与 GL 纹理坐标系(原点左下)的翻转：v_uv.y = 1.0 - v_uv.y。";

    private static final String VS = ""
            + "#version 300 es\n"
            + "layout(location=0) in vec3 a_pos;\n"
            + "layout(location=1) in vec2 a_uv;\n"
            + "uniform float u_uvScale;\n"
            + "uniform float u_flipY;\n"
            + "out vec2 v_uv;\n"
            + "void main() {\n"
            + "    vec2 uv = a_uv * u_uvScale;\n"          // 放大 uv 触发 wrap
            + "    v_uv = vec2(uv.x, mix(uv.y, 1.0 - uv.y, u_flipY));\n"
            + "    gl_Position = vec4(a_pos, 1.0);\n"
            + "}\n";

    private static final String FS = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec2 v_uv;\n"
            + "uniform sampler2D u_tex;\n"               // 采样器，值为纹理单元号
            + "uniform vec3 u_tint;\n"
            + "uniform float u_mix;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "    vec4 tex = texture(u_tex, v_uv);\n"    // 采样（隐式导数选 mip 层级）\n"
            + "    fragColor = vec4(mix(tex.rgb, tex.rgb * u_tint, u_mix), tex.a);\n"
            + "}\n";

    private static final String KEY_WRAP_S = "wrapS";
    private static final String KEY_WRAP_T = "wrapT";
    private static final String KEY_MIN = "min";
    private static final String KEY_MAG = "mag";
    private static final String KEY_SCALE = "scale";
    private static final String KEY_MIX = "mix";
    private static final String KEY_HUE = "hue";
    private static final String KEY_FLIP = "flip";

    private static final String[] WRAP_LABELS = {"GL_REPEAT", "GL_MIRRORED_REPEAT", "GL_CLAMP_TO_EDGE"};
    private static final String[] MIN_LABELS = {
            "GL_NEAREST", "GL_LINEAR",
            "GL_NEAREST_MIPMAP_NEAREST", "GL_NEAREST_MIPMAP_LINEAR",
            "GL_LINEAR_MIPMAP_NEAREST", "GL_LINEAR_MIPMAP_LINEAR"
    };
    private static final String[] MAG_LABELS = {"GL_NEAREST", "GL_LINEAR"};

    private static final int[] MIN_FILTERS = {
            GLES30.GL_NEAREST, GLES30.GL_LINEAR,
            GLES30.GL_NEAREST_MIPMAP_NEAREST, GLES30.GL_NEAREST_MIPMAP_LINEAR,
            GLES30.GL_LINEAR_MIPMAP_NEAREST, GLES30.GL_LINEAR_MIPMAP_LINEAR
    };
    private static final int[] MAG_FILTERS = {GLES30.GL_NEAREST, GLES30.GL_LINEAR};

    private ShaderProgram mProgram;
    private Mesh mQuad;
    private int mTexture;

    @Override
    public void onSurfaceCreated(int width, int height) {
        mProgram = new ShaderProgram(VS, FS);
        mQuad = new Mesh.Builder()
                .addBuffer(GeoGen_quad(0.85f),
                        new Mesh.Attrib(0, 3), new Mesh.Attrib(1, 2))
                .build();
        // 程序化生成"GL"标志纹理（Canvas 画字 + 网格底），带 mipmap
        mTexture = TextureHelper.createLogoTexture(512);
        applyTextureParams();
    }

    /** pos3 + uv2 交错四边形。 */
    private static float[] GeoGen_quad(float half) {
        return new float[]{
                -half, -half, 0, 0, 0,
                half, -half, 0, 1, 0,
                -half, half, 0, 0, 1,
                half, -half, 0, 1, 0,
                half, half, 0, 1, 1,
                -half, half, 0, 0, 1
        };
    }

    private void applyTextureParams() {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mTexture);
        TextureHelper.setParams(
                TextureHelper.wrapToGL(getOptionIndex(KEY_WRAP_S)),
                TextureHelper.wrapToGL(getOptionIndex(KEY_WRAP_T)),
                MIN_FILTERS[getOptionIndex(KEY_MIN)],
                MAG_FILTERS[getOptionIndex(KEY_MAG)]);
    }

    @Override
    public void onParamChanged(ParamSpec spec) {
        if (spec.key.equals(KEY_WRAP_S) || spec.key.equals(KEY_WRAP_T)
                || spec.key.equals(KEY_MIN) || spec.key.equals(KEY_MAG)) {
            applyTextureParams(); // glTexParameteri 必须在 GL 线程
        }
    }

    @Override
    public void onDrawFrame(float deltaTime) {
        clearFrame(0.06f, 0.08f, 0.12f);
        float[] tint = hsvToRgb(getFloat(KEY_HUE), 1f, 1f);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mTexture);

        mProgram.use();
        mProgram.set("u_tex", 0);
        mProgram.set("u_uvScale", getFloat(KEY_SCALE));
        mProgram.set("u_flipY", getBool(KEY_FLIP) ? 1f : 0f);
        mProgram.set("u_tint", tint[0], tint[1], tint[2]);
        mProgram.set("u_mix", getFloat(KEY_MIX));
        mQuad.draw(GLES30.GL_TRIANGLES);
    }

    @Override
    public List<ParamSpec> getParamSpecs() {
        ArrayList<ParamSpec> specs = new ArrayList<ParamSpec>();
        specs.add(ParamSpec.optionSpec(KEY_WRAP_S, "环绕 WRAP_S", WRAP_LABELS, 0));
        specs.add(ParamSpec.optionSpec(KEY_WRAP_T, "环绕 WRAP_T", WRAP_LABELS, 0));
        specs.add(ParamSpec.optionSpec(KEY_MIN, "缩小过滤 MIN", MIN_LABELS, 5));
        specs.add(ParamSpec.optionSpec(KEY_MAG, "放大过滤 MAG", MAG_LABELS, 1));
        specs.add(ParamSpec.floatSpec(KEY_SCALE, "UV 缩放（试 wrap）", 0.1f, 6f, 1f));
        specs.add(ParamSpec.floatSpec(KEY_MIX, "色调混合 mix()", 0f, 1f, 0f));
        specs.add(ParamSpec.floatSpec(KEY_HUE, "色调", 0f, 1f, 0.08f));
        specs.add(ParamSpec.boolSpec(KEY_FLIP, "翻转 Y 坐标", true));
        return specs;
    }

    @Override
    public void onSurfaceDestroyed() {
        mQuad.dispose();
        TextureHelper.deleteTexture(mTexture);
        mProgram.release();
    }
}
