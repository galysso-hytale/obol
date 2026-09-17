#!/usr/bin/env python3
"""
Fond de la zone « ce que je cède » de la page d'échange (addon trade) : un
rectangle jaune très translucide aux coins arrondis, servi en 9-patch
(Background: (TexturePath: "OfferPanel.png", Border: @PanelBorder)) sous la
grille de l'offre et sous la colonne des pièces cédées. Le moteur d'UI n'a
pas de coins arrondis, d'où la texture.

Sorties : addons/trade/.../ObolTrade/OfferPanel.png (24 px) et @2x (48 px).
"""

import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "addons/trade/src/main/resources/Common/UI/Custom/ObolTrade/OfferPanel")
LOGICAL = 24                 # côté de la texture, en px logiques
RADIUS = 8                   # rayon des coins, égal au Border du 9-patch
FILL = (255, 204, 0, 18)     # #ffcc00 à 7 %
S = 8                        # sur-échantillonnage


def panel(size):
    u = size / LOGICAL
    big = Image.new("RGBA", (size * S, size * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    d.rounded_rectangle((0, 0, size * S - 1, size * S - 1), radius=RADIUS * u * S, fill=FILL)
    return big.resize((size, size), Image.LANCZOS)


for scale, suffix in ((2, "@2x"), (1, "")):
    panel(LOGICAL * scale).save(f"{OUT}{suffix}.png")
    print(f"{OUT}{suffix}.png")
