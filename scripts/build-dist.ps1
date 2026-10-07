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

# Support local development overrides, otherwise fetch from GitHub Releases
if ($env:HW_ESSENTIALS_LOCAL_JAR) {
    if (!(Test-Path $env:HW_ESSENTIALS_LOCAL_JAR) -or !$env:HW_ESSENTIALS_LOCAL_SHA256 -or !(Test-Path $env:HW_ESSENTIALS_LOCAL_SHA256)) {
        throw "Local HW Essentials requires both a JAR and checksum file."
    }
    Write-Host "Using local HW Essentials JAR: $env:HW_ESSENTIALS_LOCAL_JAR"
    Copy-Item $env:HW_ESSENTIALS_LOCAL_JAR dist/hw-essentials.jar
    Copy-Item $env:HW_ESSENTIALS_LOCAL_SHA256 dist/hw-essentials.jar.sha256
} else {
# Fetch HW Essentials mod JAR from HW-Mods release
Write-Host "Fetching HW Essentials $EssentialsVersion from HW-Mods release..."
New-Item -ItemType Directory -Force dist | Out-Null

# Use curl.exe (available on Windows runners) for reliable redirect handling
function Download-File {
    param($Url, $OutFile)
    $curl = "curl.exe"
    $args = @("-L", "-f", "-o", $OutFile, $Url)
    $result = & $curl @args
    if ($LASTEXITCODE -ne 0) {
        Write-Error "curl failed with exit code $LASTEXITCODE for $Url"
        exit 1
    }
    $size = (Get-Item $OutFile).Length
    Write-Host "Downloaded $OutFile: $size bytes"
    # Quick check: ensure it's actually a JAR/ZIP (starts with PK)
    $header = Get-Content -Path $OutFile -TotalCount 4 -Raw -Encoding Byte
    if ($header.Length -ge 2 -and $header[0] -eq 0x50 -and $header[1] -eq 0x4B) {
        Write-Host "Verified ZIP/JAR magic bytes"
    } else {
        Write-Error "Downloaded file is not a valid JAR/ZIP (header: $([string]::Join(' ', $header)))"
        exit 1
    }
}

Download-File $EssentialsJarUrl "dist/hw-essentials.jar"
Download-File $EssentialsSha256Url "dist/hw-essentials.jar.sha256"

# Verify SHA-256
$ExpectedSha = (Get-Content "dist/hw-essentials.jar.sha256" -Raw).Trim().Split()[0]
$ActualSha = (Get-FileHash dist/hw-essentials.jar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($ExpectedSha -ne $ActualSha) {
    Write-Error "HW Essentials SHA-256 mismatch!"
    Write-Error "Expected: $ExpectedSha"
    Write-Error "Actual:   $ActualSha"
    exit 1
}
Write-Host "HW Essentials SHA-256 verified: $ActualSha"
}