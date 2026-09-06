// Ch31 - MSAA: multisample FBO + blit resolve (S cycles samples).
#include "../common/common.h"
#include <stdio.h>
#include <vector>

int run_ch31() {
    win::Window w;
    if (!win::open(w, "Ch31 - MSAA (S: cycle samples, W: wire)", 1000, 700, 4)) return 1;

    const char* VS = R"(#version 330
layout(location=0) in vec3 a_pos;
uniform mat4 u_mvp;
void main() { gl_Position = u_mvp * vec4(a_pos, 1.0); }
)";
    const char* FS = R"(#version 330
uniform vec4 u_color;
out vec4 fragColor;
void main() { fragColor = u_color; }
)";
    const char* DISP_VS = R"(#version 330
layout(location=0) in vec3 a_pos;
layout(location=2) in vec2 a_uv;
out vec2 v_uv;
void main() {
    v_uv = a_uv;
    gl_Position = vec4(a_pos, 1.0);
}
)";
    const char* DISP_FS = R"(#version 330
in vec2 v_uv;
uniform sampler2D u_tex;
out vec4 fragColor;
void main() { fragColor = texture(u_tex, v_uv); }
)";
    GLuint prog = makeProgram(VS, FS);
    GLuint dispProg = makeProgram(DISP_VS, DISP_FS);
    Mesh quad = makeMesh({
        -1,-1,0, 0,0,  1,-1,0, 1,0,  -1,1,0, 0,1,
         1,-1,0, 1,0,  1,1,0, 1,1,  -1,1,0, 0,1
    }, {{0,3},{2,2}});

    // bright thin cross + frame on dark board (aliasing showcase)
    std::vector<float> tri;
    auto addQuad = [&](float x0, float y0, float x1, float y1, float z,
                       float r, float g, float b) {
        tri.insert(tri.end(), {x0,y0,z, r,g,b,  x1,y0,z, r,g,b,  x0,y1,z, r,g,b,
                               x1,y0,z, r,g,b,  x1,y1,z, r,g,b,  x0,y1,z, r,g,b});
    };
    addQuad(-3, -0.02f, 3, 0.02f, 0, 1,1,1);          // thin horizontal
    addQuad(-0.02f, -3, 0.02f, 3, 0, 1,1,1);          // thin vertical
    addQuad(-3, -3, 3, 3, -0.5f, 0.10f, 0.12f, 0.2f); // dark board
    Mesh content = makeMesh(tri, {{0,3},{1,3}});

    GLint maxSamples = 4;
    glGetIntegerv(GL_MAX_SAMPLES, &maxSamples);
    printf("\n=== Ch31: S cycles samples (max %d on this device), W wire toggle ===\n", maxSamples);

    GLuint msaaFbo = 0, msaaColor = 0, msaaDepth = 0;
    GLuint resFbo = 0, resTex = 0;
    int samples = 4;
    auto rebuild = [&]() {
        glDeleteFramebuffers(1, &msaaFbo);
        glDeleteRenderbuffers(1, &msaaColor);
        glDeleteRenderbuffers(1, &msaaDepth);
        glDeleteFramebuffers(1, &resFbo);
        glDeleteTextures(1, &resTex);
        int sw = w.width / 2, sh = w.height / 2;

        glGenRenderbuffers(1, &msaaColor);
        glBindRenderbuffer(GL_RENDERBUFFER, msaaColor);
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_RGBA8, sw, sh);
        glGenRenderbuffers(1, &msaaDepth);
        glBindRenderbuffer(GL_RENDERBUFFER, msaaDepth);
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_DEPTH_COMPONENT24, sw, sh);
        glGenFramebuffers(1, &msaaFbo);
        glBindFramebuffer(GL_FRAMEBUFFER, msaaFbo);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, msaaColor);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, msaaDepth);

        glGenTextures(1, &resTex);
        glBindTexture(GL_TEXTURE_2D, resTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, sw, sh, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glGenFramebuffers(1, &resFbo);
        glBindFramebuffer(GL_FRAMEBUFFER, resFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, resTex, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            printf("[msaa] resolve fbo incomplete!\n");
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    };
    rebuild();

    bool wire = false;
    while (win::beginFrame(w)) {
        if (win::keyTap('S')) { samples = samples >= maxSamples ? 0 : (samples == 0 ? 2 : samples * 2); rebuild(); }
        if (win::keyTap('W')) wire = !wire;
        printf("\rsamples: %d  (S to change)   ", samples);

        int sw = w.width / 2, sh = w.height / 2;

        // pass 1: draw into MSAA fbo
        glBindFramebuffer(GL_FRAMEBUFFER, msaaFbo);
        glViewport(0, 0, sw, sh);
        glClearColor(0.05f, 0.06f, 0.1f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        glUseProgram(prog);
        Mat4 proj, view, model, pv, mvp;
        matPerspective(proj, 50.0f, 1.0f, 0.1f, 30.0f);
        matLookAt(view, 0, 0, 7, 0, 0, 0, 0, 1, 0);
        matMul(pv, proj, view);
        matIdentity(model);
        matRotate(model, (float)w.time * 30.0f, 0, 0, 1);
        matMul(mvp, pv, model);
        glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, mvp);
        glUniform4f(uLoc(prog, "u_color"), 1, 1, 1, 1);
        if (wire) glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
        content.draw(GL_TRIANGLES);
        if (wire) glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        glDisable(GL_DEPTH_TEST);

        // pass 2: blit resolve (MSAA -> texture)
        glBindFramebuffer(GL_READ_FRAMEBUFFER, msaaFbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, resFbo);
        glBlitFramebuffer(0, 0, sw, sh, 0, 0, sw, sh, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        // pass 3: present
        glViewport(0, 0, w.width, w.height);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, resTex);
        glUseProgram(dispProg);
        glUniform1i(uLoc(dispProg, "u_tex"), 0);
        quad.draw(GL_TRIANGLES);

        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    glDeleteFramebuffers(1, &msaaFbo);
    glDeleteRenderbuffers(1, &msaaColor);
    glDeleteRenderbuffers(1, &msaaDepth);
    glDeleteFramebuffers(1, &resFbo);
    glDeleteTextures(1, &resTex);
    content.dispose();
    quad.dispose();
    glDeleteProgram(prog);
    glDeleteProgram(dispProg);
    win::close(w);
    return 0;
}
