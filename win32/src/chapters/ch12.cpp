// Ch12 - Transform matrix: T*R*S sliders via arrow keys + axes.
#include "../common/common.h"
#include <stdio.h>

int run_ch12() {
    win::Window w;
    if (!win::open(w, "Ch12 - Transform (arrows: move, +/-: scale, Q/E: rotY)", 1000, 700)) return 1;

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

    Mesh cube = cubeMesh(0.6f);
    // colored axes (X red, Y green, Z blue)
    Mesh axes = makeMesh({
        0,0,0, 1,0.2f,0.2f,  2.2f,0,0, 1,0.2f,0.2f,
        0,0,0, 0.2f,1,0.2f,  0,2.2f,0, 0.2f,1,0.2f,
        0,0,0, 0.3f,0.4f,1,  0,0,2.2f, 0.3f,0.4f,1
    }, {{0,3},{1,3}});

    printf("\n=== Ch12: arrows translate, +/- scale, Q/E rotate ===\n");

    float px = 0, py = 0, pz = 0, scale = 1.0f, rotY = 0, rotX = 0;

    while (win::beginFrame(w)) {
        float dt = 0.016f;
        if (win::keyDown(VK_LEFT))  px -= dt;
        if (win::keyDown(VK_RIGHT)) px += dt;
        if (win::keyDown(VK_UP))    py += dt;
        if (win::keyDown(VK_DOWN))  py -= dt;
        if (win::keyDown(VK_ADD) || win::keyDown(VK_OEM_PLUS)) scale += dt;
        if (win::keyDown(VK_SUBTRACT) || win::keyDown(VK_OEM_MINUS)) scale -= dt;
        if (win::keyDown('Q')) rotY -= 60 * dt;
        if (win::keyDown('E')) rotY += 60 * dt;
        scale = clampf(scale, 0.2f, 2.0f);
        printf("\rT=(%.2f,%.2f,%.2f) S=%.2f rotY=%.0f   ", px, py, pz, scale, rotY);

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.04f, 0.05f, 0.09f);
        glEnable(GL_DEPTH_TEST);

        Mat4 proj, view, pv, model, mvp;
        matPerspective(proj, 50.0f, (float)vw / vh, 0.1f, 20.0f);
        matLookAt(view, 3.2f, 2.2f, 4.2f, 0, 0, 0, 0, 1, 0);
        matMul(pv, proj, view);
        glUseProgram(prog);

        // axes with identity model
        matIdentity(model);
        matMul(mvp, pv, model);
        glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, mvp);
        axes.draw(GL_LINES);

        // Model = T * R * S
        matIdentity(model);
        matTranslate(model, px, py, pz);
        matRotate(model, rotX, 1, 0, 0);
        matRotate(model, rotY, 0, 1, 0);
        matScale(model, scale, scale, scale);
        matMul(mvp, pv, model);
        glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, mvp);
        cube.draw(GL_TRIANGLES);

        glDisable(GL_DEPTH_TEST);
        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    cube.dispose();
    axes.dispose();
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
