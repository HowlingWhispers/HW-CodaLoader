@echo off
setlocal EnableExtensions EnableDelayedExpansion
title CodaLoader

cd /d "%~dp0"

set "CODA_JAR="
for %%F in ("CodaLoader-*.jar") do (
    if not defined CODA_JAR set "CODA_JAR=%%~fF"
)

if not defined CODA_JAR (
    echo.
    echo [CodaLoader] I cannot find CodaLoader-*.jar beside this launcher.
    echo [CodaLoader] Put Launch-CodaLoader.bat in the same folder as the CodaLoader JAR.
    echo.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo                     CodaLoader
echo ============================================================
echo [CodaLoader] Looking for Java 21 or newer...

set "JAVA_CMD="
set "JAVA_VERSION="

rem 1. Normal Java installations exposed on PATH.
for /f "delims=" %%J in ('where java 2^>nul') do (
    if not defined JAVA_CMD call :CheckJava "%%J"
)

rem 2. Java bundled by the normal Minecraft Launcher.
if not defined JAVA_CMD call :ScanJavaRoot "%APPDATA%\.minecraft\runtime"

rem 3. Microsoft Store Minecraft Launcher runtime.
if not defined JAVA_CMD call :ScanJavaRoot "%LOCALAPPDATA%\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime"

rem 4. Other common launcher/runtime locations.
if not defined JAVA_CMD call :ScanJavaRoot "%LOCALAPPDATA%\Minecraft Launcher\runtime"
if not defined JAVA_CMD call :ScanJavaRoot "%ProgramFiles%\Minecraft Launcher\runtime"
if not defined JAVA_CMD call :ScanJavaRoot "%ProgramFiles%\Eclipse Adoptium"
if not defined JAVA_CMD call :ScanJavaRoot "%ProgramFiles%\Microsoft"

if not defined JAVA_CMD (
    echo.
    echo [CodaLoader] I could not find Java 21 or newer.
    echo [CodaLoader] If Minecraft is installed, launch Minecraft once with the official launcher
    echo [CodaLoader] so its bundled Java runtime is installed, then try CodaLoader again.
    echo.
    echo [CodaLoader] Later CodaLoader will manage its own required runtime automatically.
    echo.
    pause
    exit /b 1
)

if not exist "run" mkdir "run"
if not exist "run\mods" mkdir "run\mods"

echo [CodaLoader] Java:     !JAVA_CMD!
echo [CodaLoader] Version:  !JAVA_VERSION!
echo [CodaLoader] Launcher: %~nx0
echo [CodaLoader] JAR:      %CODA_JAR%
echo [CodaLoader] Game dir: %CD%\run
echo.

"!JAVA_CMD!" -jar "%CODA_JAR%" "%CD%\run"
set "CODA_EXIT=!ERRORLEVEL!"

echo.
if "!CODA_EXIT!"=="0" (
    echo [CodaLoader] Process finished normally.
) else (
    echo [CodaLoader] Process exited with code !CODA_EXIT!.
)

echo.
pause
exit /b !CODA_EXIT!

:ScanJavaRoot
set "JAVA_ROOT=%~1"
if not exist "!JAVA_ROOT!" exit /b 0

echo [CodaLoader] Checking !JAVA_ROOT!
for /f "delims=" %%J in ('where /r "!JAVA_ROOT!" java.exe 2^>nul') do (
    if not defined JAVA_CMD call :CheckJava "%%J"
)
exit /b 0

:CheckJava
set "JAVA_CANDIDATE=%~1"
set "CANDIDATE_VERSION="
set "CANDIDATE_MAJOR="

for /f "tokens=3" %%V in ('"!JAVA_CANDIDATE!" -version 2^>^&1 ^| findstr /i /c:"version"') do (
    if not defined CANDIDATE_VERSION set "CANDIDATE_VERSION=%%~V"
)

if not defined CANDIDATE_VERSION exit /b 0

for /f "tokens=1,2 delims=." %%A in ("!CANDIDATE_VERSION!") do (
    set "CANDIDATE_MAJOR=%%A"
    if "%%A"=="1" set "CANDIDATE_MAJOR=%%B"
)

for /f "delims=0123456789" %%X in ("!CANDIDATE_MAJOR!") do exit /b 0

if !CANDIDATE_MAJOR! GEQ 21 (
    set "JAVA_CMD=!JAVA_CANDIDATE!"
    set "JAVA_VERSION=!CANDIDATE_VERSION!"
)
exit /b 0
