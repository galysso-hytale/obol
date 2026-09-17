#!/usr/bin/env python3
"""
Marque « vient du sac-à-dos » des cases de l'offre (addon trade) :
un carré noir translucide, angles droits sauf l'inférieur droit arrondi,
collé dans le coin haut-gauche de la case, avec l'icône de sac-à-dos de
l'inventaire du jeu (BackpackIcon, le bouton du panneau personnage),
détourée, dedans.

Sorties : addons/trade/.../ObolTrade/BackpackTag.png (18 px) et @2x (36 px).
"""

import os

from PIL import Image, ImageDraw

GAME = ("/home/jocelin/.var/app/com.hypixel.HytaleLauncher/data/Hytale/install/release/"
        "package/game/latest/Client/Data/Game/Interface/InGame/Pages/Inventory/BackpackIcon@2x.png")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "addons/trade/src/main/resources/Common/UI/Custom/ObolTrade/BackpackTag")
LOGICAL = 18           # côté de la marque, en px logiques
RADIUS = 5             # rayon du seul coin arrondi, en bas à droite
MARGIN = 2             # autour de l'icône
BACK = (0, 0, 0, 140)  # noir à 55 %
S = 8                  # sur-échantillonnage du fond


def background(size):
    u = size / LOGICAL
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    r = RADIUS * u
    # Le carré entier moins le coin bas-droit, puis ce coin arrondi.
    d.rectangle((0, 0, size - r, size), fill=BACK)
    d.rectangle((0, 0, size, size - r), fill=BACK)
    d.pieslice((size - 2 * r, size - 2 * r, size, size), 0, 90, fill=BACK)
    return img


icon = Image.open(GAME).convert("RGBA")
icon = icon.crop(icon.getbbox())

for scale, suffix in ((2, "@2x"), (1, "")):
    size = LOGICAL * scale
    tag = background(size * S).resize((size, size), Image.LANCZOS)
    inner = size - 2 * MARGIN * scale
    ratio = min(inner / icon.width, inner / icon.height)
    glyph = icon.resize((round(icon.width * ratio), round(icon.height * ratio)), Image.LANCZOS)
    tag.alpha_composite(glyph, ((size - glyph.width) // 2, (size - glyph.height) // 2))
    tag.save(OUT + suffix + ".png")
print("ok")
