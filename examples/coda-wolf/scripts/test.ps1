$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    New-Item -ItemType Directory -Force 'out/test-classes' | Out-Null
    & javac --release 21 -encoding UTF-8 -d 'out/test-classes' 'src/dev/howlingwhispers/codawolf/CompanionRules.java' 'src/dev/howlingwhispers/codawolf/CompanionSave.java' 'tests/CompanionRulesTest.java'
    if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
    & java -cp 'out/test-classes' CompanionRulesTest
    if ($LASTEXITCODE -ne 0) { throw 'unit tests failed' }
} finally { Pop-Location }
