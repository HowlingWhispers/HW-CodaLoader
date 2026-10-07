$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$LoaderVersion = "0.0.19"
$EssentialsVersion = "0.1.0"
$MainClass = "dev.howlingwhispers.codaloader.bootstrap.CodaBootstrap"
$AgentClass = "dev.howlingwhispers.codaloader.bootstrap.CodaAgent"
$BundleName = "CodaLoader-v$LoaderVersion-win64.zip"

# HW Essentials is now a separate repo (HW-Mods). Fetch from GitHub Releases.
$EssentialsJarUrl = "https://github.com/HowlingWhispers/HW-Mods/releases/download/v$EssentialsVersion/hw-essentials-$EssentialsVersion.jar"
$EssentialsSha256Url = "$EssentialsJarUrl.sha256"

Remove-Item -Recurse -Force out, dist -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force out/classes, out/example-classes, dist/package/run/mods | Out-Null

$loaderSources = @(Get-ChildItem -Recurse src/main/java -Filter *.java | ForEach-Object { $_.FullName })
& javac --release 21 -encoding UTF-8 -d out/classes @loaderSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if (Test-Path src/main/resources) {
    Copy-Item -Recurse -Force src/main/resources/* out/classes/
}

# Fetch HW Essentials mod JAR from HW-Mods release
Write-Host "Fetching HW Essentials $EssentialsVersion from HW-Mods release..."
New-Item -ItemType Directory -Force dist | Out-Null
Invoke-WebRequest -Uri $EssentialsJarUrl -OutFile "dist/hw-essentials.jar" -UseBasicParsing
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# Verify SHA-256 if checksum file is available
try {
    $Sha256Response = Invoke-WebRequest -Uri $EssentialsSha256Url -UseBasicParsing -ErrorAction Stop
    $ExpectedSha = ($Sha256Response.Content -split '\s+')[0]
    $ActualSha = (Get-FileHash dist/hw-essentials.jar -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($ExpectedSha -ne $ActualSha) {
        Write-Error "HW Essentials SHA-256 mismatch!"
        Write-Error "Expected: $ExpectedSha"
        Write-Error "Actual:   $ActualSha"
        exit 1
    }
    Write-Host "HW Essentials SHA-256 verified: $ActualSha"
} catch {
    Write-Host "No SHA-256 checksum file found; skipping verification"
}

# Verify mod JAR structure
$jarContents = & jar tf dist/hw-essentials.jar
if ($jarContents -notmatch "coda\.mod\.json") { Write-Error "Invalid mod JAR: missing coda.mod.json"; exit 1 }
if ($jarContents -notmatch "HwEssentialsMod\.class") { Write-Error "Invalid mod JAR: missing HwEssentialsMod.class"; exit 1 }

# Embed mod in CodaLoader for automatic profile installation
New-Item -ItemType Directory -Force out/classes/codaloader/mods | Out-Null
Copy-Item dist/hw-essentials.jar out/classes/codaloader/mods/

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
Copy-Item dist/hw-essentials.jar dist/package/run/mods/hw-essentials.jar

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
$EssentialsSha = (Get-FileHash dist/package/run/mods/hw-essentials.jar -Algorithm SHA256).Hash.ToLowerInvariant()
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
    "run/mods/hello-coda.jar": "$HelloSha",
    "run/mods/hw-essentials.jar": "$EssentialsSha"
  }
}
"@ | Set-Content -Encoding UTF8 dist/update-manifest.json

Write-Host "Built:"
Write-Host "  dist/CodaLoader.jar"
Write-Host "  dist/hello-coda.jar"
Write-Host "  dist/Launch-CodaLoader.bat"
Write-Host "  dist/hw-essentials.jar (from HW-Mods v$EssentialsVersion)"
Write-Host "  dist/$BundleName"
Write-Host "  dist/update-manifest.json"