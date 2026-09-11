@echo off
rem ============================================================
rem  一键部署：编译 APK 并按约定拷贝到共享目录
rem  命名规则: StudyOpenGL-OpenGL_ES3.0_Lab-<类型>_<月日时分秒>.apk
rem  用法: deploy_apk.bat [目标目录]
rem    默认目标: \\192.168.0.104\work\demo\apk
rem ============================================================
setlocal
cd /d %~dp0

set TARGET=%~1
if "%TARGET%"=="" set TARGET=\\192.168.0.104\work\demo\apk

set TS=%DATE:~5,2%%DATE:~8,2%_%TIME:~0,2%%TIME:~3,2%%TIME:~6,2%
set TS=%TS: =0%
set NAME=StudyOpenGL-OpenGL_ES3.0_Lab-debug_%TS%.apk
set SRC=app\build\outputs\apk\debug\app-debug.apk

echo [1/3] compiling APK...
call gradlew.bat assembleDebug --offline
if errorlevel 1 ( echo [error] build failed & exit /b 1 )

echo [2/3] copying to %TARGET%\%NAME% ...
if not exist "%TARGET%\" (
    echo [error] target dir not reachable: %TARGET%
    exit /b 1
)
copy /y "%SRC%" "%TARGET%\%NAME%" >nul
if errorlevel 1 ( echo [error] copy failed & exit /b 1 )

echo [3/3] done.
echo     %TARGET%\%NAME%
endlocal
