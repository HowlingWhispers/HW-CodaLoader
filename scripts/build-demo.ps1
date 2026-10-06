$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

Remove-Item -Recurse -Force out, run -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force out/classes, out/example-classes, run/mods | Out-Null

$loaderSources = Get-ChildItem -Recurse src/main/java -Filter *.java | ForEach-Object FullName
javac --release 21 -encoding UTF-8 -d out/classes $loaderSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$exampleSources = Get-ChildItem -Recurse examples/hello-coda/src -Filter *.java | ForEach-Object FullName
javac --release 21 -encoding UTF-8 -cp out/classes -d out/example-classes $exampleSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Copy-Item examples/hello-coda/resources/coda.mod.json out/example-classes/
jar --create --file run/mods/hello-coda.jar -C out/example-classes .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

java -cp out/classes dev.howlingwhispers.codaloader.bootstrap.CodaBootstrap run
