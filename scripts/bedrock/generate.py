"""Libreria for Bedrock players (who join through Geyser): a Bedrock resource pack with the pictures of Libreria's own
items, and Geyser's item mappings that tie each Java item (paper with Libreria's item model, as Polymer sends it) to it.

Bedrock's game can't use the Java pack, so what it draws (windows, models on display entities) Libreria leaves out for
Bedrock players; items in their inventory need these. Libreria puts both files in Geyser's folders when it starts (see
BedrockFiles), Geyser loads them.

Writes core/src/main/resources/libreria/bedrock/{Libreria.mcpack, libreria_items.json}.

    python3 scripts/bedrock/generate.py
"""
import hashlib
import io
import json
import os
import zipfile

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
OUT = os.path.join(ROOT, "core", "src", "main", "resources", "libreria", "bedrock")
ICONS = os.path.join(ROOT, "core", "src", "main", "resources", "assets", "libreria", "textures", "item", "icon")
PACK_ICON = os.path.join(ROOT, "core", "src", "main", "resources", "assets", "libreria", "icon.png")

# Fixed, so Bedrock knows it's the same pack after an update (the version changes with what's in it)
HEADER_UUID = "6f2f8a51-3c5e-4f8e-9a0b-6c2e1d7b4a10"
MODULE_UUID = "0b7e4c2d-91a3-4d6f-8e5b-2a9c3f1e7d64"

# id: (name, Java item the client gets, picture, stack size)
ITEMS = {
    "waystone": ("Waystone", "minecraft:paper", "waystone", 64),
    "dark_waystone": ("Dark Waystone", "minecraft:paper", "dark_waystone", 64),
    "waystone_pearl": ("Waystone Pearl", "minecraft:paper", "waystone_pearl", 16),
    "waystone_heart": ("Waystone Heart", "minecraft:paper", "waystone_heart", 16),
}


def png(path, size=None):
    im = Image.open(path).convert("RGBA")
    if size:
        im = im.resize(size, Image.NEAREST)
    buf = io.BytesIO()
    im.save(buf, "PNG", optimize=False)
    return buf.getvalue()


def main():
    os.makedirs(OUT, exist_ok=True)
    files = {}
    texture_data = {}
    for item_id, (name, java, picture, stack) in ITEMS.items():
        files["textures/items/libreria/%s.png" % item_id] = png(os.path.join(ICONS, picture + ".png"))
        texture_data["libreria_" + item_id] = {"textures": "textures/items/libreria/" + item_id}
    files["textures/item_texture.json"] = json.dumps({"resource_pack_name": "libreria", "texture_name": "atlas.items",
                                                      "texture_data": texture_data}, indent=2).encode()
    files["pack_icon.png"] = png(PACK_ICON, (128, 128))
    # The version follows what's in it (Bedrock keeps packs it has seen by their version)
    digest = hashlib.sha1(b"".join(files[k] for k in sorted(files))).hexdigest()
    version = [1, int(digest[:4], 16) % 1000, int(digest[4:8], 16) % 1000]
    files["manifest.json"] = json.dumps({
        "format_version": 2,
        "header": {"name": "Libreria", "description": "Libreria's items, for Bedrock players on this server",
                   "uuid": HEADER_UUID, "version": version, "min_engine_version": [1, 21, 0]},
        "modules": [{"type": "resources", "uuid": MODULE_UUID, "version": version}],
    }, indent=2).encode()
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        for k in sorted(files):
            info = zipfile.ZipInfo(k, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, files[k])
    with open(os.path.join(OUT, "Libreria.mcpack"), "wb") as f:
        f.write(buf.getvalue())

    items = {}
    for item_id, (name, java, picture, stack) in ITEMS.items():
        items.setdefault(java, []).append({
            "type": "definition",
            "model": "libreria:" + item_id,
            "bedrock_identifier": "libreria:" + item_id,
            "display_name": name,
            "bedrock_options": {"icon": "libreria_" + item_id, "allow_offhand": True, "creative_category": "items"},
            "components": {"minecraft:max_stack_size": stack},
        })
    with open(os.path.join(OUT, "libreria_items.json"), "w") as f:
        json.dump({"format_version": 2, "items": items}, f, indent=2)
        f.write("\n")
    print("Bedrock pack %s (%d files) and mappings for %d items" % (".".join(map(str, version)), len(files), len(ITEMS)))


if __name__ == "__main__":
    main()
