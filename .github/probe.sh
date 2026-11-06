#!/usr/bin/env bash
# Dumps the API of the classes in .github/probe.txt to out/javap.txt (searches the Minecraft, Fabric API and Polymer jars).
set +e
OUT=out/javap.txt
: > "$OUT"
JARS=$(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -E 'minecraft-merged|fabric-api|fabric-[a-z-]*-v[0-9]|net.fabricmc|eu.pb4|polymer|sgui' | grep -v sources | sort -u)
while read -r cls; do
  [ -z "$cls" ] && continue
  case "$cls" in "?"*) continue;; esac
  # "+ klasse" = ook private leden laten zien
  flag="-protected"
  case "$cls" in "+"*) flag="-p"; cls="${cls#+}";; esac
  # "! klasse" = bytecode too (to read constants)
  case "$cls" in "!"*) flag="-c -p"; cls="${cls#!}";; esac
  path="$(echo "$cls" | tr . /).class"
  found=""
  for j in $JARS; do
    if unzip -l "$j" "$path" >/dev/null 2>&1; then found="$j"; break; fi
  done
  if [ -n "$found" ]; then
    javap -cp "$found" $flag "$cls" >> "$OUT" 2>&1
  else
    echo "!! $cls niet gevonden" >> "$OUT"
  fi
done < .github/probe.txt

# Lines "? pattern" in probe.txt: search class names in the jars (useful when a package moved)
grep '^? ' .github/probe.txt | while read -r _ pat; do
  for j in $JARS; do
    unzip -l "$j" 2>/dev/null | awk '{print $4}' | grep -E "$pat" | sed "s|^|?? $pat -> |" >> "$OUT"
  done
done

# The client's own GUI textures and shaders (to see what a resource pack can restyle)
for j in $(find ~/.gradle/caches .gradle -name '*.jar' 2>/dev/null | grep -i minecraft | sort -u); do
  if unzip -l "$j" 'assets/minecraft/textures/gui/sprites/widget/button.png' >/dev/null 2>&1; then
    mkdir -p out/vanilla
    unzip -q -o "$j" 'assets/minecraft/textures/gui/*' 'assets/minecraft/shaders/*' 'assets/minecraft/font/*' -d out/vanilla
    # trims and worn armour (for effects that follow a trim's lines)
    unzip -q -o "$j" 'assets/minecraft/textures/trims/*' 'assets/minecraft/textures/entity/equipment/*' 'assets/minecraft/equipment/*' \
      'assets/minecraft/atlases/*' -d out/vanilla || true
    unzip -q -o "$j" 'data/minecraft/trim_pattern/*' 'data/minecraft/trim_material/*' -d out/vanilla || true
    echo "vanilla assets from $j" >> "$OUT"
    break
  fi
done

exit 0
