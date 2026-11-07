#!/usr/bin/env bash
# CI only: puts Iris + Sodium (for 26.3, from Modrinth) and a shader pack in the test client's run folder, with the
# pack's resource-pack PBR option (LabPBR) on, so the shader walk in Screens can check "Glow with shaders".
set -u
RUN=testclient/build/run/clientGameTest
OUT=out
mkdir -p "$RUN/mods" "$RUN/shaderpacks" "$RUN/config" "$OUT"
api() { curl -sS -H "User-Agent: libreria-ci (github.com/GekkeBoyJeff/kistdb-mod)" "$@"; }
# project, loader, folder, [game version filter on/off]
get() {
  local q="https://api.modrinth.com/v2/project/$1/version?loaders=%5B%22$2%22%5D"
  [ "${4:-on}" = on ] && q="$q&game_versions=%5B%2226.3%22%5D"
  api "$q" > "/tmp/$1.json"
  local url name
  # the newest release (a beta only when there is no release)
  jq '[([.[] | select(.version_type=="release")][0] // .[0])]' "/tmp/$1.json" > "/tmp/$1.pick.json" && mv "/tmp/$1.pick.json" "/tmp/$1.json"
  url=$(jq -r '[.[0].files[] | select(.primary==true)][0].url // .[0].files[0].url' "/tmp/$1.json")
  name=$(jq -r '[.[0].files[] | select(.primary==true)][0].filename // .[0].files[0].filename' "/tmp/$1.json")
  if [ -z "$url" ] || [ "$url" = null ]; then echo "$1: none found" >> "$OUT/shader-setup.txt"; return 1; fi
  api -L -o "$3/$name" "$url"
  echo "$1: $name ($(jq -r '.[0].version_number + " · " + (.[0].game_versions|join(","))' "/tmp/$1.json"))" >> "$OUT/shader-setup.txt"
  echo "$name"
}
get iris fabric "$RUN/mods" > /dev/null
get sodium fabric "$RUN/mods" > /dev/null
PACK=$(get complementary-reimagined iris "$RUN/shaderpacks" || get complementary-reimagined iris "$RUN/shaderpacks" off)
BSL=$(get bsl-shaders iris "$RUN/shaderpacks" || get bsl-shaders iris "$RUN/shaderpacks" off)

# The options that read resource pack PBR: the value whose label says "lab" (LabPBR) in the pack's English names
opts() {
  local zip="$RUN/shaderpacks/$1"
  unzip -p "$zip" 'shaders/lang/en_[Uu][Ss].lang' 2>/dev/null | tr -d '\r' > /tmp/lang.txt
  echo "== $1" >> "$OUT/shader-setup.txt"
  grep -iE 'lab|pbr|emissi|RP_MODE|MATERIAL' /tmp/lang.txt | head -60 >> "$OUT/shader-setup.txt"
  grep -ioE '^value\.[A-Za-z0-9_]+\.[-0-9]+=.*lab' /tmp/lang.txt | head -1 | sed -E 's/^value\.([A-Za-z0-9_]+)\.([-0-9]+)=.*/\1=\2/'
}
if [ -n "$PACK" ]; then
  O=$(opts "$PACK")
  echo "$O" > "$RUN/shaderpacks/$PACK.txt"
  echo "set in $PACK: $O" >> "$OUT/shader-setup.txt"
  printf 'enableShaders=true\nshaderPack=%s\ndisableUpdateMessage=true\n' "$PACK" > "$RUN/config/iris.properties"
fi
[ -n "$BSL" ] && opts "$BSL" > /dev/null
ls -la "$RUN/mods" "$RUN/shaderpacks" >> "$OUT/shader-setup.txt"
cat "$OUT/shader-setup.txt"
