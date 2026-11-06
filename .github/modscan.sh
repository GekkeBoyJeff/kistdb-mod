#!/usr/bin/env bash
# The other mods on the live server: which of them add synced entity data (a client without that mod can't read it).
set +e
OUT=out/modscan.txt
: > "$OUT"
mkdir -p /tmp/modscan && cd /tmp/modscan
for slug in 3d-maps clumps collective followers-teleport-too teleport_commands teleport-commands jei appleskin geyser floodgate; do
  url=$(curl -s "https://api.modrinth.com/v2/project/$slug/version?game_versions=%5B%2226.3%22%5D&loaders=%5B%22fabric%22%5D" \
    | jq -r '.[0].files[] | select(.primary==true) | .url' 2>/dev/null | head -1)
  [ -z "$url" ] || [ "$url" = "null" ] && { echo "== $slug: no 26.3 fabric file on Modrinth" >> "$OUT"; continue; }
  echo "== $slug: $url" >> "$OUT"
  rm -rf "x_$slug" && mkdir "x_$slug" && curl -sL "$url" -o "$slug.jar" && unzip -q -o "$slug.jar" -d "x_$slug"
  # nested jars too
  for nj in $(find "x_$slug" -name '*.jar'); do unzip -q -o "$nj" -d "x_$slug/nested" 2>/dev/null; done
  grep -h '"environment"' $(find "x_$slug" -name fabric.mod.json) >> "$OUT" 2>/dev/null
  for c in $(grep -rl --include='*.class' -E 'defineId|EntityDataAccessor' "x_$slug"); do
    echo "-- $c" >> "$OUT"
    javap -c -p "$c" 2>/dev/null | grep -E 'class |defineId|EntityDataAccessor|@Mixin|Mixin' | head -40 >> "$OUT"
  done
  for m in $(find "x_$slug" -name '*.mixins.json' -o -name '*mixin*.json' | grep -v refmap); do
    echo "-- mixins $m" >> "$OUT"; cat "$m" >> "$OUT"; echo >> "$OUT"
  done
done
