// Ch29 - FBO / RTT: scene -> FBO texture -> fullscreen quad (Z zoom, X mirror).
#include "../common/common.h"
#include <stdio.h>

int run_ch29() {
    win::Window w;
    if (!win::open(w, "Ch29 - FBO RTT (Z: zoom, X: mirror)", 1000, 700)) return 1;

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
uniform float u_mirror, u_zoom;
out vec2 v_uv;
void main() {
    vec2 uv = (a_uv - 0.5) * u_zoom + 0.5;
    v_uv = vec2(mix(uv.x, 1.0 - uv.x, u_mirror), uv.y);
    gl_Position = vec4(a_pos, 1.0);
}
)";
    const char* DISP_FS = R"(#version 330
in vec2 v_uv;
uniform sampler2D u_scene;
out vec4 fragColor;
void main() { fragColor = texture(u_scene, v_uv); }
)";
    GLuint sceneProg = makeProgram(SCENE_VS, SCENE_FS);
    GLuint dispProg = makeProgram(DISP_VS, DISP_FS);

    Mesh cube = cubeMesh(0.6f);
    Mesh quad = makeMesh({
        -1,-1,0, 0,0,  1,-1,0, 1,0,  -1,1,0, 0,1,
         1,-1,0, 1,0,  1,1,0, 1,1,  -1,1,0, 0,1
    }, {{0,3},{2,2}});

    FBO fbo;
    fbo.create(1024, 1024, 0);

    printf("\n=== Ch29: Z zoom, X mirror. Scene renders into FBO, then to screen ===\n");
    float zoom = 1.0f; bool mirror = false;

    while (win::beginFrame(w)) {
        if (win::keyDown('Z')) zoom -= 0.01f;
        if (win::keyDown('X')) { }
        if (win::keyTap('X')) mirror = !mirror;
        if (win::keyDown('C')) zoom += 0.01f;
        zoom = clampf(zoom, 0.2f, 1.0f);
        printf("\rzoom=%.2f mirror=%d   ", zoom, mirror ? 1 : 0);
        if (win::keyDown('X')) mirror = !mirror ? mirror : mirror;

        float t = (float)w.time;
        // ---- pass 1: render into FBO ----
        fbo.bind();
        glClearColor(0.09f, 0.1f, 0.14f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        glUseProgram(sceneProg);
        Mat4 proj, view, model, pv, mvp;
        matPerspective(proj, 50.0f, 1.0f, 0.1f, 30.0f);
        matLookAt(view, 3.4f, 2.4f, 4.2f, 0, 0.4f, 0, 0, 1, 0);
        matMul(pv, proj, view);
        GLint uMvp = uLoc(sceneProg, "u_mvp");
        for (int i = 0; i < 4; i++) {
            matIdentity(model);
            matTranslate(model, i % 2 == 0 ? -0.8f : 0.8f, 0.5f, i < 2 ? -0.8f : 0.8f);
            matRotate(model, t * 40.0f + i * 45.0f, 0.4f, 1, 0.2f);
            matMul(mvp, pv, model);
            glUniformMatrix4fv(uMvp, 1, GL_FALSE, mvp);
            cube.draw(GL_TRIANGLES);
        }
        glDisable(GL_DEPTH_TEST);

        // ---- pass 2: RTT -> screen ----
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, w.width, w.height);
        glClearColor(0.02f, 0.02f, 0.04f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, fbo.colorTex);
        glUseProgram(dispProg);
        glUniform1i(uLoc(dispProg, "u_scene"), 0);
        glUniform1f(uLoc(dispProg, "u_mirror"), mirror ? 1.0f : 0.0f);
        glUniform1f(uLoc(dispProg, "u_zoom"), zoom);
        quad.draw(GL_TRIANGLES);

        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    fbo.dispose();
    cube.dispose();
    quad.dispose();
    glDeleteProgram(sceneProg);
    glDeleteProgram(dispProg);
    win::close(w);
    return 0;
}
