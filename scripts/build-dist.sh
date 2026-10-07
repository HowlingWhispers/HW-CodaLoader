#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

LOADER_VERSION="0.0.19"
EXAMPLE_VERSION="0.0.1"
ESSENTIALS_VERSION="0.1.0"
MAIN_CLASS="dev.howlingwhispers.codaloader.bootstrap.CodaBootstrap"
AGENT_CLASS="dev.howlingwhispers.codaloader.bootstrap.CodaAgent"
BUNDLE_NAME="CodaLoader-v${LOADER_VERSION}-win64.zip"

# HW Essentials is now a separate repo (HW-Mods). Fetch from GitHub Releases.
ESSENTIALS_JAR_URL="${HW_ESSENTIALS_JAR_URL:-https://github.com/HowlingWhispers/HW-Mods/releases/download/v${ESSENTIALS_VERSION}/hw-essentials-${ESSENTIALS_VERSION}.jar}"
ESSENTIALS_SHA256_URL="${HW_ESSENTIALS_SHA256_URL:-${ESSENTIALS_JAR_URL}.sha256}"

rm -rf out dist
mkdir -p out/classes out/example-classes dist/package/run/mods

mapfile -d '' LOADER_SOURCES < <(find src/main/java -name '*.java' -print0)
if [[ ${#LOADER_SOURCES[@]} -eq 0 ]]; then
  echo "No CodaLoader Java sources found." >&2
  exit 1
fi

javac --release 21 -encoding UTF-8 -d out/classes "${LOADER_SOURCES[@]}"
if [[ -d src/main/resources ]]; then
  cp -R src/main/resources/. out/classes/
fi

# Both the release and local development paths require a verified mod JAR.
if [[ -n "${HW_ESSENTIALS_LOCAL_JAR:-}" ]]; then
  [[ -f "$HW_ESSENTIALS_LOCAL_JAR" && -f "${HW_ESSENTIALS_LOCAL_SHA256:-}" ]] || {
    echo "Local HW Essentials requires both a JAR and checksum file." >&2; exit 1;
  }
  cp "$HW_ESSENTIALS_LOCAL_JAR" dist/hw-essentials.jar
  cp "$HW_ESSENTIALS_LOCAL_SHA256" dist/hw-essentials.jar.sha256
else
  echo "Fetching HW Essentials ${ESSENTIALS_VERSION} from HW-Mods release..."
  curl --fail --location --retry 3 -o dist/hw-essentials.jar "$ESSENTIALS_JAR_URL"
  curl --fail --location --retry 3 -o dist/hw-essentials.jar.sha256 "$ESSENTIALS_SHA256_URL"
fi
EXPECTED_SHA="$(awk 'NR==1 {print $1}' dist/hw-essentials.jar.sha256 | tr '[:upper:]' '[:lower:]')"
ACTUAL_SHA="$(sha256sum dist/hw-essentials.jar | awk '{print $1}')"
if [[ ! "$EXPECTED_SHA" =~ ^[0-9a-f]{64}$ || "$EXPECTED_SHA" != "$ACTUAL_SHA" ]]; then
  echo "HW Essentials checksum is invalid or does not match the downloaded JAR." >&2
  exit 1
fi
echo "HW Essentials SHA-256 verified: $ACTUAL_SHA"
jar tf dist/hw-essentials.jar > out/essentials-contents.txt
grep -Fx "coda.mod.json" out/essentials-contents.txt >/dev/null || { echo "Invalid mod JAR: missing coda.mod.json" >&2; exit 1; }
grep -Fx "dev/howlingwhispers/essentials/HwEssentialsMod.class" out/essentials-contents.txt >/dev/null || { echo "Invalid mod JAR: missing entrypoint" >&2; exit 1; }

# Embed mod in CodaLoader for automatic profile installation
mkdir -p out/classes/codaloader/mods
cp dist/hw-essentials.jar out/classes/codaloader/mods/

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
cp dist/hw-essentials.jar dist/package/run/mods/hw-essentials.jar

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
ESSENTIALS_SHA="$(sha256sum dist/package/run/mods/hw-essentials.jar | awk '{print $1}')"
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
    "run/mods/hello-coda.jar": "${HELLO_SHA}",
    "run/mods/hw-essentials.jar": "${ESSENTIALS_SHA}"
  }
}
EOF

echo "Built:"
echo "  dist/CodaLoader.jar"
echo "  dist/hello-coda.jar"
echo "  dist/Launch-CodaLoader.bat"
echo "  dist/hw-essentials.jar (from HW-Mods v${ESSENTIALS_VERSION})"
echo "  dist/${BUNDLE_NAME}"
echo "  dist/update-manifest.json"