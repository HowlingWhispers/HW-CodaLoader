$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$versionSource = Get-Content -Raw "src/main/java/dev/howlingwhispers/codaloader/core/CodaTarget.java"
$versionMatch = [regex]::Match($versionSource, 'LOADER_VERSION\s*=\s*"(\d+\.\d+\.\d+)"')
if (!$versionMatch.Success) { throw "Invalid version in CodaTarget.LOADER_VERSION" }
$LoaderVersion = $versionMatch.Groups[1].Value
$EssentialsVersion = "0.2.0"
$MainClass = "dev.howlingwhispers.codaloader.bootstrap.CodaBootstrap"
$AgentClass = "dev.howlingwhispers.codaloader.bootstrap.CodaAgent"
$BundleName = "CodaLoader-v$LoaderVersion-win64.zip"

# HW Essentials is now a separate repo (HW-Mods). Fetch from GitHub Releases.
$EssentialsJarUrl = "https://github.com/HowlingWhispers/HW-Mods/releases/download/v$EssentialsVersion/hw-essentials-$EssentialsVersion.jar"
$EssentialsSha256Url = "$EssentialsJarUrl.sha256"

Remove-Item -Recurse -Force out, dist -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force out/classes, out/example-classes, dist/package | Out-Null

# Bundle pinned ASM for Java 25-compatible menu instrumentation.
New-Item -ItemType Directory -Force out/libraries | Out-Null
Invoke-WebRequest -Uri "https://repo.maven.apache.org/maven2/org/ow2/asm/asm/9.9/asm-9.9.jar" -OutFile out/libraries/asm.jar
if ((Get-FileHash out/libraries/asm.jar -Algorithm SHA256).Hash.ToLowerInvariant() -ne "03d99a74ad1ee5c71334ef67437f4ef4fe3488caa7c96d8645abc73c8e2017d4") {
    throw "ASM checksum mismatch"
}
Push-Location out/classes
try {
    & jar xf ../libraries/asm.jar
    if ($LASTEXITCODE -ne 0) { throw "Cannot unpack ASM" }
} finally { Pop-Location }
Remove-Item out/classes/module-info.class, out/classes/META-INF/MANIFEST.MF -ErrorAction SilentlyContinue

$loaderSources = @(Get-ChildItem -Recurse src/main/java -Filter *.java | ForEach-Object { $_.FullName })
& javac --release 21 -encoding UTF-8 -cp out/libraries/asm.jar -d out/classes @loaderSources
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

# Compile-only API and editable starter for third-party HOWL mods.
& jar --create --file "dist/howl-api-$LoaderVersion.jar" -C out/classes dev/howlingwhispers/codaloader/api
if ($LASTEXITCODE -ne 0) { throw "API packaging failed" }
New-Item -ItemType Directory -Force dist/sdk/lib, dist/sdk/api-src/dev/howlingwhispers/codaloader | Out-Null
Copy-Item "dist/howl-api-$LoaderVersion.jar" dist/sdk/lib/howl-api.jar
Copy-Item -Recurse examples/hello-coda/src, examples/hello-coda/resources dist/sdk/
Copy-Item -Recurse src/main/java/dev/howlingwhispers/codaloader/api dist/sdk/api-src/dev/howlingwhispers/codaloader/
Copy-Item sdk/README.md, sdk/build-mod.sh, sdk/build-mod.ps1 dist/sdk/
Copy-Item docs/HOWL-API.md dist/sdk/
Compress-Archive -Path dist/sdk/* -DestinationPath "dist/HOWL-SDK-v$LoaderVersion.zip" -Force

Copy-Item Launch-CodaLoader.bat dist/Launch-CodaLoader.bat
Copy-Item dist/CodaLoader.jar dist/package/CodaLoader.jar
Copy-Item dist/Launch-CodaLoader.bat dist/package/Launch-CodaLoader.bat

@"
H.O.W.L. $LoaderVersion - Howling Open Works Loader

1. Extract the entire ZIP into its own folder.
2. Double-click Launch-CodaLoader.bat.
3. Keep CodaLoader.jar beside the BAT.
4. Put HOWL mods in the active Minecraft game's mods folder.
5. Put custom menu .ogg music in run\music\menu.

CodaLoader checks public GitHub Releases for updates automatically.
"@ | Set-Content -Encoding UTF8 dist/package/README-FIRST.txt

Compress-Archive -Path dist/package/* -DestinationPath "dist/$BundleName" -Force

$JarSha = (Get-FileHash dist/package/CodaLoader.jar -Algorithm SHA256).Hash.ToLowerInvariant()
$BatSha = (Get-FileHash dist/package/Launch-CodaLoader.bat -Algorithm SHA256).Hash.ToLowerInvariant()
$BundleSha = (Get-FileHash "dist/$BundleName" -Algorithm SHA256).Hash.ToLowerInvariant()

@"
{
  "schema": 1,
  "version": "$LoaderVersion",
  "bundle": "$BundleName",
  "sha256": "$BundleSha",
  "files": {
    "CodaLoader.jar": "$JarSha",
    "Launch-CodaLoader.bat": "$BatSha"
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
Write-Host "  dist/HOWL-SDK-v$LoaderVersion.zip"
Write-Host "  dist/howl-api-$LoaderVersion.jar"
