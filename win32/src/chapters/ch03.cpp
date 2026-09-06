// Ch03 - MVP: the same triangle in 4 spaces (local / world / view / clip-frustum).
#include "../common/common.h"
#include <stdio.h>

int run_ch03() {
    win::Window w;
    if (!win::open(w, "Ch03 - Vertex Shader MVP (4 viewports = 4 spaces)", 1000, 700)) return 1;

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

    std::vector<float> tri = {
        -0.45f, -0.30f, 0,  1, 0.3f, 0.25f,
         0.45f, -0.25f, 0,  0.25f, 1, 0.4f,
         0.02f,  0.42f, 0,  0.3f, 0.55f, 1.0f
    };
    Mesh triMesh = makeMesh(tri, {{0,3},{1,3}});
    // world axes
    Mesh axes = makeMesh({
        0,0,0, 1,0.3f,0.3f,  0.9f,0,0, 1,0.3f,0.3f,
        0,0,0, 0.3f,1,0.4f,  0,0.9f,0, 0.3f,1,0.4f,
        0,0,0, 0.35f,0.5f,1, 0,0,0.9f, 0.35f,0.5f,1
    }, {{0,3},{1,3}});

    printf("\n=== Ch03: 4 viewports top-to-bottom = Local / World / View / Clip ===\n");

    while (win::beginFrame(w)) {
        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.04f, 0.05f, 0.09f);
        glEnable(GL_DEPTH_TEST);

        int rowH = vh / 4;
        float spin = (float)w.time * 60.0f;
        float orbit = (float)w.time * 0.9f;

        Mat4 proj, view, model, pv, mvp;
        for (int row = 0; row < 4; row++) {
            glViewport(0, vh - (row + 1) * rowH, vw, rowH);
            float aspect = (float)vw / rowH;
            matPerspective(proj, 45.0f, aspect, 0.1f, 10.0f);
            glUseProgram(prog);

            if (row == 0) {                                   // local: spin in place
                matLookAt(view, 0, 0, 3, 0, 0, 0, 0, 1, 0);
                matIdentity(model);
                matRotate(model, spin, 0, 1, 0);
            } else if (row == 1) {                            // world: T*R into scene
                matLookAt(view, 0, 0.5f, 3.4f, 0, 0.2f, 0, 0, 1, 0);
                matIdentity(model);
                matIdentity(mvp);
                matMul(mvp, proj, view);                      // draw axes first
                glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, mvp);
                axes.draw(GL_LINES);
                matIdentity(model);
                matTranslate(model, 0.65f, 0, 0);
                matRotate(model, spin, 0, 1, 0);
            } else if (row == 2) {                            // view: orbit camera
                float r = 3.6f;
                matLookAt(view, r * sinf(orbit), 1.2f, r * cosf(orbit),
                          0, 0.2f, 0, 0, 1, 0);
                matIdentity(model);
                matIdentity(mvp);
                matMul(mvp, proj, view);
                glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, mvp);
                axes.draw(GL_LINES);
                matIdentity(model);
                matTranslate(model, 0.65f, 0, 0);
                matRotate(model, spin, 0, 1, 0);
            } else {                                          // clip: frustum side view
                matLookAt(view, -2.4f, 1.7f, 3.0f, 0.5f, 0, -1.2f, 0, 1, 0);
                matIdentity(model);
                matScale(model, 0.12f, 0.12f, 0.12f);
                matMul(mvp, proj, view);
                glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, mvp);
                // camera cube (flat yellow box)
                Mesh cam = cubeMesh(0.5f);
                cam.draw(GL_LINES);
                cam.dispose();
                matIdentity(model);
                matScale(model, 2.2f, 1.5f, 1.0f);
                matMul(mvp, proj, view);
                // frustum wire (pyramid from origin)
                static Mesh frustum;
                static bool built = false;
                if (!built) {
                    frustum = makeMesh({
                        0,0,0, 1,0.9f,0.4f,  -1,-0.65f,-2.6f, 1,0.9f,0.4f,
                        0,0,0, 1,0.9f,0.4f,   1,-0.65f,-2.6f, 1,0.9f,0.4f,
                        0,0,0, 1,0.9f,0.4f,  -1, 0.75f,-2.6f, 1,0.9f,0.4f,
                        0,0,0, 1,0.9f,0.4f,   1, 0.75f,-2.6f, 1,0.9f,0.4f
                    }, {{0,3},{1,3}});
                    built = true;
                }
                frustum.draw(GL_LINES);
                matIdentity(model);
                matTranslate(model, 0.1f, 0.1f, -1.5f);
                matRotate(model, spin * 0.5f, 0, 1, 0);
            }

            matMul(pv, proj, view);
            matMul(mvp, pv, model);
            glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, mvp);
            triMesh.draw(GL_TRIANGLES);
        }

        glViewport(0, 0, vw, vh);
        glDisable(GL_DEPTH_TEST);
        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    triMesh.dispose();
    axes.dispose();
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
