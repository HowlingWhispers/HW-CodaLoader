#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

LOADER_VERSION="0.0.1-foundation"
EXAMPLE_VERSION="0.0.1"
MAIN_CLASS="dev.howlingwhispers.codaloader.bootstrap.CodaBootstrap"

rm -rf out dist
mkdir -p out/classes out/example-classes dist

mapfile -d '' LOADER_SOURCES < <(find src/main/java -name '*.java' -print0)
if [[ ${#LOADER_SOURCES[@]} -eq 0 ]]; then
  echo "No CodaLoader Java sources found." >&2
  exit 1
fi

javac --release 21 -encoding UTF-8 -d out/classes "${LOADER_SOURCES[@]}"
jar --create \
    --file "dist/CodaLoader-${LOADER_VERSION}.jar" \
    --main-class "${MAIN_CLASS}" \
    -C out/classes .

mapfile -d '' EXAMPLE_SOURCES < <(find examples/hello-coda/src -name '*.java' -print0)
if [[ ${#EXAMPLE_SOURCES[@]} -eq 0 ]]; then
  echo "No Hello Coda Java sources found." >&2
  exit 1
fi

javac --release 21 -encoding UTF-8 \
    -cp out/classes \
    -d out/example-classes \
    "${EXAMPLE_SOURCES[@]}"

cp examples/hello-coda/resources/coda.mod.json out/example-classes/
jar --create \
    --file "dist/hello-coda-${EXAMPLE_VERSION}.jar" \
    -C out/example-classes .

cp Launch-CodaLoader.bat dist/Launch-CodaLoader.bat

echo "Built:"
echo "  dist/CodaLoader-${LOADER_VERSION}.jar"
echo "  dist/hello-coda-${EXAMPLE_VERSION}.jar"
echo "  dist/Launch-CodaLoader.bat"
