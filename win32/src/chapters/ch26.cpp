// Ch26 - Uniform Buffer: two programs share one LightBlock (H hue, I intensity).
#include "../common/common.h"
#include <stdio.h>

static const char* VS = R"(#version 330
layout(location=0) in vec3 a_pos;
layout(location=1) in vec3 a_normal;
uniform mat4 u_mvp, u_model;
out vec3 v_normal, v_worldPos;
void main() {
    v_normal = mat3(u_model) * a_normal;
    v_worldPos = (u_model * vec4(a_pos, 1.0)).xyz;
    gl_Position = u_mvp * vec4(a_pos, 1.0);
}
)";
static const char* FS_A = R"(#version 330
layout(std140) uniform LightBlock {
    vec4 lightColor;
    float intensity;
};
in vec3 v_normal, v_worldPos;
uniform vec3 u_objectColor;
out vec4 fragColor;
void main() {
    vec3 N = normalize(v_normal);
    float d = max(dot(N, normalize(vec3(0.6, 1, 0.8))), 0.0);
    fragColor = vec4(u_objectColor * (0.2 + 0.8*d) * lightColor.rgb * intensity, 1.0);
}
)";
static const char* FS_B = R"(#version 330
layout(std140) uniform LightBlock {
    vec4 lightColor;
    float intensity;
};
in vec3 v_normal, v_worldPos;
uniform vec3 u_objectColor;
out vec4 fragColor;
void main() {
    vec3 N = normalize(v_normal);
    float d = max(dot(N, normalize(vec3(0.6, 1, 0.8))), 0.0);
    d = floor(d * 3.0) / 3.0;                     // toon steps
    fragColor = vec4(u_objectColor * (0.25 + 0.75*d) * lightColor.rgb * intensity, 1.0);
}
)";

int run_ch26() {
    win::Window w;
    if (!win::open(w, "Ch26 - Uniform Buffer (H: hue, I: intensity)", 1000, 700)) return 1;

    GLuint progA = makeProgram(VS, FS_A);
    GLuint progB = makeProgram(VS, FS_B);
    int iA = glGetUniformBlockIndex(progA, "LightBlock");
    int iB = glGetUniformBlockIndex(progB, "LightBlock");
    glUniformBlockBinding(progA, iA, 0);
    glUniformBlockBinding(progB, iB, 0);

    // UBO with std140 layout: vec4 color (16B) + float intensity (+12 pad) = 32B
    GLuint ubo = 0;
    glGenBuffers(1, &ubo);
    glBindBuffer(GL_UNIFORM_BUFFER, ubo);
    float zeros[8] = {1,1,1,1, 1,0,0,0};
    glBufferData(GL_UNIFORM_BUFFER, 32, zeros, GL_DYNAMIC_DRAW);
    glBindBufferBase(GL_UNIFORM_BUFFER, 0, ubo);
    glBindBuffer(GL_UNIFORM_BUFFER, 0);

    Mesh cube = cubeMesh(0.6f);

    printf("\n=== Ch26: H cycles hue, I intensity — ONE UBO update drives BOTH cubes ===\n");
    float hue = 0.12f, intensity = 1.0f;

    while (win::beginFrame(w)) {
        if (win::keyDown('H')) hue += 0.01f;
        if (win::keyDown('I')) intensity += 0.01f;
        if (win::keyDown('K')) intensity -= 0.01f;
        intensity = clampf(intensity, 0.2f, 3.0f);
        printf("\rhue=%.2f intensity=%.2f   ", hue, intensity);

        // update the shared UBO once
        float c = 1, x = c * (1.0f - fabsf(fmodf(hue * 6.0f, 2.0f) - 1.0f));
        float r, g, b;
        if (hue < 1.0f/6)      { r = c; g = x; b = 0; }
        else if (hue < 2.0f/6) { r = x; g = c; b = 0; }
        else if (hue < 0.5f)   { r = 0; g = c; b = x; }
        else if (hue < 4.0f/6) { r = 0; g = x; b = c; }
        else if (hue < 5.0f/6) { r = x; g = 0; b = c; }
        else                   { r = c; g = 0; b = x; }
        float block[8] = { r, g, b, 1, intensity, 0, 0, 0 };
        glBindBuffer(GL_UNIFORM_BUFFER, ubo);
        glBufferSubData(GL_UNIFORM_BUFFER, 0, 32, block);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.06f, 0.08f, 0.12f);
        glEnable(GL_DEPTH_TEST);

        Mat4 proj, view, pv, model, mvp;
        matPerspective(proj, 50.0f, (float)vw / vh, 0.1f, 30.0f);
        matLookAt(view, 3.2f, 2.2f, 5, 0, 0.5f, 0, 0, 1, 0);
        matMul(pv, proj, view);

        glUseProgram(progA);
        glUniform3f(uLoc(progA, "u_objectColor"), 0.95f, 0.5f, 0.3f);
        matIdentity(model);
        matTranslate(model, -1.1f, 0.6f, 0);
        matRotate(model, (float)w.time * 40.0f, 0.3f, 1, 0);
        matMul(mvp, pv, model);
        glUniformMatrix4fv(uLoc(progA, "u_mvp"), 1, GL_FALSE, mvp);
        glUniformMatrix4fv(uLoc(progA, "u_model"), 1, GL_FALSE, model);
        cube.draw(GL_TRIANGLES);

        glUseProgram(progB);
        glUniform3f(uLoc(progB, "u_objectColor"), 0.35f, 0.65f, 0.95f);
        matIdentity(model);
        matTranslate(model, 1.1f, 0.6f, 0);
        matRotate(model, -(float)w.time * 40.0f, 0.3f, 1, 0);
        matMul(mvp, pv, model);
        glUniformMatrix4fv(uLoc(progB, "u_mvp"), 1, GL_FALSE, mvp);
        glUniformMatrix4fv(uLoc(progB, "u_model"), 1, GL_FALSE, model);
        cube.draw(GL_TRIANGLES);

        glDisable(GL_DEPTH_TEST);
        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    glDeleteBuffers(1, &ubo);
    cube.dispose();
    glDeleteProgram(progA);
    glDeleteProgram(progB);
    win::close(w);
    return 0;
}
