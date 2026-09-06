// Ch15 - Blending: translucent quads, factor/equation presets (B/R/D keys).
#include "../common/common.h"
#include <stdio.h>

int run_ch15() {
    win::Window w;
    if (!win::open(w, "Ch15 - Blending (B: blend preset, R: reverse order, D: depthMask)", 1000, 700)) return 1;

    const char* VS = R"(#version 330
layout(location=0) in vec3 a_pos;
layout(location=1) in vec4 a_color;
uniform mat4 u_mvp;
out vec4 v_color;
void main() {
    v_color = a_color;
    gl_Position = u_mvp * vec4(a_pos, 1.0);
}
)";
    const char* FS = R"(#version 330
in vec4 v_color;
out vec4 fragColor;
void main() { fragColor = v_color; }
)";
    GLuint prog = makeProgram(VS, FS);
    Mesh quad = makeMesh({
        -1,-1,0, 1,1,0,0.6f,   1,-1,0, 1,1,0,0.6f,   -1,1,0, 1,1,0,0.6f,
         1,-1,0, 1,1,0,0.6f,    1,1,0, 1,1,0,0.6f,   -1,1,0, 1,1,0,0.6f
    }, {{0,3},{1,4}});
    Mesh back = makeMesh({
        -3,-2,-2.2f, 0.12f,0.14f,0.18f,1,  3,-2,-2.2f, 0.12f,0.14f,0.18f,1,
        -3, 2,-2.2f, 0.12f,0.14f,0.18f,1,  3,-2,-2.2f, 0.12f,0.14f,0.18f,1,
         3, 2,-2.2f, 0.12f,0.14f,0.18f,1, -3, 2,-2.2f, 0.12f,0.14f,0.18f,1
    }, {{0,3},{1,4}});

    printf("\n=== Ch15: B blend preset, R reverse draw order, D depthMask ===\n");
    const char* presetNames[4] = { "SRC_ALPHA/1-A (standard)", "ONE/1-A (premult)",
                                   "SRC_ALPHA/ONE (add glow)", "ONE/ZERO (opaque)" };
    GLenum srcs[4] = { GL_SRC_ALPHA, GL_ONE, GL_SRC_ALPHA, GL_ONE };
    GLenum dsts[4] = { GL_ONE_MINUS_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO };

    int preset = 0; bool reverse = false, depthMaskOn = false;

    while (win::beginFrame(w)) {
        if (win::keyTap('B')) preset = (preset + 1) % 4;
        if (win::keyTap('R')) reverse = !reverse;
        if (win::keyTap('D')) depthMaskOn = !depthMaskOn;
        printf("\rpreset: %-26s reverse=%d depthMask=%d  ",
               presetNames[preset], reverse ? 1 : 0, depthMaskOn ? 1 : 0);

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.06f, 0.08f, 0.12f);
        glEnable(GL_DEPTH_TEST);
        glUseProgram(prog);

        // opaque background first (blend off, depth write on)
        Mat4 proj, view, model, pv, mvp;
        matPerspective(proj, 45.0f, (float)vw / vh, 0.1f, 20.0f);
        matLookAt(view, 0, 0, 5, 0, 0, 0, 0, 1, 0);
        matMul(pv, proj, view);
        matIdentity(model);
        matMul(mvp, pv, model);
        glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, mvp);
        back.draw(GL_TRIANGLES);

        // translucent quads
        glEnable(GL_BLEND);
        glBlendFunc(srcs[preset], dsts[preset]);
        glDepthMask(depthMaskOn ? GL_TRUE : GL_FALSE);
        for (int i = 0; i < 3; i++) {
            int order = reverse ? 2 - i : i;
            matIdentity(model);
            matTranslate(model, (order - 1) * 1.1f * sinf((float)w.time),
                         (order - 1) * -0.7f * sinf((float)w.time), -1.5f + order * 1.2f);
            matRotate(model, (float)w.time * 40.0f + order * 30.0f, 0, 0, 1);
            matRotate(model, 60.0f, 1, 0, 0);
            matMul(mvp, pv, model);
            glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, mvp);
            quad.draw(GL_TRIANGLES);
        }
        glDepthMask(GL_TRUE);
        glDisable(GL_BLEND);
        glDisable(GL_DEPTH_TEST);

        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    quad.dispose();
    back.dispose();
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
