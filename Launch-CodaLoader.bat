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
    call :PauseIfNeeded
    exit /b 1
)

echo.
echo ============================================================
echo                     CodaLoader
echo ============================================================
echo [CodaLoader] Looking for Java...

set "JAVA_CMD="

rem Prefer JAVA_HOME when an installer configured it.
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
    )
)

rem Then try the normal Windows PATH.
if not defined JAVA_CMD (
    for /f "delims=" %%J in ('where java.exe 2^>nul') do (
        if not defined JAVA_CMD set "JAVA_CMD=%%~fJ"
    )
)

rem Then search common Java and Minecraft runtime locations.
if not defined JAVA_CMD call :FindJava "%ProgramFiles%\Java"
if not defined JAVA_CMD call :FindJava "%ProgramFiles%\Eclipse Adoptium"
if not defined JAVA_CMD call :FindJava "%APPDATA%\.minecraft\runtime"
if not defined JAVA_CMD call :FindJava "%LOCALAPPDATA%\Minecraft Launcher"
if not defined JAVA_CMD call :FindJava "%LOCALAPPDATA%\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe"
if not defined JAVA_CMD call :FindJava "C:\XboxGames\Minecraft Launcher"

if not defined JAVA_CMD (
    echo.
    echo [CodaLoader] I could not find java.exe.
    echo [CodaLoader] Java 26 is supported.
    echo [CodaLoader] If you just installed it, closing and reopening this launcher may help.
    echo.
    echo [CodaLoader] Common Java 26 location:
    echo             C:\Program Files\Java\jdk-26\bin\java.exe
    echo.
    call :PauseIfNeeded
    exit /b 1
)

if not exist "run" mkdir "run"
if not exist "run\mods" mkdir "run\mods"

echo [CodaLoader] Java:     !JAVA_CMD!
"!JAVA_CMD!" -version
if errorlevel 1 (
    echo.
    echo [CodaLoader] Java was found, but it could not be started.
    call :PauseIfNeeded
    exit /b 1
)

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
call :PauseIfNeeded
exit /b !CODA_EXIT!

:FindJava
set "CODA_SEARCH_ROOT=%~1"
if not defined CODA_SEARCH_ROOT exit /b 0
if not exist "!CODA_SEARCH_ROOT!" exit /b 0

echo [CodaLoader] Checking !CODA_SEARCH_ROOT!
for /r "!CODA_SEARCH_ROOT!" %%J in (java.exe) do (
    if not defined JAVA_CMD set "JAVA_CMD=%%~fJ"
)
exit /b 0

:PauseIfNeeded
if not defined CODA_NO_PAUSE pause
exit /b 0
