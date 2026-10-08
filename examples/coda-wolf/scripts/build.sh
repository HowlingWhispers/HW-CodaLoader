#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
API_JAR="${1:-${HOWL_API_JAR:-}}"
if [[ -z "$API_JAR" || ! -f "$API_JAR" ]]; then
  echo "Usage: bash scripts/build.sh /path/to/howl-api.jar" >&2
  exit 2
fi
rm -rf out/classes
mkdir -p out/classes
find src -name '*.java' -print0 | xargs -0 javac --release 21 -encoding UTF-8 -cp "$API_JAR" -d out/classes
cp resources/coda.mod.json out/classes/coda.mod.json
jar --create --file out/coda-wolf-0.1.0-dev.jar -C out/classes .
echo "Built out/coda-wolf-0.1.0-dev.jar"
