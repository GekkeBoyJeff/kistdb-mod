"""Pose icons for the mannequin window: a little person (side view, facing right) per pose, 16x16, as item models
(libreria:mannequin/pose_<name>). Run from the repository root."""
import json, os
from PIL import Image

OUT = 'info/src/main/resources/assets/libreria'
SKIN, SKIN_D = (226, 172, 132), (186, 132, 98)
HAIR = (92, 62, 40)
SHIRT, SHIRT_D, SHIRT_L = (64, 170, 182), (40, 128, 140), (96, 196, 206)
PANTS, PANTS_D = (64, 76, 150), (44, 52, 112)
SHOE = (70, 70, 78)
GROUND, GROUND_D = (120, 126, 136), (88, 92, 102)
WOOD, WOOD_D, WOOD_L = (176, 132, 82), (128, 92, 54), (204, 162, 108)
BED, BED_D, PILLOW = (186, 56, 56), (140, 36, 40), (236, 236, 236)
EYE = (40, 40, 48)


def canvas():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


def px(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def rect(img, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(img, x, y, c)


def head(img, x, y, face=1):
    """A 4x4 head with its top row hair, an eye on the side it faces (face 1: right, -1: left, 0: up, 2: down)."""
    rect(img, x, y, x + 3, y + 3, SKIN)
    rect(img, x, y, x + 3, y, HAIR)
    if face == 1:
        px(img, x, y + 1, HAIR)
        px(img, x + 3, y + 2, EYE)
        px(img, x, y + 3, SKIN_D)
    elif face == -1:
        px(img, x + 3, y + 1, HAIR)
        px(img, x, y + 2, EYE)


def ground(img, y=15, x0=1, x1=14):
    rect(img, x0, y, x1, y, GROUND)


def stand():
    i = canvas()
    head(i, 6, 1)
    rect(i, 7, 5, 8, 9, SHIRT)      # body
    rect(i, 7, 5, 8, 8, SHIRT_D)    # arm, in front
    rect(i, 7, 9, 8, 9, SKIN)       # hand
    rect(i, 7, 10, 8, 13, PANTS)
    px(i, 7, 13, PANTS_D)
    rect(i, 7, 14, 9, 14, SHOE)
    ground(i)
    return i


def chair(i):
    """A stair as a chair, seen from the side: the high back on the left, the seat at y 10."""
    rect(i, 2, 5, 4, 14, WOOD)
    rect(i, 2, 5, 4, 5, WOOD_L)
    rect(i, 2, 10, 9, 14, WOOD)
    rect(i, 5, 10, 9, 10, WOOD_L)
    rect(i, 2, 14, 9, 14, WOOD_D)
    rect(i, 4, 6, 4, 9, WOOD_D)
    ground(i)


def sit():
    i = canvas()
    chair(i)
    head(i, 5, 1)
    rect(i, 6, 5, 7, 9, SHIRT)
    rect(i, 7, 5, 8, 8, SHIRT_D)    # arm, a little forward
    px(i, 8, 9, SKIN)
    rect(i, 8, 8, 12, 9, PANTS)     # thighs and shins straight out, as Minecraft sits
    rect(i, 8, 9, 12, 9, PANTS_D)
    rect(i, 13, 7, 13, 9, SHOE)
    return i


def lean():
    i = canvas()
    chair(i)
    # bent forward from the hips, elbows on the knees
    for y, x0, x1 in ((9, 6, 7), (8, 6, 8), (7, 7, 8), (6, 7, 9), (5, 8, 9)):
        rect(i, x0, y, x1, y, SHIRT)
    head(i, 8, 1)
    rect(i, 8, 8, 12, 9, PANTS)
    rect(i, 10, 9, 12, 9, PANTS_D)
    rect(i, 13, 7, 13, 9, SHOE)
    for x, y in ((9, 6), (9, 7), (10, 7)):
        px(i, x, y, SHIRT_D)
    rect(i, 10, 8, 11, 8, SHIRT_D)
    px(i, 12, 7, SKIN)
    return i


def crouch():
    i = canvas()
    # legs straight, a little back; the body leans forward
    rect(i, 5, 10, 6, 13, PANTS)
    px(i, 5, 13, PANTS_D)
    rect(i, 5, 14, 7, 14, SHOE)
    rect(i, 6, 8, 7, 9, SHIRT)
    rect(i, 7, 7, 8, 8, SHIRT)
    rect(i, 8, 6, 9, 7, SHIRT)
    head(i, 8, 2)
    rect(i, 8, 7, 9, 10, SHIRT_D)
    rect(i, 8, 11, 9, 11, SKIN)
    ground(i)
    return i


def lie():
    i = canvas()
    # a bed: the blanket, the wooden frame, a pillow on the left
    rect(i, 1, 10, 14, 12, BED)
    rect(i, 1, 12, 14, 12, BED_D)
    rect(i, 1, 13, 14, 13, WOOD)
    px(i, 1, 14, WOOD_D)
    px(i, 14, 14, WOOD_D)
    rect(i, 1, 8, 4, 9, PILLOW)
    # on its back, the head on the pillow, the face up
    rect(i, 1, 4, 4, 7, SKIN)
    rect(i, 1, 4, 1, 7, HAIR)
    px(i, 3, 5, EYE)
    # under the blanket, an arm on top of it, the feet making a bump
    rect(i, 5, 8, 14, 9, BED)
    rect(i, 5, 8, 14, 8, (214, 86, 82))
    rect(i, 13, 7, 14, 7, BED)
    rect(i, 5, 8, 8, 8, SHIRT)
    px(i, 9, 8, SKIN)
    return i


def crawl():
    i = canvas()
    # face down, low over the ground, an arm reaching ahead
    rect(i, 1, 11, 5, 12, PANTS)
    rect(i, 1, 12, 5, 12, PANTS_D)
    rect(i, 0, 11, 0, 12, SHOE)
    rect(i, 6, 10, 10, 12, SHIRT)
    rect(i, 6, 10, 10, 10, SHIRT_L)
    head(i, 11, 8)
    rect(i, 10, 12, 13, 13, SHIRT_D)
    px(i, 14, 13, SKIN)
    ground(i, 14, 0, 15)
    return i


POSES = {'stand': stand, 'sit': sit, 'lean': lean, 'crouch': crouch, 'lie': lie, 'crawl': crawl}


def main():
    tex = os.path.join(OUT, 'textures/item/mannequin')
    mod = os.path.join(OUT, 'models/item/mannequin')
    items = os.path.join(OUT, 'items/mannequin')
    for d in (tex, mod, items):
        os.makedirs(d, exist_ok=True)
    sheet = Image.new('RGBA', (16 * len(POSES) * 8 + 8 * (len(POSES) + 1), 16 * 8 + 16), (34, 38, 46, 255))
    for n, (name, draw) in enumerate(POSES.items()):
        img = draw()
        img.save(os.path.join(tex, 'pose_' + name + '.png'))
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'libreria:item/mannequin/pose_' + name}, 'gui_light': 'front'},
                  open(os.path.join(mod, 'pose_' + name + '.json'), 'w'))
        json.dump({'model': {'type': 'minecraft:model', 'model': 'libreria:item/mannequin/pose_' + name}},
                  open(os.path.join(items, 'pose_' + name + '.json'), 'w'))
        sheet.alpha_composite(img.resize((128, 128), Image.NEAREST), (8 + n * 136, 8))
    os.makedirs('/tmp/mannequin', exist_ok=True)
    sheet.save('/tmp/mannequin/poses.png')


if __name__ == '__main__':
    main()
