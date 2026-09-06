// Ch32 - MRT: one draw writes albedo (attachment 0) + normals (attachment 1).
#include "../common/common.h"
#include <stdio.h>

int run_ch32() {
    win::Window w;
    if (!win::open(w, "Ch32 - MRT (V: view attachment / split)", 1000, 700)) return 1;

    const char* GB_VS = R"(#version 330
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
    const char* GB_FS = R"(#version 330
in vec3 v_normal, v_worldPos;
layout(location=0) out vec4 o_albedo;
layout(location=1) out vec4 o_normal;
void main() {
    float ck = mod(floor(v_worldPos.x * 2.0) + floor(v_worldPos.y * 2.0)
                 + floor(v_worldPos.z * 2.0), 2.0);
    o_albedo = vec4(mix(vec3(0.85, 0.4, 0.25), vec3(0.95, 0.9, 0.8), ck), 1.0);
    o_normal = vec4(normalize(v_normal) * 0.5 + 0.5, 1.0);
}
)";
    const char* SHOW_VS = R"(#version 330
layout(location=0) in vec3 a_pos;
layout(location=2) in vec2 a_uv;
out vec2 v_uv;
void main() {
    v_uv = a_uv;
    gl_Position = vec4(a_pos, 1.0);
}
)";
    const char* SHOW_FS = R"(#version 330
in vec2 v_uv;
uniform sampler2D u_tex0, u_tex1;
uniform int u_mode;
out vec4 fragColor;
void main() {
    vec3 a = texture(u_tex0, v_uv).rgb;
    vec3 n = texture(u_tex1, v_uv).rgb;
    if (u_mode == 0)      fragColor = vec4(a, 1.0);
    else if (u_mode == 1) fragColor = vec4(n, 1.0);
    else                  fragColor = vec4(v_uv.x < 0.5 ? a : n, 1.0);
}
)";
    GLuint gbufProg = makeProgram(GB_VS, GB_FS);
    GLuint showProg = makeProgram(SHOW_VS, SHOW_FS);
    Mesh cube = cubeMesh(0.7f);
    Mesh quad = makeMesh({
        -1,-1,0, 0,0,  1,-1,0, 1,0,  -1,1,0, 0,1,
         1,-1,0, 1,0,  1,1,0, 1,1,  -1,1,0, 0,1
    }, {{0,3},{2,2}});

    // MRT fbo: 2 color textures + depth
    const int RT = 1024;
    GLuint fbo = 0, colorTex[2] = {0,0}, depthRbo = 0;
    glGenTextures(2, colorTex);
    for (int i = 0; i < 2; i++) {
        glBindTexture(GL_TEXTURE_2D, colorTex[i]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, RT, RT, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }
    glGenRenderbuffers(1, &depthRbo);
    glBindRenderbuffer(GL_RENDERBUFFER, depthRbo);
    glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, RT, RT);
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    for (int i = 0; i < 2; i++)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + i, GL_TEXTURE_2D, colorTex[i], 0);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthRbo);
    GLenum bufs[2] = { GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1 };
    glDrawBuffers(2, bufs);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
        printf("[mrt] fbo incomplete!\n");
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    GLenum back[1] = { GL_BACK };
    glDrawBuffers(1, back);

    printf("\n=== Ch32: V cycles attachment view (albedo / normal / split) ===\n");
    int mode = 2;

    while (win::beginFrame(w)) {
        if (win::keyTap('V')) mode = (mode + 1) % 3;
        printf("\rview: %s   ", mode == 0 ? "attachment 0 (albedo)" :
                                 mode == 1 ? "attachment 1 (normals)" : "split");

        float t = (float)w.time;
        // pass 1: G-buffer
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        GLenum bufs[2] = { GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1 };
        glDrawBuffers(2, bufs);
        glViewport(0, 0, RT, RT);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        glUseProgram(gbufProg);
        Mat4 proj, view, model, pv, mvp;
        matPerspective(proj, 50.0f, 1.0f, 0.1f, 30.0f);
        matLookAt(view, 2.8f, 2.0f, 3.6f, 0, 0.2f, 0, 0, 1, 0);
        matMul(pv, proj, view);
        matIdentity(model);
        matRotate(model, t * 40.0f, 0.4f, 1, 0.2f);
        matMul(mvp, pv, model);
        glUniformMatrix4fv(uLoc(gbufProg, "u_mvp"), 1, GL_FALSE, mvp);
        glUniformMatrix4fv(uLoc(gbufProg, "u_model"), 1, GL_FALSE, model);
        cube.draw(GL_TRIANGLES);
        glDisable(GL_DEPTH_TEST);

        // pass 2: display
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDrawBuffers(1, back);
        glViewport(0, 0, w.width, w.height);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, colorTex[0]);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, colorTex[1]);
        glUseProgram(showProg);
        glUniform1i(uLoc(showProg, "u_tex0"), 0);
        glUniform1i(uLoc(showProg, "u_tex1"), 1);
        glUniform1i(uLoc(showProg, "u_mode"), mode);
        quad.draw(GL_TRIANGLES);
        glActiveTexture(GL_TEXTURE0);

        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    glDeleteTextures(2, colorTex);
    glDeleteRenderbuffers(1, &depthRbo);
    glDeleteFramebuffers(1, &fbo);
    cube.dispose();
    quad.dispose();
    glDeleteProgram(gbufProg);
    glDeleteProgram(showProg);
    win::close(w);
    return 0;
}
