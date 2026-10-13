#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
CLIENT="${1:?Supply Mojang 26.4-snapshot-3 client.jar}"
LIBRARY_DIR="${2:?Supply directory containing its Mojang libraries}"
CLIENT="$(realpath "$CLIENT")"; LIBRARY_DIR="$(realpath "$LIBRARY_DIR")"
[[ "$(sha1sum "$CLIENT" | cut -d' ' -f1)" == '96611d3cbefeae43527552fd008a7031b6f46e71' ]] || { echo 'Wrong Snapshot client checksum' >&2; exit 1; }
mkdir -p out/sorting-native-test
javac --release 25 -cp dist/CodaLoader.jar -d out/sorting-native-test \
    tests/src/dev/howlingwhispers/codaloader/bootstrap/NativeGameTestAgent.java \
    tests/src/dev/howlingwhispers/codaloader/bootstrap/SortingPipesNativeSmoke.java
printf 'Premain-Class: dev.howlingwhispers.codaloader.bootstrap.NativeGameTestAgent\n\n' > out/sorting-native-test.mf
jar --create --file out/sorting-native-agent.jar --manifest out/sorting-native-test.mf -C out/sorting-native-test dev/howlingwhispers/codaloader/bootstrap/NativeGameTestAgent.class
CP="out/sorting-native-test:dist/CodaLoader.jar:$CLIENT"
while IFS= read -r lib; do CP="$CP:$lib"; done < <(find "$LIBRARY_DIR" -name '*.jar' -type f | sort)
java -Djava.awt.headless=true -javaagent:out/sorting-native-agent.jar -cp "$CP" dev.howlingwhispers.codaloader.bootstrap.SortingPipesNativeSmoke
