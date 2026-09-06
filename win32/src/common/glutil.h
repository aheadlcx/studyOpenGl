// glutil.h - shader/mesh/texture helpers shared by all chapters
#pragma once
#include <vector>
#include <string>
#include <math.h>
#include <stdio.h>
#include "glfuncs.h"

// ---------- shaders ----------
inline GLuint compileShader(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[2048];
        glGetShaderInfoLog(s, sizeof(log), nullptr, log);
        printf("[shader %s compile failed]\n%s\n", type == GL_VERTEX_SHADER ? "vs" : "fs", log);
    }
    return s;
}

// xfbVaryings/xfbCount: optional Transform Feedback capture list (must be pre-link)
inline GLuint makeProgram(const char* vs, const char* fs,
                          const char* const* xfbVaryings = nullptr, int xfbCount = 0) {
    GLuint v = compileShader(GL_VERTEX_SHADER, vs);
    GLuint f = compileShader(GL_FRAGMENT_SHADER, fs);
    GLuint p = glCreateProgram();
    glAttachShader(p, v);
    glAttachShader(p, f);
    if (xfbVaryings && xfbCount > 0) {
        glTransformFeedbackVaryings(p, xfbCount, xfbVaryings, GL_INTERLEAVED_ATTRIBS);
    }
    glLinkProgram(p);
    GLint ok = 0;
    glGetProgramiv(p, GL_LINK_STATUS, &ok);
    if (!ok) {
        char log[2048];
        glGetProgramInfoLog(p, sizeof(log), nullptr, log);
        printf("[program link failed]\n%s\n", log);
    }
    glDeleteShader(v);
    glDeleteShader(f);
    return p;
}

inline GLint uLoc(GLuint prog, const char* name) {
    return glGetUniformLocation(prog, name);
}

// ---------- meshes ----------
struct Attr { GLuint loc; GLint size; };

struct Mesh {
    GLuint vao = 0, vbo = 0, ebo = 0;
    int indexCount = 0, vertexCount = 0;
    bool indexed = false;

    void draw(GLenum mode) const {
        glBindVertexArray(vao);
        if (indexed) glDrawElements(mode, indexCount, GL_UNSIGNED_INT, 0);
        else         glDrawArrays(mode, 0, vertexCount);
        glBindVertexArray(0);
    }
    void drawInstanced(GLenum mode, GLsizei instances) const {
        glBindVertexArray(vao);
        if (indexed) glDrawElementsInstanced(mode, indexCount, GL_UNSIGNED_INT, 0, instances);
        else         glDrawArraysInstanced(mode, 0, vertexCount, instances);
        glBindVertexArray(0);
    }
    void dispose() const {
        if (vao) glDeleteVertexArrays(1, &vao);
        if (vbo) glDeleteBuffers(1, &vbo);
        if (ebo) glDeleteBuffers(1, &ebo);
    }
};

struct AttrDef { GLuint loc; GLint size; };

inline Mesh makeMesh(const std::vector<float>& verts,
                     const std::vector<AttrDef>& attrs,
                     const std::vector<GLuint>* indices = nullptr) {
    Mesh m;
    glGenVertexArrays(1, &m.vao);
    glBindVertexArray(m.vao);
    glGenBuffers(1, &m.vbo);
    glBindBuffer(GL_ARRAY_BUFFER, m.vbo);
    glBufferData(GL_ARRAY_BUFFER, verts.size() * sizeof(float), verts.data(), GL_STATIC_DRAW);

    int stride = 0;
    for (const AttrDef& a : attrs) stride += a.size * 4;
    int offset = 0;
    for (const AttrDef& a : attrs) {
        glVertexAttribPointer(a.loc, a.size, GL_FLOAT, GL_FALSE, stride, (const void*)(long)offset);
        glEnableVertexAttribArray(a.loc);
        offset += a.size * 4;
    }
    m.vertexCount = (int)verts.size() / (stride / 4);

    if (indices && !indices->empty()) {
        glGenBuffers(1, &m.ebo);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, m.ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices->size() * sizeof(GLuint), indices->data(), GL_STATIC_DRAW);
        m.indexCount = (int)indices->size();
        m.indexed = true;
    }
    glBindVertexArray(0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    return m;
}

// instanced buffer added to an existing mesh (separate VBO, divisor=1)
inline void meshAddInstanced(Mesh& m, const std::vector<float>& data,
                             const std::vector<AttrDef>& attrs) {
    glBindVertexArray(m.vao);
    GLuint vbo = 0;
    glGenBuffers(1, &vbo);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER, data.size() * sizeof(float), data.data(), GL_STATIC_DRAW);
    int stride = 0;
    for (const AttrDef& a : attrs) stride += a.size * 4;
    int offset = 0;
    for (const AttrDef& a : attrs) {
        glVertexAttribPointer(a.loc, a.size, GL_FLOAT, GL_FALSE, stride, (const void*)(long)offset);
        glEnableVertexAttribArray(a.loc);
        glVertexAttribDivisor(a.loc, 1);
        offset += a.size * 4;
    }
    glBindVertexArray(0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
}

// ---------- procedural images (RGBA, 0..255) ----------
inline std::vector<unsigned char> checkerImage(int size, int cells,
                                               unsigned char ra, unsigned char ga, unsigned char ba,
                                               unsigned char rb, unsigned char gb, unsigned char bb) {
    std::vector<unsigned char> px(size * size * 4);
    float cell = size / (float)cells;
    for (int y = 0; y < size; y++)
        for (int x = 0; x < size; x++) {
            bool a = ((int)(x / cell) + (int)(y / cell)) % 2 == 0;
            int i = (y * size + x) * 4;
            px[i]   = a ? ra : rb;
            px[i+1] = a ? ga : gb;
            px[i+2] = a ? ba : bb;
            px[i+3] = 255;
        }
    return px;
}

inline std::vector<unsigned char> brickImage(int w, int h) {
    std::vector<unsigned char> px(w * h * 4);
    int rows = 6, bh = h / rows, bw = w / 3;
    unsigned seed = 7;
    auto rnd = [&]() { seed = seed * 1103515245u + 12345u; return (seed >> 16) & 0xFF; };
    for (int y = 0; y < h; y++)
        for (int x = 0; x < w; x++) {
            int row = y / bh, col = (x + (row % 2 ? bw / 2 : 0)) / bw;
            bool mortar = (y % bh) < 4 || ((x + (row % 2 ? bw / 2 : 0)) % bw) < 4;
            int i = (y * w + x) * 4;
            unsigned char shade = (unsigned char)(150 + rnd() % 60);
            if (mortar) { px[i]=120; px[i+1]=112; px[i+2]=104; }
            else { px[i] = (unsigned char)(shade % 255); px[i+1] = (unsigned char)(shade - 40); px[i+2] = (unsigned char)(shade - 60); }
            px[i+3] = 255;
        }
    return px;
}

inline std::vector<unsigned char> noiseImage(int w, int h, unsigned seed) {
    std::vector<unsigned char> px(w * h * 4);
    for (int i = 0; i < w * h; i++) {
        seed = seed * 1103515245u + 12345u;
        unsigned char g = (unsigned char)(40 + (seed >> 16) % 215);
        px[i*4] = g; px[i*4+1] = g; px[i*4+2] = g; px[i*4+3] = 255;
    }
    return px;
}

inline std::vector<unsigned char> stripesImage(int w, int h) {
    std::vector<unsigned char> px(w * h * 4);
    for (int y = 0; y < h; y++)
        for (int x = 0; x < w; x++) {
            int i = (y * w + x) * 4;
            bool on = (y / (h / 8)) % 3 == 0;
            px[i] = on ? 90 : 10; px[i+1] = on ? 220 : 10; px[i+2] = on ? 255 : 10; px[i+3] = 255;
        }
    return px;
}

inline GLuint texFromImage(const std::vector<unsigned char>& px, int w, int h, bool mipmap) {
    GLuint id = 0;
    glGenTextures(1, &id);
    glBindTexture(GL_TEXTURE_2D, id);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, px.data());
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, mipmap ? GL_LINEAR_MIPMAP_LINEAR : GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    if (mipmap) glGenerateMipmap(GL_TEXTURE_2D);
    return id;
}

inline GLuint checkerTex(int size, int cells, bool mipmap = true) {
    auto px = checkerImage(size, cells, 235,235,240, 60,110,170);
    return texFromImage(px, size, size, mipmap);
}

// brick height field -> normal map (RGB = n*0.5+0.5), matches App chapter 23
inline std::vector<unsigned char> brickNormalImage(int size) {
    std::vector<unsigned char> px(size * size * 4);
    float bw = size / 3.0f, bh = size / 6.0f;
    auto hAt = [&](float x, float y) -> float {
        int row = (int)(y / bh);
        float fy = y / bh - row;
        float off = (row % 2 == 0) ? 0.0f : bw / 2.0f;
        float fx = fmodf((x + off) / bw, 1.0f); if (fx < 0) fx += 1.0f;
        float gap = 0.08f;
        if (fy < gap || fy > 1 - gap || fx < gap || fx > 1 - gap) return 0.0f;
        float e = fy - gap; e = e < (gap + 1 - fy) ? e : (gap + 1 - fy);
        float ex = fx - gap; ex = ex < (gap + 1 - fx) ? ex : (gap + 1 - fx);
        if (ex < e) e = ex;
        return e / 0.06f < 1.0f ? e / 0.06f : 1.0f;
    };
    float dx = 1.5f;
    for (int y = 0; y < size; y++)
        for (int x = 0; x < size; x++) {
            float hR = hAt(fmodf(x + dx, (float)size), (float)y);
            float hL = hAt(fmodf(x - dx + size, (float)size), (float)y);
            float hD = hAt((float)x, fmodf(y + dx, (float)size));
            float hU = hAt((float)x, fmodf(y - dx + size, (float)size));
            float nx = (hL - hR) * 2.2f, ny = (hU - hD) * 2.2f, nz = 1.0f;
            float len = sqrtf(nx*nx + ny*ny + nz*nz);
            int i = (y * size + x) * 4;
            px[i]   = (unsigned char)((nx/len*0.5f+0.5f)*255);
            px[i+1] = (unsigned char)((ny/len*0.5f+0.5f)*255);
            px[i+2] = (unsigned char)((nz/len*0.5f+0.5f)*255);
            px[i+3] = 255;
        }
    return px;
}

// ---------- geometry generators (all CCW from outside) ----------
inline void cubeArrays(std::vector<float>& pos, std::vector<float>& nrm,
                       std::vector<float>& uv, std::vector<GLuint>& idx) {
    float faces[6][3]   = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    float uaxes[6][3]   = {{0,0,-1},{0,0,1},{1,0,0},{1,0,0},{1,0,0},{-1,0,0}};
    float vaxes[6][3]   = {{0,1,0},{0,1,0},{0,0,-1},{0,0,1},{0,1,0},{0,1,0}};
    pos.resize(72); nrm.resize(72); uv.resize(48); idx.resize(36);
    int p = 0, t = 0;
    for (int f = 0; f < 6; f++)
        for (int j = 0; j < 2; j++)
            for (int i = 0; i < 2; i++) {
                float a = i - 0.5f, b = j - 0.5f;
                pos[p]   = faces[f][0]*0.5f + uaxes[f][0]*a + vaxes[f][0]*b;
                pos[p+1] = faces[f][1]*0.5f + uaxes[f][1]*a + vaxes[f][1]*b;
                pos[p+2] = faces[f][2]*0.5f + uaxes[f][2]*a + vaxes[f][2]*b;
                nrm[p] = faces[f][0]; nrm[p+1] = faces[f][1]; nrm[p+2] = faces[f][2];
                uv[t] = (float)i; uv[t+1] = (float)j;
                p += 3; t += 2;
            }
    for (int f = 0; f < 6; f++) {
        int base = f * 4, k = f * 6;
        int q[6] = {0,1,2, 2,1,3};
        for (int i = 0; i < 6; i++) idx[k+i] = base + q[i];
    }
}

inline Mesh cubeMesh(float half = 0.5f, bool withUv = false) {
    std::vector<float> pos, nrm, uv; std::vector<GLuint> idx;
    cubeArrays(pos, nrm, uv, idx);
    int vc = (int)pos.size() / 3;
    std::vector<float> inter(vc * (withUv ? 8 : 6));
    for (int i = 0; i < vc; i++) {
        inter[i*6] = pos[i*3]*half; inter[i*6+1] = pos[i*3+1]*half; inter[i*6+2] = pos[i*3+2]*half;
        inter[i*6+3] = nrm[i*3]; inter[i*6+4] = nrm[i*3+1]; inter[i*6+5] = nrm[i*3+2];
        if (withUv) { inter[i*8+6] = uv[i*2]; inter[i*8+7] = uv[i*2+1]; }
    }
    std::vector<AttrDef> attrs;
    attrs.push_back({0, 3});
    attrs.push_back({1, 3});
    if (withUv) attrs.push_back({2, 2});
    return makeMesh(inter, attrs, &idx);
}

// fullscreen / textured quad in NDC (pos2+uv2)
inline Mesh quadUV() {
    std::vector<float> v = {
        -1,-1, 0,0,  1,-1, 1,0,  -1,1, 0,1,
         1,-1, 1,0,  1,1, 1,1,  -1,1, 0,1
    };
    std::vector<AttrDef> attrs = {{0,2},{1,2}};
    return makeMesh(v, attrs);
}

// ---------- framebuffer ----------
struct FBO {
    GLuint fbo = 0, colorTex = 0, depthRbo = 0, colorRbo = 0;
    int w = 0, h = 0;
    int msaa = 0;              // 0 = non-multisample (color attached as texture)
    bool create(int width, int height, int samples = 0) {
        dispose();
        w = width; h = height; msaa = samples;
        glGenFramebuffers(1, &fbo);
        glGenRenderbuffers(1, &depthRbo);
        glBindRenderbuffer(GL_RENDERBUFFER, depthRbo);
        if (samples > 0)
            glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_DEPTH_COMPONENT24, w, h);
        else
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, w, h);
        if (samples > 0) {
            glGenRenderbuffers(1, &colorRbo);
            glBindRenderbuffer(GL_RENDERBUFFER, colorRbo);
            glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_RGBA8, w, h);
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, colorRbo);
        } else {
            glGenTextures(1, &colorTex);
            glBindTexture(GL_TEXTURE_2D, colorTex);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTex, 0);
        }
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthRbo);
        GLenum st = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        if (st != GL_FRAMEBUFFER_COMPLETE) { printf("[fbo] incomplete 0x%04X\n", (unsigned)st); return false; }
        return true;
    }
    void bind() { glBindFramebuffer(GL_FRAMEBUFFER, fbo); glViewport(0, 0, w, h); }
    void dispose() {
        if (fbo) glDeleteFramebuffers(1, &fbo);
        if (colorTex) glDeleteTextures(1, &colorTex);
        if (depthRbo) glDeleteRenderbuffers(1, &depthRbo);
        if (colorRbo) glDeleteRenderbuffers(1, &colorRbo);
        fbo = colorTex = depthRbo = colorRbo = 0;
    }
};

// small helper: reset common state at frame start
inline void resetState(int w, int h, float r, float g, float b) {
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_BLEND);
    glDisable(GL_CULL_FACE);
    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_STENCIL_TEST);
    glDepthMask(GL_TRUE);
    glStencilMask(0xFF);
    glViewport(0, 0, w, h);
    glClearColor(r, g, b, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
}
