// Ch14 - Depth test: 8 funcs, depth mask, polygon offset (D/M/O keys).
#include "../common/common.h"
#include <stdio.h>

int run_ch14() {
    win::Window w;
    if (!win::open(w, "Ch14 - Depth test (D: func, M: mask, O: polygon offset)", 1000, 700)) return 1;

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

    // two intersecting cubes (interleaved pos+color)
    Mesh cubeA = cubeMesh(0.6f);
    std::vector<GLuint> idx;
    for (int i = 0; i < 36; i++) idx.push_back(i);
    Mesh cubeB = cubeMesh(0.75f);

    // floor + grid lines in the SAME plane (z-fight demo)
    Mesh floorQuad = makeMesh({
        -3,-0.01f,-3, 0.25f,0.3f,0.36f,   3,-0.01f,-3, 0.25f,0.3f,0.36f,
        -3,-0.01f, 3, 0.25f,0.3f,0.36f,   3,-0.01f,-3, 0.25f,0.3f,0.36f,
         3,-0.01f, 3, 0.25f,0.3f,0.36f,  -3,-0.01f, 3, 0.25f,0.3f,0.36f
    }, {{0,3},{1,3}});
    std::vector<float> lines;
    for (int i = -3; i <= 3; i++) {
        lines.insert(lines.end(), { (float)i, 0.0f, -3.0f, 0.9f,0.9f,0.9f,
                                    (float)i, 0.0f,  3.0f, 0.9f,0.9f,0.9f });
        lines.insert(lines.end(), { -3.0f, 0.0f, (float)i, 0.9f,0.9f,0.9f,
                                     3.0f, 0.0f, (float)i, 0.9f,0.9f,0.9f });
    }
    Mesh gridLines = makeMesh(lines, {{0,3},{1,3}});

    printf("\n=== Ch14: D depth func, M depth mask, O polygon offset ===\n");
    const char* fnNames[8] = { "NEVER", "LESS", "EQUAL", "LEQUAL", "GREATER", "NOTEQUAL", "GEQUAL", "ALWAYS" };
    GLenum fnValues[8] = { GL_NEVER, GL_LESS, GL_EQUAL, GL_LEQUAL,
                           GL_GREATER, GL_NOTEQUAL, GL_GEQUAL, GL_ALWAYS };

    int func = 1, offset = 1; bool maskOn = true;

    while (win::beginFrame(w)) {
        if (win::keyTap('D')) func = (func + 1) % 8;
        if (win::keyTap('M')) maskOn = !maskOn;
        if (win::keyTap('O')) offset = !offset;
        printf("\rdepthFunc=%-9s mask=%d polygonOffset=%d  ", fnNames[func], maskOn ? 1 : 0, offset);

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.06f, 0.08f, 0.12f);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(fnValues[func]);
        glDepthMask(maskOn ? GL_TRUE : GL_FALSE);

        Mat4 proj, view, pv, model, mvp;
        matPerspective(proj, 50.0f, (float)vw / vh, 0.1f, 50.0f);
        matLookAt(view, 3.5f, 2.6f, 4.5f, 0, 0.6f, 0, 0, 1, 0);
        matMul(pv, proj, view);
        glUseProgram(prog);
        GLint uMvp = uLoc(prog, "u_mvp");

        // floor with polygon offset (pushes fill back so grid lines show)
        if (offset) { glEnable(GL_POLYGON_OFFSET_FILL); glPolygonOffset(2.0f, 2.0f); }
        matIdentity(model);
        matMul(mvp, pv, model);
        glUniformMatrix4fv(uMvp, 1, GL_FALSE, mvp);
        floorQuad.draw(GL_TRIANGLES);
        if (offset) glDisable(GL_POLYGON_OFFSET_FILL);
        gridLines.draw(GL_LINES);

        // two intersecting cubes
        matIdentity(model);
        matTranslate(model, -0.55f, 0.75f, 0.15f);
        matRotate(model, (float)w.time * 30.0f, 1, 0.6f, 0);
        matMul(mvp, pv, model);
        glUniformMatrix4fv(uMvp, 1, GL_FALSE, mvp);
        cubeA.draw(GL_TRIANGLES);
        matIdentity(model);
        matTranslate(model, 0.55f, 0.9f, -0.1f);
        matRotate(model, -(float)w.time * 40.0f, 0.4f, 1, 0.2f);
        matMul(mvp, pv, model);
        glUniformMatrix4fv(uMvp, 1, GL_FALSE, mvp);
        cubeB.draw(GL_TRIANGLES);

        glDepthMask(GL_TRUE);
        glDisable(GL_DEPTH_TEST);
        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    floorQuad.dispose();
    gridLines.dispose();
    cubeA.dispose();
    cubeB.dispose();
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
