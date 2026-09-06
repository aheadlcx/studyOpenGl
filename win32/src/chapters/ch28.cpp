// Ch28 - Transform feedback: GPU particles with ping-pong buffers (G gravity, +/- count).
#include "../common/common.h"
#include <stdio.h>
#include <random>

int run_ch28() {
    win::Window w;
    if (!win::open(w, "Ch28 - Transform Feedback particles (G gravity, +/- count)", 1000, 700)) return 1;

    const char* UPDATE_VS = R"(#version 330
layout(location=0) in vec4 a_posLife;
layout(location=1) in vec4 a_velSeed;
uniform float u_dt, u_gravity, u_speed;
out vec4 v_posLife;
out vec4 v_velSeed;
void main() {
    vec3 pos = a_posLife.xyz;
    vec3 vel = a_velSeed.xyz * u_speed;
    vel.y -= u_gravity * u_dt * 4.0;
    pos += vel * u_dt;
    vec3 lo = vec3(-2.4, -1.6, -1.0), hi = vec3(2.4, 1.6, 1.0);
    for (int i = 0; i < 3; i++) {
        if (pos[i] < lo[i]) { pos[i] = lo[i]; vel[i] = abs(vel[i]); }
        if (pos[i] > hi[i]) { pos[i] = hi[i]; vel[i] = -abs(vel[i]); }
    }
    float life = a_posLife.w - u_dt;
    if (life <= 0.0) {
        float s = a_velSeed.w;
        pos = vec3(sin(s * 12.9898) * 2.2, 1.4, cos(s * 78.233) * 0.8);
        vel = vec3(sin(s * 43.1), -0.4, cos(s * 91.7)) * 0.4;
        life = 2.5 + s * 2.0;
    }
    v_posLife = vec4(pos, life);
    v_velSeed = vec4(vel / u_speed, a_velSeed.w);
}
)";
    const char* UPDATE_FS = R"(#version 330
precision mediump float;
out vec4 fragColor;
void main() { fragColor = vec4(1.0); }
)";
    const char* RENDER_VS = R"(#version 330
layout(location=0) in vec4 a_posLife;
uniform mat4 u_mvp;
uniform float u_psize;
out float v_life;
void main() {
    v_life = a_posLife.w;
    gl_Position = u_mvp * vec4(a_posLife.xyz, 1.0);
    gl_PointSize = u_psize;
}
)";
    const char* RENDER_FS = R"(#version 330
in float v_life;
out vec4 fragColor;
void main() {
    vec3 cool = vec3(0.35, 0.75, 1.0), warm = vec3(1.0, 0.75, 0.35);
    fragColor = vec4(mix(cool, warm, clamp(v_life * 0.4, 0.0, 1.0)),
                     clamp(v_life, 0.0, 1.0));
}
)";
    const char* xfbVaryings[2] = { "v_posLife", "v_velSeed" };
    GLuint updateProg = makeProgram(UPDATE_VS, UPDATE_FS, xfbVaryings, 2);
    GLuint renderProg = makeProgram(RENDER_VS, RENDER_FS);

    int count = 2000;
    GLuint vbos[2], vaos[2], xfb;
    glGenBuffers(2, vbos);
    glGenVertexArrays(2, vaos);
    glGenTransformFeedbacks(1, &xfb);

    std::mt19937 rng(1234);
    auto rebuild = [&]() {
        glDeleteBuffers(2, vbos);
        glDeleteVertexArrays(2, vaos);
        glGenBuffers(2, vbos);
        glGenVertexArrays(2, vaos);
        std::vector<float> init(count * 8);
        std::uniform_real_distribution<float> d(-1.0f, 1.0f);
        for (int i = 0; i < count; i++) {
            init[i*8+0] = d(rng) * 2.2f; init[i*8+1] = d(rng) * 1.5f; init[i*8+2] = d(rng) * 0.9f;
            init[i*8+3] = d(rng) * 2.0f + 2.0f;
            init[i*8+4] = d(rng) * 0.5f; init[i*8+5] = d(rng) * 0.4f + 0.2f; init[i*8+6] = d(rng) * 0.5f;
            init[i*8+7] = d(rng) * 10.0f;
        }
        for (int i = 0; i < 2; i++) {
            glBindBuffer(GL_ARRAY_BUFFER, vbos[i]);
            glBufferData(GL_ARRAY_BUFFER, count * 32, init.data(), GL_DYNAMIC_COPY);
            glBindVertexArray(vaos[i]);
            glVertexAttribPointer(0, 4, GL_FLOAT, GL_FALSE, 32, (const void*)0);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 4, GL_FLOAT, GL_FALSE, 32, (const void*)16);
            glEnableVertexAttribArray(1);
            glBindVertexArray(0);
        }
        glBindTransformFeedback(GL_TRANSFORM_FEEDBACK, xfb);
        glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, 0, vbos[1]);
        glBindTransformFeedback(GL_TRANSFORM_FEEDBACK, 0);
    };
    rebuild();

    printf("\n=== Ch28: +/- particle count, G gravity ===\n");
    bool gravity = true;
    int cur = 0;
    GLint uDt = glGetUniformLocation(updateProg, "u_dt");
    GLint uGr = glGetUniformLocation(updateProg, "u_gravity");
    GLint uSp = glGetUniformLocation(updateProg, "u_speed");

    while (win::beginFrame(w)) {
        if (win::keyTap('G')) gravity = !gravity;
        if (win::keyTap(VK_ADD)) { count = count < 6000 ? count + 1000 : count; rebuild(); }
        if (win::keyTap(VK_SUBTRACT)) { count = count > 1000 ? count - 1000 : count; rebuild(); }
        printf("\rparticles: %d gravity: %d   ", count, gravity ? 1 : 0);

        int vw = w.vpWidth(), vh = w.vpHeight();
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        glClearColor(0.04f, 0.05f, 0.09f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        int dst = 1 - cur;

        Mat4 proj, view, mvp;
        matPerspective(proj, 50.0f, (float)vw / vh, 0.1f, 30.0f);
        matLookAt(view, 0, 0.4f, 6.5f, 0, 0, 0, 0, 1, 0);
        matMul(mvp, proj, view);

        // pass 1: physics update, capture VS output into vbos[dst]
        glUseProgram(updateProg);
        glBindVertexArray(vaos[cur]);
        glUniform1f(uDt, 0.016f);
        glUniform1f(uGr, gravity ? 1.0f : 0.0f);
        glUniform1f(uSp, 1.0f);
        glBindTransformFeedback(GL_TRANSFORM_FEEDBACK, xfb);
        glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, 0, vbos[dst]);
        glEnable(GL_RASTERIZER_DISCARD);
        glBeginTransformFeedback(GL_POINTS);
        glDrawArrays(GL_POINTS, 0, count);
        glEndTransformFeedback();
        glDisable(GL_RASTERIZER_DISCARD);
        glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, 0, 0);
        glBindTransformFeedback(GL_TRANSFORM_FEEDBACK, 0);
        glBindVertexArray(0);

        // pass 2: render newest
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        glDepthMask(GL_FALSE);
        glUseProgram(renderProg);
        glUniformMatrix4fv(uLoc(renderProg, "u_mvp"), 1, GL_FALSE, mvp);
        glUniform1f(uLoc(renderProg, "u_psize"), 6.0f);
        glBindVertexArray(vaos[dst]);
        glDrawArrays(GL_POINTS, 0, count);
        glBindVertexArray(0);
        glDepthMask(GL_TRUE);
        glDisable(GL_BLEND);

        cur = dst;
        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    glDeleteBuffers(2, vbos);
    glDeleteVertexArrays(2, vaos);
    glDeleteTransformFeedbacks(1, &xfb);
    glDeleteProgram(updateProg);
    glDeleteProgram(renderProg);
    win::close(w);
    return 0;
}
