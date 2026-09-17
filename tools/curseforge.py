#!/usr/bin/env python3
"""
Generateur du logo CurseForge d'Obol et de ses add-ons.

CurseForge affiche le logo sur des cartes claires ou sombres selon le
theme, a 64 px dans les listes. Une icone a fond transparent (docs/icon.png)
y perd le cuivre et le gris. Ce script pose l'icone sur une plaque opaque,
la meme pour toute la famille, pour que chaque page se reconnaisse comme
un membre d'Obol.

La plaque est du pixel art comme l'icone : dessinee sur une grille en
unites (1 unite = 1 pixel de l'icone native), puis agrandie sans lissage.
Carre a coins arrondis, biseau clair en haut et a gauche, ombre en bas et
a droite, comme les panneaux vanilla, et un halo plus clair derriere
l'icone pour que les pieces se detachent. Bleu-ardoise sombre : froid, il
laisse l'or et le cuivre au premier plan sans les concurrencer.

Reutilisation pour un add-on : meme plaque, l'icone change, et une teinte
optionnelle (--tint) tire le fond vers la couleur de l'add-on pour marquer
la famille sans que les pages se confondent. Les teintes connues sont
dans TINTS, appelables par nom.

L'icone doit etre du pixel art a echelle entiere (64 natif x4 = 256 pour
docs/icon.png) : l'echelle est detectee, l'icone est ramenee a sa taille
native puis re-agrandie au meme facteur que la plaque, sans flou.

Usage :
    python3 tools/curseforge.py docs/icon.png
    python3 tools/curseforge.py addons/purse/docs/icon.png --tint purse
Sortie : curseforge.png a cote de l'icone (640x640 par defaut) et une
planche tmp/_curseforge_apercu.png qui montre le logo a 64 px sur carte claire
et sombre, comme sur le site.
"""

import argparse
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TMP = os.path.join(ROOT, "tmp")

# Plaque : couleurs par role. Meme famille de bleu-ardoise que les cadres
# de l'interface d'Obol, du plus sombre (contour) au plus clair (halo).
PLATE = {
    "outline": "#12151f",   # contour 1 unite
    "light":   "#3f4763",   # biseau haut et gauche
    "shade":   "#1a1e2b",   # ombre bas et droite
    "fill":    "#262b3c",   # fond
    "glow":    ["#2b3146", "#303752"],  # halo derriere l'icone, du bord vers le centre
}
RADIUS = 7          # rayon des coins, en unites
MARGIN = 8          # unites entre le bord de la plaque et l'icone
SCALE = 8           # facteur d'agrandissement : 80 unites x 8 = 640 px
TINT_STRENGTH = 0.28   # part de la teinte melangee dans fill, light, shade, glow

# Teintes des add-ons : la couleur qui les identifie dans le jeu.
TINTS = {
    "obol":    None,        # le mod de base garde la plaque nue
    "purse":   "#6e2a3a",   # cuir lie-de-vin de la bourse
    "lootbag": "#8a6b3a",   # toile de jute du sac
    "trade":   "#2f6b4f",   # vert de l'echange
}


def hex_to_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def blend(a, b, t):
    """a vers b, t dans [0, 1]."""
    ra, rb = hex_to_rgb(a), hex_to_rgb(b)
    return tuple(round(x + (y - x) * t) for x, y in zip(ra, rb))


def native_scale(img):
    """Facteur entier auquel l'icone a ete agrandie (1 si non detecte)."""
    w, h = img.size
    for f in (8, 6, 5, 4, 3, 2):
        if w % f or h % f:
            continue
        small = img.resize((w // f, h // f), Image.NEAREST)
        if small.resize((w, h), Image.NEAREST).tobytes() == img.tobytes():
            return f
    return 1


def palette(tint):
    """La plaque, teintee ou non. Le contour reste noir bleute."""
    if tint is None:
        return {k: (hex_to_rgb(v) if isinstance(v, str) else [hex_to_rgb(c) for c in v])
                for k, v in PLATE.items()}
    return {
        "outline": hex_to_rgb(PLATE["outline"]),
        "light":   blend(PLATE["light"], tint, TINT_STRENGTH),
        "shade":   blend(PLATE["shade"], tint, TINT_STRENGTH),
        "fill":    blend(PLATE["fill"], tint, TINT_STRENGTH),
        "glow":    [blend(c, tint, TINT_STRENGTH) for c in PLATE["glow"]],
    }


def draw_plate(units, pal):
    """Plaque en unites : contour, biseau, fond, halo."""
    img = Image.new("RGBA", (units, units), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    n = units - 1
    d.rounded_rectangle((0, 0, n, n), RADIUS, fill=pal["outline"])
    d.rounded_rectangle((1, 1, n - 1, n - 1), RADIUS - 1, fill=pal["light"])
    d.rounded_rectangle((2, 2, n - 1, n - 1), RADIUS - 1, fill=pal["shade"])
    d.rounded_rectangle((2, 2, n - 2, n - 2), RADIUS - 2, fill=pal["fill"])

    # Halo : ellipses concentriques, centre un peu au-dessus du milieu pour
    # eclairer le haut des pieces, la ou l'oeil se pose.
    cx, cy = units / 2, units / 2 - 2
    rings = pal["glow"]
    for i, color in enumerate(rings):
        r = units * (0.42 - 0.13 * i)
        d.ellipse((cx - r, cy - r * 0.9, cx + r, cy + r * 0.9), fill=color)
    return img


def compose(icon_path, tint=None, margin=MARGIN, scale=SCALE):
    icon = Image.open(icon_path).convert("RGBA")
    f = native_scale(icon)
    icon = icon.resize((icon.width // f, icon.height // f), Image.NEAREST)

    units = max(icon.size) + 2 * margin
    plate = draw_plate(units, palette(tint))
    x = (units - icon.width) // 2
    y = (units - icon.height) // 2
    plate.alpha_composite(icon, (x, y))
    return plate.resize((units * scale, units * scale), Image.NEAREST)


def preview(logo, path):
    """Le logo a 64 px, comme dans une liste CurseForge, sur carte claire
    et sombre, et une fois en grand."""
    small = logo.resize((64, 64), Image.NEAREST)
    big = logo.resize((160, 160), Image.NEAREST)
    sheet = Image.new("RGBA", (2 * 120 + 200, 200), (0, 0, 0, 0))
    d = ImageDraw.Draw(sheet)
    for i, card in enumerate(("#f5f5f7", "#1c1d21")):
        d.rectangle((i * 120, 0, i * 120 + 119, 199), fill=card)
        sheet.alpha_composite(small, (i * 120 + 28, 68))
    d.rectangle((240, 0, 439, 199), fill="#2a2c33")
    sheet.alpha_composite(big, (260, 20))
    sheet.save(path)


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("icon", help="icone pixel art a fond transparent")
    p.add_argument("-o", "--out", help="fichier de sortie (defaut : curseforge.png a cote de l'icone)")
    p.add_argument("--tint", help="nom dans TINTS (obol, purse, lootbag, trade) ou couleur #rrggbb")
    p.add_argument("--margin", type=int, default=MARGIN, help="marge en unites (defaut %(default)s)")
    p.add_argument("--scale", type=int, default=SCALE, help="agrandissement (defaut %(default)s)")
    a = p.parse_args()

    tint = TINTS.get(a.tint, a.tint) if a.tint else None
    out = a.out or os.path.join(os.path.dirname(a.icon), "curseforge.png")

    logo = compose(a.icon, tint, a.margin, a.scale)
    logo.save(out)
    preview(logo, os.path.join(TMP, "_curseforge_apercu.png"))
    print(f"{out} ({logo.width}x{logo.height})")


if __name__ == "__main__":
    main()
