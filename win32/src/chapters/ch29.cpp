// Ch29 - FBO attachments visualizer: COLOR / DEPTH / STENCIL views (keys 1/2/3).
// The depth attachment is a *depth texture* (sampleable), the stencil effect is
// visualized by painting green through a stencil-tested fullscreen pass.
#include "../common/common.h"
#include <stdio.h>

int run_ch29() {
    win::Window w;
    if (!win::open(w, "Ch29 - FBO attachments (1: color, 2: depth, 3: stencil)", 1000, 700)) return 1;

    const char* SCENE_VS = R"(#version 330
layout(location=0) in vec3 a_pos;
layout(location=1) in vec3 a_color;
uniform mat4 u_mvp;
out vec3 v_color;
void main() {
    v_color = a_color;
    gl_Position = u_mvp * vec4(a_pos, 1.0);
}
)";
    const char* SCENE_FS = R"(#version 330
in vec3 v_color;
out vec4 fragColor;
void main() { fragColor = vec4(v_color, 1.0); }
)";
    const char* DISP_VS = R"(#version 330
layout(location=0) in vec3 a_pos;
layout(location=2) in vec2 a_uv;
uniform float u_zoom;
out vec2 v_uv;
void main() {
    v_uv = (a_uv - 0.5) * u_zoom + 0.5;
    gl_Position = vec4(a_pos, 1.0);
}
)";
    const char* COLOR_FS = R"(#version 330
in vec2 v_uv;
uniform sampler2D u_colorTex;
out vec4 fragColor;
void main() { fragColor = texture(u_colorTex, v_uv); }
)";
    const char* DEPTH_FS = R"(#version 330
in vec2 v_uv;
uniform sampler2D u_depthTex;
out vec4 fragColor;
void main() {
    // sample the DEPTH ATTACHMENT (a depth texture): r channel = depth 0..1
    float d = texture(u_depthTex, v_uv).r;
    fragColor = vec4(vec3(pow(d, 8.0)), 1.0);   // pow expands the near range
}
)";
    const char* STENCIL_FS = R"(#version 330
in vec2 v_uv;
uniform sampler2D u_colorTex;
out vec4 fragColor;
void main() {
    vec3 c = texture(u_colorTex, v_uv).rgb;
    fragColor = vec4(c * 0.4 + vec3(0.0, 0.6, 0.1) * 0.6, 1.0);   // green tint
}
)";

    GLuint sceneProg = makeProgram(SCENE_VS, SCENE_FS);
    GLuint dispColorProg = makeProgram(DISP_VS, COLOR_FS);
    GLuint dispDepthProg = makeProgram(DISP_VS, DEPTH_FS);
    GLuint stencilProg = makeProgram(DISP_VS, STENCIL_FS);
    Mesh cube = cubeMesh(0.55f);
    Mesh quad = makeMesh({
        -1,-1,0, 0,0,  1,-1,0, 1,0,  -1,1,0, 0,1,
         1,-1,0, 1,0,  1,1,0, 1,1,  -1,1,0, 0,1
    }, {{0,3},{2,2}});

    // FBO: color texture + DEPTH TEXTURE (sampleable!) + stencil via renderbuffer
    const int RT = 1024;
    GLuint fbo = 0, colorTex = 0, depthTex = 0, stencilRbo = 0;
    glGenTextures(1, &colorTex);
    glBindTexture(GL_TEXTURE_2D, colorTex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, RT, RT, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glGenTextures(1, &depthTex);
    glBindTexture(GL_TEXTURE_2D, depthTex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, RT, RT, 0,
                 GL_DEPTH_COMPONENT, GL_UNSIGNED_INT, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glGenRenderbuffers(1, &stencilRbo);
    glBindRenderbuffer(GL_RENDERBUFFER, stencilRbo);
    glRenderbufferStorage(GL_RENDERBUFFER, GL_STENCIL_INDEX8, RT, RT);
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTex, 0);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTex, 0);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_RENDERBUFFER, stencilRbo);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
        printf("[ch29] FBO incomplete (some drivers dislike depth-texture + stencil combo)\n");
    }
    glBindFramebuffer(GL_FRAMEBUFFER, 0);

    printf("\n=== Ch29: 1 color view, 2 depth view, 3 stencil view (green hole) ===\n");

    int view = 0;   // 0 color, 1 depth, 2 stencil
    while (win::beginFrame(w)) {
        if (win::keyTap('1')) view = 0;
        if (win::keyTap('2')) view = 1;
        if (win::keyTap('3')) view = 2;
        printf("\rview: %s   ", view == 0 ? "COLOR attachment"
                             : view == 1 ? "DEPTH attachment" : "STENCIL visualization");

        float t = (float)w.time;

        // ---- pass 1: scene into FBO (color + depth textures), stencil window written ----
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, RT, RT);
        glClearColor(0, 0, 0, 1);                       // clear depth = 1.0 (far)
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_STENCIL_TEST);

        // stencil pass: mark a circular "window" region as 1
        glStencilFunc(GL_ALWAYS, 1, 0xFF);
        glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
        glStencilMask(0xFF);
        glUseProgram(sceneProg);
        Mat4 proj, view_, model, pv, mvp;
        matPerspective(proj, 50.0f, 1.0f, 0.1f, 30.0f);
        matLookAt(view_, 3.4f, 2.4f, 4.2f, 0, 0.4f, 0, 0, 1, 0);
        matMul(pv, proj, view_);
        matIdentity(model);
        matMul(mvp, pv, model);
        glUniformMatrix4fv(uLoc(sceneProg, "u_mvp"), 1, GL_FALSE, mvp);
        {
            // a screen-space center quad writes stencil 1 (the "hole")
            std::vector<float> hole = {
                -0.35f,-0.35f,0, 0.1f,0.6f,0.2f,   0.35f,-0.35f,0, 0.1f,0.6f,0.2f,
                -0.35f, 0.35f,0, 0.1f,0.6f,0.2f,   0.35f,-0.35f,0, 0.1f,0.6f,0.2f,
                 0.35f, 0.35f,0, 0.1f,0.6f,0.2f,  -0.35f, 0.35f,0, 0.1f,0.6f,0.2f
            };
            Mesh holeQuad = makeMesh(hole, {{0,3},{1,3}});
            holeQuad.draw(GL_TRIANGLES);
            holeQuad.dispose();
        }

        // depth test on for the scene
        glDepthMask(GL_TRUE);
        glEnable(GL_DEPTH_TEST);
        glStencilFunc(GL_ALWAYS, 0, 0xFF);   // rest of scene: don't touch stencil
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);

        matIdentity(model);
        matRotate(model, t * 40.0f, 0.4f, 1, 0.2f);
        matMul(mvp, pv, model);
        glUniformMatrix4fv(uLoc(sceneProg, "u_mvp"), 1, GL_FALSE, mvp);
        cube.draw(GL_TRIANGLES);

        // ---- stencil visualization: paint green where stencil == 1 ----
        if (view == 2) {
            glEnable(GL_STENCIL_TEST);
            glStencilFunc(GL_EQUAL, 1, 0xFF);
            glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
            glStencilMask(0x00);
            glDepthMask(GL_FALSE);
            glDisable(GL_DEPTH_TEST);
            glUseProgram(stencilProg);
            glUniform1f(uLoc(stencilProg, "u_zoom"), 1.0f);
            quad.draw(GL_TRIANGLES);
            glStencilMask(0xFF);
            glDisable(GL_STENCIL_TEST);
            glDepthMask(GL_TRUE);
        }
        glDisable(GL_STENCIL_TEST);
        glDisable(GL_DEPTH_TEST);

        // ---- pass 2: display selected attachment ----
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, w.width, w.height);
        glClearColor(0.02f, 0.02f, 0.04f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, view == 1 ? depthTex : colorTex);
        GLuint useProg = (view == 1) ? dispDepthProg : dispColorProg;
        glUseProgram(useProg);
        glUniform1f(uLoc(useProg, "u_zoom"), 1.0f);
        if (view == 1) glUniform1i(uLoc(useProg, "u_depthTex"), 0);
        else           glUniform1i(uLoc(useProg, "u_colorTex"), 0);
        quad.draw(GL_TRIANGLES);

        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    glDeleteTextures(1, &colorTex);
    glDeleteTextures(1, &depthTex);
    glDeleteRenderbuffers(1, &stencilRbo);
    glDeleteFramebuffers(1, &fbo);
    cube.dispose();
    quad.dispose();
    glDeleteProgram(sceneProg);
    glDeleteProgram(dispColorProg);
    glDeleteProgram(dispDepthProg);
    glDeleteProgram(stencilProg);
    win::close(w);
    return 0;
}
