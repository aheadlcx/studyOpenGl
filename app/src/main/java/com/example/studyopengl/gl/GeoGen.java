package com.example.studyopengl.gl;

/**
 * 程序化几何生成器。所有顶点绕序统一为【从外部看逆时针 CCW】，
 * 与 glFrontFace(GL_CCW) + glCullFace(GL_BACK) 默认约定一致。
 */
public final class GeoGen {

    private GeoGen() {
    }

    /** 一组几何数据：分离的属性数组 + 索引，Demo 按需拼装 interleaved 布局。 */
    public static final class GeoData {
        public float[] positions;
        public float[] normals;
        public float[] uvs;
        public int[] indices;
    }

    /**
     * 立方体（24 顶点：每面 4 个独立顶点，法线/UV 不共享）+ 36 索引。
     * 边长 1，中心在原点。
     */
    public static GeoData cube() {
        float[][] faces = {
                {1, 0, 0}, {-1, 0, 0},   // ±X
                {0, 1, 0}, {0, -1, 0},   // ±Y
                {0, 0, 1}, {0, 0, -1}    // ±Z
        };
        // 每个面的切线基 u/v，保证 cross(u,v)=法线 => 顶点序从外看 CCW
        float[][] uAxes = {
                {0, 0, -1}, {0, 0, 1},
                {1, 0, 0}, {1, 0, 0},
                {1, 0, 0}, {-1, 0, 0}
        };
        float[][] vAxes = {
                {0, 1, 0}, {0, 1, 0},
                {0, 0, -1}, {0, 0, 1},
                {0, 1, 0}, {0, 1, 0}
        };

        float[] pos = new float[24 * 3];
        float[] nrm = new float[24 * 3];
        float[] uv = new float[24 * 2];
        int[] idx = new int[36];

        int p = 0, n = 0, t = 0;
        for (int f = 0; f < 6; f++) {
            float[] face = faces[f];
            float[] u = uAxes[f];
            float[] v = vAxes[f];
            for (int j = 0; j < 2; j++) {
                for (int i = 0; i < 2; i++) {
                    float a = i - 0.5f, b = j - 0.5f;
                    pos[p++] = face[0] * 0.5f + u[0] * a + v[0] * b;
                    pos[p++] = face[1] * 0.5f + u[1] * a + v[1] * b;
                    pos[p++] = face[2] * 0.5f + u[2] * a + v[2] * b;
                    nrm[n++] = face[0];
                    nrm[n++] = face[1];
                    nrm[n++] = face[2];
                    uv[t++] = i;
                    uv[t++] = j;
                }
            }
            int base = f * 4;
            int k = f * 6;
            // 两个三角形：(0,1,2) (2,1,3)，角点顺序 (0,0)(1,0)(0,1)(1,1)
            idx[k] = base;
            idx[k + 1] = base + 1;
            idx[k + 2] = base + 2;
            idx[k + 3] = base + 2;
            idx[k + 4] = base + 1;
            idx[k + 5] = base + 3;
        }

        GeoData data = new GeoData();
        data.positions = pos;
        data.normals = nrm;
        data.uvs = uv;
        data.indices = idx;
        return data;
    }

    /** UV 球：顶点 (stacks+1)*(slices+1)，从外部看 CCW。 */
    public static GeoData sphere(int stacks, int slices, float radius) {
        int vertCount = (stacks + 1) * (slices + 1);
        float[] pos = new float[vertCount * 3];
        float[] nrm = new float[vertCount * 3];
        float[] uv = new float[vertCount * 2];

        int p = 0, t = 0;
        for (int i = 0; i <= stacks; i++) {
            float theta = (float) (Math.PI * i / stacks);       // 0..PI，自 +Y 向下
            float sinT = (float) Math.sin(theta);
            float cosT = (float) Math.cos(theta);
            for (int j = 0; j <= slices; j++) {
                float phi = (float) (2.0 * Math.PI * j / slices);
                float x = sinT * (float) Math.cos(phi);
                float y = cosT;
                float z = sinT * (float) Math.sin(phi);
                pos[p++] = x * radius;
                pos[p++] = y * radius;
                pos[p++] = z * radius;
                nrm[p - 3] = x;
                nrm[p - 2] = y;
                nrm[p - 1] = z;
                uv[t++] = (float) j / slices;
                uv[t++] = 1f - (float) i / stacks;
            }
        }

        int quadCount = stacks * slices;
        int[] idx = new int[quadCount * 6];
        int k = 0;
        int rowStride = slices + 1;
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                int first = i * rowStride + j;
                int second = first + rowStride;
                idx[k++] = first;
                idx[k++] = first + 1;
                idx[k++] = second;
                idx[k++] = second;
                idx[k++] = first + 1;
                idx[k++] = second + 1;
            }
        }

        GeoData data = new GeoData();
        data.positions = pos;
        data.normals = nrm;
        data.uvs = uv;
        data.indices = idx;
        return data;
    }

    /**
     * XZ 平面网格（法线 +Y），边长 size，segments 段。
     * uv 已乘 repeat，直接用于平铺纹理。
     */
    public static GeoData gridPlane(int segments, float size, float uvRepeat) {
        int vertsPerRow = segments + 1;
        int vertCount = vertsPerRow * vertsPerRow;
        float[] pos = new float[vertCount * 3];
        float[] nrm = new float[vertCount * 3];
        float[] uv = new float[vertCount * 2];

        int p = 0, t = 0;
        for (int i = 0; i <= segments; i++) {
            for (int j = 0; j <= segments; j++) {
                float x = -size / 2f + size * i / segments;
                float z = -size / 2f + size * j / segments;
                pos[p++] = x;
                pos[p++] = 0;
                pos[p++] = z;
                nrm[p - 3] = 0;
                nrm[p - 2] = 1;
                nrm[p - 1] = 0;
                uv[t++] = uvRepeat * i / segments;
                uv[t++] = uvRepeat * j / segments;
            }
        }

        int[] idx = new int[segments * segments * 6];
        int k = 0;
        for (int i = 0; i < segments; i++) {
            for (int j = 0; j < segments; j++) {
                int a = i * vertsPerRow + j;
                int b = a + vertsPerRow;
                idx[k++] = a;
                idx[k++] = b;
                idx[k++] = a + 1;
                idx[k++] = a + 1;
                idx[k++] = b;
                idx[k++] = b + 1;
            }
        }

        GeoData data = new GeoData();
        data.positions = pos;
        data.normals = nrm;
        data.uvs = uv;
        data.indices = idx;
        return data;
    }

    /** XY 平面两三角形（非索引 6 顶点），uv 0..1，用于全屏四边形/贴图演示。 */
    public static float[] quadPositions(float half) {
        return new float[]{
                -half, -half, 0, 0, 0,
                half, -half, 0, 1, 0,
                -half, half, 0, 0, 1,
                half, -half, 0, 1, 0,
                half, half, 0, 1, 1,
                -half, half, 0, 0, 1
        };
    }

    /** XZ 地面两三角形（法线 +Y），用于地板。 */
    public static float[] groundQuad(float half) {
        return new float[]{
                -half, 0, -half, 0, 0, 0, 1, 0,
                half, 0, -half, 1, 0, 0, 1, 0,
                -half, 0, half, 0, 1, 0, 1, 0,
                half, 0, -half, 1, 0, 0, 1, 0,
                half, 0, half, 1, 1, 0, 1, 0,
                -half, 0, half, 0, 1, 0, 1, 0
        };
    }
}
