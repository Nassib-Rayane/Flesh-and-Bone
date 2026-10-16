"""Generates Flesh & Bone's provisional textures (pure standard library: zlib + struct, no Pillow).

Usage: python tools/gen_textures.py   (from the project root)
Replace the generated PNGs with real art whenever you like; this script only writes files that are missing,
unless run with --force.
"""
import math
import os
import struct
import sys
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'fleshbone', 'textures')
FORCE = '--force' in sys.argv


def write_png(path, width, height, pixels):
    """pixels: list of rows, each a list of (r, g, b, a) tuples."""
    if os.path.exists(path) and not FORCE:
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = bytearray()
    for row in pixels:
        raw.append(0)  # filter: none
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(kind, data):
        body = kind + data
        return struct.pack('>I', len(data)) + body + struct.pack('>I', zlib.crc32(body) & 0xFFFFFFFF)

    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(bytes(raw), 9))
    png += chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(png)
    print('wrote', os.path.relpath(path))


def smoothstep(edge0, edge1, x):
    t = max(0.0, min(1.0, (x - edge0) / (edge1 - edge0)))
    return t * t * (3 - 2 * t)


def vignette():
    """White, transparent in the middle, opaque at the edges; tinted when drawn."""
    size = 256
    rows = []
    for y in range(size):
        row = []
        for x in range(size):
            u = (x + 0.5) / size * 2 - 1
            v = (y + 0.5) / size * 2 - 1
            d = math.sqrt(u * u + v * v) / 1.25
            a = smoothstep(0.32, 1.0, d) ** 1.4
            row.append((255, 255, 255, int(a * 255)))
        rows.append(row)
    write_png(os.path.join(ROOT, 'gui', 'vignette.png'), size, size, rows)


# ------------------------------------------------------------------------------------------------ items
# Each sprite: 16 rows of 16 characters; '.' is transparent. One palette shared by all sprites.
PALETTE = {
    'k': (24, 20, 18, 255),      # outline
    'w': (238, 236, 228, 255),   # white cloth
    'W': (255, 255, 255, 255),   # highlight
    'g': (190, 186, 176, 255),   # cloth shade
    'G': (140, 136, 128, 255),   # darker shade / thread
    'b': (166, 140, 110, 255),   # rag beige
    'B': (122, 98, 72, 255),     # rag dark
    'r': (200, 30, 30, 255),     # red
    'R': (130, 16, 16, 255),     # dark red
    'o': (110, 60, 20, 255),     # iodine brown
    'O': (160, 96, 40, 255),     # iodine light
    'c': (60, 60, 64, 255),      # cap / dark strap
    's': (170, 174, 182, 255),   # steel
    'S': (220, 224, 232, 255),   # steel light
    'l': (150, 210, 240, 255),   # ice light blue
    'L': (90, 160, 210, 255),    # ice blue
    'i': (220, 245, 255, 255),   # ice white
    't': (150, 104, 60, 255),    # wood
    'T': (104, 70, 38, 255),     # wood dark
    'e': (60, 130, 200, 255),    # blue label
    'n': (232, 120, 30, 255),    # orange cap
    'q': (200, 236, 255, 160),   # glass
    'p': (240, 220, 120, 230),   # morphine
}

SPRITES = {
    'rag': [
        '................',
        '................',
        '...kkkk.........',
        '..kbbbbkk.......',
        '..kbBbbbbkkk....',
        '.kbbbbbBbbbbk...',
        '.kbbBbbbbbBbbk..',
        '..kbbbbbBbbbbk..',
        '..kkbbBbbbbbBk..',
        '...kbbbbbBbbbbk.',
        '...kbBbbbbbbbk..',
        '....kbbbBbbbk...',
        '.....kkbbbkk....',
        '.......kkk......',
        '................',
        '................',
    ],
    'bandage': [
        '................',
        '................',
        '.....kkkkkk.....',
        '....kwwwwwwk....',
        '...kwWgggggwk...',
        '...kwgkkkkgwk...',
        '...kwgk..kgwk...',
        '...kwgk..kgwk...',
        '...kwgkkkkgwk...',
        '...kwwgggggwkk..',
        '....kwwwwwwkwwk.',
        '.....kkkkkk.kwwk',
        '.............kwk',
        '..............k.',
        '................',
        '................',
    ],
    'sterile_bandage': [
        '................',
        '..kkkkkkkkkkkk..',
        '..keeeeeeeeeek..',
        '..kewwwwwwwwek..',
        '..kewwwrrwwwek..',
        '..kewwwrrwwwek..',
        '..kewrrrrrrwek..',
        '..kewrrrrrrwek..',
        '..kewwwrrwwwek..',
        '..kewwwrrwwwek..',
        '..kewwwwwwwwek..',
        '..keeeeeeeeeek..',
        '..kkkkkkkkkkkk..',
        '................',
        '................',
        '................',
    ],
    'antiseptic': [
        '................',
        '......kkkk......',
        '......kcck......',
        '......kcck......',
        '.....kkkkkk.....',
        '.....kooook.....',
        '....kooOooook...',
        '....kwwwwwwk....',
        '....kwrrrrwk....',
        '....kwwwwwwk....',
        '....koooooOk....',
        '....kooOooook...',
        '....kooooooOk...',
        '.....kkkkkkk....',
        '................',
        '................',
    ],
    'suture_kit': [
        '................',
        '................',
        '..........kk....',
        '.........kSsk...',
        '........ksk.k...',
        '.......ksk......',
        '......ksk.......',
        '.....ksk...kk...',
        '....kSk...kGGk..',
        '....kk...kG..Gk.',
        '.........kG..Gk.',
        '..........kGGk..',
        '...........kk...',
        '................',
        '................',
        '................',
    ],
    'tweezers': [
        '................',
        '..kk......kk....',
        '..kSk....kSk....',
        '...kSk..kSk.....',
        '...kSk..kSk.....',
        '....kSkkSk......',
        '....kSkkSk......',
        '.....kSSk.......',
        '.....ksSk.......',
        '.....kssk.......',
        '.....kssk.......',
        '.....kssk.......',
        '.....kssk.......',
        '......kk........',
        '................',
        '................',
    ],
    'tourniquet': [
        '................',
        '................',
        '....kkkkkkk.....',
        '...kcccccccck...',
        '..kcck....kcck..',
        '..kck......kck..',
        '.kcck......kcck.',
        '.kck..kkkk..kck.',
        '.kck.kssssk.kck.',
        '.kcckcskkscckck.',
        '..kcksk..kskck..',
        '...kkskkkksk....',
        '.....kssssk.....',
        '......kkkk......',
        '................',
        '................',
    ],
    'ice_pack': [
        '................',
        '................',
        '...kkkkkkkkkk...',
        '..klllllllllLk..',
        '..klikkllkkiLk..',
        '..klkiiklkiiLk..',
        '..klkiiklkiiLk..',
        '..kllkklllkkLk..',
        '..klllkkklllLk..',
        '..kllkiiiklllk..',
        '..kllkiiiklLLk..',
        '..klllkkkllLLk..',
        '...kLLLLLLLLk...',
        '....kkkkkkkk....',
        '................',
        '................',
    ],
    'splint': [
        '................',
        '....kk....kk....',
        '....ktk..ktk....',
        '....ktk..ktk....',
        '...kwwwwwwwwk...',
        '....ktk..ktk....',
        '....ktk..ktk....',
        '....kTk..kTk....',
        '....ktk..ktk....',
        '...kwwwwwwwwk...',
        '....ktk..ktk....',
        '....ktk..ktk....',
        '....kTk..kTk....',
        '....kk....kk....',
        '................',
        '................',
    ],
    'painkillers': [
        '................',
        '.....kkkkkk.....',
        '.....knnnnk.....',
        '.....kkkkkk.....',
        '....kwwwwwwk....',
        '....kWwwwwwk....',
        '....keeeeeek....',
        '....kewwwwek....',
        '....kewrrwek....',
        '....keeeeeek....',
        '....kwwwwwwk....',
        '....kWwwwwgk....',
        '....kwwwwggk....',
        '.....kkkkkk.....',
        '................',
        '................',
    ],
    'morphine': [
        '................',
        '.............kk.',
        '............kRk.',
        '...........krRk.',
        '..........kqqk..',
        '.........kpqk...',
        '........kppk....',
        '.......kppk.....',
        '......kppk......',
        '.....kqpk.......',
        '....kkkk........',
        '...ksk..........',
        '..ksk...........',
        '..kk............',
        '................',
        '................',
    ],
    'empty_syringe': [
        '................',
        '.............kk.',
        '............kck.',
        '...........kcck.',
        '..........kqqk..',
        '.........kqqk...',
        '........kqqk....',
        '.......kqqk.....',
        '......kqqk......',
        '.....kqqk.......',
        '....kkkk........',
        '...ksk..........',
        '..ksk...........',
        '..kk............',
        '................',
        '................',
    ],
    'antibiotics': [
        '................',
        '.....kkkkkk.....',
        '.....kcccck.....',
        '.....kkkkkk.....',
        '....kwwwwwwk....',
        '....kWwwwwwk....',
        '....kkkkkkkk....',
        '....kGgGgGgk....',
        '....kgGgGgGk....',
        '....kkkkkkkk....',
        '....kwwwwwwk....',
        '....kWwwwwgk....',
        '....kwwwwggk....',
        '.....kkkkkk.....',
        '................',
        '................',
    ],
    'bone_saw': [
        '................',
        '................',
        '.kkkkkkkkkkk....',
        '.kSSSSSSSSSsk...',
        '.ksssssssssssk..',
        '.kskskskskskkTk.',
        '..k.k.k.k.k.kTTk',
        '............kTtk',
        '............kTtk',
        '............kTk.',
        '.............k..',
        '................',
        '................',
        '................',
        '................',
        '................',
    ],
}


SPRITES.update({
    'defibrillator': [
        '................',
        '..kkkkkkkkkkkk..',
        '..knnnnnnnnnnk..',
        '..knkkkkkkkknk..',
        '..knkyyyyyyknk..',
        '..knkyykyyyknk..',
        '..knkykkkyyknk..',
        '..knkyyykkyknk..',
        '..knkyyyykyknk..',
        '..knkkkkkkkknk..',
        '..knnnnnnnnnnk..',
        '..kkkkkkkkkkkk..',
        '...kck....kck...',
        '..kcck....kcck..',
        '..kkk......kkk..',
        '................',
    ],
    'empty_blood_bag': [
        '................',
        '.......kk.......',
        '......kssk......',
        '....kkkkkkkk....',
        '...kqqqqqqqqk...',
        '...kqWqqqqqqk...',
        '...kqqqqqqqqk...',
        '...kqqqqqqqqk...',
        '...kqqqqqqqqk...',
        '...kqqqqqqqqk...',
        '...kqqqqqqqqk...',
        '....kqqqqqqk....',
        '.....kkkkkk.....',
        '.......kk.......',
        '.......kk.......',
        '................',
    ],
    'blood_bag': [
        '................',
        '.......kk.......',
        '......kssk......',
        '....kkkkkkkk....',
        '...kqqqqqqqqk...',
        '...kqWwwwwwqk...',
        '...kqwrRrrwqk...',
        '...krrrrrrrrk...',
        '...krRrrrrRrk...',
        '...krrrrrrrrk...',
        '...krrrRrrrrk...',
        '....krrrrrrk....',
        '.....kkkkkk.....',
        '.......kR.......',
        '.......kR.......',
        '................',
    ],
    'saline_bag': [
        '................',
        '.......kk.......',
        '......kssk......',
        '....kkkkkkkk....',
        '...kqqqqqqqqk...',
        '...kqWeeeeqqk...',
        '...kqewwwweqk...',
        '...kqeeeeeeqk...',
        '...klllllllllk..',
        '...klllllllllk..',
        '...kllllllllk...',
        '....kllllllk....',
        '.....kkkkkk.....',
        '.......kk.......',
        '.......kk.......',
        '................',
    ],
    'chest_drain': [
        '................',
        '..kk............',
        '..ksk...........',
        '...ksk..........',
        '....kGk.........',
        '.....kGk........',
        '......kGkk......',
        '.......kGGk.....',
        '........kkGk....',
        '.......kkkkkkk..',
        '.......kqqqqqk..',
        '.......kqqqqqk..',
        '.......krrrrrk..',
        '.......kRrrrRk..',
        '........kkkkk...',
        '................',
    ],
    'vitals_monitor': [
        '................',
        '.kkkkkkkkkkkkkk.',
        '.kccccccccccccck',
        '.kcxxxxxxxxxxxck',
        '.kcxxxxxxzxxxxck',
        '.kcxxxxxzxzxxxck',
        '.kczzzzzxxxzzzck',
        '.kcxxxxxxxxxxxck',
        '.kcxxxxxxxxxxxck',
        '.kccccccccccccck',
        '.kcrcecccccccsck',
        '.kccccccccccccck',
        '.kkkkkkkkkkkkkkk',
        '....kk....kk....',
        '................',
        '................',
    ],
})
SPRITES.update({
    'canteen': [
        '................',
        '......kkkk......',
        '......kcck......',
        '.....kkkkkk.....',
        '....kBbbbbBk....',
        '...kBbbbbbbBk...',
        '...kbsssssssk...',
        '...kbbbbbbbbk...',
        '...kbBbbbbBbk...',
        '...kbbbbbbbbk...',
        '...kbsssssssk...',
        '...kBbbbbbbBk...',
        '....kBbbbbBk....',
        '.....kkkkkk.....',
        '................',
        '................',
    ],
    'purified_water': [
        '................',
        '.......kk.......',
        '......kTTk......',
        '......kqqk......',
        '......kqqk......',
        '.....kqqqqk.....',
        '....kqWqqqqk....',
        '....klllllLk....',
        '....klillllk....',
        '....kllllLLk....',
        '....klllLLLk....',
        '.....kLLLLk.....',
        '......kkkk......',
        '................',
        '................',
        '................',
    ],
    'coffee': [
        '................',
        '.......kk.......',
        '......kTTk......',
        '......kqqk......',
        '......kqqk......',
        '.....kqqqqk.....',
        '....kqWqqqqk....',
        '....kTTTTTTk....',
        '....kToTTTTk....',
        '....kTTTTook....',
        '....kTTToook....',
        '.....kooook.....',
        '......kkkk......',
        '................',
        '................',
        '................',
    ],
})
PALETTE.update({
    'y': (240, 210, 60, 255),     # defibrillator screen
    'x': (12, 30, 14, 255),       # monitor screen
    'z': (80, 255, 110, 255),     # ECG trace
})

# Auto-injectors: the morphine sprite with another liquid and cap colour
SYRINGES = {
    'epinephrine': ((255, 160, 50, 230), (230, 120, 30, 255)),
    'antiarrhythmic': ((120, 220, 120, 230), (40, 140, 60, 255)),
    'naloxone': ((110, 170, 255, 230), (40, 90, 200, 255)),
    'procoagulant': ((150, 20, 20, 240), (90, 10, 10, 255)),
    'thrombolytic': ((190, 110, 230, 230), (120, 50, 160, 255)),
    'antivenom': ((235, 225, 170, 230), (150, 120, 50, 255)),
}


def items():
    for name, rows in SPRITES.items():
        assert len(rows) == 16 and all(len(r) == 16 for r in rows), name
        pixels = [[PALETTE.get(ch, (0, 0, 0, 0)) for ch in row] for row in rows]
        write_png(os.path.join(ROOT, 'item', name + '.png'), 16, 16, pixels)
    for name, (liquid, cap) in SYRINGES.items():
        palette = dict(PALETTE, p=liquid, r=cap, R=tuple(max(0, c - 50) if i < 3 else c for i, c in enumerate(cap)))
        pixels = [[palette.get(ch, (0, 0, 0, 0)) for ch in row] for row in SPRITES['morphine']]
        write_png(os.path.join(ROOT, 'item', name + '.png'), 16, 16, pixels)


def cabinet():
    """Medicine cabinet: white doors with a red cross, simple panelling."""
    white, shade, frame, red, handle = (232, 232, 226, 255), (204, 204, 198, 255), (120, 112, 100, 255), (200, 30, 30, 255), (90, 90, 90, 255)
    def face(kind):
        rows = []
        for y in range(16):
            row = []
            for x in range(16):
                c = white
                if x in (0, 15) or y in (0, 15):
                    c = frame
                elif kind == 'front':
                    if x == 8:
                        c = shade
                    if 5 <= x <= 10 and 7 <= y <= 8 or 7 <= x <= 8 and 5 <= y <= 10:
                        c = red
                    if y == 9 and x in (6, 9) and False:
                        c = handle
                    if (x, y) in ((7, 12), (9, 12)):
                        c = handle
                elif kind == 'side':
                    if y in (5, 10):
                        c = shade
                else:
                    if (x + y) % 7 == 0:
                        c = shade
                row.append(c)
            rows.append(row)
        return rows
    for kind in ('front', 'side', 'top'):
        write_png(os.path.join(ROOT, 'block', 'medicine_cabinet_' + kind + '.png'), 16, 16, face(kind))


def doctor_overlay():
    """Villager profession overlay: a white coat with a red cross (villager UV layout)."""
    coat, edge, red = (238, 238, 232, 255), (196, 196, 190, 255), (200, 30, 30, 255)
    rows = [[(0, 0, 0, 0)] * 64 for _ in range(64)]
    def fill(x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                rows[y][x] = c
    fill(16, 20, 40, 38, coat)   # body
    fill(0, 38, 28, 64, coat)    # robe
    fill(40, 38, 56, 46, coat)   # crossed arms
    fill(44, 22, 60, 30, coat)   # upper arms
    for x in range(0, 28):
        rows[63][x] = edge
    fill(9, 46, 11, 52, red)     # cross on the robe front
    fill(7, 48, 13, 50, red)
    fill(25, 28, 27, 34, red)    # and on the chest
    fill(23, 30, 29, 32, red)
    for folder in ('villager', 'zombie_villager'):
        write_png(os.path.join(ROOT, 'entity', folder, 'profession', 'doctor.png'), 64, 64, rows)


if __name__ == '__main__':
    vignette()
    items()
    cabinet()
    doctor_overlay()
