// Ch02 - Vertex data: interleaved vs separate buffers (V key toggles).
#include "../common/common.h"
#include <stdio.h>

int run_ch02() {
    win::Window w;
    if (!win::open(w, "Ch02 - Vertex Data (V: toggle interleaved/separate)", 1000, 700)) return 1;

    const char* VS = R"(#version 330
layout(location=0) in vec3 a_pos;
layout(location=1) in vec3 a_color;
uniform mat4 u_mvp;
out vec3 v_color;
void main() {
    v_color = a_color;
    gl_Position = u_mvp * vec4(a_pos, 1.0);
}
)";
    const char* FS = R"(#version 330
in vec3 v_color;
out vec4 fragColor;
void main() { fragColor = vec4(v_color, 1.0); }
)";
    GLuint prog = makeProgram(VS, FS);

    // interleaved: pos3+color3 per vertex
    float inter[] = {
        -0.75f, -0.55f, 0,  1.0f, 0.3f, 0.25f,
         0.85f, -0.45f, 0,  0.25f, 1.0f, 0.40f,
         0.05f,  0.80f, 0,  0.30f, 0.55f, 1.00f
    };
    Mesh interMesh = makeMesh(std::vector<float>(inter, inter + 18), {{0,3},{1,3}});

    // separate: two VBOs, each stride 0
    float pArr[] = { -0.75f,-0.55f,0,  0.85f,-0.45f,0,  0.05f,0.80f,0 };
    float cArr[] = { 1.0f,0.3f,0.25f,  0.25f,1.0f,0.4f,  0.30f,0.55f,1.0f };
    Mesh sepMesh;
    glGenVertexArrays(1, &sepMesh.vao);
    glBindVertexArray(sepMesh.vao);
    GLuint vbos[2];
    glGenBuffers(2, vbos);
    glBindBuffer(GL_ARRAY_BUFFER, vbos[0]);
    glBufferData(GL_ARRAY_BUFFER, sizeof(pArr), pArr, GL_STATIC_DRAW);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 0, nullptr);
    glEnableVertexAttribArray(0);
    glBindBuffer(GL_ARRAY_BUFFER, vbos[1]);
    glBufferData(GL_ARRAY_BUFFER, sizeof(cArr), cArr, GL_STATIC_DRAW);
    glVertexAttribPointer(1, 3, GL_FLOAT, GL_FALSE, 0, nullptr);
    glEnableVertexAttribArray(1);
    glBindVertexArray(0);
    sepMesh.vertexCount = 3;

    bool interleaved = true;
    printf("\n=== Ch02: V toggles interleaved / separate vertex layout ===\n");

    while (win::beginFrame(w)) {
        if (win::keyTap('V')) interleaved = !interleaved;
        printf(interleaved ? "\r[interleaved] " : "\r[separate   ] ");

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.04f, 0.05f, 0.09f);

        Mat4 proj, view, model, pv, mvp;
        matPerspective(proj, 45.0f, (float)vw / vh, 0.1f, 10.0f);
        matLookAt(view, 0, 0, 3, 0, 0, 0, 0, 1, 0);
        matIdentity(model);
        matRotate(model, (float)w.time * 30.0f, 0, 1, 0);
        matMul(pv, proj, view);
        matMul(mvp, pv, model);

        glUseProgram(prog);
        glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, mvp);
        (interleaved ? interMesh : sepMesh).draw(GL_TRIANGLES);

        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    interMesh.dispose();
    glDeleteBuffers(2, nullptr); // individual vbos deleted on exit anyway
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
