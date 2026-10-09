#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p out/test-classes
javac --release 21 -encoding UTF-8 -d out/test-classes src/dev/howlingwhispers/codawolf/CompanionRules.java src/dev/howlingwhispers/codawolf/CompanionSave.java src/dev/howlingwhispers/codawolf/CompanionAwareness.java tests/CompanionRulesTest.java tests/CompanionAwarenessTest.java
java -cp out/test-classes CompanionRulesTest
java -cp out/test-classes CompanionAwarenessTest
