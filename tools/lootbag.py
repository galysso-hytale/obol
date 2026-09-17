#!/usr/bin/env python3
"""
Generateur de l'item lootbag de l'add-on Obol Lootbag : le visuel (modele,
textures, icones, planche d'apercu), les JSON des items et la langue.

Cinq sacs, un par rarete vanilla (Common, Uncommon, Rare, Epic, Legendary).
Un sac de toile ferme, construit comme le sac de farine vanilla (une boite
pour le corps, les plis peints en bandes verticales, des reprises en
croix), col serre par une corde de chanvre nouee en boucle, une collerette
au-dessus. Devant, petite, brodee en fil clair, l'etoile a huit branches
d'Obol (celle de l'icone du mod) : la signature, pas une piece. La rarete
du sac ne dit rien de la monnaie qu'il contient (c'est la config du
serveur), donc aucun metal de piece sur le sac. Deux regles a apprendre :

  1. La couleur de la toile dit la rarete, comme le sac de graines vanilla
     est vert de haut en bas : toute la toile est teinte a la couleur du
     cadre de case. Common est la toile ecrue, grise comme son cadre.
  2. Le sac se remplit : affaisse (Common), plein (Uncommon, Rare), tendu
     (Epic, Legendary). L'icone garde l'echelle.
  Rien d'autre : pas de ferrure ni d'eclat, un pixel de metal isole se lit
  comme un defaut a cette taille, et aucun sac vanilla n'en porte.

Rien de commun avec la bourse (tools/purse.py) : jute beige et non cuir
lie-de-vin, trapeze et non huit pans, tete courte et non fronce en etoile,
boucle de corde et non glands, couleur de rarete et non couleur de piece.

A l'ouverture (plan, §4 « Son »), un son par rarete, cinq evenements
sonores sur des fichiers vanilla, references par chemin, jamais copies.
Le tintement de pieces est la base des cinq (c'est de la monnaie qui
rentre), et au-dessus rien pour les deux petits sacs, une couche de plus
en plus nette pour les trois autres (SOUNDS). Dose bas : le commun est
le cas de loin le plus frequent, il ne doit jamais fatiguer. Le
tintement porte l'evenement a lui seul : une couche de tissu dessous
(inaudible sous les pieces) et une gerbe de pieces en particules
(pollution visuelle en troisieme personne, invisible en premiere) ont
ete essayees le 16 et retirees. C'est l'"Effects" de l'interaction du
clic droit, ecrite dans chacun des cinq items avec son propre son (le
gabarit garde celui du Common, un repli). Au ramassage (OpenOn: Pickup),
la racine Obol_Lootbag_Pickup n'a pas d'"Effects" : le jeu ne
synchronise pas cette chaine au client, c'est le plugin qui joue le son
de la rarete (LootbagOps.chime).

Produit dans addons/lootbag/src/main/resources/ :
  Common/Items/Obol/Lootbag_<Slack|Full|Tight>.blockymodel
  Common/Items/Obol/Lootbag_<Rarete>_Texture.png
  Common/Icons/ItemsGenerated/Obol_Lootbag_<Rarete>.png
  Server/Item/Items/Obol/Obol_Lootbag.json            le gabarit, jamais donne
  Server/Item/Items/Obol/Obol_Lootbag_<Rarete>.json   les cinq items, Parent: Obol_Lootbag
  Server/Languages/en-US/server.lang
  Server/Audio/SoundEvents/Obol/SFX_Obol_Lootbag_Open_<Rarete>.json
  Server/Item/RootInteractions/Obol/Obol_Lootbag_Pickup.json

et dans tmp/ :
  _lootbag_apercu.png   cinq icones x3, les cinq a 24 px sur une case, la bourse
                        d'or a cote pour comparer, texture Legendary x3, trois
                        vues du modele tendu.

Importe purse.py (rendu d'icone, modele, bruit). Pillow seulement. Deterministe :
relance, le script ne change aucun octet.
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
ITEM_DIR = os.path.join(RES, "Server", "Item", "Items", "Obol")
LANG_DIR = os.path.join(RES, "Server", "Languages", "en-US")
SOUND_DIR = os.path.join(RES, "Server", "Audio", "SoundEvents", "Obol")
ROOT_DIR = os.path.join(RES, "Server", "Item", "RootInteractions", "Obol")
TMP = purse.TMP

TEX_SIZE = 64
ICON_ROTATION = (22.5, 30.0, 10.0)   # comme la bourse : l'ecusson de face

random.seed(0x10B4)

# Rarete -> (couleur de teinture, remplissage). Couleurs pipettees
# sur les cadres de case vanilla (Slot<Rarete>@2x.png). Common : pas de
# teinture, toile ecrue. Legendary : un or franc, l'ocre du cadre ferait
# une toile de jute de plus.
RARITIES = {
    "Common":    (None, "Slack"),
    "Uncommon":  ("#2c9550", "Full"),
    "Rare":      ("#2770b7", "Full"),
    "Epic":      ("#8b339e", "Tight"),
    "Legendary": ("#f4b91e", "Tight"),
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

import colorsys

# La toile ecrue du Common : une jute grise, delavee, comme le cadre gris de
# la rarete. Les autres sont cette toile teinte (dye_palette).
ECRU = {"light": (208, 198, 174), "base": (172, 160, 134), "dark": (118, 108, 86),
        "seam": (76, 68, 54)}
# La corde et le fil de chanvre, les memes sur les cinq.
HEMP = {"light": (196, 160, 104), "base": (158, 122, 70), "dark": (104, 78, 42)}
THREAD = {"light": (250, 240, 212), "base": (232, 216, 176), "dark": (150, 130, 96)}
JUTE = ECRU     # pour les coutures (seam) et l'ombre de la broderie


def dye_palette(color, strength=0.45):
    """La toile ecrue teinte : chaque ton garde sa luminosite, prend la
    teinte de `color` et une part `strength` de sa saturation (le sac de
    graines vanilla est un vert eteint, pas un vert de peinture)."""
    hue, _, sat = colorsys.rgb_to_hls(*(v / 255 for v in hex_rgb(color)))
    out = {}
    for k, rgb in ECRU.items():
        _, light, _ = colorsys.rgb_to_hls(*(v / 255 for v in rgb))
        r, g, b = colorsys.hls_to_rgb(hue, light - 0.03, min(1.0, sat * strength))
        out[k] = (round(r * 255), round(g * 255), round(b * 255))
    return out


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
    "...#...",
    "#..#..#",
    ".#.#.#.",
    "#######",
    ".#.#.#.",
    "#..#..#",
    "...#...",
]


def paint_star(px, rect, thread):
    """L'etoile a huit branches d'Obol, brodee a meme la toile en fil clair,
    comme la gerbe du sac de farine vanilla."""
    x0, y0, w, h = rect
    n = len(STAR)
    ox, oy = x0 + (w - n) // 2, y0 + (h - n) // 2
    for y in range(n):
        for x in range(n):
            if STAR[y][x] == "#":
                px[ox + x, oy + y] = (thread["light"] if (x + y) % 2 else thread["base"]) + (255,)


def paint_texture(boxes, rarity):
    color, _ = RARITIES[rarity]
    pal = dye_palette(color, 0.7 if rarity == "Legendary" else 0.45) if color else ECRU
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
                paint_cloth(px, rect, seed, face, pal)
                if box.name == "Body" and face == "front":
                    paint_star(px, rect, THREAD)
                elif box.name == "Body" and side:
                    paint_darn(px, rect, seed, pal)
            elif m == "folds":
                paint_folds(px, rect, seed, face, pal)
            elif m == "rope":
                paint_rope(px, rect, seed, HEMP)
            elif m == "knot":
                paint_knot(px, rect, HEMP)
    return img


# =============================================================================
# Les items et leur langue
# =============================================================================

COMMENT = "Genere par tools/lootbag.py, ne pas editer a la main."


def template_json():
    """Obol_Lootbag.json, le gabarit : tout ce que les cinq sacs partagent,
    par le "Parent" vanilla (Potion_Health herite de Potion_Template). Sa
    rarete est "Template" (celle des gabarits du jeu, cachee de la
    recherche), il n'est jamais donne. Pas de "Recipe" : une lootbag ne se
    fabrique pas, elle se trouve. Ses visuels et son son sont ceux du
    Common, un repli que chaque enfant remplace.

    "Interactions" : le clic droit (interactions_json), redit par chaque
    enfant avec son propre son. Un enfant qui declare "Interactions"
    remplace l'entree "Secondary" du parent, pas la chaine dedans :
    Item herite la map du parent (lambda inherit : child.interactions =
    parent.interactions) puis decode la sienne par
    MapUtil.combineUnmodifiable(parent, child), donc cle par cle, l'enfant
    gagne (verifie au bytecode d'Item et AssetBuilderCodec le 16). Pas
    d'entree "Pickup" ici : le plugin la pose au demarrage, et seulement
    si OpenOn: Pickup (voir pickup_root_json)."""
    return {
        "$Comment": COMMENT,
        "TranslationProperties": {
            "Name": "server.items.Obol_Lootbag.name",
            "Description": "server.items.Obol_Lootbag.description",
        },
        "Quality": "Template",
        "Categories": ["Items.Tools"],
        "Icon": "Icons/ItemsGenerated/Obol_Lootbag_Common.png",
        "Model": "Items/Obol/Lootbag_Slack.blockymodel",
        "Texture": "Items/Obol/Lootbag_Common_Texture.png",
        "Scale": 0.8,
        # 50 et non 10 comme la bourse : les sacs s'accumulent pendant un
        # donjon.
        "MaxStack": 50,
        "PlayerAnimationsId": "Item",
        # Les memes que les sacs vanilla ; l'icone livree est rendue par ce
        # script avec la meme rotation.
        "IconProperties": {
            "Scale": 0.6,
            "Rotation": list(ICON_ROTATION),
            "Translation": [0.0, -15.0],
        },
        "Tags": {"Type": ["Utility"], "Family": ["Cloth_Linen"]},
        "ItemSoundSetId": "ISS_Items_Leather",
        "DropOnDeath": True,
        "Interactions": interactions_json("Common"),
    }


def interactions_json(rarity):
    """L'"Interactions" d'un sac : le clic droit, une racine en ligne (la
    forme de Root_Secondary_Consume_Potion), RequireNewClick pour qu'un
    clic maintenu n'enchaine pas. Un "Condition" vanilla sur
    l'accroupissement (la forme de Potion_Health), puis notre type
    "ObolOpenLootbag", enregistre par le plugin (OpenLootbagInteraction),
    qui retire les sacs et credite : accroupi toute la pile ("All"),
    debout un sac. Le meme "Effects" sur les deux branches, le son de la
    rarete, joue une fois par clic."""
    return {
        "Secondary": {
            "RequireNewClick": True,
            "Interactions": [
                {
                    "Type": "Condition",
                    "Crouching": True,
                    "Next": {"Type": OPEN_TYPE, "All": True, "Effects": open_effects(rarity)},
                    "Failed": {"Type": OPEN_TYPE, "All": False, "Effects": open_effects(rarity)},
                },
            ],
        },
    }


def item_json(rarity):
    """Obol_Lootbag_<Rarete>.json : le gabarit, plus le visuel, la rarete
    et le clic droit avec le son de la rarete. Rien d'autre : le cadre de
    case, le cadre et la fleche du tooltip, la couleur du nom, l'etiquette
    sous le nom et le halo au sol viennent de la rarete vanilla."""
    _, fill = RARITIES[rarity]
    return {
        "$Comment": COMMENT,
        "Parent": "Obol_Lootbag",
        "Quality": rarity,
        "Icon": "Icons/ItemsGenerated/Obol_Lootbag_%s.png" % rarity,
        "Model": "Items/Obol/Lootbag_%s.blockymodel" % fill,
        "Texture": "Items/Obol/Lootbag_%s_Texture.png" % rarity,
        "Interactions": interactions_json(rarity),
    }


def lang():
    """server.lang : le nom, herite par les cinq (l'etiquette de rarete dit
    le reste), et la description de repli d'un sac sans metadonnees. Le
    cas normal a son tooltip dans le stack (plan, §3 « Contenu »)."""
    return ("items.Obol_Lootbag.name = Lootbag\n"
            "items.Obol_Lootbag.description = A bag of coins.\n")


# =============================================================================
# L'ouverture : le son, la racine du ramassage
# =============================================================================

# Le son d'une rarete : SFX_Obol_Lootbag_Open_<Rarete> (Rarity.soundEvent()
# cote plugin).
SOUND_PREFIX = "SFX_Obol_Lootbag_Open_"
# Le type d'interaction du plugin (OpenLootbagInteraction.ID).
OPEN_TYPE = "ObolOpenLootbag"
# La racine du ramassage au sol (PickupWiring.ROOT_ID).
PICKUP_ROOT_ID = "Obol_Lootbag_Pickup"

# Le tintement de pieces, la base des cinq sons : la couche principale de
# SFX_Coins_Break (sans sa couche de metal qui casse), un peu plus aigu
# que pour un bloc, une poignee, pas un coffre. Mesure (ffmpeg
# volumedetect) : crete -10.9 dB, moyenne -28.9 dB.
COINS = ["Sounds/Blocks/Coins/Coins_Break_%02d.ogg" % i for i in range(1, 6)]

# Rarete -> (volume des pieces en dB, couches ajoutees). Une couche
# ajoutee : (fichiers, volume en dB, delai de depart en s). Regles :
#
#  - Les pieces restent la base a tous les niveaux, c'est l'identite du
#    sac. La rarete s'entend par ce qui vient au-dessus, jamais par une
#    autre base.
#  - Les deux petits sacs n'ont rien au-dessus, et le commun descend de
#    2 dB par rapport au son unique d'avant (-2) : c'est le cas de loin
#    le plus frequent, il ne doit jamais fatiguer. L'uncommon garde le
#    niveau d'avant, la difference entre les deux est voulue tenue.
#  - Chaque couche ajoutee vient APRES les pieces (0.75 s utiles), pas
#    dessus : lancee quand elles s'eteignent, sinon elle se fond dedans
#    et la rarete ne s'entend pas. Dosee en loudness (ffmpeg ebur128,
#    mixes rendus dans le scratchpad le 16) par rapport aux pieces
#    (-24 LUFS le fichier, -29 a -5 dB) : le scintillement du rare
#    (-41 LUFS le fichier, +10 dB) reste 4 dB sous elles, une traine.
#    Le carillon de l'epique (-25 LUFS, -4 dB) sonne a leur niveau. Le
#    legendaire empile les deux, un peu plus haut, la traine la plus
#    longue des cinq. Une montee, pas une autre identite : rien, rien
#    mais plus fort, une traine, un carillon, un carillon avec traine.
#    Ce qui reste apres les pieces : -33 / -50 / -31 LUFS (rare, epique,
#    legendaire), contre -49 / -58 / -40 avec le premier dosage, pose
#    au calcul sur les cretes et inaudible pour le rare. Le tout un
#    cran (3 dB) sous le dosage du 16 au soir.
#  - Rien qui soit reconnu pour autre chose que du butin (decide le
#    16) : le coffre legendaire vanilla
#    (Chest_Legendary_Open_Player_Stereo_01, essaye : le fichier
#    contient le couvercle et une montee, « une trappe qui bat, un arc
#    qu'on tend » en jeu), Memory_Restored, FanFare_1, Heal.ogg (une
#    potion), Ping_01 (de la magie) sont ecartes. Les cristaux et les
#    gemmes sont des sons de matiere, pas d'evenement.
#  - Un ramassage groupe ou une pile jouent une fois (le plugin joue la
#    rarete la plus haute), et MaxInstance 2 partout : un troisieme sac
#    dans la meme seconde ne s'entend pas.
# Scintillement de gemme, tres doux (-41 LUFS, crete -26 a -20 dB, 1 s
# puis 2 s de traine). Les variantes 01 a 04 seulement : 05, 06 et 07
# sont 13 a 18 dB plus faibles (-54 a -59 LUFS), muettes dans un mix.
SHIMMER = ["Sounds/Items/Gem/Gem_Emit_Shimmer_%02d.ogg" % i for i in range(1, 5)]
# Cristal magique, un carillon net (-25 LUFS, crete -9.2 dB, 0.9 s),
# cinq variantes au meme niveau.
CHIME = ["Sounds/Blocks/Crystal/Crystal_Magic_Sweetener_%02d.ogg" % i for i in range(1, 6)]

# Le volume d'une couche est borne par le serveur a [-100, +10] dB
# (SoundEventLayer.CODEC, Validators.range) : au-dela, l'evenement
# entier est refuse au chargement et le sac ne fait AUCUN son (vu le
# 16 en jeu avec +12 et +14 sur le scintillement, « Failed to decode
# asset ... Key: Layers.1.Volume » dans le journal). Le scintillement
# demande +10, le plafond, donc c'est les pieces qui descendent pour
# lui laisser la place, ce qui va avec une gamme entiere un cran plus
# sobre (les sacs courants sont les plus frequents et ne doivent pas
# etre omnipresents, demande le 16).
MAX_LAYER_VOLUME = 10.0

SOUNDS = {
    "Common":    (-7.0, []),
    "Uncommon":  (-5.0, []),
    # La traine sort quand les pieces s'eteignent, 4 dB sous elles.
    "Rare":      (-5.0, [(SHIMMER, MAX_LAYER_VOLUME, 0.45)]),
    # Le carillon sonne apres les pieces, a leur niveau.
    "Epic":      (-5.0, [(CHIME, -4.0, 0.35)]),
    # Le carillon 2 dB plus haut que l'epique, puis la traine, assez
    # tard pour sortir du carillon.
    "Legendary": (-5.0, [(CHIME, -2.0, 0.3), (SHIMMER, MAX_LAYER_VOLUME, 0.6)]),
}


def sound_id(rarity):
    return SOUND_PREFIX + rarity


def open_effects(rarity):
    """L'"Effects" de l'interaction d'ouverture, joue une fois par clic quel
    que soit le nombre de sacs ouverts : un son local (LocalSoundEventId),
    pour celui qui ouvre seulement, comme manger ou boire chez vanilla
    (Consume_SFX). C'est le retour de son geste, pas un evenement du
    monde : pour les autres ce serait du bruit. Rien de visuel : une
    gerbe de pieces (Particles, puis FirstPersonParticles) a ete essayee
    le 16, jugee polluante en troisieme personne et invisible en
    premiere, le son suffit."""
    return {"LocalSoundEventId": sound_id(rarity)}


def sound_event(rarity):
    """SFX_Obol_Lootbag_Open_<Rarete> : la couche de pieces, et les couches
    de la rarete s'il y en a (SOUNDS). SFX_Attn_Quiet comme parent,
    comme SFX_Coins_Break (joue en local, l'attenuation ne compte pas)."""
    coins_volume, extras = SOUNDS[rarity]
    for volume in [coins_volume] + [v for _, v, _ in extras]:
        assert -100.0 <= volume <= MAX_LAYER_VOLUME, "%s: volume %s hors de [-100, %s] dB" % (rarity, volume, MAX_LAYER_VOLUME)
    layers = [
        {
            "Files": COINS,
            "RandomSettings": {"MinPitch": -1, "MaxPitch": 2, "MinVolume": -1},
            "RoundRobinHistorySize": 2,
            "Volume": coins_volume,
        },
    ]
    for files, volume, delay in extras:
        layer = {"Files": files, "Volume": volume}
        if len(files) > 1:
            layer["RandomSettings"] = {"MinPitch": -1, "MaxPitch": 1}
            layer["RoundRobinHistorySize"] = 2
        if delay:
            layer["StartDelay"] = delay
        layers.append(layer)
    return {
        "$Comment": COMMENT,
        "Parent": "SFX_Attn_Quiet",
        "AudioCategory": "AudioCat_SFX",
        "Layers": layers,
        "Volume": 0,
        "MaxInstance": 2,
    }


def pickup_root_json():
    """Obol_Lootbag_Pickup : la chaine que le jeu execute sur le joueur
    quand il marche sur un sac au sol, si l'item declare une interaction
    "Pickup" (PlayerItemEntityPickupSystem), ce que le plugin pose sur les
    cinq items au demarrage en mode Pickup. Notre type seul : au ramassage
    la cible est l'entite item, l'interaction credite la pile entiere et
    le jeu retire l'entite. Pas d'"Effects" : une chaine lancee par le
    serveur n'est pas envoyee au client (rien a synchroniser), le plugin
    joue le son lui-meme. "OnItemChangeBehavior": "Ignore", obligatoire :
    le jeu execute cette chaine avec l'entite item pour reference (pas le
    joueur), et le controle "l'item en main a-t-il change" de
    Interaction.tick compare la case active de cette entite (elle n'a pas
    de hotbar : -1) a celle du joueur. Sans Ignore la chaine est annulee
    avant notre code, l'entite disparait et rien n'est credite (vu en jeu
    le 16)."""
    return {
        "$Comment": COMMENT,
        "Interactions": [{"Type": OPEN_TYPE, "OnItemChangeBehavior": "Ignore"}],
    }


def write_opening():
    os.makedirs(SOUND_DIR, exist_ok=True)
    stale = os.path.join(SOUND_DIR, "SFX_Obol_Lootbag_Open.json")
    if os.path.exists(stale):
        os.remove(stale)
    for rarity in RARITIES:
        with open(os.path.join(SOUND_DIR, sound_id(rarity) + ".json"), "w") as f:
            json.dump(sound_event(rarity), f, indent=2)
            f.write("\n")
    os.makedirs(ROOT_DIR, exist_ok=True)
    with open(os.path.join(ROOT_DIR, PICKUP_ROOT_ID + ".json"), "w") as f:
        json.dump(pickup_root_json(), f, indent=2)
        f.write("\n")


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
    for path in (MODEL_DIR, ICON_DIR, ITEM_DIR, LANG_DIR):
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
    for rarity, (color, fill) in RARITIES.items():
        boxes, model = models[fill]
        texture = paint_texture(boxes, rarity)
        texture.save(os.path.join(MODEL_DIR, "Lootbag_%s_Texture.png" % rarity))
        # meme echelle pour les trois remplissages : un sac affaisse est plus bas
        icon = render_icon(model, texture, rotation=ICON_ROTATION,
                           fit=0.9 * height(boxes) / tallest)
        icon.save(os.path.join(ICON_DIR, "Obol_Lootbag_%s.png" % rarity))
        icons.append(icon)

    with open(os.path.join(ITEM_DIR, "Obol_Lootbag.json"), "w") as f:
        json.dump(template_json(), f, indent=2)
        f.write("\n")
    for rarity in RARITIES:
        with open(os.path.join(ITEM_DIR, "Obol_Lootbag_%s.json" % rarity), "w") as f:
            json.dump(item_json(rarity), f, indent=2)
            f.write("\n")
    with open(os.path.join(LANG_DIR, "server.lang"), "w") as f:
        f.write(lang())
    write_opening()

    boxes, model = models["Tight"]
    preview(icons, paint_texture(boxes, "Legendary"), model)
    print("ok")
