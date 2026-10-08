#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out/classes
mkdir -p out/classes
mapfile -d '' sources < <(find src -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -cp lib/howl-api.jar -d out/classes "${sources[@]}"
cp -R resources/. out/classes/
jar --create --file out/hello-coda.jar -C out/classes .
echo 'Built out/hello-coda.jar. Copy it into your HW Minecraft mods folder.'
