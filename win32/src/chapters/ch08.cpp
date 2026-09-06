// Ch08 - Interpolation: smooth vs flat (F toggles).
#include "../common/common.h"
#include <stdio.h>

int run_ch08() {
    win::Window w;
    if (!win::open(w, "Ch08 - Interpolation (F: smooth/flat)", 1000, 700)) return 1;

    const char* VS_FMT = R"(#version 330
layout(location=0) in vec3 a_pos;
layout(location=1) in vec3 a_color;
uniform mat4 u_mvp;
%s out vec3 v_color;
void main() {
    v_color = a_color;
    gl_Position = u_mvp * vec4(a_pos, 1.0);
}
)";
    const char* FS_FMT = R"(#version 330
%s in vec3 v_color;
out vec4 fragColor;
void main() { fragColor = vec4(v_color, 1.0); }
)";
    char vs[512], fs[512];
    snprintf(vs, sizeof(vs), VS_FMT, "smooth");
    snprintf(fs, sizeof(fs), FS_FMT, "smooth");
    GLuint smoothProg = makeProgram(vs, fs);
    snprintf(vs, sizeof(vs), VS_FMT, "flat");
    snprintf(fs, sizeof(fs), FS_FMT, "flat");
    GLuint flatProg = makeProgram(vs, fs);

    Mesh tri = makeMesh({
        -0.9f, -0.55f, -0.4f,  1.0f, 0.1f, 0.1f,
         0.9f, -0.55f, -0.4f,  0.1f, 1.0f, 0.1f,
         0.0f,  0.85f,  0.6f,  0.15f, 0.3f, 1.0f
    }, {{0,3},{1,3}});

    printf("\n=== Ch08: F toggles smooth/flat. flat shows the PROVOKING (last) vertex ===\n");

    bool flat = false;
    while (win::beginFrame(w)) {
        if (win::keyTap('F')) flat = !flat;
        printf("\rinterpolation: %s   ", flat ? "flat   " : "smooth ");

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.04f, 0.05f, 0.09f);

        Mat4 proj, view, model, pv, mvp;
        matPerspective(proj, 50.0f, (float)vw / vh, 0.1f, 10.0f);
        matLookAt(view, 0, 0, 2.4f, 0, 0, 0, 0, 1, 0);
        matIdentity(model);
        matRotate(model, (float)w.time * 40.0f, 1, 0, 0);   // spin around X: depth differs
        matMul(pv, proj, view);
        matMul(mvp, pv, model);

        glUseProgram(flat ? flatProg : smoothProg);
        glUniformMatrix4fv(uLoc(flat ? flatProg : smoothProg, "u_mvp"), 1, GL_FALSE, mvp);
        tri.draw(GL_TRIANGLES);

        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    tri.dispose();
    glDeleteProgram(smoothProg);
    glDeleteProgram(flatProg);
    win::close(w);
    return 0;
}
