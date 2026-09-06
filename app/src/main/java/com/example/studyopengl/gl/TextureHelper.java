package com.example.studyopengl.gl;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * 程序化纹理工厂：不依赖任何图片资源，全部用 Canvas/像素算法生成，
 * 演示多种上传路径：
 *  - GLUtils.texImage2D（Bitmap 直传，内部处理了行对齐）
 *  - glTexStorage2D + glTexSubImage2D（ES3 不可变存储）
 *  - glTexImage2D + 手动 RGBA 字节流（UNPACK_ALIGNMENT 细节）
 *  - glTexImage3D（GL_TEXTURE_2D_ARRAY，ES3 新增）
 */
public final class TextureHelper {

    private static final String TAG = "TextureHelper";

    public static final int WRAP_REPEAT = 0;
    public static final int WRAP_MIRRORED = 1;
    public static final int WRAP_CLAMP = 2;

    private TextureHelper() {
    }

    public static int wrapToGL(int wrap) {
        switch (wrap) {
            case WRAP_MIRRORED:
                return GLES30.GL_MIRRORED_REPEAT;
            case WRAP_CLAMP:
                return GLES30.GL_CLAMP_TO_EDGE; // 注意：ES 没有 CLAMP_TO_BORDER
            case WRAP_REPEAT:
            default:
                return GLES30.GL_REPEAT;
        }
    }

    // ==================== Bitmap 生成 ====================

    public static Bitmap makeCheckerBitmap(int size, int cells, int colorA, int colorB) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint();
        float cell = size / (float) cells;
        for (int y = 0; y < cells; y++) {
            for (int x = 0; x < cells; x++) {
                paint.setColor((x + y) % 2 == 0 ? colorA : colorB);
                canvas.drawRect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell, paint);
            }
        }
        return bmp;
    }

    /** 砖墙：灰浆底 + 交错砖块，每块颜色微随机。 */
    public static Bitmap makeBrickBitmap(int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.rgb(120, 112, 104)); // 灰浆
        Paint paint = new Paint();
        Random rnd = new Random(7);
        int rows = 6;
        int brickH = h / rows;
        int brickW = w / 3;
        for (int row = 0; row < rows; row++) {
            int offset = (row % 2 == 0) ? 0 : brickW / 2;
            for (int col = -1; col <= w / brickW; col++) {
                int shade = 150 + rnd.nextInt(60);
                paint.setColor(Color.rgb(shade, shade - 35 - rnd.nextInt(20), shade - 50));
                float left = col * brickW + offset + 4;
                float top = row * brickH + 4;
                canvas.drawRect(left, top, left + brickW - 8, top + brickH - 8, paint);
            }
        }
        return bmp;
    }

    /** 白噪点（灰度），用作镜面/自发光遮罩。 */
    public static Bitmap makeNoiseBitmap(int w, int h, long seed) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[w * h];
        Random rnd = new Random(seed);
        for (int i = 0; i < pixels.length; i++) {
            int g = 40 + rnd.nextInt(215);
            // 让部分像素接近黑色，模拟粗糙度斑块
            if (rnd.nextInt(5) == 0) g = 20;
            pixels[i] = Color.rgb(g, g, g);
        }
        bmp.setPixels(pixels, 0, w, 0, 0, w, h);
        return bmp;
    }

    /** 粒子精灵：中心亮、边缘透明的径向渐变，配 gl_PointCoord 使用。 */
    public static Bitmap makeParticleBitmap(int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint();
        paint.setShader(new RadialGradient(size / 2f, size / 2f, size / 2f,
                new int[]{Color.WHITE, 0xCCFFFFFF, 0x00000000},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        return bmp;
    }

    /** 星空（立方体贴图的一个面）。 */
    private static Bitmap makeStarFaceBitmap(int size, long seed, int topColor, int bottomColor) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint();
        paint.setShader(new LinearGradient(0, 0, 0, size, topColor, bottomColor,
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, size, size, paint);
        Random rnd = new Random(seed);
        paint.setShader(null);
        for (int i = 0; i < 60; i++) {
            int brightness = 160 + rnd.nextInt(95);
            paint.setColor(Color.rgb(brightness, brightness, brightness));
            float x = rnd.nextInt(size);
            float y = rnd.nextInt(size);
            float r = 1f + rnd.nextFloat() * (size / 128f);
            canvas.drawCircle(x, y, r, paint);
        }
        return bmp;
    }

    /** 彩虹同心环（纹理数组演示用层）。 */
    private static Bitmap makeRingsBitmap(int size, int rings) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint();
        paint.setShader(new RadialGradient(size / 2f, size / 2f, size / 2f,
                new int[]{0xFFF44336, 0xFFFFEB3B, 0xFF4CAF50, 0xFF2196F3},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size / (float) rings / 2f);
        paint.setColor(Color.argb(180, 20, 20, 20));
        for (int i = 1; i <= rings; i++) {
            canvas.drawCircle(size / 2f, size / 2f, size / 2f * i / rings, paint);
        }
        return bmp;
    }

    /** 条纹图（自发光贴图）：部分横条亮起。 */
    public static Bitmap makeStripesBitmap(int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.BLACK);
        Paint paint = new Paint();
        int bands = 8;
        for (int i = 0; i < bands; i++) {
            if (i % 3 == 0) {
                paint.setColor(Color.rgb(90, 220, 255));
                canvas.drawRect(0, i * h / bands, w, (i + 1) * h / bands, paint);
            }
        }
        return bmp;
    }

    public static int createStripesTexture(int size) {
        return createBitmapTexture(makeStripesBitmap(size, size), true);
    }

    /**
     * 文字标签纹理（GL 内绘制阶段名用）：白字 + 黑色描边阴影，透明背景。
     * 返回的纹理配合"单位矩形 + u_rect 缩放偏移"即可贴在任意视口角落。
     */
    public static int createTextTexture(String text, int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextSize(h * 0.5f);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setShadowLayer(8, 0, 0, Color.argb(220, 0, 0, 0));
        float y = h / 2f - (paint.ascent() + paint.descent()) / 2f;
        canvas.drawText(text, 14, y, paint);
        int id = createBitmapTexture(bmp, false);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id);
        setParams(GLES30.GL_CLAMP_TO_EDGE, GLES30.GL_CLAMP_TO_EDGE,
                GLES30.GL_LINEAR, GLES30.GL_LINEAR);
        return id;
    }

    /** "GL" 标志纹理：网格底 + 圆角框 + 文字，验证纹理方向是否翻转最直观。 */    public static Bitmap makeLogoBitmap(int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.rgb(46, 58, 74));
        Paint paint = new Paint();
        paint.setColor(Color.rgb(62, 78, 98));
        float grid = size / 8f;
        for (int i = 1; i < 8; i++) {
            canvas.drawLine(i * grid, 0, i * grid, size, paint);
            canvas.drawLine(0, i * grid, size, i * grid, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size / 32f);
        paint.setColor(Color.rgb(79, 195, 247));
        canvas.drawRect(size / 16f, size / 16f, size - size / 16f, size - size / 16f, paint);
        // 左上角亮块：翻转 Y 后它应出现在屏幕左下
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(255, 183, 77));
        canvas.drawRect(0, 0, size / 4f, size / 4f, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(size * 0.42f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setColor(Color.WHITE);
        canvas.drawText("GL", size / 2f, size * 0.66f, paint);
        return bmp;
    }

    public static int createLogoTexture(int size) {
        return createBitmapTexture(makeLogoBitmap(size), true);
    }

    // ==================== 纹理创建 ====================

    /** Bitmap 直传（GLUtils 内部做 UNPACK 对齐处理），生成 mipmap。 */
    public static int createBitmapTexture(Bitmap bmp, boolean mipmaps) {
        int[] ids = new int[1];
        GLES30.glGenTextures(1, ids, 0);
        int id = ids[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id);
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0);
        setParams(GLES30.GL_REPEAT, GLES30.GL_REPEAT,
                mipmaps ? GLES30.GL_LINEAR_MIPMAP_LINEAR : GLES30.GL_LINEAR,
                GLES30.GL_LINEAR);
        if (mipmaps) {
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);
        }
        return id;
    }

    /**
     * 演示 ES3 的"不可变存储"上传路径：glTexStorage2D 一次性分配完整 mip 链，
     * 之后用 glTexSubImage2D 填充 level0，再 glGenerateMipmap 补全其余层。
     * 不可变存储分配后格式/尺寸不可改，驱动可以预优化内存布局。
     */
    public static int createCheckerTexture(int size) {
        Bitmap bmp = makeCheckerBitmap(size, 8, Color.WHITE, Color.rgb(70, 130, 180));
        int levels = 32 - Integer.numberOfLeadingZeros(size);
        int[] ids = new int[1];
        GLES30.glGenTextures(1, ids, 0);
        int id = ids[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id);
        GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, levels, GLES30.GL_RGBA8, size, size);
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, size, size,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, bitmapToBuffer(bmp));
        setParams(GLES30.GL_REPEAT, GLES30.GL_REPEAT,
                GLES30.GL_LINEAR_MIPMAP_LINEAR, GLES30.GL_LINEAR);
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);
        return id;
    }

    public static int createBrickTexture(int w, int h) {
        return createBitmapTexture(makeBrickBitmap(w, h), true);
    }

    public static int createNoiseTexture(int w, int h, long seed) {
        return createBitmapTexture(makeNoiseBitmap(w, h, seed), true);
    }

    public static int createParticleTexture(int size) {
        return createBitmapTexture(makeParticleBitmap(size), false);
    }

    /**
     * 程序化法线贴图：由砖墙高度场中心差分求梯度，
     * 编码 RGB = normal * 0.5 + 0.5（切线空间，+Z 朝外）。
     */
    public static int createBrickNormalTexture(int size) {
        float brickW = size / 3f;
        float brickH = size / 6f;
        int[] pixels = new int[size * size];
        float dx = 1.5f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                // 高度场中心差分求梯度
                float hR = brickHeight((x + dx) % size, y, brickW, brickH);
                float hL = brickHeight((x - dx + size) % size, y, brickW, brickH);
                float hD = brickHeight(x, (y + dx) % size, brickW, brickH);
                float hU = brickHeight(x, (y - dx + size) % size, brickW, brickH);
                float nx = (hL - hR) * 2.2f;
                float ny = (hU - hD) * 2.2f;
                float nz = 1f;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                int r = (int) ((nx / len * 0.5f + 0.5f) * 255f);
                int g = (int) ((ny / len * 0.5f + 0.5f) * 255f);
                int b = (int) ((nz / len * 0.5f + 0.5f) * 255f);
                pixels[y * size + x] = Color.argb(255, r, g, b);
            }
        }
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        bmp.setPixels(pixels, 0, size, 0, 0, size, size);
        return createBitmapTexture(bmp, true);
    }

    /** 砖墙高度场：砖面 1、灰浆缝 0，边缘 0.06 宽度内平滑过渡。 */
    private static float brickHeight(float x, float y, float brickW, float brickH) {
        int rowIdx = (int) (y / brickH);
        float fy = y / brickH - rowIdx;
        float colOffset = (rowIdx % 2 == 0) ? 0 : brickW / 2f;
        float fx = ((x + colOffset) / brickW) % 1f;
        if (fx < 0) fx += 1f;
        float gap = 0.08f;
        if (fy < gap || fy > 1 - gap || fx < gap || fx > 1 - gap) {
            return 0f;
        }
        float edge = Math.min(fy - gap, Math.min(gap + 1 - fy, Math.min(fx - gap, gap + 1 - fx)));
        return Math.min(1f, edge / 0.06f);
    }

    /**
     * 立方体贴图：6 个面各自上传一次。
     * 目标顺序固定：+X, -X, +Y, -Y, +Z, -Z。
     * samplerCube 在 shader 里用"方向向量"采样，返回该方向命中的面的纹素。
     */
    public static int createStarCubemap(int size) {
        int[] ids = new int[1];
        GLES30.glGenTextures(1, ids, 0);
        int id = ids[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, id);
        int[] faceSeeds = {11, 22, 33, 44, 55, 66};
        for (int i = 0; i < 6; i++) {
            Bitmap face = makeStarFaceBitmap(size, faceSeeds[i],
                    Color.rgb(8, 14, 34), Color.rgb(2, 4, 12));
            GLUtils.texImage2D(GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, face, 0);
            face.recycle();
        }
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP,
                GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP,
                GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP,
                GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP,
                GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP,
                GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        return id;
    }

    /**
     * 2D 纹理数组（ES3 新增）：glTexImage3D 一次上传 layers 层等尺寸 2D 图像。
     * shader 里 sampler2DArray 用 vec3(u, v, layer) 采样，layer 可以来自顶点属性。
     */
    public static int createTexture2DArray(int size, int layers) {
        Bitmap[] layerBitmaps = new Bitmap[]{
                makeCheckerBitmap(size, 8, Color.WHITE, Color.rgb(200, 60, 60)),
                makeBrickBitmap(size, size),
                makeNoiseBitmap(size, size, 99),
                makeRingsBitmap(size, 6)
        };
        ByteBuffer all = ByteBuffer.allocateDirect(size * size * 4 * layers);
        for (int l = 0; l < layers; l++) {
            all.put(bitmapToBuffer(layerBitmaps[l]));
        }
        all.position(0);

        int[] ids = new int[1];
        GLES30.glGenTextures(1, ids, 0);
        int id = ids[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, id);
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_2D_ARRAY, 0, GLES30.GL_RGBA8,
                size, size, layers, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, all);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY,
                GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY,
                GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY,
                GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY,
                GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT);
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D_ARRAY);
        return id;
    }

    /** 统一设置 2D 纹理采样参数。 */
    public static void setParams(int wrapS, int wrapT, int minFilter, int magFilter) {
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_WRAP_S, wrapS);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_WRAP_T, wrapT);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MIN_FILTER, minFilter);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MAG_FILTER, magFilter);
    }

    public static void deleteTexture(int id) {
        if (id != 0) {
            GLES30.glDeleteTextures(1, new int[]{id}, 0);
        }
    }

    /** Bitmap ARGB -> GL 需要的 RGBA 字节序（GLUtils 内部亦做此转换）。 */
    public static ByteBuffer bitmapToBuffer(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] pixels = new int[w * h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);
        ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 4);
        for (int p : pixels) {
            buffer.put((byte) Color.red(p));
            buffer.put((byte) Color.green(p));
            buffer.put((byte) Color.blue(p));
            buffer.put((byte) Color.alpha(p));
        }
        buffer.position(0);
        return buffer;
    }
}
