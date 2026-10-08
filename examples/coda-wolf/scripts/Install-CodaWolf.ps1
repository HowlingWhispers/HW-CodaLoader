# Install the compiled Coda Wolf mod into CodaLauncher's active profile.
# Never writes loader/launcher code or world saves.
[CmdletBinding()]
param(
    [ValidateSet('Auto','Stable','Nightly')][string]$Channel = 'Auto',
    [string]$GameRoot
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
# Windows PowerShell 5.1 needs FileSystem for ZipFile; PowerShell 7 exposes it directly.
if (-not ('System.IO.Compression.ZipFile' -as [type])) {
    try { Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction Stop }
    catch { Add-Type -AssemblyName System.IO.Compression -ErrorAction Stop }
}
$jar = Join-Path $PSScriptRoot 'coda-wolf-0.1.0-dev.jar'
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
    throw 'Keep this installer alongside coda-wolf-0.1.0-dev.jar in the extracted package.'
}
function Read-ModMetadata([string]$file) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($file)
    try {
        $entry = $zip.GetEntry('coda.mod.json')
        if ($null -eq $entry) { return $null }
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try { return ($reader.ReadToEnd() | ConvertFrom-Json) }
        finally { $reader.Dispose() }
    } finally { $zip.Dispose() }
}
$metadata = Read-ModMetadata $jar
if ($null -eq $metadata -or $metadata.id -ne 'coda_wolf' -or
    $metadata.minecraft -ne '26.4-snapshot-3' -or
    $metadata.entrypoint -ne 'dev.howlingwhispers.codawolf.CodaWolfMod') {
    throw 'Unexpected mod metadata. Refusing to install.'
}
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
try {
    if ($null -eq $zip.GetEntry('dev/howlingwhispers/codawolf/CodaWolfMod.class')) {
        throw 'The mod entrypoint class is missing.'
    }
} finally { $zip.Dispose() }
if ([string]::IsNullOrWhiteSpace($GameRoot)) {
    if ([string]::IsNullOrWhiteSpace($env:APPDATA)) { throw 'APPDATA is unset. Supply -GameRoot.' }
    # Match CodaLauncher's legacy-root preference exactly.
    $legacy = Join-Path $env:APPDATA '.howlingshispers'
    $root = if (Test-Path -LiteralPath $legacy -PathType Container) { $legacy }
            else { Join-Path $env:APPDATA '.howlingwhispers' }
    if ($Channel -eq 'Auto') {
        $Channel = 'Stable'
        $settings = Join-Path $root 'launcher/settings.json'
        if (Test-Path -LiteralPath $settings -PathType Leaf) {
            $saved = Get-Content -LiteralPath $settings -Raw | ConvertFrom-Json
            if ($null -ne $saved.PSObject.Properties['updateChannel'] -and $saved.updateChannel -eq 'nightly') { $Channel = 'Nightly' }
        }
    }
    $GameRoot = if ($Channel -eq 'Nightly') { Join-Path $root 'nightly/minecraft' }
                else { Join-Path $root 'minecraft' }
}
$mods = Join-Path $GameRoot 'mods'
$duplicates = @()
if (Test-Path -LiteralPath $mods -PathType Container) {
    foreach ($item in @(Get-ChildItem -LiteralPath $mods -Filter '*.jar' -File)) {
        try { $found = Read-ModMetadata $item.FullName }
        catch { throw "Cannot check $($item.Name): $_" }
        if ($null -ne $found -and $found.id -eq 'coda_wolf') { $duplicates += $item.FullName }
    }
}
$sha = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
if ($duplicates.Count -gt 0) {
    if ($duplicates.Count -eq 1 -and (Get-FileHash -LiteralPath $duplicates[0] -Algorithm SHA256).Hash -eq $sha) {
        Write-Host "Coda Wolf already installed in $mods"; exit 0
    }
    throw "Conflicting coda_wolf JAR(s) found in $mods. No files overwritten; remove the old version manually."
}
New-Item -ItemType Directory -Force -Path $mods | Out-Null
$target = Join-Path $mods 'coda-wolf-0.1.0-dev.jar'
if (Test-Path -LiteralPath $target) { throw "Refusing to overwrite existing $target" }
$temp = Join-Path $mods ('.coda-wolf-' + [guid]::NewGuid().ToString('N') + '.tmp')
try {
    Copy-Item -LiteralPath $jar -Destination $temp
    if ((Get-FileHash -LiteralPath $temp -Algorithm SHA256).Hash -ne $sha) {
        throw 'Copied JAR failed SHA256 verification.'
    }
    [System.IO.File]::Move($temp,$target)
} finally { if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Force } }
Write-Host "Installed $($metadata.name) ($($metadata.version)) into $mods"
Write-Host 'CodaLauncher > Mods > Refresh should now show Coda Wolf Companion as Recognized.'
Write-Host 'Experimental Snapshot 3 wolf hook. Use only a disposable singleplayer world.'
