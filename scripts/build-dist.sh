#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

LOADER_VERSION="0.0.7-menu-stable"
EXAMPLE_VERSION="0.0.1"
MAIN_CLASS="dev.howlingwhispers.codaloader.bootstrap.CodaBootstrap"
AGENT_CLASS="dev.howlingwhispers.codaloader.bootstrap.CodaAgent"
BUNDLE_NAME="CodaLoader-v${LOADER_VERSION}-win64.zip"

rm -rf out dist
mkdir -p out/classes out/example-classes dist/package/run/mods

mapfile -d '' LOADER_SOURCES < <(find src/main/java -name '*.java' -print0)
if [[ ${#LOADER_SOURCES[@]} -eq 0 ]]; then
  echo "No CodaLoader Java sources found." >&2
  exit 1
fi

javac --release 21 -encoding UTF-8 -d out/classes "${LOADER_SOURCES[@]}"
cat > out/manifest.mf <<EOF
Manifest-Version: 1.0
Main-Class: ${MAIN_CLASS}
Premain-Class: ${AGENT_CLASS}
Can-Redefine-Classes: false
Can-Retransform-Classes: false

EOF

jar --create \
    --file "dist/CodaLoader.jar" \
    --manifest out/manifest.mf \
    -C out/classes .

mapfile -d '' EXAMPLE_SOURCES < <(find examples/hello-coda/src -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -cp out/classes -d out/example-classes "${EXAMPLE_SOURCES[@]}"
cp examples/hello-coda/resources/coda.mod.json out/example-classes/
jar --create --file "dist/hello-coda.jar" -C out/example-classes .

cp Launch-CodaLoader.bat dist/Launch-CodaLoader.bat
cp dist/CodaLoader.jar dist/package/CodaLoader.jar
cp dist/Launch-CodaLoader.bat dist/package/Launch-CodaLoader.bat
cp dist/hello-coda.jar dist/package/run/mods/hello-coda.jar

cat > dist/package/README-FIRST.txt <<EOF
CodaLoader ${LOADER_VERSION}

1. Extract the entire ZIP into its own folder.
2. Double-click Launch-CodaLoader.bat.
3. Keep CodaLoader.jar beside the BAT.
4. Put CodaLoader mods in run\\mods.
5. Put custom menu .ogg music in run\\music\\menu.

CodaLoader checks public GitHub Releases for updates automatically.
EOF

(
  cd dist/package
  zip -q -r "../${BUNDLE_NAME}" .
)

JAR_SHA="$(sha256sum dist/package/CodaLoader.jar | awk '{print $1}')"
BAT_SHA="$(sha256sum dist/package/Launch-CodaLoader.bat | awk '{print $1}')"
HELLO_SHA="$(sha256sum dist/package/run/mods/hello-coda.jar | awk '{print $1}')"
BUNDLE_SHA="$(sha256sum "dist/${BUNDLE_NAME}" | awk '{print $1}')"

cat > dist/update-manifest.json <<EOF
{
  "schema": 1,
  "version": "${LOADER_VERSION}",
  "bundle": "${BUNDLE_NAME}",
  "sha256": "${BUNDLE_SHA}",
  "files": {
    "CodaLoader.jar": "${JAR_SHA}",
    "Launch-CodaLoader.bat": "${BAT_SHA}",
    "run/mods/hello-coda.jar": "${HELLO_SHA}"
  }
}
EOF

echo "Built:"
echo "  dist/CodaLoader.jar"
echo "  dist/hello-coda.jar"
echo "  dist/Launch-CodaLoader.bat"
echo "  dist/${BUNDLE_NAME}"
echo "  dist/update-manifest.json"
