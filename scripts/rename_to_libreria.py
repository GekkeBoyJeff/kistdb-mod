#!/usr/bin/env python3
"""Eenmalig: hernoemt het Handig-framework naar Libreria (mappen, pakketten, klassen, mod-ID's, opslagpaden)."""
import os
import re
import subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(ROOT)

DIRS = {  # oude map -> nieuwe map
    'kern': 'core', 'kistdb': 'chests', 'gereedschap': 'tools', 'wegwijzer': 'waypoints', 'info': 'info',
    'gemak': 'comfort', 'graf': 'graves', 'verzamelaar': 'collector', 'naam': 'nicknames', 'gebied': 'claims',
    'glans': 'glint',
}
PACKAGES = {  # oud pakket -> nieuw pakket (en de bijbehorende mappen)
    'nl.handig.kern': 'nl.libreria.core', 'nl.kistdb': 'nl.libreria.chests', 'nl.handig.gereedschap': 'nl.libreria.tools',
    'nl.handig.wegwijzer': 'nl.libreria.waypoints', 'nl.handig.info': 'nl.libreria.info', 'nl.handig.gemak': 'nl.libreria.comfort',
    'nl.handig.graf': 'nl.libreria.graves', 'nl.handig.verzamelaar': 'nl.libreria.collector', 'nl.handig.naam': 'nl.libreria.nicknames',
    'nl.handig.gebied': 'nl.libreria.claims', 'nl.handig.glans': 'nl.libreria.glint',
}
# Klassen: (module of None = overal) -> {oud: nieuw}
CLASSES = {
    None: {'Handig': 'Libreria', 'KernTests': 'CoreTests', 'KistDB': 'ChestsMod', 'KistCommands': 'ChestCommands',
           'KistTests': 'ChestsTests', 'Gereedschap': 'ToolsMod', 'GereedschapTests': 'ToolsTests', 'Wegwijzer': 'WaypointsMod',
           'WegwijzerTests': 'WaypointsTests', 'Gemak': 'ComfortMod', 'Graf': 'GravesMod', 'GrafTests': 'GravesTests',
           'Verzamelaar': 'CollectorMod', 'VerzamelaarTests': 'CollectorTests', 'Naam': 'NicknamesMod', 'NaamTests': 'NicknamesTests',
           'Gebied': 'ClaimsMod', 'GebiedTests': 'ClaimsTests', 'Glans': 'GlintMod', 'GlansTests': 'GlintTests'},
    'info': {'Info': 'InfoMod'},
}
MOD_IDS = {
    'handig_gereedschap': 'libreria_tools', 'handig_wegwijzer': 'libreria_waypoints', 'handig_info': 'libreria_info',
    'handig_gemak': 'libreria_comfort', 'handig_graf': 'libreria_graves', 'handig_verzamelaar': 'libreria_collector',
    'handig_naam': 'libreria_nicknames', 'handig_gebied': 'libreria_claims', 'handig_glans': 'libreria_glint',
    'handig_testserver': 'libreria_testserver',
}
STORES = {
    '"handig/opties"': '"libreria/settings"', '"handig/wegwijzer"': '"libreria/waypoints"', '"handig/naam"': '"libreria/nicknames"',
    '"handig/graven.json"': '"libreria/graves.json"', '"handig/glans"': '"libreria/glint"', '"handig/verzamelaar"': '"libreria/collector"',
    '"handig/gebieden.json"': '"libreria/claims.json"',
}

STRING_RE = re.compile(r'"(?:\\.|[^"\\])*"')


def outside_strings(line, fn):
    """Past fn toe op alles behalve Java-stringliterals."""
    out, last = [], 0
    for m in STRING_RE.finditer(line):
        out.append(fn(line[last:m.start()]))
        out.append(m.group(0))
        last = m.end()
    out.append(fn(line[last:]))
    return ''.join(out)


def git(*args):
    subprocess.run(['git', *args], check=True)


# 1. Modulemappen
for old, new in DIRS.items():
    if old != new and os.path.isdir(old):
        git('mv', old, new)

# 2. Pakketmappen
for old, new in PACKAGES.items():
    for module in DIRS.values():
        src = os.path.join(module, 'src/main/java', *old.split('.'))
        if os.path.isdir(src):
            dst = os.path.join(module, 'src/main/java', *new.split('.'))
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            git('mv', src, dst)
    # lege oude ouder-mappen opruimen
for module in DIRS.values():
    for p in ('src/main/java/nl/handig', 'src/main/java/nl/kistdb'):
        d = os.path.join(module, p)
        if os.path.isdir(d) and not os.listdir(d):
            os.rmdir(d)

# 3. Klassebestanden hernoemen
for module in DIRS.values():
    for dirpath, _, files in os.walk(os.path.join(module, 'src/main/java')):
        for f in files:
            name = f[:-5]
            mapping = dict(CLASSES[None])
            mapping.update(CLASSES.get(module, {}))
            if name in mapping:
                git('mv', os.path.join(dirpath, f), os.path.join(dirpath, mapping[name] + '.java'))

# 4. Mixin-configs en icoon
for module, old_json in (('core', 'handig.mixins.json'), ('tools', 'handig_gereedschap.mixins.json'), ('graves', 'handig_graf.mixins.json'),
                         ('collector', 'handig_verzamelaar.mixins.json'), ('nicknames', 'handig_naam.mixins.json')):
    p = os.path.join(module, 'src/main/resources', old_json)
    if os.path.exists(p):
        new_json = 'libreria.mixins.json' if module == 'core' else 'libreria_' + module + '.mixins.json'
        git('mv', p, os.path.join(module, 'src/main/resources', new_json))
if os.path.isdir('core/src/main/resources/assets/handig'):
    git('mv', 'core/src/main/resources/assets/handig', 'core/src/main/resources/assets/libreria')

# 5. Teksten aanpassen
TEXT_EXT = ('.java', '.json', '.gradle', '.properties', '.yml', '.sh', '.md', '.txt')


def edit(path, module):
    with open(path, encoding='utf-8') as f:
        s = f.read()
    orig = s
    for old, new in sorted(PACKAGES.items(), key=lambda kv: -len(kv[0])):
        s = s.replace(old + '.', new + '.').replace(old + ';', new + ';').replace('"' + old + '"', '"' + new + '"')
        s = s.replace('"package": "' + old, '"package": "' + new)
    for old, new in MOD_IDS.items():
        s = s.replace(old, new)
    for old, new in STORES.items():
        s = s.replace(old, new)
    s = s.replace('handig_gereedschap.mixins.json', 'libreria_tools.mixins.json')
    s = s.replace('"handig.mixins.json"', '"libreria.mixins.json"')
    for m in ('graf', 'verzamelaar', 'naam'):
        s = s.replace('handig_' + DIRS[m] + '.mixins.json', 'libreria_' + DIRS[m] + '.mixins.json')
    s = s.replace('libreria_graves.mixins.json', 'libreria_graves.mixins.json')
    s = s.replace('assets/handig/icon.png', 'assets/libreria/icon.png')
    s = s.replace('HANDIG-SELFTEST', 'LIBRERIA-SELFTEST').replace('handig.selftest', 'libreria.selftest')
    s = s.replace('"handig_naam"', '"libreria_nick"')
    if path.endswith('.java'):
        mapping = dict(CLASSES[None])
        mapping.update(CLASSES.get(module, {}))
        word = re.compile(r'\b(' + '|'.join(sorted(map(re.escape, mapping), key=len, reverse=True)) + r')\b')
        s = '\n'.join(outside_strings(line, lambda seg: word.sub(lambda mm: mapping[mm.group(1)], seg)) for line in s.split('\n'))
        # "Handig" in teksten wordt "Libreria" (de rest vertalen we apart)
        s = s.replace('Handig', 'Libreria')
    if path.endswith('fabric.mod.json'):
        s = s.replace('"handig": ', '"libreria": ').replace('"id": "handig"', '"id": "libreria"').replace('"id": "kistdb"', '"id": "libreria_chests"')
        s = s.replace('${kern_version}', '${core_version}')
    if s != orig:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(s)


for module in list(DIRS.values()) + ['testserver']:
    for dirpath, _, files in os.walk(module):
        if '/build' in dirpath or '/run' in dirpath or '/assets/minecraft' in dirpath or '/assets/handig' in dirpath:
            continue
        for f in files:
            if f.endswith(TEXT_EXT):
                edit(os.path.join(dirpath, f), module)
for f in ('.github/workflows/build.yml', 'build.gradle', 'gradle.properties', 'settings.gradle', 'testserver/build.gradle'):
    edit(f, None)
print('klaar')
