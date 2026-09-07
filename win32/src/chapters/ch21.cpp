// Ch21 - Phong lighting: lit cube + orbiting light (A/D/S/F keys adjust).
#include "../common/common.h"
#include "../common/hud.h"
#include <stdio.h>

int run_ch21() {
    win::Window w;
    if (!win::open(w, "Ch21 - Phong (A ambient, D diffuse, S specular, F shininess)", 1000, 700)) return 1;

    const char* VS = shaders::VS_LIT;
    const char* FS = shaders::FS_PHONG;
    GLuint prog = makeProgram(VS, FS);

    int size = 128;
    Mesh cube = cubeMesh(0.5f, true);
    auto white = std::vector<unsigned char>(size * size * 4, 255);
    for (auto& c : white) { c = 255; }
    GLuint whiteTex = texFromImage(white, size, size, false);
    Mesh floorQ = makeMesh({
        -4,-1,-4, 0,1,0, 0,0,   4,-1,-4, 0,1,0, 0,1,
        -4,-1, 4, 0,1,0, 0,1,   4,-1, 4, 0,1,0, 1,1
    }, {{0,3},{1,3},{2,2}});
    // fix floor winding: proper 6 verts
    floorQ.dispose();
    floorQ = makeMesh({
        -4,-1,-4, 0,1,0, 0,0,   4,-1,-4, 0,1,0, 1,0,  -4,-1,4, 0,1,0, 0,1,
         4,-1,-4, 0,1,0, 1,0,   4,-1, 4, 0,1,0, 1,1,  -4,-1,4, 0,1,0, 0,1
    }, {{0,3},{1,3},{2,2}});

    printf("\n=== Ch21: A ambient, D diffuse, S specular, F shininess, G ground ===\n");

    float ambient = 0.15f, diffuse = 0.9f, specular = 0.6f, shininess = 32.0f;

    while (win::beginFrame(w)) {
        if (win::keyDown('A')) ambient += 0.002f;
        if (win::keyDown('Z')) ambient -= 0.002f;
        if (win::keyDown('D')) diffuse += 0.005f;
        if (win::keyDown('C')) diffuse -= 0.005f;
        if (win::keyDown('S')) specular += 0.005f;
        if (win::keyDown('X')) specular -= 0.005f;
        if (win::keyDown('F')) shininess += 1.0f;
        if (win::keyDown('V')) shininess -= 1.0f;
        ambient = clampf(ambient, 0, 1); diffuse = clampf(diffuse, 0, 2);
        specular = clampf(specular, 0, 2); shininess = clampf(shininess, 2, 128);
        hud::frame(6,
            "[CH21] Phong  fps %.1f\n"
            "ambient %.2f   diffuse %.2f\n"
            "specular %.2f  shininess %.0f\n"
            "light orbiting: angle %.1f rad\n"
            "keys: A/Z ambient | D/C diffuse | S/X spec | F/V shininess | ESC quit",
            w.fps, ambient, diffuse, specular, shininess, (float)w.time * 0.8f);

        float lx = sinf((float)w.time * 0.8f) * 4.0f;
        float lz = cosf((float)w.time * 0.8f) * 4.0f;

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.06f, 0.08f, 0.12f);
        glEnable(GL_DEPTH_TEST);
        glUseProgram(prog);
        glUniform3f(uLoc(prog, "u_lightPos"), lx, 3.0f, lz);
        glUniform3f(uLoc(prog, "u_viewPos"), 5, 4, 6);
        glUniform3f(uLoc(prog, "u_lightColor"), 1.0f, 0.96f, 0.88f);
        glUniform1f(uLoc(prog, "u_ambient"), ambient);
        glUniform1f(uLoc(prog, "u_diffuseK"), diffuse);
        glUniform1f(uLoc(prog, "u_specularK"), specular);
        glUniform1f(uLoc(prog, "u_shininess"), shininess);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, whiteTex);
        glUniform1i(uLoc(prog, "u_diffuse"), 0);

        Mat4 proj, view, pv, model, mvp;
        matPerspective(proj, 45.0f, (float)vw / vh, 0.1f, 30.0f);
        matLookAt(view, 5, 4, 6, 0, 0.5f, 0, 0, 1, 0);
        matMul(pv, proj, view);

        matIdentity(model);
        matTranslate(model, 0, 0.75f, 0);
        matRotate(model, (float)w.time * 30.0f, 0.2f, 1, 0.1f);
        matMul(mvp, pv, model);
        glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, mvp);
        glUniformMatrix4fv(uLoc(prog, "u_model"), 1, GL_FALSE, model);
        cube.draw(GL_TRIANGLES);

        matIdentity(model);
        matMul(mvp, pv, model);
        glUniformMatrix4fv(uLoc(prog, "u_mvp"), 1, GL_FALSE, mvp);
        glUniformMatrix4fv(uLoc(prog, "u_model"), 1, GL_FALSE, model);
        floorQ.draw(GL_TRIANGLES);

        glDisable(GL_DEPTH_TEST);
        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    glDeleteTextures(1, &whiteTex);
    cube.dispose();
    floorQ.dispose();
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
