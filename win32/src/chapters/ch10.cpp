// Ch10 - Viewport & scissor: 3x3 split screen (S toggles scissor).
#include "../common/common.h"
#include <stdio.h>

int run_ch10() {
    win::Window w;
    if (!win::open(w, "Ch10 - Viewport & Scissor (S: scissor on/off)", 1000, 700)) return 1;

    const char* VS = R"(#version 330
layout(location=0) in vec3 a_pos;
layout(location=1) in vec3 a_normal;
uniform mat4 u_mvp;
out vec3 v_normal;
void main() {
    v_normal = mat3(u_mvp) * a_normal;
    gl_Position = u_mvp * vec4(a_pos, 1.0);
}
)";
    const char* FS = R"(#version 330
in vec3 v_normal;
uniform vec3 u_tint;
out vec4 fragColor;
void main() {
    float d = max(dot(normalize(v_normal), normalize(vec3(0.5, 1, 0.6))), 0.0);
    fragColor = vec4(u_tint * (0.35 + 0.65 * d), 1.0);
}
)";
    GLuint prog = makeProgram(VS, FS);
    Mesh cube = cubeMesh(0.6f);

    printf("\n=== Ch10: S toggles scissor. Viewport maps NDC, scissor clips pixels ===\n");

    bool scissor = true;
    while (win::beginFrame(w)) {
        if (win::keyTap('S')) scissor = !scissor;
        printf("\rscissor: %s   ", scissor ? "ON " : "OFF");

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.03f, 0.04f, 0.07f);
        glEnable(GL_DEPTH_TEST);

        int grid = 3;
        float cellW = vw / (float)grid, cellH = vh / (float)grid;
        glUseProgram(prog);
        for (int gy = 0; gy < grid; gy++)
            for (int gx = 0; gx < grid; gx++) {
                int ox = (int)(gx * cellW), oy = (int)(gy * cellH);
                int cw = (int)cellW, ch = (int)cellH;
                if (scissor) {
                    glEnable(GL_SCISSOR_TEST);
                    glScissor(ox, oy, cw, ch);
                    float bg = 0.08f + 0.05f * ((gx + gy) % 3);
                    glClearColor(bg, bg * 1.2f, bg * 1.5f, 1.0f);
                    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                }
                // viewport deliberately smaller than the cell -> overflow visible when scissor off
                int inset = (int)(cw < ch ? cw : ch) * 0.1f;
                glViewport(ox + inset, oy + inset, cw - inset * 2, ch - inset * 2);

                Mat4 proj, view, model, pv, mvp;
                matPerspective(proj, 50.0f, (cw - 2.0f*inset) / (float)(ch - 2.0f*inset), 0.1f, 20.0f);
                matLookAt(view, 3, 2.4f, 3, 0, 0, 0, 0, 1, 0);
                matIdentity(model);
                matRotate(model, (float)w.time * 60.0f + (gx + gy) * 45.0f, 0.5f, 1, 0.3f);
                matMul(pv, proj, view);
                matMul(mvp, pv, model);
                glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, mvp);
                float hue = fmodf(gx * 0.13f + gy * 0.31f, 1.0f);
                glUniform3f(uLoc(prog, "u_tint"),
                            0.5f + 0.5f * sinf(hue * 6.28f),
                            0.5f + 0.5f * sinf(hue * 6.28f + 2.1f),
                            0.5f + 0.5f * sinf(hue * 6.28f + 4.2f));
                cube.draw(GL_TRIANGLES);
            }

        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_DEPTH_TEST);
        glViewport(0, 0, vw, vh);
        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    cube.dispose();
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
