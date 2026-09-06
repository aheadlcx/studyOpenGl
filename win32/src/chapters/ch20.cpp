// Ch20 - Cubemap skybox + environment reflection (O: reflect strength).
#include "../common/common.h"
#include <stdio.h>

int run_ch20() {
    win::Window w;
    if (!win::open(w, "Ch20 - Cubemap skybox + reflection (O: reflect strength)", 1000, 700)) return 1;

    const char* SKY_VS = R"(#version 330
layout(location=0) in vec3 a_pos;
uniform mat4 u_viewRot, u_proj;
uniform float u_rot;
out vec3 v_dir;
void main() {
    float c = cos(u_rot), s = sin(u_rot);
    v_dir = vec3(a_pos.x * c - a_pos.y * s, a_pos.x * s + a_pos.y * c, a_pos.z);
    vec4 p = u_proj * u_viewRot * vec4(a_pos, 1.0);
    gl_Position = p.xyww;            // z = w -> depth 1.0 (far plane)
}
)";
    const char* SKY_FS = R"(#version 330
in vec3 v_dir;
uniform samplerCube u_sky;
out vec4 fragColor;
void main() { fragColor = texture(u_sky, v_dir); }
)";
    const char* CUBE_VS = R"(#version 330
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
    const char* CUBE_FS = R"(#version 330
in vec3 v_normal, v_worldPos;
uniform samplerCube u_sky;
uniform vec3 u_viewPos;
uniform float u_reflect;
out vec4 fragColor;
void main() {
    vec3 N = normalize(v_normal);
    vec3 R = reflect(normalize(v_worldPos - u_viewPos), N);
    vec3 env = texture(u_sky, R).rgb;
    float diff = max(dot(N, normalize(vec3(0.5, 1, 0.6))), 0.0);
    fragColor = vec4(mix(vec3(0.25,0.3,0.4) * (0.4 + 0.6*diff), env, u_reflect), 1.0);
}
)";
    GLuint skyProg = makeProgram(SKY_VS, SKY_FS);
    GLuint cubeProg = makeProgram(CUBE_VS, CUBE_FS);

    // star cubemap: 6 procedural faces
    int size = 128;
    GLuint tex = 0;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_CUBE_MAP, tex);
    for (int f = 0; f < 6; f++) {
        std::vector<unsigned char> px(size * size * 4);
        unsigned seed = 11u + f * 77u;
        for (int i = 0; i < size * size; i++) {
            seed = seed * 1103515245u + 12345u;
            bool star = ((seed >> 16) % 512) < 2;
            px[i*4] = star ? 240 : 10; px[i*4+1] = star ? 240 : 14;
            px[i*4+2] = star ? 240 : 34; px[i*4+3] = 255;
        }
        glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + f, 0, GL_RGBA,
                     size, size, 0, GL_RGBA, GL_UNSIGNED_BYTE, px.data());
    }
    glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS);

    // skybox = big cube, positions only (built lazily in the loop below)
    Mesh skyMesh;

    Mesh cube = cubeMesh(0.55f, false);

    printf("\n=== Ch20: O reflect strength (drag mouse not supported; auto-orbit) ===\n");
    float reflect = 0.7f;

    while (win::beginFrame(w)) {
        if (win::keyDown(VK_ADD)) reflect += 0.01f;
        if (win::keyDown(VK_SUBTRACT)) reflect -= 0.01f;
        reflect = clampf(reflect, 0.0f, 1.0f);
        printf("\rreflect=%.2f   ", reflect);

        int vw = w.vpWidth(), vh = w.vpHeight();
        glDisable(GL_DEPTH_TEST);
        glViewport(0, 0, vw, vh);
        glClearColor(0.02f, 0.02f, 0.04f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);

        float yaw = (float)w.time * 0.2f, pitch = 0.3f;
        float r = 7.0f;
        float ex = r * cosf(pitch) * sinf(yaw), ey = r * sinf(pitch), ez = r * cosf(pitch) * cosf(yaw);

        Mat4 proj, view, viewRot;
        matPerspective(proj, 60.0f, (float)vw / vh, 0.1f, 200.0f);
        matLookAt(view, ex, ey, ez, 0, 0, 0, 0, 1, 0);
        memcpy(viewRot, view, sizeof(Mat4));
        viewRot[12] = viewRot[13] = viewRot[14] = 0.0f;   // strip translation!

        // sky
        glDepthFunc(GL_LEQUAL);
        glDepthMask(GL_FALSE);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_CUBE_MAP, tex);
        glUseProgram(skyProg);
        glUniformMatrix4fv(uLoc(skyProg, "u_viewRot"), 1, GL_FALSE, viewRot);
        glUniformMatrix4fv(uLoc(skyProg, "u_proj"), 1, GL_FALSE, proj);
        glUniform1f(uLoc(skyProg, "u_rot"), (float)w.time * 0.2f);
        glUniform1i(uLoc(skyProg, "u_sky"), 0);
        if (skyMesh.vao == 0) {
            std::vector<float> spos;
            std::vector<GLuint> sidx;
            float faces[6][3] = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
            float uaxes[6][3] = {{0,0,-1},{0,0,1},{1,0,0},{1,0,0},{1,0,0},{-1,0,0}};
            float vaxes[6][3] = {{0,1,0},{0,1,0},{0,0,-1},{0,0,1},{0,1,0},{0,1,0}};
            int p = 0;
            spos.resize(72);
            for (int f = 0; f < 6; f++)
                for (int j = 0; j < 2; j++)
                    for (int i = 0; i < 2; i++) {
                        float a = i - 0.5f, b = j - 0.5f;
                        spos[p++] = (faces[f][0] + uaxes[f][0]*a*2 + vaxes[f][0]*b*2) * 60.0f;
                        spos[p++] = (faces[f][1] + uaxes[f][1]*a*2 + vaxes[f][1]*b*2) * 60.0f;
                        spos[p++] = (faces[f][2] + uaxes[f][2]*a*2 + vaxes[f][2]*b*2) * 60.0f;
                    }
            for (int f = 0; f < 6; f++) {
                int base = f * 4;
                int q[6] = {0,1,2, 2,1,3};
                for (int i = 0; i < 6; i++) sidx.push_back(base + q[i]);
            }
            skyMesh = makeMesh(spos, {{0,3}}, &sidx);
        }
        skyMesh.draw(GL_TRIANGLES);
        glDepthMask(GL_TRUE);

        // center cube with reflection
        glUseProgram(cubeProg);
        glUniform1i(uLoc(cubeProg, "u_sky"), 0);
        glUniform3f(uLoc(cubeProg, "u_viewPos"), ex, ey, ez);
        glUniform1f(uLoc(cubeProg, "u_reflect"), reflect);
        Mat4 model, pv, mvp;
        matIdentity(model);
        matRotate(model, (float)w.time * 30.0f, 0.3f, 1, 0.1f);
        matMul(pv, proj, view);
        matMul(mvp, pv, model);
        glUniformMatrix4fv(uLoc(cubeProg, "u_mvp"), 1, GL_FALSE, mvp);
        glUniformMatrix4fv(uLoc(cubeProg, "u_model"), 1, GL_FALSE, model);
        cube.draw(GL_TRIANGLES);

        glDepthFunc(GL_LESS);
        glDisable(GL_DEPTH_TEST);
        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    glDeleteTextures(1, &tex);
    cube.dispose();
    skyMesh.dispose();
    glDeleteProgram(skyProg);
    glDeleteProgram(cubeProg);
    win::close(w);
    return 0;
}
