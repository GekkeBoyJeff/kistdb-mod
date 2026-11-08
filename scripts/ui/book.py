"""The book screen in Libreria's look (minecraft:textures/gui/book.png): a walnut leather cover with a bronze rim and
gold corners, aged parchment pages with a soft darker edge, and a small gold diamond in the bottom margin. Every book
uses this screen (the lore book, written books, the book and quill): Minecraft has one picture for all of them.

The page keeps the vanilla size and place, so text, page numbers and the arrows land where they always do.

    python3 scripts/ui/book.py [out_dir]
"""
import os
import random
import sys

from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '../../core/src/main/resources')

# The book's box in the 256 x 256 texture (as vanilla: x 20-165, y 1-181)
X0, Y0, X1, Y1 = 20, 1, 165, 181
LEATHER = (72, 42, 24)
LEATHER_DARK = (46, 26, 14)
BRONZE = (176, 126, 58)
BRONZE_DARK = (120, 82, 36)
GOLD = (255, 203, 82)
GOLD_LIGHT = (255, 232, 150)
PAPER = (247, 238, 214)
PAPER_EDGE = (226, 208, 170)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def book():
    im = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    px = im.load()
    rnd = random.Random(11)
    w, h = X1 - X0 + 1, Y1 - Y0 + 1
    # The cover: leather, darker towards its edge, rounded corners
    for y in range(Y0, Y1 + 1):
        for x in range(X0, X1 + 1):
            dx, dy = min(x - X0, X1 - x), min(y - Y0, Y1 - y)
            if dx + dy < 3:
                continue
            edge = min(dx, dy)
            c = LEATHER_DARK if edge == 0 else lerp(LEATHER_DARK, LEATHER, min(1, edge / 4))
            n = rnd.choice((0, 0, 0, -4, 3))
            px[x, y] = tuple(max(0, min(255, v + n)) for v in c) + (255,)
    # A bronze rim just inside the edge
    for x in range(X0 + 2, X1 - 1):
        for y in (Y0 + 2, Y1 - 2):
            px[x, y] = BRONZE + (255,) if y == Y0 + 2 else BRONZE_DARK + (255,)
    for y in range(Y0 + 2, Y1 - 1):
        px[X0 + 2, y] = BRONZE + (255,)
        px[X1 - 2, y] = BRONZE_DARK + (255,)
    # The page (where vanilla has it), parchment with a soft darker edge and a few fibres
    PX0, PY0, PX1, PY1 = 29, 6, 160, 176
    for y in range(PY0, PY1 + 1):
        for x in range(PX0, PX1 + 1):
            d = min(x - PX0, PX1 - x, y - PY0, PY1 - y)
            t = max(0.0, 1 - d / 7)
            c = lerp(PAPER, PAPER_EDGE, t * t)
            n = rnd.choice((0, 0, 0, 0, 0, -3, 2, -2))
            px[x, y] = tuple(max(0, min(255, v + n)) for v in c) + (255,)
    # The page's own thin edge, and its shadow on the cover
    for x in range(PX0, PX1 + 1):
        px[x, PY0] = (210, 190, 150, 255)
        px[x, PY1] = (190, 168, 128, 255)
        px[x, PY1 + 1] = LEATHER_DARK + (255,)
    for y in range(PY0, PY1 + 1):
        px[PX0, y] = (214, 194, 156, 255)
        px[PX1, y] = (190, 168, 128, 255)
        px[PX1 + 1, y] = LEATHER_DARK + (255,)
    # The spine on the left: bronze rivets instead of stitches
    for i in range(13):
        y = 12 + i * 13
        for (dx, dy, c) in ((0, 0, GOLD_LIGHT), (1, 0, GOLD), (0, 1, GOLD), (1, 1, BRONZE_DARK)):
            px[24 + dx, y + dy] = c + (255,)
    # Gold corners on the cover
    for (cx, cy, sx, sy) in ((X0 + 3, Y0 + 3, 1, 1), (X1 - 3, Y0 + 3, -1, 1), (X0 + 3, Y1 - 3, 1, -1), (X1 - 3, Y1 - 3, -1, -1)):
        for k in range(5):
            px[cx + sx * k, cy] = GOLD + (255,)
            px[cx, cy + sy * k] = GOLD + (255,)
        px[cx + sx, cy + sy] = GOLD_LIGHT + (255,)
    # A small gold diamond in the bottom margin, under the text
    mx, my = (PX0 + PX1) // 2, 167
    for dy in range(-3, 4):
        for dx in range(-3, 4):
            if abs(dx) + abs(dy) <= 3:
                c = GOLD_LIGHT if dx <= 0 and dy <= 0 and abs(dx) + abs(dy) <= 2 else GOLD if abs(dx) + abs(dy) <= 2 else BRONZE
                px[mx + dx, my + dy] = c + (255,)
    for dx in range(6, 22):
        a = int(200 * (1 - (dx - 6) / 16))
        for sx in (-1, 1):
            px[mx + sx * dx, my] = BRONZE + (a,)
    return im


if __name__ == '__main__':
    path = os.path.join(OUT, 'assets/minecraft/textures/gui/book.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    book().save(path)
    print(path)
