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
javac --release 21 -encoding UTF-8 -cp dist/CodaLoader.jar -d out/api-classes "${API_SOURCES[@]}"

# Compile command bridge and menu fixtures
mapfile -d '' TEST_SOURCES < <(find tests/src -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -cp out/api-classes:dist/CodaLoader.jar:dist/hw-essentials.jar:out/test-libraries/brigadier.jar -d out/test-classes "${TEST_SOURCES[@]}"

# On Java 25, also test transformation of class files compiled for Minecraft's runtime.
if [[ -n "${MENU_FIXTURE_RELEASE:-}" ]]; then
  javac --release "$MENU_FIXTURE_RELEASE" -encoding UTF-8 -cp out/test-classes:out/api-classes -d out/test-classes tests/src/net/minecraft/client/gui/screens/*.java
fi
TEST_CP=out/test-classes:out/api-classes:dist/hw-essentials.jar:out/test-libraries/brigadier.jar:dist/CodaLoader.jar
java -cp "$TEST_CP" dev.howlingwhispers.codaloader.bootstrap.CommandBridgeTest
java -cp "$TEST_CP" dev.howlingwhispers.codaloader.bootstrap.CodaMenusTest
java -cp "$TEST_CP" dev.howlingwhispers.codaloader.bootstrap.CodaOwnedMenusTest
java -cp "$TEST_CP" dev.howlingwhispers.codaloader.bootstrap.MenuSceneVisitsTest
java -cp "$TEST_CP" dev.howlingwhispers.codaloader.bootstrap.HowlSplashesTest
java -cp "$TEST_CP" dev.howlingwhispers.codaloader.core.RequiredWorldPacksTest

# Run the production transformer through the JVM instrumentation API.
printf 'Premain-Class: dev.howlingwhispers.codaloader.bootstrap.MenuTestAgent\n\n' > out/menu-test-manifest.mf
jar --create --file out/menu-test-agent.jar --manifest out/menu-test-manifest.mf -C out/test-classes dev/howlingwhispers/codaloader/bootstrap/MenuTestAgent.class
java -javaagent:out/menu-test-agent.jar -cp "$TEST_CP" dev.howlingwhispers.codaloader.bootstrap.MenuLifecycleTest

# This runs the real production ASM transformer against a named server fixture.
# It does NOT claim that Snapshot 3's live server has been tested.
printf 'Premain-Class: dev.howlingwhispers.codaloader.bootstrap.ServerTickTestAgent\n\n' > out/server-tick-test-manifest.mf
jar --create --file out/server-tick-test-agent.jar --manifest out/server-tick-test-manifest.mf -C out/test-classes dev/howlingwhispers/codaloader/bootstrap/ServerTickTestAgent.class
java -javaagent:out/server-tick-test-agent.jar -cp "$TEST_CP" dev.howlingwhispers.codaloader.bootstrap.ServerTickLifecycleTest
java -javaagent:out/server-tick-test-agent.jar -cp "$TEST_CP" dev.howlingwhispers.codaloader.bootstrap.WorldInventoryTickTest
java -cp "$TEST_CP" dev.howlingwhispers.codaloader.bootstrap.SingleplayerTransferTest
java -cp "$TEST_CP" dev.howlingwhispers.codaloader.bootstrap.NativeRegistryTransformerTest
java -cp "$TEST_CP" dev.howlingwhispers.codaloader.api.NativeBlockEntityDeclarationTest
java -cp "$TEST_CP" dev.howlingwhispers.codaloader.bootstrap.CreativeInventoryTest
java -cp "$TEST_CP" dev.howlingwhispers.codaloader.bootstrap.CodaWolfTextureTest
java -cp "$TEST_CP" dev.howlingwhispers.codaloader.bootstrap.BuildCraftResourceInstallerTest
java -cp "$TEST_CP" dev.howlingwhispers.codaloader.bootstrap.MinecraftVersionMetadataTest

# Verify the same new-world ASM hook that the real Minecraft agent installs.
# This is a named JVM fixture, not a live Snapshot 3 world-creation test.
printf 'Premain-Class: dev.howlingwhispers.codaloader.bootstrap.WorldCreationTestAgent\n\n' > out/world-creation-test-manifest.mf
jar --create --file out/world-creation-test-agent.jar --manifest out/world-creation-test-manifest.mf -C out/test-classes dev/howlingwhispers/codaloader/bootstrap/WorldCreationTestAgent.class
java -javaagent:out/world-creation-test-agent.jar -cp "$TEST_CP" dev.howlingwhispers.codaloader.bootstrap.QuietUndergroundCreationTest
