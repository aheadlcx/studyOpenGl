// Ch25 - Instancing: one draw call draws N*N cubes (+/-: grid size, W: wave).
#include "../common/common.h"
#include <stdio.h>

int run_ch25() {
    win::Window w;
    if (!win::open(w, "Ch25 - Instancing (+/-: grid N, W: wave)", 1000, 700)) return 1;

    const char* VS = R"(#version 330
layout(location=0) in vec3 a_pos;
layout(location=1) in vec3 a_normal;
layout(location=2) in vec4 i_offset;   // divisor=1
layout(location=3) in vec3 i_color;    // divisor=1
uniform mat4 u_vp;
uniform float u_time, u_wave;
out vec3 v_normal, v_color;
void main() {
    float id = float(gl_InstanceID);
    float a = u_time + id * 0.7;
    float c = cos(a), s = sin(a);
    vec3 p = a_pos * i_offset.w;
    p = vec3(p.x * c - p.z * s, p.y, p.x * s + p.z * c);
    p.y += sin(u_time * 2.0 + i_offset.x * 2.0 + i_offset.z * 2.0) * u_wave;
    vec3 world = p + i_offset.xyz;
    v_normal = vec3(s, 0.4, c);
    v_color = vec3(0.5 + 0.5*sin(id*0.37), 0.5 + 0.5*sin(id*0.37+2.1), 0.5 + 0.5*sin(id*0.37+4.2));
    gl_Position = u_vp * vec4(world, 1.0);
}
)";
    const char* FS = R"(#version 330
in vec3 v_normal, v_color;
out vec4 fragColor;
void main() {
    float d = max(dot(normalize(v_normal), normalize(vec3(0.5, 1, 0.7))), 0.0);
    fragColor = vec4(v_color * (0.35 + 0.65 * d), 1.0);
}
)";
    GLuint prog = makeProgram(VS, FS);
    Mesh cube = cubeMesh(0.5f);

    printf("\n=== Ch25: +/- grid size (N*N instances), W wave, C color mode ===\n");

    float gridF = 12.0f, wave = 0.4f;
    Mesh instMesh;
    int count = 0;
    auto rebuild = [&]() {
        if (instMesh.vao) instMesh.dispose();
        int grid = (int)gridF;
        std::vector<float> cv;
        std::vector<GLuint> ci;
        // small cube vertices
        float f6[6][3] = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        float u6[6][3] = {{0,0,-1},{0,0,1},{1,0,0},{1,0,0},{1,0,0},{-1,0,0}};
        float v6[6][3] = {{0,1,0},{0,1,0},{0,0,-1},{0,0,1},{0,1,0},{0,1,0}};
        for (int f = 0; f < 6; f++)
            for (int j = 0; j < 2; j++)
                for (int i = 0; i < 2; i++) {
                    float a = i-0.5f, b = j-0.5f;
                    cv.push_back((f6[f][0] + u6[f][0]*a + v6[f][0]*b) * 0.5f);
                    cv.push_back((f6[f][1] + u6[f][1]*a + v6[f][1]*b) * 0.5f);
                    cv.push_back((f6[f][2] + u6[f][2]*a + v6[f][2]*b) * 0.5f);
                }
        for (int f = 0; f < 6; f++) {
            int base = f*4, q[6] = {0,1,2,2,1,3};
            for (int i = 0; i < 6; i++) ci.push_back(base + q[i]);
        }
        instMesh = makeMesh(cv, {{0,3},{1,3}}, &ci);
        count = grid * grid;
        std::vector<float> inst;
        float spacing = 96.0f / grid / grid + 0.55f; if (spacing > 2.2f) spacing = 2.2f;
        float half = (grid - 1) * spacing / 2;
        for (int i = 0; i < grid; i++)
            for (int j = 0; j < grid; j++) {
                inst.push_back(i*spacing - half);
                inst.push_back(0.55f);
                inst.push_back(j*spacing - half);
                inst.push_back(0.25f + (i + j) % 3 * 0.12f);
                inst.push_back(0.3f + 0.7f*i/grid); inst.push_back(0.3f + 0.6f*j/grid); inst.push_back(0.9f - 0.5f*i/grid);
            }
        meshAddInstanced(instMesh, inst, {{2,4},{3,3}});
    };
    rebuild();

    while (win::beginFrame(w)) {
        bool rebuildNeeded = false;
        if (win::keyDown(VK_ADD)) { gridF += 0.15f; rebuildNeeded = true; }
        if (win::keyDown(VK_SUBTRACT)) { gridF -= 0.15f; rebuildNeeded = true; }
        gridF = clampf(gridF, 1.0f, 40.0f);
        if (win::keyTap('W')) wave = wave > 0 ? 0.0f : 0.5f;
        if (rebuildNeeded) rebuild();
        printf("\rgrid: %d x %d (%d instances, 1 draw call) wave=%.1f   ",
               (int)gridF, (int)gridF, count, wave);

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.04f, 0.05f, 0.09f);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        float camDist = 8.0f + gridF * 0.45f;
        Mat4 proj, view, vp;
        matPerspective(proj, 50.0f, (float)vw / vh, 0.1f, 200.0f);
        matLookAt(view, camDist*0.7f, camDist*0.55f, camDist, 0, 0, 0, 0, 1, 0);
        matMul(vp, proj, view);
        glUseProgram(prog);
        glUniformMatrix4fv(uLoc(prog, "u_vp"), 1, GL_FALSE, vp);
        glUniform1f(uLoc(prog, "u_time"), (float)w.time);
        glUniform1f(uLoc(prog, "u_wave"), wave);
        instMesh.drawInstanced(GL_TRIANGLES, count);

        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    instMesh.dispose();
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
