// glfuncs.cpp - runtime loading of GL 2.0+/3.0+ functions
#include "glfuncs.h"
#include <stdio.h>

#define GL_DEF_FN(name, ret, args) PFN_##name name = nullptr;
GL_FUNCTIONS(GL_DEF_FN)

bool loadGLFunctions() {
    struct Entry { const char* name; void** target; };
    static const Entry table[] = {
#define GL_ENT(name, ret, args) { #name, (void**)&name },
        GL_FUNCTIONS(GL_ENT)
#undef GL_ENT
    };
    const int count = sizeof(table) / sizeof(table[0]);
    bool ok = true;
    for (int i = 0; i < count; i++) {
        void* p = (void*)wglGetProcAddress(table[i].name);
        *table[i].target = p;
        if (!p) {
            printf("[gl] missing function: %s (driver too old?)\n", table[i].name);
            ok = false;
        }
    }
    if (ok) printf("[gl] loaded %d GL functions\n", count);
    return ok;
}

void glCheckError(const char* where) {
    GLenum e;
    while ((e = glGetError()) != GL_NO_ERROR) {
        printf("[gl error] 0x%04X at %s\n", (unsigned)e, where);
    }
}
