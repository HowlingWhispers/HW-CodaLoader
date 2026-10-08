$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
Remove-Item -Recurse -Force out/classes -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force out/classes | Out-Null
$sources = @(Get-ChildItem -Recurse src -Filter *.java | ForEach-Object { $_.FullName })
& javac --release 21 -encoding UTF8 -cp lib/howl-api.jar -d out/classes @sources
if ($LASTEXITCODE -ne 0) { throw "Mod compilation failed" }
Copy-Item -Recurse -Force resources/* out/classes/
& jar --create --file out/hello-coda.jar -C out/classes .
if ($LASTEXITCODE -ne 0) { throw "Mod packaging failed" }
Write-Host 'Built out/hello-coda.jar. Copy it into your HW Minecraft mods folder.'
