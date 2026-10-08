#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Requires hw-essentials JAR (fetched by build-dist.sh)
if [[ ! -f dist/hw-essentials.jar ]]; then
  echo "Mod JAR not found. Run ./scripts/build-dist.sh first" >&2
  exit 1
fi

mkdir -p out/test-classes out/test-libraries

# Test dependency only; production uses Minecraft's own Brigadier.
curl --fail --location --retry 3 -o out/test-libraries/brigadier.jar https://libraries.minecraft.net/com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar

# Compile API classes
mkdir -p out/api-classes
mapfile -d '' API_SOURCES < <(find src/main/java -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -d out/api-classes "${API_SOURCES[@]}"

# Compile tests (only command bridge test remains in HW-CodaLoader)
mapfile -d '' TEST_SOURCES < <(find tests/src -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -cp out/api-classes:dist/hw-essentials.jar:out/test-libraries/brigadier.jar -d out/test-classes "${TEST_SOURCES[@]}"

TEST_CP=out/test-classes:out/api-classes:dist/hw-essentials.jar:out/test-libraries/brigadier.jar
java -cp "$TEST_CP" dev.howlingwhispers.codaloader.bootstrap.CommandBridgeTest
java -cp "$TEST_CP" dev.howlingwhispers.codaloader.bootstrap.CodaMenusTest
