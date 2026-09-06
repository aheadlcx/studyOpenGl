// Ch17 - 2D texture: wrap/filter/uv-flip (W wrap, F filters, +/- uv scale, Y flip).
#include "../common/common.h"
#include <stdio.h>

int run_ch17() {
    win::Window w;
    if (!win::open(w, "Ch17 - Texture 2D (W wrap, F filter, +/- uv scale, Y flip)", 1000, 700)) return 1;

    const char* VS = shaders::VS_TEX;
    const char* FS = R"(#version 330
in vec2 v_uv;
uniform sampler2D u_tex;
uniform float u_flip;
out vec4 fragColor;
void main() {
    vec2 uv = vec2(v_uv.x, mix(v_uv.y, 1.0 - v_uv.y, u_flip));
    fragColor = texture(u_tex, uv);
}
)";
    GLuint prog = makeProgram(VS, FS);
    Mesh quad = makeMesh({
        -0.85f,-0.85f,0, 0,0,  0.85f,-0.85f,0, 1,0,  -0.85f,0.85f,0, 0,1,
         0.85f,-0.85f,0, 1,0,   0.85f,0.85f,0, 1,1,   -0.85f,0.85f,0, 0,1
    }, {{0,3},{2,2}});

    // procedural "GL" style texture: checker + white square
    int size = 256;
    std::vector<unsigned char> px(size * size * 4);
    for (int y = 0; y < size; y++)
        for (int x = 0; x < size; x++) {
            int i = (y * size + x) * 4;
            bool grid = (x % (size / 8) < 2) || (y % (size / 8) < 2);
            bool square = x < size / 4 && y < size / 4;
            if (square) { px[i]=250; px[i+1]=185; px[i+2]=90; }
            else if (grid) { px[i]=90; px[i+1]=190; px[i+2]=245; }
            else { px[i]=46; px[i+1]=58; px[i+2]=74; }
            px[i+3] = 255;
        }
    GLuint tex = texFromImage(px, size, size, true);
    glGenerateMipmap(GL_TEXTURE_2D);

    printf("\n=== Ch17: W wrap cycle, F filter cycle, +/- uv scale, Y flip ===\n");
    const char* wrapNames[3] = { "REPEAT", "MIRRORED", "CLAMP_EDGE" };
    const char* minNames[3] = { "NEAREST", "LINEAR", "MIPMAP_LINEAR" };
    int wrap = 0, filter = 2;
    bool flip = true;
    float uvScale = 1.0f;
    GLenum wraps[3] = { GL_REPEAT, GL_MIRRORED_REPEAT, GL_CLAMP_TO_EDGE };
    GLenum mins[3] = { GL_NEAREST, GL_LINEAR, GL_LINEAR_MIPMAP_LINEAR };

    GLint uScale = uLoc(prog, "u_uvScale");
    GLint uFlip = uLoc(prog, "u_flip");

    while (win::beginFrame(w)) {
        if (win::keyTap('W')) wrap = (wrap + 1) % 3;
        if (win::keyTap('F')) filter = (filter + 1) % 3;
        if (win::keyTap('Y')) flip = !flip;
        if (win::keyDown(VK_ADD)) uvScale += 0.02f;
        if (win::keyDown(VK_SUBTRACT)) uvScale -= 0.02f;
        uvScale = clampf(uvScale, 0.1f, 6.0f);
        printf("\rwrap=%-11s min=%-17s uvScale=%.2f flip=%d  ",
               wrapNames[wrap], minNames[filter], uvScale, flip ? 1 : 0);

        glBindTexture(GL_TEXTURE_2D, tex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wraps[wrap]);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wraps[wrap]);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, mins[filter]);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER,
                        filter == 0 ? GL_NEAREST : GL_LINEAR);

        int vw = w.vpWidth(), vh = w.vpHeight();
        resetState(vw, vh, 0.04f, 0.05f, 0.09f);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, tex);
        glUseProgram(prog);
        glUniform1i(uLoc(prog, "u_tex"), 0);
        glUniform1f(uScale, uvScale);
        glUniform1f(uFlip, flip ? 1.0f : 0.0f);
        quad.draw(GL_TRIANGLES);

        if (win::keyDown(VK_ESCAPE)) break;
        win::endFrame(w);
    }
    quad.dispose();
    glDeleteTextures(1, &tex);
    glDeleteProgram(prog);
    win::close(w);
    return 0;
}
