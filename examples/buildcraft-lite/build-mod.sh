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

# Reuse the original, license-covered BCCE artwork byte-for-byte.
# Git blob IDs are pinned to the 8.0.23 release commit; refuse drift.
BCCE_REF=23c6af3
BCCE_ASSETS=https://raw.githubusercontent.com/BCCE-team/BuildCraft/${BCCE_REF}/source-shared/src/main/resources/assets/buildcrafttransport/textures/pipes
mkdir -p out/buildcraft-lite-classes/assets/hw_buildcraft_lite/textures/block
for spec in 'wood_item dd7d51cc65539ddf6aa30067e6f0edd8ab872fef' \
            'stone_item 5b4bd0b79124686ac8e1b3d4c963ab9161e5b433'; do
  read -r name expected <<< "$spec"
  dest="out/buildcraft-lite-classes/assets/hw_buildcraft_lite/textures/block/${name}.png"
  curl --fail --location --retry 3 --silent --show-error "$BCCE_ASSETS/${name}.png" -o "$dest"
  [[ "$(git hash-object "$dest")" == "$expected" ]] || {
    echo "Refusing modified BuildCraft texture: $name" >&2; exit 1;
  }
done

BCCE_CORE=https://raw.githubusercontent.com/BCCE-team/BuildCraft/${BCCE_REF}/source-shared/src/main/resources/assets/buildcraftcore/textures
mkdir -p out/buildcraft-lite-classes/assets/hw_buildcraft_lite/textures/item
while read -r output original expected; do
  [[ -z "$output" ]] && continue
  folder=block; [[ "$output" == "wrench" ]] && folder=item
  dest="out/buildcraft-lite-classes/assets/hw_buildcraft_lite/textures/$folder/${output}.png"
  curl --fail --location --retry 3 --silent --show-error "$BCCE_CORE/$original.png" -o "$dest"
  [[ "$(git hash-object "$dest")" == "$expected" ]] || { echo "Bad BuildCraft art $output" >&2; exit 1; }
done <<'BCCE_ART'
engine_wood_back blocks/engine/wood/back ff4b486887c26bb3bd2d75f611e0227bce0763c3
engine_wood_side blocks/engine/wood/side 4ee76ebc5350d130fe8fea7db224223ecde2866f
engine_trunk blocks/engine/trunk cb390c7b92646ac959cf962a1ecff0951a6be871
wrench items/wrench f3c25782bb74fea4466ea88298e50c19740f2423
BCCE_ART

mkdir -p out/buildcraft-lite-classes/META-INF/licenses
curl --fail --location --retry 3 --silent --show-error \
  "https://raw.githubusercontent.com/BCCE-team/BuildCraft/${BCCE_REF}/LICENSE.txt" \
  -o out/buildcraft-lite-classes/META-INF/licenses/BCCE-MPL-2.0.txt
cat > out/buildcraft-lite-classes/META-INF/NOTICE-BuildCraft-Lite.txt <<'NOTICE'
BuildCraft Lite is an independent H.O.W.L. adaptation.
Original wood_item.png and stone_item.png assets are copied unchanged from
BuildCraft Community Edition 8.0.23, BCCE-team/BuildCraft at commit 23c6af3.
Original BuildCraft and BCCE credits remain with their respective authors.
See META-INF/licenses/BCCE-MPL-2.0.txt and public BCCE repository for source.
NOTICE
jar --create --file dist/buildcraft-lite-0.1.0-dev.jar -C out/buildcraft-lite-classes .
jar tf dist/buildcraft-lite-0.1.0-dev.jar | grep -Fx 'coda.mod.json'
echo 'Built dist/buildcraft-lite-0.1.0-dev.jar (experimental single-player transport)'
