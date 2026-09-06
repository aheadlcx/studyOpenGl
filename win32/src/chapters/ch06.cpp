// Ch06 - Per-fragment tests: scissor / depth / blend with a quad scene.
#include "../common/common.h"
#include <stdio.h>

int run_ch06() {
    win::Window w;
    if (!win::open(w, "Ch06 - Tests & blend (S: scissor, M: depthMask)", 1000, 700)) return 1;

    const char* VS = R"(#version 330
layout(location=0) in vec3 a_pos;
uniform mat4 u_mvp;
void main() { gl_Position = u_mvp * vec4(a_pos, 1.0); }
)";
    const char* FS = R"(#version 330
uniform vec4 u_color;
out vec4 fragColor;
void main() { fragColor = u_color; }
)";
    GLuint prog = makeProgram(VS, FS);

    // unit quad centered at origin (NDC scale via model matrix)
    Mesh quad = makeMesh({
        -1,-1,0,  1,-1,0,  -1,1,0,   1,-1,0,  1,1,0,  -1,1,0
    }, {{0,3}});

    GLint uMvp = uLoc(prog, "u_mvp");
    GLint uCol = uLoc(prog, "u_color");
    printf("\n=== Ch06: S toggles scissor, M toggles depthMask ===\n");

    bool scissor = true, depthMaskOn = true;

    while (win::beginFrame(w)) {
        if (win::keyTap('S')) scissor = !scissor;
        if (win::keyTap('M')) depthMaskOn = !depthMaskOn;
        printf("\rscissor=%d depthMask=%d   ", scissor ? 1 : 0, depthMaskOn ? 1 : 0);

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.04f, 0.05f, 0.09f);
        glEnable(GL_DEPTH_TEST);

        if (scissor) {
            glEnable(GL_SCISSOR_TEST);
            glScissor(vw / 4, vh / 4, vw / 2, vh / 2);
        }

        Mat4 proj, view, pv, model, mvp;
        matPerspective(proj, 45.0f, (float)vw / vh, 0.1f, 20.0f);
        matLookAt(view, 0, 0, 5, 0, 0, 0, 0, 1, 0);
        matMul(pv, proj, view);
        glUseProgram(prog);

        // far quad (red)
        matIdentity(model); matTranslate(model, 0, 0, -1.5f); matScale(model, 1.5f, 1.5f, 1);
        matMul(mvp, pv, model);
        glUniformMatrix4fv(uMvp, 1, GL_FALSE, mvp);
        glUniform4f(uCol, 0.9f, 0.35f, 0.3f, 1.0f);
        quad.draw(GL_TRIANGLES);

        // near quad (blue-green), depth-mask off => ghost (does not hide red)
        glDepthMask(depthMaskOn ? GL_TRUE : GL_FALSE);
        matIdentity(model); matTranslate(model, 0.4f, 0.3f, 0.5f); matScale(model, 0.8f, 0.8f, 1);
        matMul(mvp, pv, model);
        glUniformMatrix4fv(uMvp, 1, GL_FALSE, mvp);
        glUniform4f(uCol, 0.3f, 0.85f, 0.75f, 0.85f);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        quad.draw(GL_TRIANGLES);
        glDepthMask(GL_TRUE);
        glDisable(GL_BLEND);

        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_DEPTH_TEST);
        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    quad.dispose();
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
