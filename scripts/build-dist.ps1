$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$LoaderVersion = "0.0.7-menu-stable"
$MainClass = "dev.howlingwhispers.codaloader.bootstrap.CodaBootstrap"
$AgentClass = "dev.howlingwhispers.codaloader.bootstrap.CodaAgent"
$BundleName = "CodaLoader-v$LoaderVersion-win64.zip"

Remove-Item -Recurse -Force out, dist -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force out/classes, out/example-classes, dist/package/run/mods | Out-Null

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

& jar --create --file "dist/CodaLoader.jar" --manifest out/manifest.mf -C out/classes .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$exampleSources = @(Get-ChildItem -Recurse examples/hello-coda/src -Filter *.java | ForEach-Object { $_.FullName })
& javac --release 21 -encoding UTF-8 -cp out/classes -d out/example-classes @exampleSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Copy-Item examples/hello-coda/resources/coda.mod.json out/example-classes/
& jar --create --file "dist/hello-coda.jar" -C out/example-classes .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Copy-Item Launch-CodaLoader.bat dist/Launch-CodaLoader.bat
Copy-Item dist/CodaLoader.jar dist/package/CodaLoader.jar
Copy-Item dist/Launch-CodaLoader.bat dist/package/Launch-CodaLoader.bat
Copy-Item dist/hello-coda.jar dist/package/run/mods/hello-coda.jar

@"
CodaLoader $LoaderVersion

1. Extract the entire ZIP into its own folder.
2. Double-click Launch-CodaLoader.bat.
3. Keep CodaLoader.jar beside the BAT.
4. Put CodaLoader mods in run\mods.
5. Put custom menu .ogg music in run\music\menu.

CodaLoader checks public GitHub Releases for updates automatically.
"@ | Set-Content -Encoding UTF8 dist/package/README-FIRST.txt

Compress-Archive -Path dist/package/* -DestinationPath "dist/$BundleName" -Force

$JarSha = (Get-FileHash dist/package/CodaLoader.jar -Algorithm SHA256).Hash.ToLowerInvariant()
$BatSha = (Get-FileHash dist/package/Launch-CodaLoader.bat -Algorithm SHA256).Hash.ToLowerInvariant()
$HelloSha = (Get-FileHash dist/package/run/mods/hello-coda.jar -Algorithm SHA256).Hash.ToLowerInvariant()
$BundleSha = (Get-FileHash "dist/$BundleName" -Algorithm SHA256).Hash.ToLowerInvariant()

@"
{
  "schema": 1,
  "version": "$LoaderVersion",
  "bundle": "$BundleName",
  "sha256": "$BundleSha",
  "files": {
    "CodaLoader.jar": "$JarSha",
    "Launch-CodaLoader.bat": "$BatSha",
    "run/mods/hello-coda.jar": "$HelloSha"
  }
}
"@ | Set-Content -Encoding UTF8 dist/update-manifest.json

Write-Host "Built:"
Write-Host "  dist/CodaLoader.jar"
Write-Host "  dist/hello-coda.jar"
Write-Host "  dist/Launch-CodaLoader.bat"
Write-Host "  dist/$BundleName"
Write-Host "  dist/update-manifest.json"
