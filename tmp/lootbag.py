#!/usr/bin/env python3
"""
Generateur de l'item lootbag de l'add-on Obol Lootbag : le visuel seulement
(modele, textures, icones, planche d'apercu). Les JSON d'items, les raretes
et la langue viendront quand le plan aura tranche les raretes.

Cinq sacs, un par rarete vanilla (Common, Uncommon, Rare, Epic,
Legendary). Meme concept a cinq niveaux : un sac de jute trapu, ferme par
une corde nouee en boucle, la tete du sac qui retombe sur le cote. Trois
regles a apprendre, toutes monotones :

  1. La couleur dit la rarete. Corde, noeud et ecusson sont de la couleur
     du nom et du cadre de case de la rarete vanilla (TextColor) : le joueur
     la connait deja.
  2. La taille dit la rarete. Trois gabarits, S (Common, Uncommon),
     M (Rare, Epic), L (Legendary). L'icone garde l'echelle.
  3. Le sac s'enrichit d'un cran par palier, cumulatif :
       Common     jute delavee, corde de chanvre pale, rien d'autre
       Uncommon   + ecusson cousu sur le ventre, avec une piece dessinee
       Rare       + coutures de couleur sur les aretes
       Epic       + ferrures d'argent (coins du socle, cerclage du col)
       Legendary  + les ferrures passent a l'or, la piece de l'ecusson luit
     et la toile fonce d'un palier a l'autre (jute delavee -> drap sombre).

Rien de commun avec la bourse (tmp/purse.py) : jute beige et non cuir
lie-de-vin, large et bas et non haut a huit pans, tete qui retombe et non
fronce en etoile, boucle de corde sur le devant et non glands pendants,
couleur de rarete et non couleur de piece.

Produit dans addons/lootbag/src/main/resources/ :
  Common/Items/Obol/Lootbag_<S|M|L>.blockymodel
  Common/Items/Obol/Lootbag_<Rarete>_Texture.png
  Common/Icons/ItemsGenerated/Obol_Lootbag_<Rarete>.png

et dans tmp/ :
  _lootbag_apercu.png   cinq icones x3, les cinq a 24 px sur une case, la bourse
                        d'or a cote pour comparer, texture Legendary x3, trois
                        vues du modele L.

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
ICON_ROTATION = (22.5, 35.0, 10.0)   # un peu plus de lacet que la bourse : on
                                      # voit le noeud, l'ecusson et la tete qui
                                      # retombe a droite

random.seed(0x10B4)

# Rarete -> (couleur vanilla TextColor, gabarit, richesse 0..1, metal).
# Raretes vanilla seulement : le 0.6.5 s'arrete a Legendary.
SILVER = (200, 206, 216)
GOLD = (236, 192, 84)
RARITIES = {
    "Common":    ("#c9d2dd", "S", 0.00, None),
    "Uncommon":  ("#3e9049", "S", 0.18, None),
    "Rare":      ("#2770b7", "M", 0.36, None),
    "Epic":      ("#8b339e", "M", 0.58, SILVER),
    "Legendary": ("#bb8a2c", "L", 0.85, GOLD),
}
LEVEL = {name: i for i, name in enumerate(RARITIES)}
SIZES = {"S": 0.8, "M": 0.9, "L": 1.0}


def hex_rgb(text):
    return tuple(int(text[i:i + 2], 16) for i in (1, 3, 5))


# =============================================================================
# Le modele : un sac de jute trapu, quatre etages, corde nouee, tete qui retombe
# =============================================================================

def lootbag_boxes(scale):
    """Le sac, a la maniere du Feedbag vanilla (des boites empilees, la
    rondeur dans la texture). Le ventre est l'etage le plus large, le socle
    et l'epaule rentrent : un sac plein pose au sol. La tete (Flap, Tip) penche a droite, deux boites
    inclinees autour de Z. Le noeud et sa boucle sont devant, a plat, et non
    des glands qui pendent comme sur la bourse."""
    s = scale

    def S(*v):
        return tuple(x * s for x in v)

    b = [
        Box("Base", S(13, 2.5, 11), S(0, 1.25, 0), "cloth"),
        Box("Belly", S(15, 7.5, 13), S(0, 6.25, 0), "cloth"),
        Box("Shoulder", S(12, 3, 10), S(0, 11.5, 0), "cloth"),
        Box("Neck", S(7, 3, 6), S(0, 14, 0), "folds"),
        Box("Rope", S(8.5, 2, 7.5), S(0, 14.2, 0), "rope"),
        Box("Knot", S(3, 2.4, 2), S(0, 14.2, 4.2), "knot"),
        Box("Loop_L", S(4, 1.6, 1.4), S(-3.2, 14.0, 4.3), "rope",
            orientation=q_axis((0, 0, 1), 18)),
        Box("Loop_R", S(4, 1.6, 1.4), S(3.2, 14.0, 4.3), "rope",
            orientation=q_axis((0, 0, 1), -18)),
        Box("Flap", S(7.5, 4.5, 5.5), S(0.9, 17.2, 0), "folds",
            orientation=q_axis((0, 0, 1), -18)),
        Box("Tip", S(5, 2.5, 4), S(3.4, 19.4, 0), "folds",
            orientation=q_axis((0, 0, 1), -48)),
    ]
    return b


def pack_uv(boxes):
    """Comme purse.pack_uv, mais le devant du ventre a sa propre region (il
    porte l'ecusson) et la corde partage la sienne entre ses boites."""
    wanted = []
    for box in boxes:
        for face in FACES:
            w, h = (math.ceil(v) for v in face_dims(box.size, face))
            side = {"front": "fb", "back": "fb", "right": "lr", "left": "lr",
                    "top": "tb", "bottom": "tb"}[face]
            if box.name == "Belly" and side == "fb":
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

# Jute delavee (Common) et drap sombre (Mythic) : la toile de chaque rarete
# est entre les deux, selon sa richesse.
JUTE = {"light": (214, 190, 142), "base": (182, 152, 102), "dark": (134, 106, 66),
        "seam": (96, 72, 44)}
CLOTH = {"light": (108, 84, 92), "base": (76, 56, 66), "dark": (46, 32, 40),
         "seam": (24, 16, 20)}


def cloth_palette(richness):
    return {k: lerp(JUTE[k], CLOTH[k], richness) for k in JUTE}


def rope_palette(color):
    return {"light": lerp(color, (255, 255, 255), 0.4), "base": color,
            "dark": lerp(color, (0, 0, 0), 0.35)}


def paint_cloth(px, rect, seed, face, pal):
    """Jute tissee : trame en damier a un pixel, fils de chaine un peu plus
    clairs, vignettage vers les bords comme les textures vanilla."""
    x0, y0, w, h = rect
    noise = value_noise(w, h, 3, seed)
    rnd = random.Random(seed + 1)
    cx, cy = (w - 1) / 2, (h - 1) * 0.4
    for y in range(h):
        for x in range(w):
            dx, dy = (x - cx) / max(1, cx), (y - cy) / max(1, (h - 1) / 2)
            d = min(1.0, math.hypot(dx, dy * 0.8))
            if face == "bottom":
                c = lerp(pal["base"], pal["dark"], 0.5 + 0.3 * d)
            elif face == "top":
                c = lerp(pal["light"], pal["base"], 0.3 + 0.4 * d)
            else:
                c = lerp(pal["light"], pal["base"], 0.3 + 0.7 * d * d)
                c = lerp(c, pal["dark"], max(0.0, d - 0.6) * 1.3)
            # la trame : un damier, et une ligne sur deux un peu plus sombre
            weave = 0.10 if (x + y) % 2 else -0.06
            if y % 3 == 2:
                weave += 0.08
            c = lerp(c, pal["dark"], max(0.0, weave)) if weave > 0 else lerp(c, pal["light"], -weave)
            c = lerp(c, pal["dark"], noise[y][x] * 0.15)
            g = rnd.randint(-3, 3)
            px[x0 + x, y0 + y] = tuple(max(0, min(255, v + g)) for v in c) + (255,)


def paint_folds(px, rect, seed, face, pal):
    """Le col et la tete du sac : des plis verticaux, plus sombres vers le
    bas, la ou la corde serre. Le dessus est ferme (jute sombre)."""
    x0, y0, w, h = rect
    if face in ("top", "bottom"):
        paint_cloth(px, rect, seed, "bottom", pal)
        return
    rnd = random.Random(seed)
    for x in range(w):
        col = (pal["light"], pal["light"], pal["base"], pal["base"], pal["dark"])[(x + rnd.randint(0, 1)) % 5]
        for y in range(h):
            c = lerp(col, pal["dark"], 0.3 * (y / max(1, h - 1)))
            px[x0 + x, y0 + y] = c + (255,)


def paint_rope(px, rect, seed, rope, twist=True):
    """Corde torsadee : trois tons en diagonale."""
    x0, y0, w, h = rect
    rnd = random.Random(seed)
    for y in range(h):
        for x in range(w):
            k = ((x + y) if twist else x) % 3
            c = (rope["light"], rope["base"], rope["dark"])[k]
            g = rnd.randint(-5, 5)
            px[x0 + x, y0 + y] = tuple(max(0, min(255, v + g)) for v in c) + (255,)


def paint_knot(px, rect, rope):
    """Le noeud : une boule de corde, clair au centre."""
    x0, y0, w, h = rect
    cx, cy = (w - 1) / 2, (h - 1) / 2
    for y in range(h):
        for x in range(w):
            d = math.hypot((x - cx) / max(1, cx), (y - cy) / max(1, cy))
            c = rope["light"] if d < 0.5 else rope["base"] if d < 0.95 else rope["dark"]
            px[x0 + x, y0 + y] = c + (255,)


def paint_patch(px, rect, rope, level, metal):
    """L'ecusson sur le devant du ventre (Uncommon et plus) : un carre de
    tissu de la couleur de la rarete, coutures sombres tout autour, une
    piece dessinee au centre. Legendary : lisere d'or et la piece luit,
    presque blanche."""
    x0, y0, w, h = rect
    size = 7 if w >= 12 else 5
    ox, oy = x0 + (w - size) // 2, y0 + (h - size) // 2 + 1
    border = rope["dark"]
    if level >= LEVEL["Legendary"]:
        border = metal
    for y in range(size):
        for x in range(size):
            edge = x in (0, size - 1) or y in (0, size - 1)
            c = border if edge else rope["base"]
            # une couture sombre en pointille autour, hors du carre
            px[ox + x, oy + y] = c + (255,)
    for i in range(-1, size + 1):
        for (sx, sy) in ((ox + i, oy - 1), (ox + i, oy + size), (ox - 1, oy + i), (ox + size, oy + i)):
            if i % 2 == 0 and x0 <= sx < x0 + w and y0 <= sy < y0 + h:
                px[sx, sy] = JUTE["seam"] + (255,)
    # la piece : un disque de 3 (ou 5) pixels, clair, un reflet
    cx, cy = ox + size // 2, oy + size // 2
    r = 1 if size == 5 else 2
    coin = rope["light"]
    if level >= LEVEL["Legendary"]:
        coin = (255, 244, 214)
    for y in range(-r, r + 1):
        for x in range(-r, r + 1):
            if x * x + y * y <= r * r + (1 if r == 2 else 0):
                px[cx + x, cy + y] = coin + (255,)
    px[cx, cy] = (lerp(coin, rope["dark"], 0.35) if level < LEVEL["Legendary"] else (255, 252, 240)) + (255,)


def paint_seams(px, rect, rope):
    """Rare et plus : coutures de couleur sur les aretes verticales."""
    x0, y0, w, h = rect
    for y in range(h):
        if y % 2 == 0:
            px[x0, y0 + y] = rope["base"] + (255,)
            px[x0 + w - 1, y0 + y] = rope["base"] + (255,)


def paint_rivets(px, rect, metal, face):
    """Epic et plus : des ferrures, clous aux coins du socle et cerclage du
    haut de l'epaule."""
    x0, y0, w, h = rect
    bright = lerp(metal, (255, 255, 255), 0.5)
    dark = lerp(metal, (0, 0, 0), 0.4)
    if face == "rim":
        for x in range(w):
            px[x0 + x, y0] = (bright if x % 4 == 1 else metal) + (255,)
            if h > 1:
                px[x0 + x, y0 + 1] = dark + (255,)
        return
    for (x, y) in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
        px[x0 + x, y0 + y] = metal + (255,)
        if x + 1 < w and y + 1 < h:
            px[x0 + x + 1, y0 + y + 1] = dark + (255,)
        px[x0 + x, y0 + y] = bright + (255,)


def paint_texture(boxes, rarity):
    color, _, richness, metal = RARITIES[rarity]
    level = LEVEL[rarity]
    pal = cloth_palette(richness)
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
            if m == "cloth":
                paint_cloth(px, rect, seed, face, pal)
                side = face in ("front", "back", "left", "right")
                if side and level >= LEVEL["Rare"] and box.name in ("Belly", "Shoulder"):
                    paint_seams(px, rect, rope)
                if metal and side and box.name == "Base":
                    paint_rivets(px, rect, metal, face)
                if metal and side and box.name == "Shoulder":
                    paint_rivets(px, rect, metal, "rim")
                if box.name == "Belly" and face == "front" and level >= LEVEL["Uncommon"]:
                    paint_patch(px, rect, rope, level, metal)
            elif m == "folds":
                paint_folds(px, rect, seed, face, pal)
            elif m == "rope":
                paint_rope(px, rect, seed, rope)
                if metal and box.name == "Rope" and face in ("front", "back", "left", "right"):
                    # le cerclage : une ligne de metal au bord bas de la corde
                    x0, y0, w, h = rect
                    for x in range(w):
                        px[x0 + x, y0 + h - 1] = (metal if x % 3 else lerp(metal, (0, 0, 0), 0.4)) + (255,)
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
    for size, scale in SIZES.items():
        boxes = lootbag_boxes(scale)
        pack_uv(boxes)
        models[size] = (boxes, model_json(boxes))
        with open(os.path.join(MODEL_DIR, "Lootbag_%s.blockymodel" % size), "w") as f:
            json.dump(models[size][1], f, indent=2)
            f.write("\n")

    icons = []
    for rarity, (color, size, richness, metal) in RARITIES.items():
        boxes, model = models[size]
        texture = paint_texture(boxes, rarity)
        texture.save(os.path.join(MODEL_DIR, "Lootbag_%s_Texture.png" % rarity))
        # meme echelle pour les trois gabarits : un petit sac est petit dans la case
        icon = render_icon(model, texture, rotation=ICON_ROTATION, fit=0.9 * SIZES[size])
        icon.save(os.path.join(ICON_DIR, "Obol_Lootbag_%s.png" % rarity))
        icons.append(icon)

    boxes, model = models["L"]
    preview(icons, paint_texture(boxes, "Legendary"), model)
    print("ok")
