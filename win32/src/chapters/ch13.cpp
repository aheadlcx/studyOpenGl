// Ch13 - Camera & projection: orbit camera, fov/near adjustable.
#include "../common/common.h"
#include "../common/hud.h"
#include <stdio.h>

int run_ch13() {
    win::Window w;
    if (!win::open(w, "Ch13 - Camera & Projection (arrows orbit, F fov, N near)", 1000, 700)) return 1;

    const char* VS = R"(#version 330
layout(location=0) in vec3 a_pos;
uniform mat4 u_mvp;
void main() { gl_Position = u_mvp * vec4(a_pos, 1.0); }
)";
    const char* FS = R"(#version 330
uniform vec3 u_color;
out vec4 fragColor;
void main() { fragColor = vec4(u_color, 1.0); }
)";
    GLuint prog = makeProgram(VS, FS);
    Mesh cube = cubeMesh(0.6f);

    printf("\n=== Ch13: arrows orbit, F/F fov, N/n near ===\n");

    float yaw = 0.6f, pitch = 0.5f, fov = 50.0f, nearZ = 0.1f;

    while (win::beginFrame(w)) {
        yaw += 0.15f * (float)(w.time * 0 + 1) * 0.016f * 60.0f * 0.02f; // slow auto orbit
        if (win::keyDown(VK_LEFT))  yaw -= 0.02f;
        if (win::keyDown(VK_RIGHT)) yaw += 0.02f;
        if (win::keyDown(VK_UP))    pitch += 0.02f;
        if (win::keyDown(VK_DOWN))  pitch -= 0.02f;
        if (win::keyTap('F')) fov = fov >= 100.0f ? 30.0f : fov + 10.0f;
        if (win::keyTap('N')) nearZ = nearZ >= 2.0f ? 0.1f : nearZ + 0.2f;
        pitch = clampf(pitch, -1.3f, 1.3f);
        hud::frame(5,
            "[CH13] fps %.1f\n"
            "camera: yaw %.2f rad  pitch %.2f rad\n"
            "orbit radius 14, always looking at origin\n"
            "fov %.0f deg  near %.1f  far 60\n"
            "keys: arrows orbit | F fov | N/n near | ESC quit",
            w.fps, yaw, pitch, fov, nearZ);

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.35f, 0.55f, 0.75f);
        glEnable(GL_DEPTH_TEST);

        float radius = 14.0f;
        float ex = radius * cosf(pitch) * sinf(yaw);
        float ey = radius * sinf(pitch);
        float ez = radius * cosf(pitch) * cosf(yaw);

        Mat4 proj, view, pv, model, mvp;
        matPerspective(proj, fov, (float)vw / vh, nearZ, 60.0f);
        matLookAt(view, ex, ey, ez, 0, 0, 0, 0, 1, 0);
        matMul(pv, proj, view);
        glUseProgram(prog);

        // ground grid
        for (int i = -5; i <= 5; i++) {
            matIdentity(model);
            matTranslate(model, i * 1.6f, 0.55f, 0);
            matMul(mvp, pv, model);
            glUniform3f(uLoc(prog, "u_color"), 0.35f, 0.6f, 0.85f);
            cube.draw(GL_TRIANGLES);
            matIdentity(model);
            matTranslate(model, 0, 0.55f, i * 1.6f);
            matMul(mvp, pv, model);
            cube.draw(GL_TRIANGLES);
        }

        glDisable(GL_DEPTH_TEST);
        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    cube.dispose();
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
