#!/usr/bin/env python3
"""
Generateur des raretes de l'add-on Obol Purse.

Une rarete (ItemQuality) par palier de pieces, Obol_Copper a Obol_Mythril :
c'est le canal du jeu pour « cet objet vaut quelque chose », et il tient
tout ensemble, cadre de la case d'inventaire, cadre et couleur du nom dans
le tooltip, etiquette sous le nom, halo au sol. Chaque etat de la bourse
(tmp/purse.py) declare la sienne, comme les poissons vanilla declarent une
rarete par etat.

Chaque rarete copie une rarete vanilla (Uncommon pour le cuivre, jusqu'a
Legendary pour le mythril, la meme echelle que les halos de tmp/halos.py),
avec ses textures recolorees : chaque pixel est converti en HSL, la teinte
devient celle de la piece, la saturation est ponderee par la sienne,
luminosite et alpha sont gardes (d'une rarete vanilla a l'autre, seule la
teinte change aussi). L'argent prend un gris bleute, un gris pur serait le
cadre « Common ». Les valeurs (QualityValue 2 a 5) sont celles des raretes copiees,
pour qu'un mod qui trie par valeur voie quelque chose de sense.

Produit dans addons/purse/src/main/resources/ :
  Server/Item/Qualities/Obol_<Palier>.json
  Common/UI/ItemQualities/Obol/Slot_<Palier>.png, @2x   (case d'inventaire)
  Common/UI/ItemQualities/Obol/Tooltip_<Palier>.png, @2x, TooltipArrow_<Palier>.png, @2x

Le jeu ne livre ces textures qu'en @2x et les cite sans le suffixe dans les
JSON : on livre les deux tailles, l'@2x recoloree et la simple reduite de
moitie. Les etiquettes (general.qualities.Obol_<Palier>) sont dans
server.lang, ecrit par tmp/purse.py. Deterministe, Pillow seulement.
"""

import colorsys
import json
import os
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "addons", "purse", "src", "main", "resources")
QUALITY_DIR = os.path.join(RES, "Server", "Item", "Qualities")
TEXTURE_DIR = os.path.join(RES, "Common", "UI", "ItemQualities", "Obol")
ASSETS_ZIP = os.path.expanduser(
    "~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/install/release/package/game/latest/Assets.zip")

# Un cadre gris est le cadre « Common » du jeu : l'argent prend une pointe
# de froid dans ses textures pour se lire « metal ». Son nom reste gris.
SILVER_FRAME = "#B8CCE0"

# Palier -> (rarete vanilla copiee, couleur du nom (Denomination.color()),
#            couleur des textures)
TIERS = {
    "Copper": ("Uncommon", "#B87333", "#B87333"),
    "Silver": ("Rare", "#C0C0C0", SILVER_FRAME),
    "Gold": ("Epic", "#FFD700", "#FFD700"),
    "Mythril": ("Legendary", "#7FDBFF", "#7FDBFF"),
}


def hex_to_rgb(text):
    return tuple(int(text[i:i + 2], 16) / 255 for i in (1, 3, 5))


def recolor_image(img, tier_color):
    """`img` avec la teinte de `tier_color` : chaque pixel garde sa
    luminosite et son alpha, sa saturation est multipliee par celle de
    `tier_color` (l'or, sature, garde celle de la texture vanilla, l'argent
    bleute n'en garde qu'un tiers), seule la teinte change vraiment, comme
    d'une rarete vanilla a l'autre (le fond sombre de la case reste sombre
    et discret)."""
    hue, _, tier_sat = colorsys.rgb_to_hls(*hex_to_rgb(tier_color))
    img = img.convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            _, light, sat = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            nr, ng, nb = colorsys.hls_to_rgb(hue, light, sat * tier_sat)
            px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return img


def save_both(img, name):
    """L'@2x telle quelle, et la taille simple reduite de moitie."""
    os.makedirs(TEXTURE_DIR, exist_ok=True)
    img.save(os.path.join(TEXTURE_DIR, name + "@2x.png"))
    img.resize((img.width // 2, img.height // 2), Image.LANCZOS).save(
        os.path.join(TEXTURE_DIR, name + ".png"))


def main():
    os.makedirs(QUALITY_DIR, exist_ok=True)
    with zipfile.ZipFile(ASSETS_ZIP) as z:
        for tier, (rarity, color, texture_color) in TIERS.items():
            source = json.loads(z.read("Server/Item/Qualities/%s.json" % rarity))
            for kind, src, out in (("Slots", "Slot%s" % rarity, "Slot_%s" % tier),
                                   ("Tooltips", "ItemTooltip%s" % rarity, "Tooltip_%s" % tier),
                                   ("Tooltips", "ItemTooltip%sArrow" % rarity, "TooltipArrow_%s" % tier)):
                with z.open("Common/UI/ItemQualities/%s/%s@2x.png" % (kind, src)) as f:
                    save_both(recolor_image(Image.open(f), texture_color), out)
            quality = {
                "QualityValue": source["QualityValue"],
                "ItemTooltipTexture": "UI/ItemQualities/Obol/Tooltip_%s.png" % tier,
                "ItemTooltipArrowTexture": "UI/ItemQualities/Obol/TooltipArrow_%s.png" % tier,
                "SlotTexture": "UI/ItemQualities/Obol/Slot_%s.png" % tier,
                "BlockSlotTexture": "UI/ItemQualities/Obol/Slot_%s.png" % tier,
                "SpecialSlotTexture": "UI/ItemQualities/Obol/Slot_%s.png" % tier,
                "TextColor": color,
                "LocalizationKey": "server.general.qualities.Obol_%s" % tier,
                "VisibleQualityLabel": True,
                "RenderSpecialSlot": True,
                "ItemEntityConfig": {"ParticleSystemId": "Drop_Obol_%s" % tier},
            }
            with open(os.path.join(QUALITY_DIR, "Obol_%s.json" % tier), "w") as f:
                json.dump(quality, f, indent=2)
                f.write("\n")
            print("%s: %s, value %d" % (tier, rarity, source["QualityValue"]))


if __name__ == "__main__":
    main()
