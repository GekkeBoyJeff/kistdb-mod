#!/usr/bin/env bash
# CI only: puts Iris + Sodium (for 26.3, from Modrinth) and a shader pack in the test client's run folder, with the
# options PACK_OPTIONS ("NAME=value" lines, "\n" between them), so the shader walk in Screens can be seen with it.
# PACK_SLUG: the shader pack on Modrinth. EUPHORIA=1: also the Euphoria Patcher mod, which makes "<pack> + Euphoria
# Patches" at launch; that one is used.
set -u
RUN=testclient/build/run/clientGameTest
OUT=out
mkdir -p "$RUN/mods" "$RUN/shaderpacks" "$RUN/config" "$OUT"
api() { curl -sS -H "User-Agent: libreria-ci (github.com/GekkeBoyJeff/kistdb-mod)" "$@"; }
# project, loader, folder, [game version filter on/off], [exact version number]
get() {
  local q="https://api.modrinth.com/v2/project/$1/version?loaders=%5B%22$2%22%5D"
  [ "${4:-on}" = on ] && q="$q&game_versions=%5B%2226.3%22%5D"
  api "$q" > "/tmp/$1.json"
  if [ -n "${5:-}" ]; then
    jq --arg v "$5" '[[.[] | select(.version_number == $v)][0]]' "/tmp/$1.json" > "/tmp/$1.pick.json"
  else
    # the newest release (a beta only when there is no release)
    jq '[([.[] | select(.version_type=="release")][0] // .[0])]' "/tmp/$1.json" > "/tmp/$1.pick.json"
  fi
  mv "/tmp/$1.pick.json" "/tmp/$1.json"
  local url name
  url=$(jq -r '[.[0].files[] | select(.primary==true)][0].url // .[0].files[0].url' "/tmp/$1.json" 2>/dev/null)
  name=$(jq -r '[.[0].files[] | select(.primary==true)][0].filename // .[0].files[0].filename' "/tmp/$1.json" 2>/dev/null)
  if [ -z "$url" ] || [ "$url" = null ]; then echo "$1: none found" >> "$OUT/shader-setup.txt"; return 1; fi
  api -L -o "$3/$name" "$url"
  echo "$1: $name ($(jq -r '.[0].version_number' "/tmp/$1.json"))" >> "$OUT/shader-setup.txt"
  echo "$name"
}
get iris fabric "$RUN/mods" > /dev/null
get sodium fabric "$RUN/mods" > /dev/null
SLUG=${PACK_SLUG:-complementary-reimagined}
NAME=""
if [ "${EUPHORIA:-0}" = 1 ]; then
  EP=$(get euphoria-patches fabric "$RUN/mods" || get euphoria-patches fabric "$RUN/mods" off)
  # "1.10.5-r5.9.3-fabric": Euphoria 1.10.5 for Complementary r5.9.3
  EPV=$(jq -r '.[0].version_number' /tmp/euphoria-patches.json)
  EPN=${EPV%%-*}; CV=$(echo "$EPV" | grep -oE 'r[0-9.]+' | head -1)
  PACK=$(get "$SLUG" iris "$RUN/shaderpacks" off "$CV")
  BASE=$(unzip -l "$RUN/shaderpacks/$PACK" >/dev/null 2>&1 && echo "${PACK%.zip}")
  NAME="$BASE + EuphoriaPatches_$EPN"
  echo "euphoria: $EP → \"$NAME\"" >> "$OUT/shader-setup.txt"
else
  PACK=$(get "$SLUG" iris "$RUN/shaderpacks" || get "$SLUG" iris "$RUN/shaderpacks" off)
  NAME="$PACK"
fi
if [ -n "$NAME" ]; then
  printf '%b\n' "${PACK_OPTIONS:-}" > "$RUN/shaderpacks/$NAME.txt"
  echo "options for $NAME: $(tr '\n' ' ' < "$RUN/shaderpacks/$NAME.txt")" >> "$OUT/shader-setup.txt"
  printf 'enableShaders=true\nshaderPack=%s\ndisableUpdateMessage=true\n' "$NAME" > "$RUN/config/iris.properties"
  # what the pack calls its emission options
  unzip -p "$RUN/shaderpacks/$PACK" 'shaders/lang/en_[Uu][Ss].lang' 2>/dev/null | tr -d '\r' | grep -E 'RP_MODE|IPBR_EMISSIVE_MODE' >> "$OUT/shader-setup.txt"
fi
ls -la "$RUN/mods" "$RUN/shaderpacks" >> "$OUT/shader-setup.txt"
cat "$OUT/shader-setup.txt"
