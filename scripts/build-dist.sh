#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

LOADER_VERSION="$(sed -n 's/.*LOADER_VERSION = "\([^"]*\)";.*/\1/p' src/main/java/dev/howlingwhispers/codaloader/core/CodaTarget.java)"
[[ "$LOADER_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Invalid CodaTarget.LOADER_VERSION: $LOADER_VERSION" >&2; exit 1; }
EXAMPLE_VERSION="0.0.1"
ESSENTIALS_VERSION="0.2.0"
# Known-good Coda Wolf skin/companion binary is pinned independently of main
# (which may contain experimental environmental sensing work).
CODAWOLF_RELEASE="nightly-codawolf-20261009-a611e592da"
CODAWOLF_SHA256="18d13b3e6bb22f8d21a0911ae112d655cea735f19f2d166b8075bee379ff027a"
CODAWOLF_FILE="coda-wolf-0.1.0-dev.jar"
CODAWOLF_URL="https://github.com/HowlingWhispers/HW-Mods/releases/download/${CODAWOLF_RELEASE}/${CODAWOLF_FILE}"
MAIN_CLASS="dev.howlingwhispers.codaloader.bootstrap.CodaBootstrap"
AGENT_CLASS="dev.howlingwhispers.codaloader.bootstrap.CodaAgent"
BUNDLE_NAME="CodaLoader-v${LOADER_VERSION}-win64.zip"

# HW Essentials is now a separate repo (HW-Mods). Fetch from GitHub Releases.
ESSENTIALS_JAR_URL="${HW_ESSENTIALS_JAR_URL:-https://github.com/HowlingWhispers/HW-Mods/releases/download/v${ESSENTIALS_VERSION}/hw-essentials-${ESSENTIALS_VERSION}.jar}"
ESSENTIALS_SHA256_URL="${HW_ESSENTIALS_SHA256_URL:-${ESSENTIALS_JAR_URL}.sha256}"

rm -rf out dist
mkdir -p out/classes out/example-classes dist/package

# Bundle a pinned ASM build; Java 25 no longer exposes the old internal ASM.
mkdir -p out/libraries
curl --fail --location --retry 3 -o out/libraries/asm.jar https://repo.maven.apache.org/maven2/org/ow2/asm/asm/9.9/asm-9.9.jar
printf '%s  %s\n' '03d99a74ad1ee5c71334ef67437f4ef4fe3488caa7c96d8645abc73c8e2017d4' out/libraries/asm.jar | sha256sum --check
(cd out/classes && jar xf ../libraries/asm.jar)
rm -f out/classes/module-info.class out/classes/META-INF/MANIFEST.MF

mapfile -d '' LOADER_SOURCES < <(find src/main/java -name '*.java' -print0)
if [[ ${#LOADER_SOURCES[@]} -eq 0 ]]; then
  echo "No CodaLoader Java sources found." >&2
  exit 1
fi

javac --release 21 -encoding UTF-8 -cp out/libraries/asm.jar -d out/classes "${LOADER_SOURCES[@]}"
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

# Embed both required mods into H.O.W.L. for automatic Stable AND Nightly
# profile installation. Never take Coda from the unreviewed main branch.
mkdir -p out/classes/codaloader/mods
cp dist/hw-essentials.jar out/classes/codaloader/mods/
echo "Downloading approved Coda Companion ${CODAWOLF_RELEASE}..."
curl --fail --location --retry 3 --silent --show-error \
  "$CODAWOLF_URL" -o "dist/${CODAWOLF_FILE}"
printf '%s  %s\n' "$CODAWOLF_SHA256" "dist/${CODAWOLF_FILE}" | sha256sum --check
python3 - <<'PY'
import json,zipfile
p="dist/coda-wolf-0.1.0-dev.jar"
with zipfile.ZipFile(p) as jar:
    m=json.loads(jar.read("coda.mod.json"))
    assert m["id"]=="coda_wolf" and m["version"]=="0.1.2-dev"
    assert m["minecraft"]=="26.4-snapshot-3"
    assert m["entrypoint"]=="dev.howlingwhispers.codawolf.CodaWolfMod"
    assert "dev/howlingwhispers/codawolf/CodaWolfMod.class" in jar.namelist()
    assert "dev/howlingwhispers/codawolf/CompanionAwareness.class" not in jar.namelist(), "Unreviewed AI work must not ship in Stable"
print("PASS: pinned Coda Wolf 0.1.2, no experimental awareness module")
PY
cp "dist/${CODAWOLF_FILE}" "out/classes/codaloader/mods/${CODAWOLF_FILE}"

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

# Compile-only API and editable starter for third-party HOWL mods.
jar --create --file "dist/howl-api-${LOADER_VERSION}.jar" -C out/classes dev/howlingwhispers/codaloader/api
mkdir -p dist/sdk/lib dist/sdk/api-src/dev/howlingwhispers/codaloader
cp "dist/howl-api-${LOADER_VERSION}.jar" dist/sdk/lib/howl-api.jar
cp -R examples/hello-coda/src examples/hello-coda/resources dist/sdk/
cp -R src/main/java/dev/howlingwhispers/codaloader/api dist/sdk/api-src/dev/howlingwhispers/codaloader/
cp sdk/README.md sdk/build-mod.sh sdk/build-mod.ps1 dist/sdk/
cp docs/HOWL-API.md dist/sdk/
(cd dist/sdk && zip -q -r "../HOWL-SDK-v${LOADER_VERSION}.zip" .)

cp Launch-CodaLoader.bat dist/Launch-CodaLoader.bat
cp dist/CodaLoader.jar dist/package/CodaLoader.jar
# Also expose a transparent, auditable standalone copy in the distribution.
mkdir -p dist/package/bundled-mods
cp "dist/${CODAWOLF_FILE}" "dist/package/bundled-mods/${CODAWOLF_FILE}"
cp dist/Launch-CodaLoader.bat dist/package/Launch-CodaLoader.bat

cat > dist/package/README-FIRST.txt <<EOF
H.O.W.L. ${LOADER_VERSION} - Howling Open Works Loader

1. Extract the entire ZIP into its own folder.
2. Double-click Launch-CodaLoader.bat.
3. Keep CodaLoader.jar beside the BAT.
4. H.O.W.L. automatically installs required Coda Wolf Companion and HW Essentials.
5. Other mods are optional. Put them in the active Minecraft mods folder.
6. Put custom menu .ogg music in run\\music\\menu.

CodaLoader checks public GitHub Releases for updates automatically.
EOF

(
  cd dist/package
  zip -q -r "../${BUNDLE_NAME}" .
)

JAR_SHA="$(sha256sum dist/package/CodaLoader.jar | awk '{print $1}')"
BAT_SHA="$(sha256sum dist/package/Launch-CodaLoader.bat | awk '{print $1}')"
CODAWOLF_BUNDLE_SHA="$(sha256sum "dist/${CODAWOLF_FILE}" | awk '{print $1}')"
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
    "bundled-mods/coda-wolf-0.1.0-dev.jar": "${CODAWOLF_BUNDLE_SHA}"
  }
}
EOF

echo "Built:"
echo "  dist/CodaLoader.jar"
echo "  dist/hello-coda.jar"
echo "  dist/Launch-CodaLoader.bat"
echo "  dist/hw-essentials.jar (from HW-Mods v${ESSENTIALS_VERSION})"
echo "  dist/${CODAWOLF_FILE} (approved Coda Wolf 0.1.2; pinned SHA-256)"
echo "  dist/${BUNDLE_NAME}"
echo "  dist/update-manifest.json"
echo "  dist/HOWL-SDK-v${LOADER_VERSION}.zip"
echo "  dist/howl-api-${LOADER_VERSION}.jar"
