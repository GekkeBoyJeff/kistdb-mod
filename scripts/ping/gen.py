"""Draws the ping glyphs (font libreria:ping) for the waypoints module.

E000 diamond (the Diamond look): a hollow gold diamond, dark see-through inside, a dot in the middle
E001 gem (the Pillar look): a faceted gold gem
E002 pillar: a soft column of light, fading upwards
E003 line: the thin line from the ground up to the diamond
E004 ring: the ring lying on the ground (Diamond look)
E005 pulse: a thin ring that spreads out (Diamond look)

Run: python3 scripts/ping/gen.py
"""
import json
import math
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "waypoints", "src", "main", "resources", "assets", "libreria")
TEX = os.path.join(OUT, "textures", "font", "ping")

GOLD = (0xFF, 0xD9, 0x66)
LIGHT = (0xFF, 0xF0, 0xB3)
SHADE = (0xC9, 0x9E, 0x38)
DEEP = (0xA8, 0x82, 0x2A)
EDGE = (0x3A, 0x2E, 0x08)
FILL = (0x1E, 0x22, 0x30)


def diamond_hollow():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    c = 15.5
    for y in range(32):
        for x in range(32):
            dx, dy = x - c, y - c
            m = abs(dx) + abs(dy)
            if m > 15:
                continue
            if m == 15 or m == 11 or m == 3:
                col = (*EDGE, 255)
            elif m >= 12:
                # the band: lit from the top left, shaded to the bottom right
                if dy < 0 and dx < 0:
                    col = (*LIGHT, 255) if m == 14 else (*GOLD, 255)
                elif dy > 0 and dx > 0:
                    col = (*SHADE, 255) if m == 12 else (*GOLD, 255)
                else:
                    col = (*GOLD, 255)
            elif m <= 2:
                col = (*LIGHT, 255) if (dx < 0 and dy < 0) else (*GOLD, 255)
            else:
                col = (*FILL, 150)
            img.putpixel((x, y), col)
    return img


def gem():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    c = 15.5
    for y in range(32):
        for x in range(32):
            dx, dy = x - c, y - c
            m = abs(dx) + abs(dy)
            if m > 15:
                continue
            if m == 15:
                col = EDGE
            elif m <= 6:
                # the table of the gem
                col = LIGHT if (dx < 0 and dy < 0 and m >= 5) else (0xFF, 0xE6, 0x8F)
            elif m == 7:
                col = SHADE
            elif dy < 0 and dx < 0:
                col = LIGHT
            elif dy < 0:
                col = GOLD
            elif dx < 0:
                col = SHADE
            else:
                col = DEEP
            img.putpixel((x, y), (*col, 255))
    # a glint
    for p in ((10, 9), (9, 10), (10, 10), (11, 10), (10, 11)):
        img.putpixel(p, (255, 255, 240, 255))
    return img


def pillar():
    w, h = 16, 64
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    cx = (w - 1) / 2
    for y in range(h):
        up = (h - 1 - y) / (h - 1)  # 0 at the ground, 1 at the top
        fade = (1 - up) ** 1.4
        for x in range(w):
            d = abs(x - cx)
            if d <= 1.5:
                col, a = (0xFF, 0xF8, 0xDC), 235
            elif d <= 3.5:
                col, a = (0xFF, 0xE8, 0x96), 170
            elif d <= 5.5:
                col, a = GOLD, 105
            else:
                col, a = GOLD, 55
            a = int(a * fade)
            if a >= 28:
                img.putpixel((x, y), (*col, a))
    return img


def line():
    w, h = 4, 64
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for y in range(h):
        fade = 0.55 + 0.45 * (1 - y / (h - 1))  # a little lighter towards the ground
        for x in range(w):
            if x in (1, 2):
                img.putpixel((x, y), (0xFF, 0xEA, 0x9E, int(240 * fade)))
            else:
                img.putpixel((x, y), (*GOLD, int(90 * fade)))
    return img


def ring(inner, outer, fill):
    n, ss = 64, 4
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    c = (n - 1) / 2
    for y in range(n):
        for x in range(n):
            hit = 0
            for sy in range(ss):
                for sx in range(ss):
                    r = math.hypot(x - c + (sx + 0.5) / ss - 0.5, y - c + (sy + 0.5) / ss - 0.5)
                    if inner <= r <= outer:
                        hit += 1
            cover = hit / (ss * ss)
            r = math.hypot(x - c, y - c)
            if cover > 0:
                a = int(255 * cover)
                if a >= 28:
                    img.putpixel((x, y), (*GOLD, a))
            elif fill and r < inner:
                img.putpixel((x, y), (*GOLD, 44))
    return img


def main():
    os.makedirs(TEX, exist_ok=True)
    glyphs = [
        ("diamond", diamond_hollow(), 32, 14),
        ("gem", gem(), 32, 14),
        ("pillar", pillar(), 80, 78),
        ("line", line(), 56, 54),
        ("ring", ring(26.5, 30.5, True), 40, 18),
        ("pulse", ring(29.0, 31.0, False), 40, 18),
    ]
    providers = []
    for i, (name, img, height, ascent) in enumerate(glyphs):
        img.save(os.path.join(TEX, name + ".png"))
        providers.append({"type": "bitmap", "file": "libreria:font/ping/" + name + ".png", "height": height, "ascent": ascent,
                          "chars": [chr(0xE000 + i)]})
    os.makedirs(os.path.join(OUT, "font"), exist_ok=True)
    with open(os.path.join(OUT, "font", "ping.json"), "w") as f:
        json.dump({"providers": providers}, f, indent=1)


if __name__ == "__main__":
    main()
