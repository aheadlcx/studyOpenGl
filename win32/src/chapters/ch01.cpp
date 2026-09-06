// Ch01 - Pipeline overview: draws one triangle while the console prints
// which pipeline stage is processing its data.
#include "../common/common.h"
#include <stdio.h>
#include <string.h>

int run_ch01() {
    win::Window w;
    if (!win::open(w, "Ch01 - Pipeline Overview (triangle + console stages)", 1000, 700)) return 1;

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

    // stage 1: vertex data -> VBO (3 vertices, pos3 + color3)
    float verts[] = {
        -0.6f, -0.5f, 0.0f,  1.0f, 0.2f, 0.2f,
         0.6f, -0.5f, 0.0f,  0.2f, 1.0f, 0.2f,
         0.0f,  0.62f, 0.0f, 0.25f, 0.4f, 1.0f
    };
    std::vector<float> v(verts, verts + 18);
    Mesh mesh = makeMesh(v, {{0, 3}, {1, 3}});

    const char* stages[7] = {
        "[1] vertex data   : float[] -> VBO/VAO",
        "[2] vertex shader : gl_Position = mvp * pos (per vertex)",
        "[3] assembly      : 3 vertices -> 1 triangle",
        "[4] clip/divide   : clip to frustum, divide by w -> NDC",
        "[5] rasterize     : triangle -> fragments (interpolated color)",
        "[6] fragment shdr : compute final pixel color",
        "[7] tests/blend   : depth/blend -> framebuffer -> swap"
    };
    double last = 0;
    int stage = 0;
    printf("\n=== Ch01: pipeline stages print while the triangle renders ===\n");

    while (win::beginFrame(w)) {
        if (w.time - last > 1.0) {
            last = w.time;
            printf("%s\n", stages[stage]);
            stage = (stage + 1) % 7;
        }

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.04f, 0.05f, 0.09f);

        Mat4 proj, view, model, pv, mvp;
        matPerspective(proj, 45.0f, (float)vw / vh, 0.1f, 10.0f);
        matLookAt(view, 0, 0, 3, 0, 0, 0, 0, 1, 0);
        matIdentity(model);
        matRotate(model, (float)w.time * 40.0f, 0, 1, 0);
        matMul(pv, proj, view);
        matMul(mvp, pv, model);

        glUseProgram(prog);
        glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, mvp);
        mesh.draw(GL_TRIANGLES);

        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    mesh.dispose();
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
