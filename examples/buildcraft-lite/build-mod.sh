#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
if [[ ! -f dist/CodaLoader.jar ]]; then
  echo "Build H.O.W.L. first: bash scripts/build-dist.sh" >&2
  exit 1
fi
mkdir -p out/buildcraft-lite-classes
rm -rf out/buildcraft-lite-classes
mkdir -p out/buildcraft-lite-classes
mapfile -d '' SOURCES < <(find examples/buildcraft-lite/src -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -cp dist/CodaLoader.jar -d out/buildcraft-lite-classes "${SOURCES[@]}"
cp -R examples/buildcraft-lite/resources/. out/buildcraft-lite-classes/
jar --create --file dist/buildcraft-lite-0.1.0-dev.jar -C out/buildcraft-lite-classes .
jar tf dist/buildcraft-lite-0.1.0-dev.jar | grep -Fx 'coda.mod.json'
echo 'Built dist/buildcraft-lite-0.1.0-dev.jar (experimental single-player transport)'
