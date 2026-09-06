// Ch07 - Hello triangle: the complete minimal program (T second triangle, H hue).
#include "../common/common.h"
#include <stdio.h>

int run_ch07() {
    win::Window w;
    if (!win::open(w, "Ch07 - Hello Triangle (T: second triangle, H: hue)", 1000, 700)) return 1;

    const char* VS = shaders::VS_TRI;
    const char* FS = R"(#version 330
in vec3 v_color;
uniform vec3 u_tint;
out vec4 fragColor;
void main() { fragColor = vec4(v_color * u_tint, 1.0); }
)";
    GLuint prog = makeProgram(VS, FS);

    Mesh mainTri = makeMesh({
        -0.6f, -0.5f, 0,  1.0f, 0.2f, 0.2f,
         0.6f, -0.5f, 0,  0.2f, 1.0f, 0.2f,
         0.0f,  0.62f, 0, 0.25f, 0.4f, 1.0f
    }, {{0,3},{1,3}});
    Mesh secondTri = makeMesh({
        0.65f, 0.15f, 0,  1.0f, 1.0f, 0.2f,
        0.98f, -0.55f, 0, 0.2f, 1.0f, 1.0f,
        0.32f, -0.55f, 0, 1.0f, 0.2f, 1.0f
    }, {{0,3},{1,3}});

    GLint uMvp = uLoc(prog, "u_mvp");
    GLint uTint = uLoc(prog, "u_tint");
    printf("\n=== Ch07: T second triangle, H hue, ESC quit ===\n");

    bool second = true;
    float hue = 0.55f, rotSpeed = 0.5f, angle = 0;

    while (win::beginFrame(w)) {
        if (win::keyTap('T')) second = !second;
        if (win::keyTap('H')) hue += 0.1f;
        if (win::keyDown(VK_UP))   rotSpeed += 0.01f;
        if (win::keyDown(VK_DOWN)) rotSpeed -= 0.01f;
        angle += rotSpeed;

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.04f, 0.05f, 0.09f);

        // tint from HSV(hue)
        float c = 0.85f, x = c * (1.0f - fabsf(fmodf(hue * 6.0f, 2.0f) - 1.0f));
        float tr, tg, tb;
        if (hue < 1.0f/6)      { tr = c; tg = x; tb = 0; }
        else if (hue < 2.0f/6) { tr = x; tg = c; tb = 0; }
        else if (hue < 0.5f)   { tr = 0; tg = c; tb = x; }
        else if (hue < 4.0f/6) { tr = 0; tg = x; tb = c; }
        else if (hue < 5.0f/6) { tr = x; tg = 0; tb = c; }
        else                   { tr = c; tg = 0; tb = x; }

        Mat4 model, mvp;
        matIdentity(model);
        matRotate(model, angle, 0, 0, 1);
        matIdentity(mvp);
        matMul(mvp, mvp, model);

        glUseProgram(prog);
        glUniformMatrix4fv(uMvp, 1, GL_FALSE, mvp);
        glUniform3f(uTint, tr, tg, tb);
        mainTri.draw(GL_TRIANGLES);
        if (second) secondTri.draw(GL_TRIANGLES);

        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    mainTri.dispose();
    secondTri.dispose();
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
