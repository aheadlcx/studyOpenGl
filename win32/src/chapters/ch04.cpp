// Ch04 - Rasterization: GPU smooth (row2) vs CPU discrete fragments (row3).
#include "../common/common.h"
#include <stdio.h>

static const float AX[3] = {-1.15f, 1.05f, -0.15f};
static const float AY[3] = {-0.60f, -0.35f, 0.85f};
static const float AC[3][3] = {{1,0.3f,0.25f},{0.25f,1,0.4f},{0.3f,0.55f,1}};

int run_ch04() {
    win::Window w;
    if (!win::open(w, "Ch04 - Rasterization (arrow keys move the magnifier)", 1000, 700)) return 1;

    const char* VS = R"(#version 330
layout(location=0) in vec2 a_pos;
layout(location=1) in vec4 a_color;
uniform mat4 u_mvp;
out vec4 v_color;
void main() {
    v_color = a_color;
    gl_Position = u_mvp * vec4(a_pos, 0.0, 1.0);
}
)";
    const char* FS = R"(#version 330
in vec4 v_color;
out vec4 fragColor;
void main() { fragColor = v_color; }
)";
    GLuint prog = makeProgram(VS, FS);

    // triangle mesh: 2D pos + rgba color
    std::vector<float> tri;
    for (int v = 0; v < 3; v++) {
        tri.push_back(AX[v]); tri.push_back(AY[v]);
        tri.push_back(AC[v][0]); tri.push_back(AC[v][1]); tri.push_back(AC[v][2]); tri.push_back(1.0f);
    }
    Mesh triMesh = makeMesh(tri, {{0,2},{1,4}});

    // CPU fragment grid: interleaved pos2+color4, 6 verts per cell, rebuilt each frame
    const int N = 18;
    const float NDC = 0.96f;
    std::vector<float> grid(N * N * 6 * 6);
    GLuint gridVao = 0, gridVbo = 0;
    glGenVertexArrays(1, &gridVao);
    glGenBuffers(1, &gridVbo);
    glBindVertexArray(gridVao);
    glBindBuffer(GL_ARRAY_BUFFER, gridVbo);
    glBufferData(GL_ARRAY_BUFFER, grid.size() * sizeof(float), nullptr, GL_DYNAMIC_DRAW);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 24, (const void*)0);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(1, 4, GL_FLOAT, GL_FALSE, 24, (const void*)8);
    glEnableVertexAttribArray(1);
    glBindVertexArray(0);

    float spot[2] = {AX[0], AY[0]};   // magnifier center (world coords)
    printf("\n=== Ch04: arrow keys move the magnifier. row2=GPU smooth, row3=CPU fragments ===\n");

    while (win::beginFrame(w)) {
        if (win::keyDown(VK_LEFT))  spot[0] -= 0.02f;
        if (win::keyDown(VK_RIGHT)) spot[0] += 0.02f;
        if (win::keyDown(VK_UP))    spot[1] += 0.02f;
        if (win::keyDown(VK_DOWN))  spot[1] -= 0.02f;
        spot[0] = clampf(spot[0], -1.6f, 1.6f);
        spot[1] = clampf(spot[1], -1.1f, 1.2f);

        // rotated triangle (CPU copy for barycentric tests)
        float ang = (float)w.time * 0.5f;
        float cx = (AX[0]+AX[1]+AX[2])/3.0f, cy = (AY[0]+AY[1]+AY[2])/3.0f;
        float ca = cosf(ang), sa = sinf(ang);
        float rx[3], ry[3];
        for (int v = 0; v < 3; v++) {
            float dx = AX[v]-cx, dy = AY[v]-cy;
            rx[v] = cx + dx*ca - dy*sa;
            ry[v] = cy + dx*sa + dy*ca;
        }

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.04f, 0.05f, 0.09f);
        int total = (int)(vh * 0.9f);
        int h1 = total * 45 / 100, h2 = total * 25 / 100, h3 = total - h1 - h2;
        glUseProgram(prog);

        Mat4 mvp, rot, tmp;
        matIdentity(rot);
        matRotate(rot, ang, 0, 0, 1);

        // row1: normal view + white magnifier frame
        glViewport(0, total - h1, vw, h1);
        float a1 = vw / (float)h1;
        matOrtho(mvp, -a1, a1, -1, 1, -1, 1);
        matMul(tmp, mvp, rot);
        glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, tmp);
        triMesh.draw(GL_TRIANGLES);
        {
            float aFull = vw / (float)vh;                      // full-screen aspect
            float fw = 0.30f * aFull / a1;                     // region half-width in row1 NDC
            float fh = 0.30f;                                  // region half-height in NDC
            float pxN = spot[0] / a1, pyN = spot[1];
            float f[8][6] = {};
            const float corners[8][2] = {
                {-1,-1},{1,-1},{1,-1},{1,1},{1,1},{-1,1},{-1,1},{-1,-1}
            };
            for (int k = 0; k < 8; k++) {
                f[k][0] = pxN + corners[k][0]*fw;
                f[k][1] = pyN + corners[k][1]*fh;
                f[k][2] = f[k][3] = f[k][4] = f[k][5] = 1.0f;
            }
            Mesh fm = makeMesh(std::vector<float>(&f[0][0], &f[0][0] + 48), {{0,2},{1,4}});
            fm.draw(GL_LINES);
            fm.dispose();
        }

        // row2: GPU zoomed render (ortho window = magnifier region)
        glViewport(0, total - h1 - h2, vw, h2);
        float a2 = vw / (float)h2;
        matOrtho(mvp, spot[0] - 0.30f*a2, spot[0] + 0.30f*a2,
                       spot[1] - 0.30f,   spot[1] + 0.30f, -1, 1);
        matMul(tmp, mvp, rot);
        glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, tmp);
        triMesh.draw(GL_TRIANGLES);

        // row3: CPU discrete fragments (center sampling + barycentric color)
        glViewport(0, 0, vw, h3);
        float a3 = vw / (float)h3;
        float x0 = spot[0] - 0.30f*a3, x1 = spot[0] + 0.30f*a3;
        float y0 = spot[1] - 0.30f,    y1 = spot[1] + 0.30f;
        size_t k = 0;
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                float wx = x0 + (i + 0.5f) * (x1 - x0) / N;
                float wy = y0 + (j + 0.5f) * (y1 - y0) / N;
                float det = (ry[1]-ry[2])*(rx[0]-rx[2]) + (rx[2]-rx[1])*(ry[0]-ry[2]);
                float w1 = ((ry[1]-ry[2])*(wx-rx[2]) + (rx[2]-rx[1])*(wy-ry[2])) / det;
                float w2 = ((ry[2]-ry[0])*(wx-rx[2]) + (rx[0]-rx[2])*(wy-ry[2])) / det;
                float w3 = 1.0f - w1 - w2;
                float r = 0.12f, g = 0.14f, b = 0.18f;
                if (w1 >= 0 && w2 >= 0 && w3 >= 0) {
                    r = w1*AC[0][0] + w2*AC[1][0] + w3*AC[2][0];
                    g = w1*AC[0][1] + w2*AC[1][1] + w3*AC[2][1];
                    b = w1*AC[0][2] + w2*AC[1][2] + w3*AC[2][2];
                }
                float s = 2.0f * NDC / N * 0.9f;
                float cx0 = -NDC + (i + 0.5f) * 2 * NDC / N;
                float cy0 = -NDC + (j + 0.5f) * 2 * NDC / N;
                float quad[6][6] = {
                    {cx0-s/2, cy0-s/2, r,g,b,1}, {cx0+s/2, cy0-s/2, r,g,b,1}, {cx0-s/2, cy0+s/2, r,g,b,1},
                    {cx0+s/2, cy0-s/2, r,g,b,1}, {cx0+s/2, cy0+s/2, r,g,b,1}, {cx0-s/2, cy0+s/2, r,g,b,1}
                };
                for (int q = 0; q < 6; q++)
                    for (int c = 0; c < 6; c++) grid[k++] = quad[q][c];
            }
        glBindVertexArray(gridVao);
        glBindBuffer(GL_ARRAY_BUFFER, gridVbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, grid.size() * sizeof(float), grid.data());
        glDrawArrays(GL_TRIANGLES, 0, N * N * 6);
        glBindVertexArray(0);
    }
    glDeleteBuffers(1, &gridVbo);
    glDeleteVertexArrays(1, &gridVao);
    triMesh.dispose();
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
