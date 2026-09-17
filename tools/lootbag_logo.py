#!/usr/bin/env python3
"""
Logo CurseForge d'Obol Lootbag (addons/lootbag/docs/curseforge.png).

Un seul sac ne dit pas ce que fait l'add-on : les cinq raretes le disent.
Les icones des sacs (64x64 natifs, generees par tools/lootbag.py) sont
posees en tas sur la plaque de tools/curseforge.py teintee lootbag :
Common, Uncommon et Rare derriere, Epic et Legendary devant, comme un
butin verse au sol. Le tas remplit la plaque a peu pres carree, et la
rarete se lit dans la couleur des sacs, meme a 64 px dans une liste.

Usage : python3 tools/lootbag_logo.py
Sortie : le logo et l'apercu tmp/_curseforge_apercu.png de curseforge.py.
"""

import os

from PIL import Image

import curseforge

ROOT = curseforge.ROOT
ICONS = os.path.join(ROOT, "addons", "lootbag", "src", "main", "resources", "Common", "Icons", "ItemsGenerated")
OUT = os.path.join(ROOT, "addons", "lootbag", "docs", "curseforge.png")

# Tas : (rarete, x, y) en unites, dans l'ordre de dessin, le dernier
# devant. Les sacs font 48 unites de large dans leur icone de 64 : les
# deux du devant en cachent le bas de ceux de derriere sans masquer leur
# couleur ni l'or qui deborde.
PILE = [
    ("Common",     4,  0),
    ("Uncommon",  60,  0),
    ("Rare",      32, 10),
    ("Epic",       0, 34),
    ("Legendary", 48, 36),
]
MARGIN = 6   # unites entre la plaque et les sacs
SCALE = 5    # 122 unites x5 = 610 px, au-dessus des 400 demandes


def pile():
    """Les sacs sur fond transparent, rognes a leur boite."""
    canvas = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    for name, x, y in PILE:
        bag = Image.open(os.path.join(ICONS, f"Obol_Lootbag_{name}.png")).convert("RGBA")
        canvas.alpha_composite(bag, (x, y))
    return canvas.crop(canvas.getbbox())


def main():
    bags = pile()
    units = max(bags.size) + 2 * MARGIN
    plate = curseforge.draw_plate(units, curseforge.palette(curseforge.TINTS["lootbag"]))
    plate.alpha_composite(bags, ((units - bags.width) // 2, (units - bags.height) // 2))
    logo = plate.resize((units * SCALE, units * SCALE), Image.NEAREST)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    logo.save(OUT)
    curseforge.preview(logo, os.path.join(curseforge.TMP, "_curseforge_apercu.png"))
    print(os.path.relpath(OUT, ROOT), f"({logo.width}x{logo.height})")


if __name__ == "__main__":
    main()
