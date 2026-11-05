"""Builds the effects mock (one HTML page) from effects.json and the vanilla particle textures.

python3 build.py <path to an extracted minecraft assets folder (assets/minecraft)>
The particle textures are Mojang's: they are read from your own copy and never committed.
"""
import base64, json, os, sys

here = os.path.dirname(os.path.abspath(__file__))
assets = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser('~/ref/assets/assets/minecraft')
tex = os.path.join(assets, 'textures', 'particle')
names = sorted(f[:-4] for f in os.listdir(tex) if f.endswith('.png'))
spr = {n: 'data:image/png;base64,' + base64.b64encode(open(os.path.join(tex, n + '.png'), 'rb').read()).decode() for n in names}
spec = json.load(open(os.path.join(here, '..', '..', 'glint', 'src', 'main', 'resources', 'glint', 'effects.json')))
s = open(os.path.join(here, 'shell.html')).read()
s = s.replace('/*SPEC*/', 'const SPEC=' + json.dumps(spec) + ';').replace('/*SPR*/', 'const SPR=' + json.dumps(spr) + ';')
s = s.replace('/*APP*/', open(os.path.join(here, 'app.js')).read()).replace('/*UI*/', open(os.path.join(here, 'ui.js')).read())
out = os.path.join(here, 'effects-mock.html')
open(out, 'w').write(s)
print('wrote', out, len(s))
