// Ch09 - Primitive types: one vertex set, all draw modes (keys 1-8).
#include "../common/common.h"
#include <stdio.h>

int run_ch09() {
    win::Window w;
    if (!win::open(w, "Ch09 - Primitive types (1-8 select mode)", 1000, 700)) return 1;

    const char* VS = R"(#version 330
layout(location=0) in vec3 a_pos;
layout(location=1) in vec3 a_color;
uniform mat4 u_mvp;
uniform float u_psize;
out vec3 v_color;
void main() {
    v_color = a_color;
    gl_Position = u_mvp * vec4(a_pos, 1.0);
    gl_PointSize = u_psize;
}
)";
    const char* FS = R"(#version 330
in vec3 v_color;
out vec4 fragColor;
void main() { fragColor = vec4(v_color, 1.0); }
)";
    GLuint prog = makeProgram(VS, FS);

    // vertex 0 = center, 1..36 = circle
    const int SEG = 36;
    std::vector<float> verts((SEG + 2) * 6);
    verts[3] = verts[4] = verts[5] = 1;
    for (int i = 0; i <= SEG; i++) {
        float a = (float)(2.0 * 3.14159265 * i / SEG);
        int o = (i + 1) * 6;
        verts[o] = cosf(a) * 0.8f;
        verts[o + 1] = sinf(a) * 0.8f;
        verts[o + 3] = 0.5f + 0.5f * cosf(a);
        verts[o + 4] = 0.5f + 0.5f * sinf(a);
        verts[o + 5] = 1.0f - verts[o + 3];
    }

    // index sets per mode (0..7)
    std::vector<GLuint> idx[8];
    for (int i = 0; i < SEG; i++) idx[0].push_back(i + 1);                             // points
    for (int i = 0; i < SEG; i++) { idx[1].push_back(i+1); idx[1].push_back((i+1)%SEG+1); }
    for (int i = 0; i <= SEG; i++) idx[2].push_back(i % SEG + 1);                      // strip
    for (int i = 0; i < SEG; i++) idx[3].push_back(i + 1);                             // loop
    for (int i = 0; i < SEG; i++) { idx[4].push_back(0); idx[4].push_back(i+1); idx[4].push_back((i+1)%SEG+1); }
    for (int i = 0; i < SEG; i++) { idx[5].push_back(i+1); idx[5].push_back((i+1)%SEG+1); }
    idx[6].push_back(0);                                                               // fan
    for (int i = 0; i <= SEG; i++) idx[6].push_back(i % SEG + 1);
    for (int i = 1; i <= 19; i++) idx[7].push_back(i);                                 // restart demo
    idx[7].push_back(0xFFFFFFFF);
    for (int i = 19; i <= 36; i++) idx[7].push_back(i);
    idx[7].push_back(1);

    GLenum modes[8] = { GL_POINTS, GL_LINES, GL_LINE_STRIP, GL_LINE_LOOP,
                        GL_TRIANGLES, GL_TRIANGLE_STRIP, GL_TRIANGLE_FAN, GL_TRIANGLE_STRIP };
    const char* names[8] = { "POINTS", "LINES", "LINE_STRIP", "LINE_LOOP",
                             "TRIANGLES", "TRIANGLE_STRIP", "TRIANGLE_FAN", "RESTART 2 strips" };

    int vboCount = 8;
    GLuint vbos[8], vao[8];
    for (int m = 0; m < 8; m++) {
        glGenVertexArrays(1, &vao[m]);
        glGenBuffers(1, &vbos[m]);
        glBindVertexArray(vao[m]);
        GLuint vbo;
        glGenBuffers(1, &vbo);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (SEG + 2) * 6 * 4, verts.data(), GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 24, (const void*)0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, GL_FALSE, 24, (const void*)12);
        glEnableVertexAttribArray(1);
        GLuint ibo;
        glGenBuffers(1, &ibo);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx[m].size() * 4, idx[m].data(), GL_STATIC_DRAW);
        glBindVertexArray(0);
    }
    (void)vboCount;

    printf("\n=== Ch09: keys 1-8 select mode, +/- point size ===\n");

    int mode = 4;
    float psize = 8.0f;
    GLint uMvp = uLoc(prog, "u_mvp"), uPs = uLoc(prog, "u_psize");

    while (win::beginFrame(w)) {
        for (int k = 0; k < 8; k++)
            if (win::keyTap('1' + k)) mode = k;
        if (win::keyDown(VK_ADD) || win::keyDown(VK_OEM_PLUS)) psize += 0.5f;
        if (win::keyDown(VK_SUBTRACT) || win::keyDown(VK_OEM_MINUS)) psize -= 0.5f;
        psize = clampf(psize, 1.0f, 32.0f);
        printf("\rmode: %-18s point size: %4.1f  ", names[mode], psize);

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.04f, 0.05f, 0.09f);

        Mat4 mvp;
        matIdentity(mvp);
        matRotate(mvp, (float)w.time * 30.0f, 0, 0, 1);

        glUseProgram(prog);
        glUniformMatrix4fv(uMvp, 1, GL_FALSE, mvp);
        glUniform1f(uPs, psize);
        if (mode == 7) glEnable(GL_PRIMITIVE_RESTART_FIXED_INDEX);
        glBindVertexArray(vao[mode]);
        glDrawElements(modes[mode], (GLsizei)idx[mode].size(), GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
        if (mode == 7) glDisable(GL_PRIMITIVE_RESTART_FIXED_INDEX);

        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    for (int m = 0; m < 8; m++) { glDeleteVertexArrays(1, &vao[m]); glDeleteBuffers(1, &vbos[m]); }
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
