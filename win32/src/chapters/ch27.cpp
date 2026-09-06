// Ch27 - Buffer mapping: wave grid via glMapBufferRange (M: map/subData toggle).
#include "../common/common.h"
#include <stdio.h>

int run_ch27() {
    win::Window w;
    if (!win::open(w, "Ch27 - Buffer mapping (M: map/subData, +/-: frequency)", 1000, 700)) return 1;

    const char* VS = R"(#version 330
layout(location=0) in vec3 a_pos;
uniform mat4 u_mvp;
uniform float u_minH, u_maxH;
out float v_height;
void main() {
    v_height = (a_pos.y - u_minH) / (u_maxH - u_minH);
    gl_Position = u_mvp * vec4(a_pos, 1.0);
}
)";
    const char* FS = R"(#version 330
in float v_height;
out vec4 fragColor;
void main() {
    fragColor = vec4(mix(vec3(0.1,0.2,0.55), vec3(0.3,0.95,0.95), clamp(v_height,0.0,1.0)), 1.0);
}
)";
    GLuint prog = makeProgram(VS, FS);

    const int SEG = 64;
    int vertsPer = SEG + 1;
    int vertCount = vertsPer * vertsPer;
    GLuint vao, vbo, ibo;
    glGenVertexArrays(1, &vao);
    glGenBuffers(1, &vbo);
    glGenBuffers(1, &ibo);
    glBindVertexArray(vao);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER, vertCount * 3 * 4, nullptr, GL_DYNAMIC_DRAW);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 12, (const void*)0);
    glEnableVertexAttribArray(0);
    {
        std::vector<float> init(vertCount * 3);
        for (int i = 0, p = 0; i <= SEG; i++)
            for (int j = 0; j <= SEG; j++) {
                init[p++] = -10.0f + 20.0f * i / SEG;
                init[p++] = 0.0f;
                init[p++] = -10.0f + 20.0f * j / SEG;
            }
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertCount * 3 * 4, init.data());
    }
    // indices
    std::vector<GLuint> idx;
    idx.reserve(SEG * SEG * 6);
    for (int i = 0; i < SEG; i++)
        for (int j = 0; j < SEG; j++) {
            int a = i * vertsPer + j, b = a + vertsPer;
            idx.push_back(a); idx.push_back(b); idx.push_back(a+1);
            idx.push_back(a+1); idx.push_back(b); idx.push_back(b+1);
        }
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx.size() * 4, idx.data(), GL_STATIC_DRAW);
    glBindVertexArray(0);

    printf("\n=== Ch27: M toggles glMapBufferRange / glBufferSubData, +/- wave frequency ===\n");
    bool useMap = true;
    float freq = 2.0f;
    std::vector<float> scratch(vertCount * 3);
    GLint uMvp = uLoc(prog, "u_mvp"), uMin = uLoc(prog, "u_minH"), uMax = uLoc(prog, "u_maxH");

    while (win::beginFrame(w)) {
        if (win::keyTap('M')) useMap = !useMap;
        if (win::keyDown(VK_ADD)) freq += 0.01f;
        if (win::keyDown(VK_SUBTRACT)) freq -= 0.01f;
        freq = clampf(freq, 0.5f, 6.0f);
        printf("\rupdate: %-16s verts=%d freq=%.1f  ", useMap ? "glMapBufferRange" : "glBufferSubData", vertCount, freq);

        // ── CPU compute wave & upload ──
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        if (useMap) {
            void* mapped = glMapBufferRange(GL_ARRAY_BUFFER, 0, vertCount * 3 * 4,
                    GL_MAP_WRITE_BIT | GL_MAP_INVALIDATE_BUFFER_BIT);
            if (mapped) {
                float* p = (float*)mapped;
                for (int i = 0; i <= SEG; i++)
                    for (int j = 0; j <= SEG; j++) {
                        float x = -10.0f + 20.0f * i / SEG;
                        float z = -10.0f + 20.0f * j / SEG;
                        p[0] = x;
                        p[1] = sinf(x * freq * 0.4f + (float)w.time * 2.2f)
                             * cosf(z * freq * 0.35f + (float)w.time * 1.7f) * 0.7f;
                        p[2] = z;
                        p += 3;
                    }
                glUnmapBuffer(GL_ARRAY_BUFFER);
            }
        } else {
            for (int i = 0, p = 0; i <= SEG; i++)
                for (int j = 0; j <= SEG; j++) {
                    float x = -10.0f + 20.0f * i / SEG;
                    float z = -10.0f + 20.0f * j / SEG;
                    scratch[p++] = x;
                    scratch[p++] = sinf(x * freq * 0.4f + (float)w.time * 2.2f)
                                 * cosf(z * freq * 0.35f + (float)w.time * 1.7f) * 0.7f;
                    scratch[p++] = z;
                }
            glBufferSubData(GL_ARRAY_BUFFER, 0, vertCount * 3 * 4, scratch.data());
        }
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        int vw = w.vpWidth(), vh = w.vpHeight();
        glDisable(GL_DEPTH_TEST);
        glClearColor(0.05f, 0.07f, 0.11f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glUseProgram(prog);
        Mat4 proj, view, mvp;
        matPerspective(proj, 50.0f, (float)vw / vh, 0.1f, 60.0f);
        matLookAt(view, 13, 9, 13, 0, 0, 0, 0, 1, 0);
        matMul(mvp, proj, view);
        glUniformMatrix4fv(uMvp, 1, GL_FALSE, mvp);
        glUniform1f(uMin, -0.8f);
        glUniform1f(uMax, 0.8f);
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, (GLsizei)idx.size(), GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);

        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    glDeleteBuffers(1, &vbo);
    glDeleteBuffers(1, &ibo);
    glDeleteVertexArrays(1, &vao);
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
