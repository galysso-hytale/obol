#!/usr/bin/env python3
"""
Planche des quatre pieces pour la page CurseForge (docs/coins.png).

Les sprites du HUD (core, Common/UI/Custom/Obol/<Palier>.png, 48x48) en
ligne, du cuivre au mythril, agrandis sans lissage, sur la plaque de
tools/curseforge.py pour que la page ait un seul fond. Sous chaque piece
son nom dans sa couleur d'ecran (Denomination.color()) et, entre deux
pieces, le taux : x100 de l'une a la suivante. Pas de police embarquee,
celle par defaut de Pillow rendue en pixels, agrandie comme le reste.

Usage : python3 tools/coins_strip.py
"""

import os

from PIL import Image, ImageDraw

import curseforge

ROOT = curseforge.ROOT
COINS = os.path.join(ROOT, "core", "src", "main", "resources", "Common", "UI", "Custom", "Obol")
OUT = os.path.join(ROOT, "docs", "coins.png")

TIERS = [("Copper", "#B87333"), ("Silver", "#C0C0C0"), ("Gold", "#FFD700"), ("Mythril", "#7FDBFF")]
SCALE = 2          # 48 px natif x2 : CurseForge limite les images a 850 px de large
CELL = 48 + 44     # largeur d'une case en px natifs, piece centree, place pour le taux
GAP_TEXT = "x100"  # taux entre deux paliers
PAD = 8            # marge interieure de la plaque en px natifs


def main():
    pal = curseforge.palette(None)
    w = PAD * 2 + CELL * len(TIERS)
    h = PAD * 2 + 48 + 14
    plate = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(plate)
    n = (w - 1, h - 1)
    r = curseforge.RADIUS
    d.rounded_rectangle((0, 0, *n), r, fill=pal["outline"])
    d.rounded_rectangle((1, 1, n[0] - 1, n[1] - 1), r - 1, fill=pal["light"])
    d.rounded_rectangle((2, 2, n[0] - 1, n[1] - 1), r - 1, fill=pal["shade"])
    d.rounded_rectangle((2, 2, n[0] - 2, n[1] - 2), r - 2, fill=pal["fill"])

    for i, (name, color) in enumerate(TIERS):
        x = PAD + i * CELL
        coin = Image.open(os.path.join(COINS, name + ".png")).convert("RGBA")
        plate.alpha_composite(coin, (x + (CELL - 48) // 2, PAD))
        tw = d.textlength(name)
        d.text((x + (CELL - tw) / 2, PAD + 50), name, fill=color)
        if i < len(TIERS) - 1:
            tw = d.textlength(GAP_TEXT)
            d.text((x + CELL - tw / 2, PAD + 20), GAP_TEXT, fill=pal["light"])

    plate.resize((w * SCALE, h * SCALE), Image.NEAREST).save(OUT)
    print(os.path.relpath(OUT, ROOT), f"({w * SCALE}x{h * SCALE})")


if __name__ == "__main__":
    main()
