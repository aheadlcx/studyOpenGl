// Ch05 - Fragment shader: one quad, 6 FS input perspectives (auto-cycling).
#include "../common/common.h"
#include <stdio.h>

int run_ch05() {
    win::Window w;
    if (!win::open(w, "Ch05 - Fragment Shader inputs (SPACE: pause auto-cycling)", 1000, 700)) return 1;

    const char* VS = R"(#version 330
layout(location=0) in vec3 a_pos;
layout(location=2) in vec2 a_uv;
out vec2 v_uv;
void main() {
    v_uv = a_uv;
    gl_Position = vec4(a_pos, 1.0);
}
)";
    const char* FS = R"(#version 330
in vec2 v_uv;
uniform int u_mode;
uniform float u_time, u_density;
out vec4 fragColor;
void main() {
    vec3 c = vec3(0.95, 0.35, 0.30);
    if (u_mode == 1) {                       // gl_FragCoord stripes
        float s = step(0.5, fract(gl_FragCoord.x / u_density));
        c = vec3(0.2, 0.6, 0.95) * (0.55 + 0.45 * s);
    } else if (u_mode == 2) {                // uv visualization
        c = vec3(v_uv.x, v_uv.y, 0.35);
    } else if (u_mode == 3) {                // procedural rings
        float d = length(v_uv - 0.5);
        float ring = 0.5 + 0.5 * sin(d * 40.0 - u_time * 3.0);
        c = mix(vec3(0.95,0.55,0.20), vec3(0.20,0.55,0.95), ring);
    } else if (u_mode == 4) {                // checkerboard
        vec2 g = floor(v_uv * u_density * 0.25);
        c = mix(vec3(0.92), vec3(0.16,0.20,0.32), mod(g.x + g.y, 2.0));
    } else if (u_mode == 5) {                // discard holes
        if (length(v_uv - vec2(0.5, 0.45)) < 0.22 + 0.04 * sin(u_time * 2.0)) discard;
        c = vec3(0.30, 0.85, 0.60);
    }
    fragColor = vec4(c, 1.0);
}
)";
    GLuint prog = makeProgram(VS, FS);
    Mesh quad = makeMesh({
        -0.8f,-0.6f, 0,0,  0.8f,-0.6f, 1,0,  -0.8f,0.6f, 0,1,
         0.8f,-0.6f, 1,0,  0.8f,0.6f, 1,1,  -0.8f,0.6f, 0,1
    }, {{0,3},{2,2}});

    const char* modes[6] = {
        "v_color/vertex color", "gl_FragCoord stripes", "uv gradient",
        "procedural rings", "checkerboard", "discard holes"
    };
    printf("\n=== Ch05: SPACE pauses the auto mode cycling ===\n");

    bool paused = false;
    while (win::beginFrame(w)) {
        if (win::keyTap(VK_SPACE)) paused = !paused;
        int mode = (int)(w.time / 3.0) % 6;
        if (paused) mode = 0;

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.04f, 0.05f, 0.09f);
        printf("\rmode: %d %-28s", mode, modes[mode]);

        glUseProgram(prog);
        glUniform1i(uLoc(prog, "u_mode"), mode);
        glUniform1f(uLoc(prog, "u_time"), (float)w.time);
        glUniform1f(uLoc(prog, "u_density"), 24.0f);
        quad.draw(GL_TRIANGLES);

        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    quad.dispose();
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
