// math3d.h - tiny column-major mat4 math (same convention as android.opengl.Matrix)
#pragma once
#include <math.h>

#define M_PI_F 3.14159265358979f

typedef float Mat4[16];

inline float radf(float deg) { return deg * (M_PI_F / 180.0f); }
inline float clampf(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

inline void matIdentity(Mat4 m) {
    for (int i = 0; i < 16; i++) m[i] = 0.0f;
    m[0] = m[5] = m[10] = m[15] = 1.0f;
}

// out = a * b  (column-major; safe when out == a or b)
inline void matMul(Mat4 out, const Mat4 a, const Mat4 b) {
    Mat4 r;
    for (int c = 0; c < 4; c++)
        for (int row = 0; row < 4; row++) {
            float s = 0.0f;
            for (int k = 0; k < 4; k++)
                s += a[k * 4 + row] * b[c * 4 + k];
            r[c * 4 + row] = s;
        }
    for (int i = 0; i < 16; i++) out[i] = r[i];
}

// fovy in degrees, like Matrix.perspectiveM
inline void matPerspective(Mat4 m, float fovyDeg, float aspect, float zNear, float zFar) {
    float f = 1.0f / tanf(radf(fovyDeg) * 0.5f);
    for (int i = 0; i < 16; i++) m[i] = 0.0f;
    m[0]  = f / aspect;
    m[5]  = f;
    m[10] = (zFar + zNear) / (zNear - zFar);
    m[11] = -1.0f;
    m[14] = 2.0f * zFar * zNear / (zNear - zFar);
}

inline void matOrtho(Mat4 m, float l, float r, float b, float t, float n, float f) {
    for (int i = 0; i < 16; i++) m[i] = 0.0f;
    m[0]  = 2.0f / (r - l);
    m[5]  = 2.0f / (t - b);
    m[10] = -2.0f / (f - n);
    m[12] = -(r + l) / (r - l);
    m[13] = -(t + b) / (t - b);
    m[14] = -(f + n) / (f - n);
    m[15] = 1.0f;
}

inline void matLookAt(Mat4 m,
                      float ex, float ey, float ez,
                      float cx, float cy, float cz,
                      float ux, float uy, float uz) {
    // z = normalize(eye - center), x = cross(up, z), y = cross(z, x)
    float zx = ex - cx, zy = ey - cy, zz = ez - cz;
    float zl = sqrtf(zx*zx + zy*zy + zz*zz); if (zl) { zx/=zl; zy/=zl; zz/=zl; }
    float xx = uy*zz - uz*zy, xy = uz*zx - ux*zz, xz = ux*zy - uy*zx;
    float xl = sqrtf(xx*xx + xy*xy + xz*xz); if (xl) { xx/=xl; xy/=xl; xz/=xl; }
    float yx = zy*xz - zz*xy, yy = zz*xx - zx*xz, yz = zx*xy - zy*xx;
    m[0]=xx; m[1]=yx; m[2]=zx; m[3]=0;
    m[4]=xy; m[5]=yy; m[6]=zy; m[7]=0;
    m[8]=xz; m[9]=yz; m[10]=zz; m[11]=0;
    m[12]=-(xx*ex+xy*ey+xz*ez);
    m[13]=-(yx*ex+yy*ey+yz*ez);
    m[14]=-(zx*ex+zy*ey+zz*ez);
    m[15]=1;
}

// m = m * T(x,y,z)
inline void matTranslate(Mat4 m, float x, float y, float z) {
    Mat4 t; matIdentity(t);
    t[12]=x; t[13]=y; t[14]=z;
    matMul(m, m, t);
}

// m = m * Raxis(deg)   (axis must be normalized)
inline void matRotate(Mat4 m, float deg, float ax, float ay, float az) {
    float c = cosf(radf(deg)), s = sinf(radf(deg));
    Mat4 r; matIdentity(r);
    r[0]=ax*ax*(1-c)+c;   r[4]=ax*ay*(1-c)-az*s; r[8]=ax*az*(1-c)+ay*s;
    r[1]=ay*ax*(1-c)+az*s;r[5]=ay*ay*(1-c)+c;   r[9]=ay*az*(1-c)-ax*s;
    r[2]=az*ax*(1-c)-ay*s;r[6]=az*ay*(1-c)+ax*s;r[10]=az*az*(1-c)+c;
    matMul(m, m, r);
}

// m = m * S(sx,sy,sz)
inline void matScale(Mat4 m, float sx, float sy, float sz) {
    Mat4 s; matIdentity(s);
    s[0]=sx; s[5]=sy; s[10]=sz;
    matMul(m, m, s);
}
