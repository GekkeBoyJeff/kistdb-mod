"""The grand waystone's heart: pixel art drawn here, grey (the dye gives the colour), one texture pixel per model pixel.

crystal(): a long crystal of many facets, lit from the upper left: a rim of light down its left side, a bright ridge,
facets split by lighter lines, darker to the right, a dark outline and a few glints. Shown on crossed planes, turning.
shard(): a small one, for the shards that circle round it.
"""
from PIL import Image, ImageDraw


def crystal(w=15, h=38, shoulder=10, waist=27):
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cx = w // 2
    L, R, top, bot = 0, w - 1, 0, h - 1
    q = (cx - L) // 2          # where the inner facet lines run
    # the silhouette, then the facets on it (lightest upper left, darkest lower right)
    d.polygon([(cx, top), (R, shoulder), (R, waist), (cx, bot), (L, waist), (L, shoulder)], fill=(160, 160, 160, 255))
    shades = [
        ([(cx, top), (L, shoulder), (L + q, shoulder + 1), (cx, shoulder + 2)], 236),
        ([(cx, top), (cx, shoulder + 2), (R - q, shoulder + 1), (R, shoulder)], 196),
        ([(L, shoulder), (L + q, shoulder + 1), (L + q, waist + 1), (L, waist)], 222),
        ([(L + q, shoulder + 1), (cx, shoulder + 2), (cx, waist + 2), (L + q, waist + 1)], 204),
        ([(cx, shoulder + 2), (R - q, shoulder + 1), (R - q, waist + 1), (cx, waist + 2)], 168),
        ([(R - q, shoulder + 1), (R, shoulder), (R, waist), (R - q, waist + 1)], 142),
        ([(L, waist), (L + q, waist + 1), (cx, waist + 2), (cx, bot)], 178),
        ([(cx, waist + 2), (R - q, waist + 1), (R, waist), (cx, bot)], 122),
    ]
    for pts, g in shades:
        d.polygon(pts, fill=(g, g, g, 255))
    px = im.load()
    # lighter towards the top of each facet, a little
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a:
                k = int(12 - 24 * y / h)
                px[x, y] = (max(0, min(255, r + k)),) * 3 + (255,)
    # the facet lines: light, thin
    for y in range(shoulder + 2, waist + 1):
        for x, v in ((L + q, 240), (R - q, 186)):
            px[x, y] = (v, v, v, 255)
    # the ridge down the middle, brightest at the top
    for y in range(1, h - 1):
        v = 255 if y < h * 0.4 else 238 if y < h * 0.7 else 214
        px[cx, y] = (v, v, v, 255)
    # outline: darker along the silhouette, the left upper edge lit instead (the rim)
    src = im.copy().load()
    for y in range(h):
        for x in range(w):
            if not src[x, y][3]:
                continue
            out = [(dx, dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
                   if not (0 <= x + dx < w and 0 <= y + dy < h) or not src[x + dx, y + dy][3]]
            if out:
                lit = x < cx and y < waist and any(dx == -1 or dy == -1 for dx, dy in out)
                v = 250 if lit else max(48, src[x, y][0] - 84)
                px[x, y] = (v, v, v, 255)
    # glints
    for g in ((L + 2, shoulder + 3), (L + 2, shoulder + 4), (cx - 1, 3), (R - 3, waist - 2)):
        px[g] = (255, 255, 255, 255)
    return im


def shard():
    return crystal(7, 15, 4, 10)


def shard_small():
    """The little ones on the roof's corners."""
    return crystal(5, 11, 3, 7)


if __name__ == "__main__":
    import sys
    out = sys.argv[1] if len(sys.argv) > 1 else "/home/claude/mock/grand/tex"
    crystal().save(out + "/heart_crystal.png")
    shard().save(out + "/heart_shard.png")
