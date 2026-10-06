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
echo [CodaLoader] Looking for Java 21 or newer...

set "JAVA_CMD="
set "CODA_JAVA_RESULT=%TEMP%\codaloader-java-%RANDOM%%RANDOM%.txt"
del /q "%CODA_JAVA_RESULT%" >nul 2>nul

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='SilentlyContinue'; function TryJava([string]$j) { if ([string]::IsNullOrWhiteSpace($j) -or -not (Test-Path -LiteralPath $j -PathType Leaf)) { return $false }; try { $line = ((& $j -version 2^>^&1 | Select-Object -First 1) -join ' '); if ($line -match 'version\s+\"?([0-9]+)') { return ([int]$Matches[1] -ge 21) } } catch {}; return $false }; $direct=@(); if ($env:JAVA_HOME) { $direct += (Join-Path $env:JAVA_HOME 'bin\java.exe') }; $cmd=Get-Command java.exe -ErrorAction SilentlyContinue; if ($cmd) { $direct += $cmd.Source }; foreach ($j in ($direct | Select-Object -Unique)) { if (TryJava $j) { [IO.File]::WriteAllText($env:CODA_JAVA_RESULT,$j); exit 0 } }; $roots=@((Join-Path $env:ProgramFiles 'Java'),(Join-Path $env:ProgramFiles 'Eclipse Adoptium'),(Join-Path $env:APPDATA '.minecraft\runtime'),(Join-Path $env:LOCALAPPDATA 'Minecraft Launcher'),(Join-Path $env:LOCALAPPDATA 'Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe'),'C:\XboxGames\Minecraft Launcher'); foreach ($r in $roots) { if ($r -and (Test-Path -LiteralPath $r)) { Write-Host ('[CodaLoader] Checking ' + $r); foreach ($f in (Get-ChildItem -LiteralPath $r -Filter java.exe -File -Recurse -ErrorAction SilentlyContinue)) { if (TryJava $f.FullName) { [IO.File]::WriteAllText($env:CODA_JAVA_RESULT,$f.FullName); exit 0 } } } }; exit 1"

if exist "%CODA_JAVA_RESULT%" (
    set /p "JAVA_CMD="<"%CODA_JAVA_RESULT%"
)
del /q "%CODA_JAVA_RESULT%" >nul 2>nul

if not defined JAVA_CMD (
    echo.
    echo [CodaLoader] I could not find Java 21 or newer.
    echo [CodaLoader] You said Java 26 is installed, so this means its install location
    echo [CodaLoader] is somewhere I have not detected yet.
    echo.
    echo [CodaLoader] Run this in Command Prompt to locate it:
    echo             where /r "C:\Program Files\Java" java.exe
    echo.
    call :PauseIfNeeded
    exit /b 1
)

if not exist "run" mkdir "run"
if not exist "run\mods" mkdir "run\mods"

echo [CodaLoader] Java:     !JAVA_CMD!
"!JAVA_CMD!" -version
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

:PauseIfNeeded
if not defined CODA_NO_PAUSE pause
exit /b 0
