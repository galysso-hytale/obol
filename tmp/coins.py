#!/usr/bin/env python3
"""
Generateur d'icones de monnaie 64x64 pour Hytale.
Pixel art, sans antialiasing, alpha propre.

>>> REMPLACE LES VALEURS DE PALETTE par celles pipettees sur les
>>> lingots du jeu (hytalewiki.org/w/File:Copper_Ingot.png etc.)
"""

from PIL import Image, ImageDraw

SIZE = 64

# light = haute lumiere / base = valeur moyenne / shade = ombre / edge = tranche
PALETTES = {
    "copper":  {"light": "#e29a62", "base": "#c4703f", "shade": "#8c4a35", "edge": "#5e3230"},
    "iron":    {"light": "#d6dbe2", "base": "#9aa4b2", "shade": "#5e6878", "edge": "#3c4455"},
    "gold":    {"light": "#f7dd8e", "base": "#e8b845", "shade": "#a5762c", "edge": "#6e4a24"},
    "mithril": {"light": "#e0f4f6", "base": "#a8d8dc", "shade": "#5e93a6", "edge": "#3f6b7d"},
}

# Silhouette par palier : nombre de pieces et decalages (x, y)
LAYOUTS = {
    "copper":  {"coins": [(0, 0)],                          "sparkle": False},
    "iron":    {"coins": [(-6, 4), (6, -3)],                "sparkle": False},
    "gold":    {"coins": [(-7, 6), (7, 3), (0, -5)],        "sparkle": False},
    "mithril": {"coins": [(0, 1)],                          "sparkle": True},
}

CW, CH, THICK = 34, 23, 4   # largeur, hauteur (perspective 3/4), epaisseur


def draw_coin(d, cx, cy, pal):
    x0, y0 = cx - CW // 2, cy - CH // 2
    x1, y1 = x0 + CW, y0 + CH
    face = (x0, y0, x1, y1)

    # tranche de la piece (epaisseur), dessinee en premier
    d.ellipse((x0, y0 + THICK, x1, y1 + THICK), fill=pal["edge"])
    d.rectangle((x0, cy, x1, cy + THICK), fill=pal["edge"])

    # face
    d.ellipse(face, fill=pal["base"], outline=pal["edge"])

    # arc de lumiere haut-gauche / arc d'ombre bas-droite
    inset = (x0 + 3, y0 + 2, x1 - 3, y1 - 2)
    d.arc(inset, 175, 285, fill=pal["light"], width=2)
    d.arc(inset, 355, 105, fill=pal["shade"], width=2)

    # emblem central : losange simple, lisible a 16px
    for dy in range(-3, 4):
        w = 3 - abs(dy)
        d.line((cx - w, cy + dy, cx + w, cy + dy), fill=pal["shade"])
    d.point((cx - 1, cy - 1), fill=pal["light"])


def draw_sparkle(d, cx, cy, pal):
    d.line((cx, cy - 4, cx, cy + 4), fill=pal["light"])
    d.line((cx - 4, cy, cx + 4, cy), fill=pal["light"])
    d.point((cx, cy), fill="#ffffff")


def make(name):
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pal, lay = PALETTES[name], LAYOUTS[name]

    # tri par y croissant : les pieces du fond dessinees en premier
    for dx, dy in sorted(lay["coins"], key=lambda c: c[1]):
        draw_coin(d, SIZE // 2 + dx, SIZE // 2 + dy + 2, pal)

    if lay["sparkle"]:
        draw_sparkle(d, 18, 20, pal)

    return img


if __name__ == "__main__":
    import os
    out = "/mnt/user-data/outputs"
    os.makedirs(out, exist_ok=True)
    imgs = {}
    for name in PALETTES:
        img = make(name)
        img.save(f"{out}/Currency_{name.capitalize()}.png")
        imgs[name] = img

    # planche de controle : zoom x4 et test de lisibilite a 16px
    sheet = Image.new("RGBA", (4 * 72, 72 + 24), (28, 30, 36, 255))
    for i, name in enumerate(PALETTES):
        sheet.paste(imgs[name].resize((64, 64), Image.NEAREST), (i * 72 + 4, 4), imgs[name])
        small = imgs[name].resize((16, 16), Image.NEAREST)
        sheet.paste(small.resize((16, 16), Image.NEAREST), (i * 72 + 28, 72), small)
    sheet.resize((4 * 72 * 2, (72 + 24) * 2), Image.NEAREST).save(f"{out}/_apercu.png")
    print("ok")
