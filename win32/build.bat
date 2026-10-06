@echo off
rem ============================================================
rem  Build script: compiles all chapters into one study_gl.exe
rem  Works with MSVC (cl) or MinGW-w64 (g++).
rem  When neither is in PATH, auto-locates an installed MSVC
rem  toolset (BuildTools / IDE) and sets INCLUDE/LIB manually,
rem  so a plain cmd or double-click build works out of the box.
rem
rem  MSVC path builds in two steps (per-TU /c compile, then link):
rem  single-invocation multi-TU cl has been observed to die
rem  silently between codegen and link on some setups.
rem ============================================================
setlocal EnableExtensions
cd /d %~dp0

if not exist build mkdir build

set CHAPTERS=
for %%f in (src\chapters\ch*.cpp) do call set CHAPTERS=%%CHAPTERS%% %%f

where cl >nul 2>nul
if %errorlevel%==0 goto :msvc

where g++ >nul 2>nul
if %errorlevel%==0 goto :mingw

rem ---- auto-locate MSVC toolset under common install roots ----
set "MSVCROOT="
for %%R in ("C:\Program Files\Microsoft Visual Studio" "C:\Program Files (x86)\Microsoft Visual Studio") do (
    for /d %%Y in ("%%~R\2022\*" "%%~R\2019\*") do (
        for /d %%V in ("%%~Y\VC\Tools\MSVC\*") do (
            if exist "%%~V\bin\Hostx64\x64\cl.exe" set "MSVCROOT=%%~V"
        )
    )
)
if not defined MSVCROOT goto :nocompiler

set "SDKROOT=C:\Program Files (x86)\Windows Kits\10"
set "SDKVER="
for /d %%K in ("%SDKROOT%\Include\*") do set "SDKVER=%%~nxK"
if not defined SDKVER goto :nocompiler

set "INCLUDE=%MSVCROOT%\include;%SDKROOT%\Include\%SDKVER%\ucrt;%SDKROOT%\Include\%SDKVER%\um;%SDKROOT%\Include\%SDKVER%\shared;%SDKROOT%\Include\%SDKVER%\winrt;%SDKROOT%\Include\%SDKVER%\cppwinrt"
set "LIB=%MSVCROOT%\lib\x64;%SDKROOT%\Lib\%SDKVER%\ucrt\x64;%SDKROOT%\Lib\%SDKVER%\um\x64"
set "PATH=%MSVCROOT%\bin\Hostx64\x64;%PATH%"
echo [build] located MSVC toolset: %MSVCROOT%
echo [build] windows sdk: %SDKVER%

:msvc
echo [build] using MSVC (cl), two-step compile+link...
for %%f in (src\main.cpp src\common\glfuncs.cpp src\common\wglwin.cpp src\chapters\ch*.cpp) do (
    cl /nologo /EHsc /W3 /O2 /std:c++14 /utf-8 /c %%f /Fobuild\
    if errorlevel 1 goto :clfail
)
set OBJLIST=build\main.obj build\glfuncs.obj build\wglwin.obj
for %%f in (src\chapters\ch*.cpp) do call set OBJLIST=%%OBJLIST%% build\%%~nf.obj
link /nologo %OBJLIST% /OUT:build\study_gl.exe opengl32.lib user32.lib gdi32.lib
if errorlevel 1 goto :clfail
echo [ok] build\study_gl.exe created. Run: build\study_gl.exe
exit /b 0

:clfail
echo [error] build failed
exit /b 1

:mingw
echo [build] using MinGW g++...
set SRC=src/main.cpp src/common/glfuncs.cpp src/common/wglwin.cpp
for %%f in (src\chapters\ch*.cpp) do call set SRC=%%SRC%% %%f
g++ -O2 -std=c++14 %SRC% -o build\study_gl.exe ^
    -lopengl32 -luser32 -lgdi32
if %errorlevel%==0 echo [ok] build\study_gl.exe created. Run: build\study_gl.exe
exit /b %errorlevel%

:nocompiler
echo [error] No compiler found in PATH and no MSVC install detected.
echo   - MSVC:   install "Visual Studio Build Tools" (C++ workload)
echo   - MinGW:  install MinGW-w64 and add g++.exe to PATH
exit /b 1
