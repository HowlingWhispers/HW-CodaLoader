#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
CLIENT="$(realpath "${1:?Supply exact Snapshot 3 client.jar}")"
LIBRARY_DIR="$(realpath "${2:?Supply Mojang libraries directory}")"
MOD="$(realpath "${3:?Supply Sorting Pipes mod JAR}")"
[[ "$(sha1sum "$CLIENT" | cut -d' ' -f1)" == '96611d3cbefeae43527552fd008a7031b6f46e71' ]] || exit 1
mkdir -p out/mod-data-native
CP="dist/CodaLoader.jar:$CLIENT"
while IFS= read -r lib;do CP="$CP:$lib";done < <(find "$LIBRARY_DIR" -name '*.jar' -type f | sort)
javac --release 25 -cp "$CP" -d out/mod-data-native tests/src/dev/howlingwhispers/codaloader/bootstrap/NativeGameTestAgent.java tests/native/dev/howlingwhispers/codaloader/bootstrap/GuideBookNativeSmoke.java
printf 'Premain-Class: dev.howlingwhispers.codaloader.bootstrap.NativeGameTestAgent\n\n' > out/mod-data-native-agent.mf
jar --create --file out/mod-data-native-agent.jar --manifest out/mod-data-native-agent.mf -C out/mod-data-native dev/howlingwhispers/codaloader/bootstrap/NativeGameTestAgent.class
java -Xmx2G -Djava.awt.headless=true -javaagent:out/mod-data-native-agent.jar -cp "out/mod-data-native:$CP" dev.howlingwhispers.codaloader.bootstrap.GuideBookNativeSmoke "$MOD"
