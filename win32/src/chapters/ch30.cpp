// Ch30 - Post-processing convolution: kernels 1-7, step +/-, mix M.
#include "../common/common.h"
#include <stdio.h>

int run_ch30() {
    win::Window w;
    if (!win::open(w, "Ch30 - Post processing (1-7 kernels, +/- step, M mix)", 1000, 700)) return 1;

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
    const char* POST_VS = R"(#version 330
layout(location=0) in vec3 a_pos;
layout(location=2) in vec2 a_uv;
out vec2 v_uv;
void main() {
    v_uv = a_uv;
    gl_Position = vec4(a_pos, 1.0);
}
)";
    const char* POST_FS = R"(#version 330
in vec2 v_uv;
uniform sampler2D u_scene;
uniform float u_offset, u_mix;
uniform float u_kernel[9];
out vec4 fragColor;
void main() {
    vec2 off = vec2(u_offset);
    vec3 s =
        texture(u_scene, v_uv + off * vec2(-1, 1)).rgb * u_kernel[0] +
        texture(u_scene, v_uv + off * vec2( 0, 1)).rgb * u_kernel[1] +
        texture(u_scene, v_uv + off * vec2( 1, 1)).rgb * u_kernel[2] +
        texture(u_scene, v_uv + off * vec2(-1, 0)).rgb * u_kernel[3] +
        texture(u_scene, v_uv).rgb                     * u_kernel[4] +
        texture(u_scene, v_uv + off * vec2( 1, 0)).rgb * u_kernel[5] +
        texture(u_scene, v_uv + off * vec2(-1,-1)).rgb * u_kernel[6] +
        texture(u_scene, v_uv + off * vec2( 0,-1)).rgb * u_kernel[7] +
        texture(u_scene, v_uv + off * vec2( 1,-1)).rgb * u_kernel[8];
    vec3 orig = texture(u_scene, v_uv).rgb;
    fragColor = vec4(mix(orig, s, u_mix), 1.0);
}
)";
    GLuint sceneProg = makeProgram(SCENE_VS, SCENE_FS);
    GLuint postProg = makeProgram(POST_VS, POST_FS);

    Mesh cube = cubeMesh(0.7f);
    Mesh quad = makeMesh({
        -1,-1,0, 0,0,  1,-1,0, 1,0,  -1,1,0, 0,1,
         1,-1,0, 1,0,  1,1,0, 1,1,  -1,1,0, 0,1
    }, {{0,3},{2,2}});

    FBO fbo;
    fbo.create(1024, 1024, 0);

    float kernels[7][9] = {
        {0,0,0, 0,1,0, 0,0,0},                                     // original
        {0,-1,0, -1,5,-1, 0,-1,0},                                 // sharpen
        {1,1,1, 1,1,1, 1,1,1},                                     // box blur (x1/9 in code? keep raw)
        {1,2,1, 2,4,2, 1,2,1},                                     // gaussian (x1/16)
        {1,1,1, 1,-8,1, 1,1,1},                                    // edge
        {-2,-1,0, -1,1,1, 0,1,2},                                  // emboss
        {0,-1,0, 0,3,0, 0,-1,0}                                    // extra sharpen
    };
    const char* kNames[7] = { "ORIGINAL", "SHARPEN", "BOX BLUR", "GAUSSIAN", "EDGE", "EMBOSS", "OIL" };

    printf("\n=== Ch30: 1-7 kernels, +/- step, M mix toggle ===\n");
    int kernel = 1; float step = 1.0f, mixF = 1.0f;

    while (win::beginFrame(w)) {
        for (int k = 0; k < 7; k++)
            if (win::keyTap('1' + k)) kernel = k;
        if (win::keyDown(VK_ADD)) step += 0.02f;
        if (win::keyDown(VK_SUBTRACT)) step -= 0.02f;
        if (win::keyTap('M')) mixF = mixF > 0.5f ? 0.0f : 1.0f;
        step = clampf(step, 0.2f, 5.0f);
        printf("\rkernel: %-10s step: %.2f mix: %.1f  ", kNames[kernel], step, mixF);

        float t = (float)w.time;
        fbo.bind();
        glClearColor(0.07f, 0.09f, 0.13f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        glUseProgram(sceneProg);
        Mat4 proj, view, model, pv, mvp;
        matPerspective(proj, 50.0f, 1.0f, 0.1f, 30.0f);
        matLookAt(view, 3.2f, 2.2f, 4.0f, 0, 0.2f, 0, 0, 1, 0);
        matMul(pv, proj, view);
        matIdentity(model);
        matRotate(model, t * 40.0f, 0.4f, 1, 0.2f);
        matMul(mvp, pv, model);
        glUniformMatrix4fv(uLoc(sceneProg, "u_mvp"), 1, GL_FALSE, mvp);
        cube.draw(GL_TRIANGLES);
        glDisable(GL_DEPTH_TEST);

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, w.width, w.height);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, fbo.colorTex);
        glUseProgram(postProg);
        glUniform1i(uLoc(postProg, "u_scene"), 0);
        glUniform1f(uLoc(postProg, "u_offset"), step / 1024.0f);
        glUniform1f(uLoc(postProg, "u_mix"), mixF);
        float k = kernels[kernel][4];
        float scaled[9];
        for (int i = 0; i < 9; i++) scaled[i] = kernels[kernel][i] / k;
        glUniform1fv(uLoc(postProg, "u_kernel"), 9, scaled);
        quad.draw(GL_TRIANGLES);

        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    fbo.dispose();
    cube.dispose();
    quad.dispose();
    glDeleteProgram(sceneProg);
    glDeleteProgram(postProg);
    win::close(w);
    return 0;
}
