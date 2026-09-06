// Ch23 - Normal mapping: brick wall + procedural normal map (S: strength).
#include "../common/common.h"
#include <stdio.h>

int run_ch23() {
    win::Window w;
    if (!win::open(w, "Ch23 - Normal mapping (S: normal strength)", 1000, 700)) return 1;

    const char* VS = R"(#version 330
layout(location=0) in vec3 a_pos;
layout(location=1) in vec3 a_normal;
layout(location=2) in vec2 a_uv;
uniform mat4 u_mvp, u_model;
out vec3 v_worldPos, v_normal;
out vec2 v_uv;
out mat3 v_tbn;
void main() {
    v_worldPos = (u_model * vec4(a_pos, 1.0)).xyz;
    v_uv = a_uv;
    vec3 N = normalize(mat3(u_model) * a_normal);
    vec3 T = normalize(mat3(u_model) * vec3(1, 0, 0));
    vec3 B = cross(N, T);
    v_tbn = mat3(T, B, N);
    gl_Position = u_mvp * vec4(a_pos, 1.0);
}
)";
    const char* FS = R"(#version 330
in vec3 v_worldPos, v_normal;
in vec2 v_uv;
in mat3 v_tbn;
uniform sampler2D u_diffuse, u_normalMap;
uniform vec3 u_lightPos, u_viewPos;
uniform float u_strength;
out vec4 fragColor;
void main() {
    vec3 albedo = texture(u_diffuse, v_uv).rgb;
    vec3 sn = texture(u_normalMap, v_uv).rgb * 2.0 - 1.0;
    sn.xy *= u_strength;
    vec3 N = normalize(v_tbn * normalize(sn));
    vec3 L = normalize(u_lightPos - v_worldPos);
    vec3 V = normalize(u_viewPos - v_worldPos);
    vec3 c = 0.15f * albedo + max(dot(N, L), 0.0) * albedo;
    c += pow(max(dot(reflect(-L, N), V), 0.0), 32.0) * 0.35;
    fragColor = vec4(c, 1.0);
}
)";
    GLuint prog = makeProgram(VS, FS);

    // brick wall plane (XY)
    std::vector<float> wall;
    std::vector<GLuint> idx;
    float W = 4.0f, H = 3.0f;
    int sx = 4, sy = 3;
    for (int iy = 0; iy <= sy; iy++)
        for (int ix = 0; ix <= sx; ix++)
            wall.insert(wall.end(), {
                -W/2 + W*ix/sx, -H/2 + H*iy/sy, 0,
                0, 0, 1,
                2.0f*ix/sx, 2.0f*iy/sy
            });
    for (int iy = 0; iy < sy; iy++)
        for (int ix = 0; ix < sx; ix++) {
            int a = iy * (sx + 1) + ix, b = a + sx + 1;
            idx.insert(idx.end(), { (GLuint)a, (GLuint)a+1, (GLuint)b,
                                    (GLuint)a+1, (GLuint)b+1, (GLuint)b });
        }
    Mesh wallMesh = makeMesh(wall, {{0,3},{1,3},{2,2}}, &idx);

    GLuint diffTex = texFromImage(brickImage(256, 256), 256, 256, true);
    auto nrmImg = brickNormalImage(256);
    GLuint normTex = texFromImage(nrmImg, 256, 256, true);

    printf("\n=== Ch23: S normal strength (0 = flat) ===\n");
    float strength = 1.0f;

    while (win::beginFrame(w)) {
        if (win::keyDown('S')) strength += 0.01f;
        if (win::keyDown('X')) strength -= 0.01f;
        strength = clampf(strength, 0.0f, 3.0f);
        printf("\rnormal strength: %.2f   ", strength);

        float lx = sinf((float)w.time * 0.9f) * 4.0f;
        float ly = cosf((float)w.time * 0.6f) * 2.0f;

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.04f, 0.05f, 0.09f);
        glEnable(GL_DEPTH_TEST);

        Mat4 proj, view, pv, model, mvp;
        matPerspective(proj, 45.0f, (float)vw / vh, 0.1f, 30.0f);
        matLookAt(view, 0, 0, 6, 0, 0, 0, 0, 1, 0);
        matMul(pv, proj, view);
        matIdentity(model);
        matMul(mvp, pv, model);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, diffTex);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, normTex);
        glUseProgram(prog);
        glUniform1i(uLoc(prog, "u_diffuse"), 0);
        glUniform1i(uLoc(prog, "u_normalMap"), 1);
        glUniform3f(uLoc(prog, "u_lightPos"), lx, ly, 4.0f);
        glUniform3f(uLoc(prog, "u_viewPos"), 0, 0, 6);
        glUniform1f(uLoc(prog, "u_strength"), strength);
        glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, mvp);
        wallMesh.draw(GL_TRIANGLES);
        glDisable(GL_DEPTH_TEST);
        glActiveTexture(GL_TEXTURE0);

        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    glDeleteTextures(1, &diffTex);
    glDeleteTextures(1, &normTex);
    wallMesh.dispose();
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
