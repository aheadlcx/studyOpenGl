// Ch19 - Texture2DArray: 4 procedural layers, key +/- shifts layer offset.
#include "../common/common.h"
#include <stdio.h>

int run_ch19() {
    win::Window w;
    if (!win::open(w, "Ch19 - Texture2DArray (+/-: layer offset, A: animate)", 1000, 700)) return 1;

    const char* VS = R"(#version 330
layout(location=0) in vec3 a_pos;
layout(location=2) in vec2 a_uv;
layout(location=3) in float a_layer;
uniform mat4 u_mvp;
uniform float u_offset, u_time;
out vec2 v_uv;
out float v_layer;
void main() {
    v_uv = a_uv;
    v_layer = mod(a_layer + u_offset + floor(u_time), 4.0);
    gl_Position = u_mvp * vec4(a_pos, 1.0);
}
)";
    const char* FS = R"(#version 330
precision lowp sampler2DArray;
in vec2 v_uv;
in float v_layer;
uniform sampler2DArray u_array;
out vec4 fragColor;
void main() { fragColor = texture(u_array, vec3(v_uv, v_layer)); }
)";
    GLuint prog = makeProgram(VS, FS);

    // upload 4 procedural layers
    int size = 128, layers = 4;
    auto c1 = checkerImage(size, 8, 235,235,235, 190,60,60);
    auto c2 = brickImage(size, size);
    auto c3 = noiseImage(size, size, 99);
    std::vector<unsigned char> all;
    for (int i = 0; i < size * size; i++) {
        all.push_back(c1[i*4]); all.push_back(c1[i*4+1]); all.push_back(c1[i*4+2]); all.push_back(255);
    }
    for (int i = 0; i < size * size; i++) {
        all.push_back(c2[i*4]); all.push_back(c2[i*4+1]); all.push_back(c2[i*4+2]); all.push_back(255);
    }
    for (int i = 0; i < size * size; i++) {
        all.push_back(c3[i*4]); all.push_back(c3[i*4+1]); all.push_back(c3[i*4+2]); all.push_back(255);
    }
    auto c4 = checkerImage(size, 4, 250,250,120, 90,140,220);
    for (int i = 0; i < size * size; i++) {
        all.push_back(c4[i*4]); all.push_back(c4[i*4+1]); all.push_back(c4[i*4+2]); all.push_back(255);
    }

    GLuint tex = 0;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D_ARRAY, tex);
    glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA8, size, size, layers,
                 0, GL_RGBA, GL_UNSIGNED_BYTE, all.data());
    glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
    glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glGenerateMipmap(GL_TEXTURE_2D_ARRAY);

    // 4 quads with per-quad layer attribute (location=3)
    std::vector<float> verts;
    for (int q = 0; q < 4; q++) {
        float cx = -0.6f + (q % 2) * 0.55f, cy = q < 2 ? 0.32f : -0.32f;
        float h = 0.24f;
        float base[6][3] = { {-1,-1,0},{1,-1,0},{-1,1,0},{1,-1,0},{1,1,0},{-1,1,0} };
        for (int v = 0; v < 6; v++) {
            verts.insert(verts.end(), {
                cx + base[v][0]*h, cy + base[v][1]*h, 0,
                base[v][0] < 0 ? 0 : 1, base[v][1] < 0 ? 0 : 1,
                (float)q
            });
        }
    }
    Mesh quads = makeMesh(verts, {{0,3},{2,2},{3,1}});

    printf("\n=== Ch19: +/- layer offset, A animate ===\n");
    float offset = 0; bool animate = true;
    Mat4 id; matIdentity(id);

    while (win::beginFrame(w)) {
        if (win::keyDown(VK_ADD)) offset += 0.02f;
        if (win::keyDown(VK_SUBTRACT)) offset -= 0.02f;
        if (win::keyTap('A')) animate = !animate;
        printf("\rlayer offset: %.2f  animate: %d   ", offset, animate ? 1 : 0);

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.04f, 0.05f, 0.09f);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D_ARRAY, tex);
        glUseProgram(prog);
        glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, id);
        glUniform1i(uLoc(prog, "u_array"), 0);
        glUniform1f(uLoc(prog, "u_offset"), offset);
        glUniform1f(uLoc(prog, "u_time"), animate ? (float)w.time * 0.5f : 0.0f);
        quads.draw(GL_TRIANGLES);

        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    glDeleteTextures(1, &tex);
    quads.dispose();
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
