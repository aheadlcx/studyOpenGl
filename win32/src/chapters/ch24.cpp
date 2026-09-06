// Ch24 - Fog: linear / exp / exp2 over a receding ground (T: type, +/-: density).
#include "../common/common.h"
#include <stdio.h>

int run_ch24() {
    win::Window w;
    if (!win::open(w, "Ch24 - Fog (T: fog type, +/-: density)", 1000, 700)) return 1;

    const char* VS = R"(#version 330
layout(location=0) in vec3 a_pos;
layout(location=2) in vec2 a_uv;
uniform mat4 u_mvp, u_model;
out vec2 v_uv;
out vec3 v_worldPos;
void main() {
    v_uv = a_uv;
    v_worldPos = (u_model * vec4(a_pos, 1.0)).xyz;
    gl_Position = u_mvp * vec4(a_pos, 1.0);
}
)";
    const char* FS = R"(#version 330
in vec2 v_uv;
in vec3 v_worldPos;
uniform sampler2D u_tex;
uniform vec3 u_cameraPos, u_fogColor;
uniform int u_fogType;
uniform float u_density, u_fogEnd;
out vec4 fragColor;
void main() {
    vec3 color = texture(u_tex, v_uv).rgb;
    float d = length(v_worldPos - u_cameraPos);
    float f = 0.0;
    if (u_fogType == 1)     f = clamp((u_fogEnd - d) / (u_fogEnd * 0.6), 0.0, 1.0);
    else if (u_fogType == 2) f = 1.0 - exp(-u_density * d);
    else if (u_fogType == 3) f = 1.0 - exp(-u_density * u_density * d * d);
    fragColor = vec4(mix(color, u_fogColor, f), 1.0);
}
)";
    GLuint prog = makeProgram(VS, FS);

    const int SEG = 64; float SIZE = 120.0f, REP = 30.0f;
    std::vector<float> verts;
    std::vector<GLuint> idx;
    for (int i = 0; i <= SEG; i++)
        for (int j = 0; j <= SEG; j++)
            verts.insert(verts.end(), {
                -SIZE/2 + SIZE*i/SEG, -0.5f, -SIZE/2 + SIZE*j/SEG,
                REP*i/SEG, REP*j/SEG
            });
    for (int i = 0; i < SEG; i++)
        for (int j = 0; j < SEG; j++) {
            int a = i * (SEG+1) + j, b = a + SEG + 1;
            idx.insert(idx.end(), { (GLuint)a, (GLuint)b, (GLuint)a+1,
                                    (GLuint)a+1, (GLuint)b, (GLuint)b+1 });
        }
    Mesh ground = makeMesh(verts, {{0,3},{2,2}}, &idx);
    GLuint tex = checkerTex(256, 8, true);

    printf("\n=== Ch24: T fog type [off/linear/exp/exp2], +/- density ===\n");
    const char* typeNames[4] = { "OFF", "LINEAR", "EXP", "EXP2" };
    int type = 2; float density = 0.03f;

    while (win::beginFrame(w)) {
        if (win::keyTap('T')) type = (type + 1) % 4;
        if (win::keyDown(VK_ADD)) density += 0.001f;
        if (win::keyDown(VK_SUBTRACT)) density -= 0.001f;
        density = clampf(density, 0.0f, 0.2f);
        printf("\rfog: %-7s density: %.3f   ", typeNames[type], density);

        float fogColor[3] = {0.55f, 0.65f, 0.75f};
        glDisable(GL_DEPTH_TEST);
        glClearColor(fogColor[0], fogColor[1], fogColor[2], 1.0f);
        glViewport(0, 0, w.width, w.height);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);

        Mat4 proj, view, pv, model, mvp;
        matPerspective(proj, 55.0f, (float)w.width / w.height, 0.1f, 90.0f);
        matLookAt(view, 0, 1.6f, 10, 0, 0.8f, -12, 0, 1, 0);
        matMul(pv, proj, view);
        matIdentity(model);
        matTranslate(model, 0, 0, -30);
        matMul(mvp, pv, model);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, tex);
        glUseProgram(prog);
        glUniform1i(uLoc(prog, "u_tex"), 0);
        glUniform3f(uLoc(prog, "u_cameraPos"), 0, 1.6f, 10);
        glUniform3f(uLoc(prog, "u_fogColor"), fogColor[0], fogColor[1], fogColor[2]);
        glUniform1i(uLoc(prog, "u_fogType"), type);
        glUniform1f(uLoc(prog, "u_density"), density);
        glUniform1f(uLoc(prog, "u_fogEnd"), 25.0f);
        glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, mvp);
        glUniformMatrix4fv(uLoc(prog, "u_model"), 1, GL_FALSE, model);
        ground.draw(GL_TRIANGLES);

        glDisable(GL_DEPTH_TEST);
        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    glDeleteTextures(1, &tex);
    ground.dispose();
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
