$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$LoaderVersion = "0.0.3-hook"
$ExampleVersion = "0.0.1"
$MainClass = "dev.howlingwhispers.codaloader.bootstrap.CodaBootstrap"
$AgentClass = "dev.howlingwhispers.codaloader.bootstrap.CodaAgent"

Remove-Item -Recurse -Force out, dist -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force out/classes, out/example-classes, dist | Out-Null

$loaderSources = @(Get-ChildItem -Recurse src/main/java -Filter *.java | ForEach-Object { $_.FullName })
& javac --release 21 -encoding UTF-8 -d out/classes @loaderSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$manifest = @(
    "Manifest-Version: 1.0",
    "Main-Class: $MainClass",
    "Premain-Class: $AgentClass",
    "Can-Redefine-Classes: false",
    "Can-Retransform-Classes: false",
    ""
)
$manifest | Set-Content -Encoding ascii out/manifest.mf

& jar --create --file "dist/CodaLoader-$LoaderVersion.jar" --manifest out/manifest.mf -C out/classes .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$exampleSources = @(Get-ChildItem -Recurse examples/hello-coda/src -Filter *.java | ForEach-Object { $_.FullName })
& javac --release 21 -encoding UTF-8 -cp out/classes -d out/example-classes @exampleSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Copy-Item examples/hello-coda/resources/coda.mod.json out/example-classes/
& jar --create --file "dist/hello-coda-$ExampleVersion.jar" -C out/example-classes .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Copy-Item Launch-CodaLoader.bat dist/Launch-CodaLoader.bat

Write-Host "Built:"
Write-Host "  dist/CodaLoader-$LoaderVersion.jar"
Write-Host "  dist/hello-coda-$ExampleVersion.jar"
Write-Host "  dist/Launch-CodaLoader.bat"
