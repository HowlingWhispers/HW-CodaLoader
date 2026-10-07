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
try {
    $JarResponse = Invoke-WebRequest -Uri $EssentialsJarUrl -OutFile "dist/hw-essentials.jar" -UseBasicParsing -MaximumRedirection 10 -ErrorAction Stop
    Write-Host "Downloaded JAR: $($JarResponse.StatusCode) $($JarResponse.StatusDescription) - Size: $((Get-Item dist/hw-essentials.jar).Length) bytes"
    # Quick check: ensure it's actually a JAR (starts with PK for ZIP)
    $header = Get-Content -Path dist/hw-essentials.jar -TotalCount 4 -Raw -Encoding Byte
    if ($header[0] -ne 0x50 -or $header[1] -ne 0x4B) {
        Write-Error "Downloaded file is not a valid JAR/ZIP (magic bytes: $([string]::Join(' ', $header)))"
        exit 1
    }
} catch {
    Write-Error "Failed to download JAR: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        Write-Error "Response: $($_.Exception.Response.StatusCode) $($_.Exception.Response.StatusDescription)"
    }
    exit 1
}

# Verify SHA-256 if checksum file is available
try {
    $Sha256Response = Invoke-WebRequest -Uri $EssentialsSha256Url -UseBasicParsing -MaximumRedirection 10 -ErrorAction Stop
    Write-Host "Downloaded SHA256: $($Sha256Response.StatusCode) $($Sha256Response.StatusDescription) - Content: $($Sha256Response.Content.Trim())"
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
    Write-Host "No SHA-256 checksum file found or download failed; skipping verification"
    Write-Host "Error: $($_.Exception.Message)"
}
}