#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
CLIENT="$(realpath "${1:?Supply exact Snapshot 3 client.jar}")"
LIBRARIES="$(realpath "${2:?Supply verified Minecraft libraries}")"
[[ "$(sha1sum "$CLIENT" | cut -d' ' -f1)" == '96611d3cbefeae43527552fd008a7031b6f46e71' ]] || { echo 'Wrong Minecraft client' >&2; exit 1; }
mkdir -p out/living-native/classes
javac --release 25 -cp dist/CodaLoader.jar -d out/living-native/classes \
  tests/src/dev/howlingwhispers/codaloader/bootstrap/LivingWorldNativeAgent.java \
  tests/src/dev/howlingwhispers/codaloader/bootstrap/LivingWorldNativeSmoke.java
printf 'Premain-Class: dev.howlingwhispers.codaloader.bootstrap.LivingWorldNativeAgent\n\n' > out/living-native/agent.mf
jar --create --file out/living-native/agent.jar --manifest out/living-native/agent.mf -C out/living-native/classes .
CP="$(realpath out/living-native/classes):$(realpath dist/CodaLoader.jar):$CLIENT"
while IFS= read -r lib; do CP="$CP:$lib"; done < <(find "$LIBRARIES" -name '*.jar' -type f | sort)
# GameTestMain resets its universe. Always allocate a fresh throwaway directory; never accept a player's save path.
UNIVERSE="$(mktemp -d "${TMPDIR:-/tmp}/howl-frontier-native-XXXXXXXX")"
trap 'rm -rf "$UNIVERSE"' EXIT
java -Xmx2G -Djava.awt.headless=true -javaagent:out/living-native/agent.jar -cp "$CP" \
  net.minecraft.gametest.Main --universe "$UNIVERSE"
