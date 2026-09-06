@echo off
rem ============================================================
rem  Build script: compiles all chapters into one study_gl.exe
rem  Works with either MSVC (cl, from a VS dev prompt) or MinGW-w64 g++.
rem ============================================================
setlocal
cd /d %~dp0

set CHAPTERS=
for %%f in (src\chapters\ch*.cpp) do call set CHAPTERS=%%CHAPTERS%% %%f

where cl >nul 2>nul
if %errorlevel%==0 goto :msvc

where g++ >nul 2>nul
if %errorlevel%==0 goto :mingw

echo [error] No compiler found in PATH.
echo   - MSVC:   run this from a "x64 Native Tools Command Prompt for VS"
echo   - MinGW:  install MinGW-w64 and add g++.exe to PATH
exit /b 1

:msvc
echo [build] using MSVC (cl)...
if not exist build mkdir build
cl /nologo /EHsc /W3 /O2 /std:c++14 /utf-8 ^
   src\main.cpp src\common\glfuncs.cpp %CHAPTERS% ^
   /Fo"build\\" /Fe"build\study_gl.exe" ^
   opengl32.lib user32.lib gdi32.lib
if %errorlevel%==0 echo [ok] build\study_gl.exe created. Run: build\study_gl.exe
exit /b %errorlevel%

:mingw
echo [build] using MinGW g++...
if not exist build mkdir build
set SRC=src/main.cpp src/common/glfuncs.cpp
for %%f in (src\chapters\ch*.cpp) do call set SRC=%%SRC%% %%f
g++ -O2 -std=c++14 %SRC% -o build\study_gl.exe ^
    -lopengl32 -luser32 -lgdi32 -municode
if %errorlevel%==0 echo [ok] build\study_gl.exe created. Run: build\study_gl.exe
exit /b %errorlevel%
