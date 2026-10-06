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

rem 1. Normal Java exposed on PATH.
for /f "delims=" %%J in ('where java 2^>nul') do (
    if not defined JAVA_CMD call :CheckJava "%%J"
)

rem 2. Minecraft / Microsoft Store / Xbox launcher runtimes.
if not defined JAVA_CMD call :ScanJavaRoot "%APPDATA%\.minecraft\runtime"
if not defined JAVA_CMD call :ScanJavaRoot "%LOCALAPPDATA%\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe"
if not defined JAVA_CMD call :ScanJavaRoot "%LOCALAPPDATA%\Minecraft Launcher"
if not defined JAVA_CMD call :ScanJavaRoot "%ProgramFiles%\Minecraft Launcher"
if not defined JAVA_CMD call :ScanJavaRoot "%ProgramFiles(x86)%\Minecraft Launcher"
if not defined JAVA_CMD call :ScanJavaRoot "C:\XboxGames\Minecraft Launcher"

rem 3. Common standalone Java/JDK installs.
if not defined JAVA_CMD call :ScanJavaRoot "%ProgramFiles%\Eclipse Adoptium"
if not defined JAVA_CMD call :ScanJavaRoot "%ProgramFiles%\Java"
if not defined JAVA_CMD call :ScanJavaRoot "%ProgramFiles%\Microsoft"
if not defined JAVA_CMD call :ScanJavaRoot "%USERPROFILE%\.jdks"

if not defined JAVA_CMD (
    echo.
    echo [CodaLoader] I could not find Java 21 or newer.
    echo [CodaLoader] Launch Minecraft once with the official launcher, then try again.
    echo.
    echo [CodaLoader] If this still fails, send me this window and I will add your runtime path.
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
set "CODA_SCAN_ROOT=%~1"
if not defined CODA_SCAN_ROOT exit /b 0
if not exist "!CODA_SCAN_ROOT!" exit /b 0

echo [CodaLoader] Checking !CODA_SCAN_ROOT!

for /f "usebackq delims=" %%J in (`powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$r=$env:CODA_SCAN_ROOT; Get-ChildItem -LiteralPath $r -Filter java.exe -File -Recurse -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }"`) do (
    if not defined JAVA_CMD call :CheckJava "%%J"
)

exit /b 0

:CheckJava
set "JAVA_CANDIDATE=%~1"
set "CANDIDATE_VERSION="
set "CANDIDATE_MAJOR="

if not exist "!JAVA_CANDIDATE!" exit /b 0

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
