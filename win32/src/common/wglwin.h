// wglwin.h - Win32 window + raw WGL context (no third-party libraries).
// Mirrors what EglCore/GLThread/RenderSurface do on the Android side.
#pragma once
#include <windows.h>
// VK_OBRACKET/VK_RBRACKET do not exist in the Windows SDK - map to the
// US-layout OEM keys '[' (0xDB) and ']' (0xDD).
#ifndef VK_OBRACKET
#define VK_OBRACKET VK_OEM_4
#endif
#ifndef VK_RBRACKET
#define VK_RBRACKET VK_OEM_6
#endif


namespace win {

struct Window {
    HWND  hwnd  = nullptr;
    HDC   hdc   = nullptr;
    HGLRC hglrc = nullptr;
    int   width = 1000;
    int   height = 700;
    bool  shouldClose = false;
    double time = 0.0;          // seconds since open (updated by beginFrame)
    float fps = 0.0f;           // smoothed FPS (updated by beginFrame)

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
