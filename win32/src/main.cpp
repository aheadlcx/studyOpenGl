// main.cpp - entry point: pick a chapter (menu or command line)
#include "common/common.h"
#include <stdio.h>
#include <string.h>

int run_ch01();  int run_ch02();  int run_ch03();  int run_ch04();
int run_ch05();  int run_ch06();  int run_ch07();  int run_ch08();
int run_ch09();  int run_ch10();  int run_ch11();  int run_ch12();
int run_ch13();  int run_ch14();  int run_ch15();  int run_ch16();
int run_ch17();  int run_ch18();  int run_ch19();  int run_ch20();
int run_ch21();  int run_ch22();  int run_ch23();  int run_ch24();
int run_ch25();  int run_ch26();  int run_ch27();  int run_ch28();
int run_ch29();  int run_ch30();  int run_ch31();  int run_ch32();

struct Chapter { int num; const char* title; int (*run)(); };

static Chapter chapters[] = {
    { 1,  "Pipeline overview (zero-basics)",      run_ch01 },
    { 2,  "Station 1: vertex data VBO/VAO",       run_ch02 },
    { 3,  "Station 2: vertex shader MVP",         run_ch03 },
    { 4,  "Station 3: rasterization magnifier",   run_ch04 },
    { 5,  "Station 4: fragment shader inputs",    run_ch05 },
    { 6,  "Station 5: tests, blend & swap",       run_ch06 },
    { 7,  "Hello triangle (full pipeline)",       run_ch07 },
    { 8,  "Varying interpolation smooth/flat",    run_ch08 },
    { 9,  "Primitive types & restart index",      run_ch09 },
    { 10, "Viewport & scissor",                   run_ch10 },
    { 11, "Face culling",                         run_ch11 },
    { 12, "Transform matrix T*R*S",               run_ch12 },
    { 13, "Camera & perspective projection",      run_ch13 },
    { 14, "Depth test & polygon offset",          run_ch14 },
    { 15, "Blending",                             run_ch15 },
    { 16, "Stencil test outline",                 run_ch16 },
    { 17, "2D texture & sampling",                run_ch17 },
    { 18, "Mipmap & LOD",                         run_ch18 },
    { 19, "Texture2D array",                      run_ch19 },
    { 20, "Cubemap skybox & reflection",          run_ch20 },
    { 21, "Phong lighting",                       run_ch21 },
    { 22, "Material & light maps",                run_ch22 },
    { 23, "Normal mapping & TBN",                 run_ch23 },
    { 24, "Fog",                                  run_ch24 },
    { 25, "Instanced rendering",                  run_ch25 },
    { 26, "Uniform buffer object",                run_ch26 },
    { 27, "Buffer mapping (glMapBufferRange)",    run_ch27 },
    { 28, "Transform feedback particles",         run_ch28 },
    { 29, "FBO render-to-texture",                run_ch29 },
    { 30, "Post-processing convolution",          run_ch30 },
    { 31, "MSAA",                                 run_ch31 },
    { 32, "MRT (multiple render targets)",        run_ch32 },
};
static const int CHAPTER_COUNT = sizeof(chapters) / sizeof(chapters[0]);

int main(int argc, char** argv) {
    if (argc >= 2) {
        int n = atoi(argv[1]);
        for (int i = 0; i < CHAPTER_COUNT; i++)
            if (chapters[i].num == n) return chapters[i].run();
        printf("no chapter %d\n", n);
        return 1;
    }
    while (true) {
        printf("\n===== OpenGL ES 3.0 course (Win32 port) =====\n");
        for (int i = 0; i < CHAPTER_COUNT; i++)
            printf("  %2d. %s\n", chapters[i].num, chapters[i].title);
        printf("  0. exit\nchoose chapter: ");
        int n = 0;
        if (scanf("%d", &n) != 1 || n == 0) break;
        for (int i = 0; i < CHAPTER_COUNT; i++)
            if (chapters[i].num == n) { chapters[i].run(); break; }
    }
    return 0;
}
