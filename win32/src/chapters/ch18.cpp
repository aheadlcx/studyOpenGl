// Ch18 - Mipmap: ground receding to horizon, F min filter, +/- bias.
#include "../common/common.h"
#include <stdio.h>

int run_ch18() {
    win::Window w;
    if (!win::open(w, "Ch18 - Mipmap (F: min filter, +/-: LOD bias)", 1000, 700)) return 1;

    const char* VS = R"(#version 330
layout(location=0) in vec3 a_pos;
layout(location=2) in vec2 a_uv;
uniform mat4 u_mvp;
uniform float u_scroll;
out vec2 v_uv;
void main() {
    v_uv = vec2(a_uv.x, a_uv.y + u_scroll);
    gl_Position = u_mvp * vec4(a_pos, 1.0);
}
)";
    const char* FS = R"(#version 330
in vec2 v_uv;
uniform sampler2D u_tex;
uniform float u_bias;
out vec4 fragColor;
void main() { fragColor = texture(u_tex, v_uv, u_bias); }   // biased sampling
)";
    GLuint prog = makeProgram(VS, FS);

    // large ground, dense uv repeat
    const int SEG = 64; float SIZE = 60.0f, REP = 24.0f;
    std::vector<float> verts;
    std::vector<GLuint> idx;
    for (int i = 0; i <= SEG; i++)
        for (int j = 0; j <= SEG; j++) {
            verts.insert(verts.end(), {
                -SIZE/2 + SIZE*i/SEG, 0, -SIZE/2 + SIZE*j/SEG,
                REP * i / SEG, REP * j / SEG
            });
        }
    for (int i = 0; i < SEG; i++)
        for (int j = 0; j < SEG; j++) {
            int a = i * (SEG + 1) + j, b = a + SEG + 1;
            idx.insert(idx.end(), { (GLuint)a, (GLuint)b, (GLuint)a + 1,
                                    (GLuint)a + 1, (GLuint)b, (GLuint)b + 1 });
        }
    Mesh ground = makeMesh(verts, {{0,3},{2,2}}, &idx);
    GLuint tex = checkerTex(256, 8, true);
    glGenerateMipmap(GL_TEXTURE_2D);
    glBindTexture(GL_TEXTURE_2D, tex);
    glGenerateMipmap(GL_TEXTURE_2D);

    printf("\n=== Ch18: F cycles min filter, +/- LOD bias (watch the horizon!) ===\n");
    const char* names[6] = { "NEAREST", "LINEAR", "N_MIP_N", "N_MIP_L", "L_MIP_N", "L_MIP_L(trilinear)" };
    GLenum filters[6] = { GL_NEAREST, GL_LINEAR, GL_NEAREST_MIPMAP_NEAREST,
                          GL_NEAREST_MIPMAP_LINEAR, GL_LINEAR_MIPMAP_NEAREST, GL_LINEAR_MIPMAP_LINEAR };
    int filter = 5; float bias = 0;

    while (win::beginFrame(w)) {
        if (win::keyTap('F')) filter = (filter + 1) % 6;
        if (win::keyDown(VK_ADD)) bias += 0.02f;
        if (win::keyDown(VK_SUBTRACT)) bias -= 0.02f;
        bias = clampf(bias, -3.0f, 3.0f);
        printf("\rmin=%-20s bias=%+.2f  ", names[filter], bias);
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filters[filter]);

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.55f, 0.7f, 0.85f);   // sky color = fog
        glEnable(GL_DEPTH_TEST);

        Mat4 proj, view, model, pv, mvp;
        matPerspective(proj, 55.0f, (float)vw / vh, 0.1f, 120.0f);
        matLookAt(view, 0, 1.4f, 6, 0, 0.6f, -10, 0, 1, 0);
        matIdentity(model);
        matTranslate(model, 0, 0, -24.0f);
        matMul(pv, proj, view);
        matMul(mvp, pv, model);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, tex);
        glUseProgram(prog);
        glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, mvp);
        glUniform1i(uLoc(prog, "u_tex"), 0);
        glUniform1f(uLoc(prog, "u_bias"), bias);
        glUniform1f(uLoc(prog, "u_scroll"), (float)w.time * 0.1f);
        ground.draw(GL_TRIANGLES);
        glDisable(GL_DEPTH_TEST);

        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    glDeleteTextures(1, &tex);
    ground.dispose();
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
