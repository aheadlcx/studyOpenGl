// Ch11 - Face culling: C cycles cull face, W toggles front face winding.
#include "../common/common.h"
#include <stdio.h>

int run_ch11() {
    win::Window w;
    if (!win::open(w, "Ch11 - Face culling (C: cull face, W: winding)", 1000, 700)) return 1;

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
    Mesh cube = cubeMesh(0.7f);

    printf("\n=== Ch11: C cycles [OFF/BACK/FRONT/FRONT_AND_BACK], W toggles CCW/CW ===\n");

    int cull = 1;         // 0 off 1 back 2 front 3 both
    bool cw = false;
    const char* cullNames[4] = { "OFF", "GL_BACK", "GL_FRONT", "GL_FRONT_AND_BACK" };

    while (win::beginFrame(w)) {
        if (win::keyTap('C')) cull = (cull + 1) % 4;
        if (win::keyTap('W')) cw = !cw;
        printf("\rcull: %-18s frontFace: %s  ", cullNames[cull], cw ? "CW " : "CCW");

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.04f, 0.05f, 0.09f);
        glEnable(GL_DEPTH_TEST);

        if (cull != 0) {
            glEnable(GL_CULL_FACE);
            GLenum faces[4] = { 0, GL_BACK, GL_FRONT, GL_FRONT_AND_BACK };
            glCullFace(faces[cull]);
        }
        glFrontFace(cw ? GL_CW : GL_CCW);

        Mat4 proj, view, model, pv, mvp;
        matPerspective(proj, 50.0f, (float)vw / vh, 0.1f, 20.0f);
        matLookAt(view, 2.6f, 1.8f, 3.4f, 0, 0, 0, 0, 1, 0);
        matMul(pv, proj, view);
        matIdentity(model);
        matRotate(model, (float)w.time * 40.0f, 0.4f, 1, 0.2f);
        matMul(mvp, pv, model);
        glUseProgram(prog);
        glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, mvp);
        cube.draw(GL_TRIANGLES);

        glFrontFace(GL_CCW);
        if (cull != 0) glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    cube.dispose();
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
