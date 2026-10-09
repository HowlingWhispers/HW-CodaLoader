param([string]$LoaderDirectory)
$ErrorActionPreference = 'Stop'

try {
    if ([string]::IsNullOrWhiteSpace($LoaderDirectory)) {
        $roaming = [Environment]::GetFolderPath('ApplicationData')
        $legacy = Join-Path $roaming '.howlingshispers'
        $root = if (Test-Path -LiteralPath $legacy -PathType Container) {
            $legacy
        } else { Join-Path $roaming '.howlingwhispers' }
        $LoaderDirectory = Join-Path $root 'nightly\loader'
    }
    $LoaderDirectory = [IO.Path]::GetFullPath($LoaderDirectory)
    $target = Join-Path $LoaderDirectory 'CodaLoader.jar'
    $marker = Join-Path $LoaderDirectory '.nightly-loader-sha256'
    $tag = Join-Path $LoaderDirectory '.nightly-tag'
    foreach ($file in @($target, $marker, $tag)) {
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
            throw "No managed Nightly loader found at $LoaderDirectory. Install Nightly in CodaLauncher first."
        }
    }
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne
            (Get-Content -LiteralPath $marker -Raw).Trim()) {
        throw 'The installed loader was modified. It has been preserved; this patch cannot overwrite it.'
    }
    $manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'update-manifest.json') -Raw | ConvertFrom-Json
    $expected = [string]$manifest.files.'CodaLoader.jar'
    if ($expected -notmatch '^[a-fA-F0-9]{64}$') { throw 'Invalid patch checksum.' }
    $bundleName = [string]$manifest.bundle
    if ($bundleName -notmatch '^CodaLoader-v[0-9]+\.[0-9]+\.[0-9]+-win64\.zip$') {
        throw 'Invalid patch bundle name.'
    }
    $bundle = Join-Path $PSScriptRoot $bundleName
    if ((Get-FileHash -LiteralPath $bundle -Algorithm SHA256).Hash -ne [string]$manifest.sha256) {
        throw 'The downloaded patch bundle failed verification. Download it again.'
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($bundle)
    try {
        $entries = @($archive.Entries | Where-Object { $_.FullName -ceq 'CodaLoader.jar' })
        if ($entries.Count -ne 1 -or $entries[0].Length -gt 16MB) {
            throw 'Invalid loader entry in patch bundle.'
        }
        # Extract only the loader into a unique owned staging file. No archive
        # paths, launch scripts, mods or world files are installed.
        $staged = Join-Path $LoaderDirectory ('.buildcraft-fix-' + [Guid]::NewGuid().ToString('N') + '.tmp')
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entries[0], $staged, $false)
    } finally { $archive.Dispose() }
    try {
        if ((Get-FileHash -LiteralPath $staged -Algorithm SHA256).Hash -ne $expected) {
            throw 'The patch loader failed verification. Your installed loader was preserved.'
        }
        $backup = Join-Path $LoaderDirectory ('previous-loader-code\buildcraft-fix-' +
            [DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff') + '-' + [Guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $backup | Out-Null
        Copy-Item -LiteralPath $marker -Destination (Join-Path $backup '.nightly-loader-sha256')
        Copy-Item -LiteralPath $tag -Destination (Join-Path $backup '.nightly-tag')
        # Atomic replacement fails without deleting the old loader if Minecraft
        # holds it open. File.Replace also retains the exact previous JAR.
        [IO.File]::Replace($staged, $target, (Join-Path $backup 'CodaLoader.jar'))
        try {
            [IO.File]::WriteAllText($marker, $expected.ToLowerInvariant())
        } catch {
            [IO.File]::Copy((Join-Path $backup 'CodaLoader.jar'), $target, $true)
            [IO.File]::Copy((Join-Path $backup '.nightly-loader-sha256'), $marker, $true)
            throw
        }
        Write-Host 'BuildCraft creative inventory fix installed.'
        Write-Host "Previous loader backup: $backup"
        Write-Host 'Open CodaLauncher, select Nightly, and test creative inventory.'
        Write-Host 'This is a test patch; a future Nightly release may replace it.'
    } finally {
        if ($staged -and (Test-Path -LiteralPath $staged)) { Remove-Item -LiteralPath $staged }
    }
} catch {
    Write-Host ('Patch could not be applied: ' + $_.Exception.Message) -ForegroundColor Red
    Write-Host 'Close Minecraft and CodaLauncher before retrying.'
    exit 1
}
