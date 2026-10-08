@echo off
setlocal EnableExtensions
title H.O.W.L.

cd /d "%~dp0"

if not exist "CodaLoader.jar" (
    echo.
    echo [CodaLoader] CodaLoader.jar was not found beside this BAT file.
    echo Extract the complete CodaLoader ZIP before launching.
    echo.
    pause
    exit /b 1
)

java -jar "CodaLoader.jar"
set "CODA_EXIT=%ERRORLEVEL%"

if "%CODA_EXIT%"=="42" (
    echo [CodaLoader] Handing control to the updater...
    exit /b 0
)

if not "%CODA_EXIT%"=="0" (
    echo.
    echo [CodaLoader] CodaLoader exited with code %CODA_EXIT%.
    echo.
)

if not defined CODA_NO_PAUSE pause
exit /b %CODA_EXIT%
