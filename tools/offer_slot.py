#!/usr/bin/env python3
"""
Fond des cases de mon offre sur la page d'échange (addon trade) : la forme
exacte du fond de case du jeu (BlockSelectorSlotBackground, coins arrondis
de rayon 4) en jaune translucide, la teinte de la zone « ce que je cède ».
Posé par-dessus le fond de case gris de Slot.ui (#Zone), montré par le
serveur sur les cases de la grille de l'offre. Le carré de rareté d'un
objet le recouvre comme partout : seule la case vide porte la teinte.

Sorties : addons/trade/.../ObolTrade/OfferSlotBackground.png (46 px) et @2x (92 px).
"""

import os
import zipfile

from PIL import Image

ASSETS = ("/home/jocelin/.var/app/com.hypixel.HytaleLauncher/data/Hytale/install/release/"
          "package/game/latest/Assets.zip")
SHAPE = "Common/UI/Custom/Common/BlockSelectorSlotBackground@2x.png"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "addons/trade/src/main/resources/Common/UI/Custom/ObolTrade/OfferSlotBackground")
COLOR = (255, 204, 0)    # #ffcc00, la couleur des captions « You give »
ALPHA = 28               # 11 % en plein, là où le jeu met son gris à 8 %

with zipfile.ZipFile(ASSETS) as z:
    shape = Image.open(z.open(SHAPE)).convert("RGBA")
# Le jeu met un alpha uniforme (20) sauf dans les coins : on garde ce
# dégradé, mis à l'échelle de notre alpha.
mask = shape.getchannel("A").point(lambda a: round(a * ALPHA / 20))
big = Image.new("RGBA", shape.size, COLOR + (0,))
big.putalpha(mask)
big.save(f"{OUT}@2x.png")
small = big.resize((shape.width // 2, shape.height // 2), Image.LANCZOS)
small.save(f"{OUT}.png")
print(os.path.relpath(f"{OUT}@2x.png"), os.path.relpath(f"{OUT}.png"))
