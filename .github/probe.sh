#!/usr/bin/env bash
# Dumpt de publieke API van klassen uit .github/probe.txt naar out/javap.txt (zoekt in Minecraft- en Fabric API-jars).
set +e
OUT=out/javap.txt
: > "$OUT"
JARS=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -E 'minecraft-merged|fabric-api|fabric-[a-z-]*-v[0-9]|net.fabricmc' | grep -v sources | sort -u)
while read -r cls; do
  [ -z "$cls" ] && continue
  path="$(echo "$cls" | tr . /).class"
  found=""
  for j in $JARS; do
    if unzip -l "$j" "$path" >/dev/null 2>&1; then found="$j"; break; fi
  done
  if [ -n "$found" ]; then
    javap -cp "$found" -protected "$cls" >> "$OUT" 2>&1
  else
    echo "!! $cls niet gevonden" >> "$OUT"
  fi
done < .github/probe.txt
