// hud.h - in-place console HUD (multi-line live info, needs VT mode enabled
// by wglwin::open on Windows 10+).
#pragma once
#include <stdio.h>

namespace hud {

// Rewrites the bottom `lines` console lines with fresh text each frame.
// Usage: build the block with one printf-style call, use '\n' between rows.
inline void frame(int lines, const char* fmt, ...) {
    // move cursor up to the start of our block, then rewrite
    if (lines > 0) printf("\r");
    for (int i = 1; i < lines; i++) printf("\x1b[1A\x1b[2K");
    printf("\x1b[2K");
    va_list args;
    va_start(args, fmt);
    vprintf(fmt, args);
    va_end(args);
}

inline void separator() {
    printf("--------------------------------------------------\n");
}

} // namespace hud
