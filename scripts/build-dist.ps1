$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$LoaderVersion = "0.0.20"
$EssentialsVersion = "0.2.0"
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

# Require verification for both the published dependency and local development overrides.
if ($env:HW_ESSENTIALS_LOCAL_JAR) {
    if (!(Test-Path $env:HW_ESSENTIALS_LOCAL_JAR) -or !$env:HW_ESSENTIALS_LOCAL_SHA256 -or !(Test-Path $env:HW_ESSENTIALS_LOCAL_SHA256)) {
        throw "Local HW Essentials requires both a JAR and checksum file."
    }
    Copy-Item $env:HW_ESSENTIALS_LOCAL_JAR dist/hw-essentials.jar
    Copy-Item $env:HW_ESSENTIALS_LOCAL_SHA256 dist/hw-essentials.jar.sha256
} else {
    Write-Host "Fetching HW Essentials $EssentialsVersion from HW-Mods release..."
    Invoke-WebRequest -Uri $EssentialsJarUrl -OutFile dist/hw-essentials.jar
    Invoke-WebRequest -Uri $EssentialsSha256Url -OutFile dist/hw-essentials.jar.sha256
}
$ExpectedSha = ((Get-Content -Raw dist/hw-essentials.jar.sha256).Trim() -split '\s+')[0].ToLowerInvariant()
$ActualSha = (Get-FileHash dist/hw-essentials.jar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($ExpectedSha -notmatch '^[0-9a-f]{64}$' -or $ExpectedSha -ne $ActualSha) {
    throw "HW Essentials checksum is invalid or does not match the downloaded JAR."
}
Write-Host "HW Essentials SHA-256 verified: $ActualSha"

# -notmatch on an array returns nonmatching entries, rather than testing the whole list.
$jarContents = @(& jar tf dist/hw-essentials.jar)
if ($LASTEXITCODE -ne 0) { throw "HW Essentials is not a readable JAR." }
if ($jarContents -notcontains "coda.mod.json") { throw "Invalid mod JAR: missing coda.mod.json" }
if ($jarContents -notcontains "dev/howlingwhispers/essentials/HwEssentialsMod.class") { throw "Invalid mod JAR: missing entrypoint" }

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