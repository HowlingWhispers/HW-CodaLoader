#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

rm -rf out run
mkdir -p out/classes out/example-classes run/mods

find src/main/java -name '*.java' -print0 | xargs -0 javac --release 21 -encoding UTF-8 -d out/classes
find examples/hello-coda/src -name '*.java' -print0 | xargs -0 javac --release 21 -encoding UTF-8 -cp out/classes -d out/example-classes
cp examples/hello-coda/resources/coda.mod.json out/example-classes/
jar --create --file run/mods/hello-coda.jar -C out/example-classes .

java -cp out/classes dev.howlingwhispers.codaloader.bootstrap.CodaBootstrap run
