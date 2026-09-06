// Ch22 - Light maps: diffuse + specular + emission textures (E emission, S spec).
#include "../common/common.h"
#include <stdio.h>

int run_ch22() {
    win::Window w;
    if (!win::open(w, "Ch22 - Light maps (S: specular, E: emission)", 1000, 700)) return 1;

    const char* VS = shaders::VS_LIT;
    const char* FS = R"(#version 330
in vec3 v_normal;
in vec3 v_worldPos;
in vec2 v_uv;
uniform sampler2D u_diffuse, u_specular, u_emission;
uniform vec3 u_lightPos, u_lightColor;
uniform float u_specK, u_emisK, u_shininess;
out vec4 fragColor;
void main() {
    vec3 N = normalize(v_normal);
    vec3 L = normalize(u_lightPos - v_worldPos);
    vec3 V = normalize(vec3(0, 0.6, 5.5) - v_worldPos);
    vec3 R = reflect(-L, N);
    vec3 diffTex = texture(u_diffuse, v_uv).rgb;
    float specMask = texture(u_specular, v_uv).r;
    vec3 emis = texture(u_emission, v_uv).rgb;
    vec3 c = 0.12f * u_lightColor
           + max(dot(N, L), 0.0) * u_lightColor * diffTex
           + u_specK * pow(max(dot(R, V), 0.0), u_shininess) * specMask * u_lightColor
           + emis * u_emisK;
    fragColor = vec4(c, 1.0);
}
)";
    GLuint prog = makeProgram(VS, FS);
    Mesh wall = cubeMesh(0.8f, true);

    GLuint diffTex = texFromImage(brickImage(256, 256), 256, 256, true);
    GLuint specTex = texFromImage(noiseImage(128, 128, 42), 128, 128, true);
    GLuint emisTex = texFromImage(stripesImage(128, 128), 128, 128, true);

    printf("\n=== Ch22: S specular strength, E emission strength ===\n");
    float spec = 0.8f, emis = 0.5f;

    while (win::beginFrame(w)) {
        if (win::keyDown('S')) spec += 0.01f;
        if (win::keyDown('X')) spec -= 0.01f;
        if (win::keyDown('E')) emis += 0.01f;
        if (win::keyDown('D')) emis -= 0.01f;
        spec = clampf(spec, 0, 2); emis = clampf(emis, 0, 2);
        printf("\rspec=%.2f emission=%.2f   ", spec, emis);

        float lx = sinf((float)w.time * 0.8f) * 4.0f;
        float lz = cosf((float)w.time * 0.8f) * 4.0f;

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.04f, 0.05f, 0.09f);
        glEnable(GL_DEPTH_TEST);

        Mat4 proj, view, pv, model, mvp;
        matPerspective(proj, 45.0f, (float)vw / vh, 0.1f, 30.0f);
        matLookAt(view, 0, 0.6f, 5.5f, 0, 0, 0, 0, 1, 0);
        matMul(pv, proj, view);
        matIdentity(model);
        matRotate(model, -20.0f, 0, 1, 0);
        matMul(mvp, pv, model);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, diffTex);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, specTex);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, emisTex);

        glUseProgram(prog);
        glUniform1i(uLoc(prog, "u_diffuse"), 0);
        glUniform1i(uLoc(prog, "u_specular"), 1);
        glUniform1i(uLoc(prog, "u_emission"), 2);
        glUniform3f(uLoc(prog, "u_lightPos"), lx, 2.5f, lz);
        glUniform3f(uLoc(prog, "u_lightColor"), 1.0f, 0.96f, 0.88f);
        glUniform1f(uLoc(prog, "u_specK"), spec);
        glUniform1f(uLoc(prog, "u_emisK"), emis);
        glUniform1f(uLoc(prog, "u_shininess"), 48.0f);
        glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, mvp);
        wall.draw(GL_TRIANGLES);
        glDisable(GL_DEPTH_TEST);
        glActiveTexture(GL_TEXTURE0);

        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    glDeleteTextures(1, &diffTex);
    glDeleteTextures(1, &specTex);
    glDeleteTextures(1, &emisTex);
    wall.dispose();
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
