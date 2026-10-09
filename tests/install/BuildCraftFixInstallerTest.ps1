$ErrorActionPreference = 'Stop'
$workspace = Join-Path ([IO.Path]::GetTempPath()) ('buildcraft-fix-test-' + [Guid]::NewGuid().ToString('N'))
$checks = 0
function Check($Condition, $Message) {
    $script:checks++
    if (-not $Condition) { throw $Message }
}
New-Item -ItemType Directory -Path $workspace | Out-Null
try {
    $package = Join-Path $workspace 'package'
    $loader = Join-Path $workspace 'profile\nightly\loader'
    $saves = Join-Path $workspace 'profile\nightly\minecraft\saves'
    New-Item -ItemType Directory -Path $package, $loader, $saves | Out-Null
    Copy-Item (Join-Path $PSScriptRoot '..\..\scripts\Apply-BuildCraft-Fix.ps1') $package
    $installed = Join-Path $loader 'CodaLoader.jar'
    [IO.File]::WriteAllText($installed, 'original loader fixture')
    $originalHash = (Get-FileHash $installed -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText((Join-Path $loader '.nightly-loader-sha256'), $originalHash)
    [IO.File]::WriteAllText((Join-Path $loader '.nightly-tag'), 'existing-nightly')
    [IO.File]::WriteAllText((Join-Path $saves 'world-sentinel'), 'existing world')
    $newJar = Join-Path $workspace 'CodaLoader.jar'
    [IO.File]::WriteAllText($newJar, 'fixed loader fixture')
    $newHash = (Get-FileHash $newJar -Algorithm SHA256).Hash.ToLowerInvariant()
    $bundle = Join-Path $package 'CodaLoader-v0.0.28-win64.zip'
    Compress-Archive -LiteralPath $newJar -DestinationPath $bundle
    $manifest = @{
        bundle = [IO.Path]::GetFileName($bundle)
        sha256 = (Get-FileHash $bundle -Algorithm SHA256).Hash.ToLowerInvariant()
        files = @{ 'CodaLoader.jar' = $newHash }
    }
    $manifest | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $package 'update-manifest.json')
    $scriptFile = Join-Path $package 'Apply-BuildCraft-Fix.ps1'
    $patchOutput = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $scriptFile -LoaderDirectory $loader 2>&1
    Check ($LASTEXITCODE -eq 0) ('patch succeeds: ' + ($patchOutput -join ' '))
    Check ((Get-FileHash $installed -Algorithm SHA256).Hash -eq $newHash) 'correct loader installed'
    Check ((Get-Content (Join-Path $loader '.nightly-loader-sha256') -Raw).Trim() -eq $newHash) 'Nightly ownership marker updated'
    Check ((Get-Content (Join-Path $loader '.nightly-tag') -Raw) -eq 'existing-nightly') 'release tag preserved'
    Check ((Get-Content (Join-Path $saves 'world-sentinel') -Raw) -eq 'existing world') 'world untouched'
    $backups = @(Get-ChildItem (Join-Path $loader 'previous-loader-code') -Recurse -Filter CodaLoader.jar)
    Check ($backups.Count -eq 1) 'one previous-loader backup'
    Check ((Get-FileHash $backups[0].FullName -Algorithm SHA256).Hash -eq $originalHash) 'backup matches previous loader'
    $manifest.sha256 = '0' * 64
    $manifest | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $package 'update-manifest.json')
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $scriptFile -LoaderDirectory $loader
    Check ($LASTEXITCODE -eq 1) 'corrupt download refused'
    Check ((Get-FileHash $installed -Algorithm SHA256).Hash -eq $newHash) 'corrupt download leaves installed loader untouched'
    [IO.File]::WriteAllText($installed, 'player edited loader')
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $scriptFile -LoaderDirectory $loader
    Check ($LASTEXITCODE -eq 1) 'edited loader refused'
    Check ((Get-Content $installed -Raw) -eq 'player edited loader') 'edited loader preserved'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $scriptFile -LoaderDirectory (Join-Path $workspace 'missing')
    Check ($LASTEXITCODE -eq 1) 'missing managed installation refused'
    Write-Host "PASS: $checks BuildCraft Nightly patch installation checks"
    Write-Output "::notice::PASS: $checks BuildCraft Nightly patch installation checks"
} finally {
    Remove-Item -LiteralPath $workspace -Recurse -Force
}
# The last subprocess deliberately failed the negative-case test. GitHub's
# PowerShell wrapper otherwise mistakes that expected status for suite failure.
$global:LASTEXITCODE = 0
