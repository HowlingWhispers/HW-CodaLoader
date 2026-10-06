@echo off
setlocal EnableExtensions
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

where java >nul 2>nul
if errorlevel 1 (
    echo.
    echo [CodaLoader] Java was not found on PATH.
    echo [CodaLoader] Install Java and make sure the java command is available.
    echo.
    pause
    exit /b 1
)

if not exist "run" mkdir "run"
if not exist "run\mods" mkdir "run\mods"

echo.
echo ============================================================
echo                     CodaLoader
echo ============================================================
echo [CodaLoader] Launcher: %~nx0
echo [CodaLoader] JAR:      %CODA_JAR%
echo [CodaLoader] Game dir: %CD%\run
echo.

java -jar "%CODA_JAR%" "%CD%\run"
set "CODA_EXIT=%ERRORLEVEL%"

echo.
if "%CODA_EXIT%"=="0" (
    echo [CodaLoader] Process finished normally.
) else (
    echo [CodaLoader] Process exited with code %CODA_EXIT%.
)

echo.
pause
exit /b %CODA_EXIT%
