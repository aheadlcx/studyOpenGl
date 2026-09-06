// Ch16 - Stencil test: object outline 3-pass (O: outline, [/: scale).
#include "../common/common.h"
#include <stdio.h>

int run_ch16() {
    win::Window w;
    if (!win::open(w, "Ch16 - Stencil outline (O: outline, [ ]: outline scale)", 1000, 700)) return 1;

    const char* VS = R"(#version 330
layout(location=0) in vec3 a_pos;
layout(location=1) in vec3 a_color;
uniform mat4 u_mvp;
out vec3 v_color;
void main() {
    v_color = a_color;
    gl_Position = u_mvp * vec4(a_pos, 1.0);
}
)";
    const char* FS = R"(#version 330
in vec3 v_color;
out vec4 fragColor;
void main() { fragColor = vec4(v_color, 1.0); }
)";
    GLuint prog = makeProgram(VS, FS);
    Mesh cube = cubeMesh(0.55f);
    Mesh floorQ = makeMesh({
        -4,-1.4f,-4, 0.22f,0.28f,0.34f,  4,-1.4f,-4, 0.22f,0.28f,0.34f,
        -4,-1.4f, 4, 0.22f,0.28f,0.34f,  4,-1.4f, 4, 0.22f,0.28f,0.34f,
        -4,-1.4f, 4, 0.22f,0.28f,0.34f
    }, {{0,3},{1,3}});
    // rebuild floor properly (6 verts)
    floorQ.dispose();
    floorQ = makeMesh({
        -4,-1.4f,-4, 0.22f,0.28f,0.34f,  4,-1.4f,-4, 0.22f,0.28f,0.34f, -4,-1.4f,4, 0.22f,0.28f,0.34f,
         4,-1.4f,-4, 0.22f,0.28f,0.34f,  4,-1.4f, 4, 0.22f,0.28f,0.34f, -4,-1.4f,4, 0.22f,0.28f,0.34f
    }, {{0,3},{1,3}});

    printf("\n=== Ch16: O toggles outline, [ ] changes outline scale ===\n");

    bool outline = true;
    float oscale = 1.12f;

    while (win::beginFrame(w)) {
        if (win::keyTap('O')) outline = !outline;
        if (win::keyDown(VK_OBRACKET)) oscale -= 0.005f;
        if (win::keyDown(VK_OEM_102)) oscale += 0.005f;
        if (win::keyDown(VK_RBRACKET)) oscale += 0.005f;
        oscale = clampf(oscale, 1.02f, 1.6f);
        printf("\routline: %d  scale: %.3f  ", outline ? 1 : 0, oscale);

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.06f, 0.08f, 0.12f);
        glEnable(GL_DEPTH_TEST);
        glUseProgram(prog);
        GLint uMvp = uLoc(prog, "u_mvp");

        Mat4 proj, view, pv, model, mvp;
        matPerspective(proj, 50.0f, (float)vw / vh, 0.1f, 50.0f);
        matLookAt(view, 4, 3, 5, 0, 1, 0, 0, 1, 0);
        matMul(pv, proj, view);

        // floor: stencil write disabled
        glStencilMask(0x00);
        matIdentity(model);
        matMul(mvp, pv, model);
        glUniformMatrix4fv(uMvp, 1, GL_FALSE, mvp);
        floorQ.draw(GL_TRIANGLES);

        // pass1: draw cube, write stencil 1
        glEnable(GL_STENCIL_TEST);
        glStencilFunc(GL_ALWAYS, 1, 0xFF);
        glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
        glStencilMask(0xFF);
        matIdentity(model);
        matTranslate(model, 0, 1.05f, 0);
        matRotate(model, (float)w.time * 40.0f, 0.3f, 1, 0.2f);
        matMul(mvp, pv, model);
        glUniformMatrix4fv(uMvp, 1, GL_FALSE, mvp);
        cube.draw(GL_TRIANGLES);

        // pass2: outline (scale up, stencil != 1 only)
        if (outline) {
            glStencilFunc(GL_NOTEQUAL, 1, 0xFF);
            glStencilMask(0x00);
            glDepthMask(GL_FALSE);
            matIdentity(model);
            matTranslate(model, 0, 1.05f, 0);
            matRotate(model, (float)w.time * 40.0f, 0.3f, 1, 0.2f);
            matScale(model, oscale, oscale, oscale);
            matMul(mvp, pv, model);
            glUniformMatrix4fv(uMvp, 1, GL_FALSE, mvp);
            glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
            // draw same cube in flat orange (reuse mesh; color via tint not available -> use FS tri? simple: draw wire)
            glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
            cube.draw(GL_TRIANGLES);
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            glDepthMask(GL_TRUE);
        }

        glStencilMask(0xFF);
        glDisable(GL_STENCIL_TEST);
        glDisable(GL_DEPTH_TEST);
        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    cube.dispose();
    floorQ.dispose();
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
