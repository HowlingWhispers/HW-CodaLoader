$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "../..")
Push-Location $root
try {
    if (!(Test-Path dist/CodaLoader.jar)) { throw "Build H.O.W.L. first with scripts/build-dist.ps1" }
    Remove-Item -Recurse -Force out/buildcraft-lite-classes -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force out/buildcraft-lite-classes | Out-Null
    $sources = @(Get-ChildItem -Recurse examples/buildcraft-lite/src -Filter *.java | ForEach-Object { $_.FullName })
    & javac --release 21 -encoding UTF-8 -cp dist/CodaLoader.jar -d out/buildcraft-lite-classes @sources
    if ($LASTEXITCODE -ne 0) { throw "BuildCraft Lite Java compilation failed" }
    Copy-Item -Recurse -Force examples/buildcraft-lite/resources/* out/buildcraft-lite-classes/

    # Preserve original BCCE texture bytes and license, pinned to release 8.0.23.
    $ref = "23c6af3"
    $textures = @{
        wood_item = "dd7d51cc65539ddf6aa30067e6f0edd8ab872fef"
        stone_item = "5b4bd0b79124686ac8e1b3d4c963ab9161e5b433"
    }
    $textureDir = "out/buildcraft-lite-classes/assets/hw_buildcraft_lite/textures/block"
    New-Item -ItemType Directory -Force $textureDir | Out-Null
    foreach ($name in @("wood_item", "stone_item")) {
        $dest = Join-Path $textureDir "$name.png"
        $url = "https://raw.githubusercontent.com/BCCE-team/BuildCraft/$ref/source-shared/src/main/resources/assets/buildcrafttransport/textures/pipes/$name.png"
        Invoke-WebRequest -Uri $url -OutFile $dest
        $raw = [IO.File]::ReadAllBytes((Resolve-Path $dest))
        $header = [Text.Encoding]::ASCII.GetBytes("blob $($raw.Length)" + [char]0)
        $sum = New-Object byte[] ($header.Length + $raw.Length)
        [Array]::Copy($header, 0, $sum, 0, $header.Length)
        [Array]::Copy($raw, 0, $sum, $header.Length, $raw.Length)
        $sha = [BitConverter]::ToString(([Security.Cryptography.SHA1]::Create()).ComputeHash($sum)).Replace("-", "").ToLowerInvariant()
        if ($sha -ne $textures[$name]) { throw "Refusing changed BCCE texture: $name" }
    }
    $licenseDir = "out/buildcraft-lite-classes/META-INF/licenses"
    New-Item -ItemType Directory -Force $licenseDir | Out-Null
    Invoke-WebRequest -Uri "https://raw.githubusercontent.com/BCCE-team/BuildCraft/$ref/LICENSE.txt" -OutFile "$licenseDir/BCCE-MPL-2.0.txt"
    @"
BuildCraft Lite is an independent H.O.W.L. adaptation.
Original wood_item.png and stone_item.png assets are copied unchanged from
BuildCraft Community Edition 8.0.23, BCCE-team/BuildCraft at commit 23c6af3.
Original BuildCraft and BCCE credits remain with their respective authors.
See META-INF/licenses/BCCE-MPL-2.0.txt and public BCCE repository for source.
"@ | Set-Content -Encoding UTF8 out/buildcraft-lite-classes/META-INF/NOTICE-BuildCraft-Lite.txt
    & jar --create --file dist/buildcraft-lite-0.1.0-dev.jar -C out/buildcraft-lite-classes .
    if ($LASTEXITCODE -ne 0) { throw "BuildCraft Lite packaging failed" }
    Write-Host "Built dist/buildcraft-lite-0.1.0-dev.jar (experimental single-player transport)"
} finally { Pop-Location }
