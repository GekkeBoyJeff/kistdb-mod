"""CI only: where an option sits in a shader pack's settings screens, as the player sees it (Materials › …)."""
import re
import sys
import zipfile

zip_path, option = sys.argv[1], sys.argv[2]
z = zipfile.ZipFile(zip_path)
props = z.read('shaders/shaders.properties').decode('utf-8', 'replace')
lang = {}
for name in z.namelist():
    if re.search(r'lang/en_us\.lang$', name, re.I):
        for line in z.read(name).decode('utf-8', 'replace').splitlines():
            if '=' in line and not line.startswith('#'):
                k, v = line.split('=', 1)
                lang[k.strip()] = re.sub('§.', '', v.strip())
# "screen = A B [SUB] …", "screen.SUB = …" (lines may continue with a backslash)
props = re.sub(r'\\\s*\n', ' ', props)
screens = {}
for line in props.splitlines():
    m = re.match(r'\s*screen(\.([A-Za-z0-9_]+))?\s*=\s*(.*)', line)
    if m:
        screens[m.group(2) or ''] = m.group(3).split()


def find(screen, path):
    for item in screens.get(screen, []):
        if item == option:
            return path
        m = re.fullmatch(r'\[([A-Za-z0-9_]+)\]', item)
        if m and m.group(1) not in path:
            found = find(m.group(1), path + [m.group(1)])
            if found is not None:
                return found
    return None


path = find('', [])
print(option + ': ' + (' › '.join(lang.get('screen.' + s, s) for s in path) + ' › ' + lang.get('option.' + option, option)
                       if path is not None else 'not found'))
