// wglwin.h - Win32 window + raw WGL context (no third-party libraries).
// Mirrors what EglCore/GLThread/RenderSurface do on the Android side.
#pragma once
#include <windows.h>

namespace win {

struct Window {
    HWND  hwnd  = nullptr;
    HDC   hdc   = nullptr;
    HGLRC hglrc = nullptr;
    int   width = 1000;
    int   height = 700;
    bool  shouldClose = false;
    double time = 0.0;          // seconds since open (updated by beginFrame)

    int vpWidth()  const { return width;  }
    int vpHeight() const { return height; }
};

// Creates a window + WGL context. When msaaSamples > 0, tries to pick a
// multisample pixel format via WGL_ARB_pixel_format (falls back to 0 samples).
bool open(Window& w, const char* title, int width, int height, int msaaSamples = 0);

// Pumps messages. Returns false when the window should close.
bool beginFrame(Window& w);

// SwapBuffers: presents the back buffer.
void endFrame(Window& w);

void close(Window& w);              // destroy window + release GL

bool keyDown(int vk);               // held right now
bool keyTap(int vk);                // pressed since last frame (edge)

} // namespace win
