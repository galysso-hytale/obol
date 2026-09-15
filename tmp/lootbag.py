#!/usr/bin/env python3
"""
Generateur de l'item lootbag de l'add-on Obol Lootbag : le visuel seulement
(modele, textures, icones, planche d'apercu). Les JSON d'items et la langue
viendront avec la mecanique.

Cinq sacs, un par rarete vanilla (Common, Uncommon, Rare, Epic, Legendary).
Un sac de jute ferme, dans la silhouette des sacs vanilla (farine,
feedbag) : un peu plus large en haut qu'en bas, col serre par une corde
nouee en boucle, une collerette au-dessus. Devant, brodee a meme la toile,
l'etoile a huit branches d'Obol (celle de l'icone du mod) : la signature,
pas une piece. La rarete du sac ne dit rien de la monnaie qu'il contient
(c'est la config du serveur), donc aucun metal de piece sur le sac. Deux
regles a apprendre :

  1. La couleur dit la rarete : corde, noeud et fil de l'etoile, a la
     couleur du cadre de case vanilla. Common est l'absence de couleur
     (chanvre), comme son cadre bleu nuit.
  2. Le sac se remplit : affaisse (Common), plein (Uncommon, Rare), tendu
     (Epic, Legendary). L'icone garde l'echelle.
  Et de pres, des ferrures : argent sur Epic, or sur Legendary, plus un
  eclat sur l'etoile du Legendary. La toile reste la meme jute chaude.

Rien de commun avec la bourse (tmp/purse.py) : jute beige et non cuir
lie-de-vin, trapeze et non huit pans, tete courte et non fronce en etoile,
boucle de corde et non glands, couleur de rarete et non couleur de piece.

Produit dans addons/lootbag/src/main/resources/ :
  Common/Items/Obol/Lootbag_<Slack|Full|Tight>.blockymodel
  Common/Items/Obol/Lootbag_<Rarete>_Texture.png
  Common/Icons/ItemsGenerated/Obol_Lootbag_<Rarete>.png

et dans tmp/ :
  _lootbag_apercu.png   cinq icones x3, les cinq a 24 px sur une case, la bourse
                        d'or a cote pour comparer, texture Legendary x3, trois
                        vues du modele tendu.

Importe purse.py (rendu d'icone, modele, bruit). Pillow seulement. Deterministe.
"""

import json
import math
import os
import random

from PIL import Image

import purse
from purse import (Box, FACES, face_dims, model_json, render_icon, value_noise,
                   lerp, q_axis, zoom)

ROOT = purse.ROOT
RES = os.path.join(ROOT, "addons", "lootbag", "src", "main", "resources")
MODEL_DIR = os.path.join(RES, "Common", "Items", "Obol")
ICON_DIR = os.path.join(RES, "Common", "Icons", "ItemsGenerated")
TMP = purse.TMP

TEX_SIZE = 64
ICON_ROTATION = (22.5, 30.0, 10.0)   # comme la bourse : l'ecusson de face

random.seed(0x10B4)

SILVER = (200, 206, 216)
GOLD = (236, 192, 84)
HEMP = "#8a6a3c"    # corde et fil de chanvre : le Common, sans couleur

# Rarete -> (couleur, remplissage, ferrures). Couleurs pipettees sur les
# cadres de case vanilla (Slot<Rarete>@2x.png) : le TextColor de Uncommon
# (#3e9049) est plus terne que le coin vert de son cadre.
RARITIES = {
    "Common":    (HEMP, "Slack", None),
    "Uncommon":  ("#2c9550", "Full", None),
    "Rare":      ("#2770b7", "Full", None),
    "Epic":      ("#8b339e", "Tight", SILVER),
    "Legendary": ("#f4b91e", "Tight", GOLD),   # plus clair que #bb8a2c : ocre sur jute, invisible
}
LEVEL = {name: i for i, name in enumerate(RARITIES)}
FILL = {"Slack": 0.0, "Full": 0.5, "Tight": 1.0}


def hex_rgb(text):
    return tuple(int(text[i:i + 2], 16) for i in (1, 3, 5))


# =============================================================================
# Le modele : un sac de jute ferme, dans la silhouette des sacs vanilla
# =============================================================================

def lootbag_boxes(fill):
    """Le sac, comme le sac de farine vanilla : une seule boite pour le
    corps, la rondeur et les plis sont peints. Une epaule qui rentre, un col
    fronce serre par la corde, une collerette au-dessus. `fill` de 0
    (affaisse : bas, un peu plus large) a 1 (tendu : haut, le col force sur
    la corde)."""
    f = fill
    bw, bh, bd = 13.5 + 1.0 * f, 10.5 + 2.5 * f, 11.5 + 1.0 * f
    sy = bh + 1.0
    ny = sy + 2.2
    zf = (6.5 + f) / 2
    return [
        Box("Body", (bw, bh, bd), (0, bh / 2, 0), "cloth"),
        Box("Shoulder", (bw - 3, 2.2, bd - 3), (0, sy, 0), "cloth"),
        Box("Neck", (6 + f, 2.8, 5 + f), (0, ny, 0), "folds"),
        Box("Rope", (7.5 + f, 1.8, 6.5 + f), (0, ny + 0.1, 0), "rope"),
        Box("Knot", (2.6, 2.2, 1.6), (0, ny + 0.1, zf + 0.6), "knot"),
        Box("Loop_L", (3.4, 1.4, 1.2), (-2.7, ny - 0.1, zf + 0.65), "rope",
            orientation=q_axis((0, 0, 1), 16)),
        Box("Loop_R", (3.4, 1.4, 1.2), (2.7, ny - 0.1, zf + 0.65), "rope",
            orientation=q_axis((0, 0, 1), -16)),
        Box("Frill", (7.5 + f, 1.8, 6.5 + f), (0.2, ny + 1.9, 0), "folds",
            orientation=q_axis((0, 0, 1), -6)),
    ]


def pack_uv(boxes):
    """Comme purse.pack_uv, mais le devant du ventre a sa propre region (il
    porte l'ecusson) et la corde partage la sienne entre ses boites."""
    wanted = []
    for box in boxes:
        for face in FACES:
            w, h = (math.ceil(v) for v in face_dims(box.size, face))
            side = {"front": "fb", "back": "fb", "right": "lr", "left": "lr",
                    "top": "tb", "bottom": "tb"}[face]
            if box.name == "Body" and side == "fb":
                side = face
            owner = box.material if box.material == "rope" else id(box)
            wanted.append((h, w, box, face, (owner, w, h, side)))
    wanted.sort(key=lambda t: (-t[0], -t[1]))
    shelves = []

    def alloc(w, h):
        for sh in shelves:
            if sh[1] >= h and sh[2] + w <= TEX_SIZE:
                x = sh[2]
                sh[2] += w
                return x, sh[0]
        y = sum(sh[1] for sh in shelves)
        if y + h > TEX_SIZE:
            raise RuntimeError("texture %dx%d trop petite" % (TEX_SIZE, TEX_SIZE))
        shelves.append([y, h, w])
        return 0, y

    placed = {}
    for h, w, box, face, key in wanted:
        if key not in placed:
            placed[key] = alloc(w, h) + (w, h)
        box.uv[face] = placed[key]


# =============================================================================
# La texture
# =============================================================================

# Jute chaude, la meme pour les cinq : la toile ne dit rien de la rarete.
JUTE = {"light": (224, 190, 124), "base": (190, 146, 82), "dark": (134, 96, 48),
        "seam": (92, 68, 40)}


def rope_palette(color):
    return {"light": lerp(color, (255, 255, 255), 0.4), "base": color,
            "dark": lerp(color, (0, 0, 0), 0.35)}


def fold_profile(w, seed, lit=0.36):
    """L'eclairage d'une face de toile, colonne par colonne : des plis
    verticaux de 2 a 4 px, une colonne lumineuse a `lit` de la largeur,
    les bords dans l'ombre. Valeur -1 (ombre) a 1 (lumiere)."""
    rnd = random.Random(seed)
    prof = [0.0] * w
    x = 0
    while x < w:
        width = rnd.choice((2, 3, 3, 4))
        level = rnd.uniform(-0.6, 0.6)
        for i in range(x, min(w, x + width)):
            prof[i] = level
        x += width
    for x in range(w):
        t = x / max(1, w - 1)
        shape = 1.0 - min(1.0, abs(t - lit) / 0.55) ** 1.5      # la colonne lumineuse
        edge = -1.4 * max(0.0, (abs(t - 0.5) - 0.32) / 0.18) ** 2  # les bords
        prof[x] = prof[x] * 0.55 + shape * 0.9 + edge - 0.35
    return prof


def paint_cloth(px, rect, seed, face, pal=JUTE):
    """Jute, comme le sac de farine vanilla : des plis verticaux (colonnes
    claires et sombres), une colonne lumineuse decalee, les bords dans
    l'ombre, un peu plus sombre au ras du sol et sous l'epaule. Trame a un
    pixel par-dessus. Le dessus et le dessous sont des aplats sombres."""
    x0, y0, w, h = rect
    rnd = random.Random(seed + 1)
    if face in ("top", "bottom"):
        noise = value_noise(w, h, 3, seed)
        for y in range(h):
            for x in range(w):
                c = lerp(pal["base"], pal["dark"], 0.45 + 0.3 * noise[y][x])
                if (x + y) % 2:
                    c = lerp(c, pal["dark"], 0.1)
                px[x0 + x, y0 + y] = c + (255,)
        return
    prof = fold_profile(w, seed, lit=0.36 if face in ("front", "right") else 0.6)
    for y in range(h):
        ty = y / max(1, h - 1)
        vert = -0.35 * max(0.0, (ty - 0.8) / 0.2) - 0.2 * max(0.0, (0.12 - ty) / 0.12)
        for x in range(w):
            v = prof[x] + vert
            if v >= 0:
                c = lerp(pal["base"], pal["light"], min(1.0, v))
            else:
                c = lerp(pal["base"], pal["dark"], min(1.0, -v))
            if (x + y) % 2:
                c = lerp(c, pal["dark"], 0.08)
            if y % 3 == 1:
                c = lerp(c, pal["light"], 0.05)
            g = rnd.randint(-3, 3)
            px[x0 + x, y0 + y] = tuple(max(0, min(255, val + g)) for val in c) + (255,)


def paint_darn(px, rect, seed, pal=JUTE):
    """Une ou deux reprises cousues en croix, fil sombre, comme sur les sacs
    vanilla."""
    x0, y0, w, h = rect
    rnd = random.Random(seed + 5)
    for _ in range(rnd.choice((1, 2))):
        cx = rnd.randint(2, w - 3)
        cy = rnd.randint(2, h - 3)
        for d in (-1, 0, 1):
            px[x0 + cx + d, y0 + cy] = pal["seam"] + (255,)
            px[x0 + cx, y0 + cy + d] = pal["seam"] + (255,)


def paint_folds(px, rect, seed, face, pal=JUTE):
    """Le col et la tete du sac : des plis verticaux, plus sombres vers le
    bas, la ou la corde serre."""
    x0, y0, w, h = rect
    if face in ("top", "bottom"):
        paint_cloth(px, rect, seed, "top", pal)
        return
    rnd = random.Random(seed)
    for x in range(w):
        col = (pal["light"], pal["light"], pal["base"], pal["base"], pal["dark"])[(x + rnd.randint(0, 1)) % 5]
        for y in range(h):
            c = lerp(col, pal["dark"], 0.3 * (y / max(1, h - 1)))
            px[x0 + x, y0 + y] = c + (255,)


def paint_rope(px, rect, seed, rope):
    """Corde torsadee : trois tons en diagonale."""
    x0, y0, w, h = rect
    rnd = random.Random(seed)
    for y in range(h):
        for x in range(w):
            c = (rope["light"], rope["base"], rope["dark"])[(x + y) % 3]
            g = rnd.randint(-5, 5)
            px[x0 + x, y0 + y] = tuple(max(0, min(255, v + g)) for v in c) + (255,)


def paint_knot(px, rect, rope):
    x0, y0, w, h = rect
    cx, cy = (w - 1) / 2, (h - 1) / 2
    for y in range(h):
        for x in range(w):
            d = math.hypot((x - cx) / max(1, cx), (y - cy) / max(1, cy))
            c = rope["light"] if d < 0.5 else rope["base"] if d < 0.95 else rope["dark"]
            px[x0 + x, y0 + y] = c + (255,)


STAR = [
    ".....#.....",
    "#....#....#",
    ".#...#...#.",
    "..#..#..#..",
    "...#.#.#...",
    "###########",
    "...#.#.#...",
    "..#..#..#..",
    ".#...#...#.",
    "#....#....#",
    ".....#.....",
]


def paint_star(px, rect, thread, sparkle):
    """L'etoile a huit branches d'Obol, brodee au fil de la couleur de la
    rarete a meme la jute : point clair sur le fil, point sombre dessous
    (le relief du point de broderie). Legendary : un eclat blanc au bout
    d'une branche."""
    x0, y0, w, h = rect
    n = len(STAR)
    ox, oy = x0 + (w - n) // 2, y0 + (h - n) // 2
    shadow = lerp(JUTE["dark"], thread["dark"], 0.4)

    def star(x, y):
        return 0 <= x < n and 0 <= y < n and STAR[y][x] == "#"

    for y in range(-1, n + 1):
        for x in range(-1, n + 1):
            if star(x, y):
                continue
            if any(star(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                if x0 <= ox + x < x0 + w and y0 <= oy + y < y0 + h:
                    px[ox + x, oy + y] = shadow + (255,)     # l'ombre du point
    for y in range(n):
        for x in range(n):
            if star(x, y):
                px[ox + x, oy + y] = (thread["light"] if (x + y) % 2 else thread["base"]) + (255,)
    if sparkle:
        sx, sy = ox + n - 1, oy
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            if x0 <= sx + dx < x0 + w and y0 <= sy + dy < y0 + h:
                px[sx + dx, sy + dy] = (255, 250, 230) + (255,)


def paint_rivets(px, rect, metal, face):
    """Ferrures : clous aux coins des faces du corps, cerclage sous la corde."""
    x0, y0, w, h = rect
    bright = lerp(metal, (255, 255, 255), 0.5)
    dark = lerp(metal, (0, 0, 0), 0.4)
    if face == "rim":
        for x in range(w):
            px[x0 + x, y0 + h - 1] = (bright if x % 4 == 1 else metal) + (255,)
        return
    for (x, y) in ((1, h - 2), (w - 2, h - 2)):
        px[x0 + x, y0 + y] = bright + (255,)
        if y + 1 < h:
            px[x0 + x, y0 + y + 1] = dark + (255,)


def paint_texture(boxes, rarity):
    color, _, metal = RARITIES[rarity]
    rope = rope_palette(hex_rgb(color))
    img = Image.new("RGBA", (TEX_SIZE, TEX_SIZE), (0, 0, 0, 0))
    px = img.load()
    painted = set()
    seed = 11
    for box in boxes:
        for face in FACES:
            rect = box.uv[face]
            if rect in painted:
                continue
            painted.add(rect)
            seed += 17
            m = box.material
            side = face in ("front", "back", "left", "right")
            if m == "cloth":
                paint_cloth(px, rect, seed, face)
                if box.name == "Body" and face == "front":
                    paint_star(px, rect, rope, rarity == "Legendary")
                elif box.name == "Body" and side:
                    paint_darn(px, rect, seed)
                if metal and side and box.name == "Body":
                    paint_rivets(px, rect, metal, face)
            elif m == "folds":
                paint_folds(px, rect, seed, face)
            elif m == "rope":
                paint_rope(px, rect, seed, rope)
                if metal and box.name == "Rope" and side:
                    paint_rivets(px, rect, metal, "rim")
            elif m == "knot":
                paint_knot(px, rect, rope)
    return img


# =============================================================================
# Planche de controle
# =============================================================================

def slot(icon):
    """Une case d'inventaire de fortune, 36 px, l'icone a 24 px dedans :
    ce que le joueur voit vraiment."""
    cell = Image.new("RGBA", (36, 36), (38, 44, 58, 255))
    small = icon.resize((24, 24), Image.LANCZOS)
    cell.paste(small, (6, 6), small)
    return cell


def preview(icons, texture, model):
    views = [render_icon(model, texture, rotation=r, size=96, perspective=0, pitch=0)
             for r in ((0, 0, 0), (0, 90, 0), (90, 0, 0))]
    purse_icon = None
    p = os.path.join(purse.ICON_DIR, "Obol_Purse_Gold.png")
    if os.path.exists(p):
        purse_icon = Image.open(p).convert("RGBA")
    n = len(icons) + (1 if purse_icon else 0)
    w = n * (64 * 3 + 8) + 8 + 64 * 3 + 8 + len(views) * (96 + 8) + 8
    h = 64 * 3 + 16 + 36 + 8
    sheet = Image.new("RGBA", (w, h), (28, 30, 36, 255))
    x = 8
    for icon in icons + ([purse_icon] if purse_icon else []):
        sheet.paste(zoom(icon, 3), (x, 8), zoom(icon, 3))
        cell = slot(icon)
        sheet.paste(cell, (x, 64 * 3 + 16), cell)
        x += 64 * 3 + 8
    sheet.paste(zoom(texture, 3), (x, 8), zoom(texture, 3))
    x += 64 * 3 + 8
    for v in views:
        sheet.paste(v, (x, 8), v)
        x += 96 + 8
    sheet.save(os.path.join(TMP, "_lootbag_apercu.png"))


# =============================================================================

if __name__ == "__main__":
    for path in (MODEL_DIR, ICON_DIR):
        os.makedirs(path, exist_ok=True)

    models = {}
    for name, fill in FILL.items():
        boxes = lootbag_boxes(fill)
        pack_uv(boxes)
        models[name] = (boxes, model_json(boxes))
        with open(os.path.join(MODEL_DIR, "Lootbag_%s.blockymodel" % name), "w") as f:
            json.dump(models[name][1], f, indent=2)
            f.write("\n")

    def height(boxes):
        return max(b.position[1] + b.size[1] / 2 for b in boxes)

    tallest = height(models["Tight"][0])
    icons = []
    for rarity, (color, fill, metal) in RARITIES.items():
        boxes, model = models[fill]
        texture = paint_texture(boxes, rarity)
        texture.save(os.path.join(MODEL_DIR, "Lootbag_%s_Texture.png" % rarity))
        # meme echelle pour les trois remplissages : un sac affaisse est plus bas
        icon = render_icon(model, texture, rotation=ICON_ROTATION,
                           fit=0.9 * height(boxes) / tallest)
        icon.save(os.path.join(ICON_DIR, "Obol_Lootbag_%s.png" % rarity))
        icons.append(icon)

    boxes, model = models["Tight"]
    preview(icons, paint_texture(boxes, "Legendary"), model)
    print("ok")
