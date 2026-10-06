@echo off
setlocal EnableExtensions
title CodaLoader

cd /d "%~dp0"

set "CODA_JAR="
if exist "CodaLoader-0.0.3-hook.jar" set "CODA_JAR=%CD%\CodaLoader-0.0.3-hook.jar"
if not defined CODA_JAR (
    for %%F in ("CodaLoader-*.jar") do (
        if not defined CODA_JAR set "CODA_JAR=%%~fF"
    )
)

if not defined CODA_JAR (
    echo.
    echo [CodaLoader] CodaLoader JAR not found beside this BAT file.
    echo.
    pause
    exit /b 1
)

java -jar "%CODA_JAR%"
set "CODA_EXIT=%ERRORLEVEL%"

if not "%CODA_EXIT%"=="0" (
    echo.
    echo [CodaLoader] CodaLoader exited with code %CODA_EXIT%.
    echo.
)

if not defined CODA_NO_PAUSE pause
exit /b %CODA_EXIT%
