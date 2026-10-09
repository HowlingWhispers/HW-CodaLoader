@echo off
echo Close Minecraft and CodaLauncher before applying this Nightly test patch.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Apply-BuildCraft-Fix.ps1"
set "BUILDCRAFT_FIX_RESULT=%ERRORLEVEL%"
pause
exit /b %BUILDCRAFT_FIX_RESULT%
