param([Parameter(Mandatory=$true)][string]$HowlApiJar)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    if (-not (Test-Path $HowlApiJar)) { throw "Missing H.O.W.L. SDK API JAR: $HowlApiJar" }
    New-Item -ItemType Directory -Force 'out/classes' | Out-Null
    Get-ChildItem 'out/classes' -Recurse -File | Remove-Item -Force
    $sources = @(Get-ChildItem src -Filter *.java -Recurse | ForEach-Object FullName)
    & javac --release 21 -encoding UTF-8 -cp $HowlApiJar -d 'out/classes' @sources
    if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
    Copy-Item 'resources/coda.mod.json' 'out/classes/coda.mod.json'
    & jar --create --file 'out/coda-wolf-0.1.0-dev.jar' -C 'out/classes' .
    if ($LASTEXITCODE -ne 0) { throw 'jar failed' }
    Write-Host 'Built out/coda-wolf-0.1.0-dev.jar'
} finally { Pop-Location }
