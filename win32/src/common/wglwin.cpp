// wglwin.cpp - Win32 + WGL implementation
#include "wglwin.h"
#include "glfuncs.h"
#include <stdio.h>
#include <chrono>

namespace win {

static const char* WND_CLASS = "StudyOpenGLWnd";

struct WndData { Window* w; };

static LRESULT CALLBACK wndProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    WndData* d = (WndData*)GetWindowLongPtrA(hwnd, GWLP_USERDATA);
    switch (msg) {
    case WM_SIZE:
        if (d && d->w) {
            d->w->width  = LOWORD(lp) ? LOWORD(lp) : 1;
            d->w->height = HIWORD(lp) ? HIWORD(lp) : 1;
        }
        return 0;
    case WM_CLOSE:
    case WM_DESTROY:
        if (d && d->w) d->w->shouldClose = true;
        if (msg == WM_DESTROY) PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcA(hwnd, msg, wp, lp);
}

static bool registerClassOnce() {
    static bool done = false;
    if (done) return true;
    WNDCLASSA wc = {};
    wc.style         = CS_OWNDC;
    wc.lpfnWndProc   = wndProc;
    wc.hInstance     = GetModuleHandleA(nullptr);
    wc.hCursor       = LoadCursorA(nullptr, MAKEINTRESOURCEA(32512)); // arrow
    wc.lpszClassName = WND_CLASS;
    if (!RegisterClassA(&wc)) { printf("[win] RegisterClass failed\n"); return false; }
    done = true;
    return true;
}

static bool setPixelFormatLegacy(HDC hdc) {
    PIXELFORMATDESCRIPTOR pfd = {};
    pfd.nSize    = sizeof(pfd);
    pfd.nVersion = 1;
    pfd.dwFlags  = PFD_DRAW_TO_WINDOW | PFD_SUPPORT_OPENGL | PFD_DOUBLEBUFFER;
    pfd.iPixelType = PFD_TYPE_RGBA;
    pfd.cColorBits = 32;
    pfd.cDepthBits = 24;
    pfd.cStencilBits = 8;
    pfd.iLayerType = PFD_MAIN_PLANE;
    int pf = ChoosePixelFormat(hdc, &pfd);
    if (!pf) { printf("[win] ChoosePixelFormat failed\n"); return false; }
    if (!SetPixelFormat(hdc, pf, &pfd)) { printf("[win] SetPixelFormat failed\n"); return false; }
    return true;
}

// --- WGL_ARB_pixel_format (for multisample pixel formats) ---
typedef BOOL (WINAPI *PFN_wglChoosePixelFormatARB)(HDC, const int*, const FLOAT*, UINT, int*, UINT*);
static PFN_wglChoosePixelFormatARB pChoosePfARB = nullptr;

static void loadWglArb(HDC tmpHdc, HGLRC tmpRc) {
    wglMakeCurrent(tmpHdc, tmpRc);
    pChoosePfARB = (PFN_wglChoosePixelFormatARB)wglGetProcAddress("wglChoosePixelFormatARB");
    wglMakeCurrent(nullptr, nullptr);
}

static int choosePixelFormatARB(HDC hdc, int samples) {
    if (!pChoosePfARB) return 0;
    const int attribs[] = {
        0x2001 /*WGL_DRAW_TO_WINDOW_ARB*/, GL_TRUE,
        0x2010 /*WGL_SUPPORT_OPENGL_ARB*/, GL_TRUE,
        0x2011 /*WGL_DOUBLE_BUFFER_ARB*/,  GL_TRUE,
        0x2014 /*WGL_COLOR_BITS_ARB*/,     32,
        0x2022 /*WGL_DEPTH_BITS_ARB*/,     24,
        0x2023 /*WGL_STENCIL_BITS_ARB*/,   8,
        0x2041 /*WGL_SAMPLE_BUFFERS_ARB*/, samples > 0 ? GL_TRUE : GL_FALSE,
        0x2042 /*WGL_SAMPLES_ARB*/,        samples,
        0, 0
    };
    int pf = 0; UINT num = 0;
    if (pChoosePfARB(hdc, attribs, nullptr, 1, &pf, &num) && num > 0) return pf;
    return 0;
}

bool open(Window& w, const char* title, int width, int height, int msaaSamples) {
    if (!registerClassOnce()) return false;

    HINSTANCE inst = GetModuleHandleA(nullptr);
    DWORD style = WS_OVERLAPPEDWINDOW | WS_VISIBLE;

    // ---- phase 1: tiny dummy window + legacy context to load WGL_ARB fns ----
    HWND dummy = CreateWindowExA(0, WND_CLASS, "dummy", WS_OVERLAPPEDWINDOW,
                                 0, 0, 8, 8, nullptr, nullptr, inst, nullptr);
    HDC dummyDc = GetDC(dummy);
    if (!setPixelFormatLegacy(dummyDc)) return false;
    HGLRC dummyRc = wglCreateContext(dummyDc);
    loadWglArb(dummyDc, dummyRc);           // resolves wglChoosePixelFormatARB

    // ---- phase 2: real window ----
    RECT rc = {0, 0, width, height};
    AdjustWindowRect(&rc, style, FALSE);
    w.hwnd = CreateWindowExA(0, WND_CLASS, title, style,
                             CW_USEDEFAULT, CW_USEDEFAULT,
                             rc.right - rc.left, rc.bottom - rc.top,
                             nullptr, nullptr, inst, nullptr);
    w.width = width; w.height = height;
    WndData* d = new WndData{ &w };
    SetWindowLongPtrA(w.hwnd, GWLP_USERDATA, (LONG_PTR)d);
    ShowWindow(w.hwnd, SW_SHOW);

    w.hdc = GetDC(w.hwnd);
    // enable ANSI/VT escape sequences in the attached console (Win10+)
    if (AttachConsole(ATTACH_PARENT_PROCESS) || GetConsoleWindow()) {
        HANDLE cout = GetStdHandle(STD_OUTPUT_HANDLE);
        DWORD mode = 0;
        if (GetConsoleMode(cout, &mode))
            SetConsoleMode(cout, mode | ENABLE_VIRTUAL_TERMINAL_PROCESSING);
    }
    int pf = msaaSamples > 0 ? choosePixelFormatARB(w.hdc, msaaSamples) : 0;
    if (pf > 0) {
        PIXELFORMATDESCRIPTOR pfd = {};
        DescribePixelFormat(w.hdc, pf, sizeof(pfd), &pfd);
        if (!SetPixelFormat(w.hdc, pf, &pfd)) { printf("[win] ARB SetPixelFormat failed\n"); return false; }
        printf("[win] using MSAA pixel format (%d samples)\n", msaaSamples);
    } else {
        if (msaaSamples > 0) printf("[win] MSAA pixel format unavailable, using 0 samples\n");
        if (!setPixelFormatLegacy(w.hdc)) return false;
    }
    w.hglrc = wglCreateContext(w.hdc);
    if (!w.hglrc) { printf("[win] wglCreateContext failed\n"); return false; }
    if (!wglMakeCurrent(w.hdc, w.hglrc)) { printf("[win] wglMakeCurrent failed\n"); return false; }

    // cleanup dummy
    wglMakeCurrent(nullptr, nullptr);
    wglDeleteContext(dummyRc);
    ReleaseDC(dummy, dummyDc);
    DestroyWindow(dummy);

    static bool glLoaded = false;
    if (!glLoaded) {
        glLoaded = loadGLFunctions();
        if (!glLoaded) {
            printf("[gl] NOTE: on RDP/software displays GL may be limited to 1.1.\n");
            return false;
        }
        printf("[gl] renderer: %s\n", (const char*)glGetString(GL_RENDERER));
    }
    return true;
}

bool beginFrame(Window& w) {
    MSG msg;
    while (PeekMessageA(&msg, nullptr, 0, 0, PM_REMOVE)) {
        if (msg.message == WM_QUIT) { w.shouldClose = true; }
        TranslateMessage(&msg);
        DispatchMessageA(&msg);
    }
    static auto t0 = std::chrono::steady_clock::now();
    static double last = 0.0;
    static float fps = 0.0f;
    auto now = std::chrono::steady_clock::now();
    w.time = std::chrono::duration<double>(now - t0).count();
    if (w.time > last) {
        float instant = (float)(1.0 / (w.time - last));
        fps = fps == 0.0f ? instant : fps * 0.9f + instant * 0.1f;
        w.fps = fps;
    }
    last = w.time;
    return !w.shouldClose;
}

void endFrame(Window& w) {
    if (w.hdc) SwapBuffers(w.hdc);
}

void close(Window& w) {
    if (w.hglrc) { wglMakeCurrent(nullptr, nullptr); wglDeleteContext(w.hglrc); w.hglrc = nullptr; }
    if (w.hwnd && w.hdc) { ReleaseDC(w.hwnd, w.hdc); w.hdc = nullptr; }
    if (w.hwnd) { DestroyWindow(w.hwnd); w.hwnd = nullptr; }
}

bool keyDown(int vk) { return (GetAsyncKeyState(vk) & 0x8000) != 0; }

bool keyTap(int vk) {
    static bool prev[256] = {};
    bool down = keyDown(vk);
    bool tap = down && !prev[vk];
    prev[vk] = down;
    return tap;
}

} // namespace win
