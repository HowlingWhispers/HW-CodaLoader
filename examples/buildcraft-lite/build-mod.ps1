$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "../..")
Push-Location $root
try {
    if (!(Test-Path dist/CodaLoader.jar)) { throw "Build H.O.W.L. first with scripts/build-dist.ps1" }
    Remove-Item -Recurse -Force out/buildcraft-lite-classes -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force out/buildcraft-lite-classes | Out-Null
    $sources = @(Get-ChildItem -Recurse examples/buildcraft-lite/src -Filter *.java | ForEach-Object { $_.FullName })
    & javac --release 21 -encoding UTF-8 -cp dist/CodaLoader.jar -d out/buildcraft-lite-classes @sources
    if ($LASTEXITCODE -ne 0) { throw "BuildCraft Lite Java compilation failed" }
    Copy-Item -Recurse -Force examples/buildcraft-lite/resources/* out/buildcraft-lite-classes/
    & jar --create --file dist/buildcraft-lite-0.1.0-dev.jar -C out/buildcraft-lite-classes .
    if ($LASTEXITCODE -ne 0) { throw "BuildCraft Lite packaging failed" }
    Write-Host "Built dist/buildcraft-lite-0.1.0-dev.jar (experimental single-player transport)"
} finally { Pop-Location }
