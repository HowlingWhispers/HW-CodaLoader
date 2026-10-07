#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p out/test-classes out/test-libraries
# Test dependency only; production uses Minecraft's own Brigadier.
curl --fail --location --retry 3 -o out/test-libraries/brigadier.jar https://libraries.minecraft.net/com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar
mapfile -d '' TEST_SOURCES < <(find tests/src -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -cp out/classes:out/essentials-classes:out/test-libraries/brigadier.jar -d out/test-classes "${TEST_SOURCES[@]}"
TEST_CP=out/test-classes:out/classes:out/essentials-classes:out/test-libraries/brigadier.jar
java -cp "$TEST_CP" dev.howlingwhispers.essentials.EssentialsTest
java -cp "$TEST_CP" dev.howlingwhispers.codaloader.bootstrap.CommandBridgeTest
