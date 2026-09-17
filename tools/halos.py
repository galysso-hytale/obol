#!/usr/bin/env python3
"""
Generateur des halos au sol de l'add-on Obol Purse.

Un halo par palier de pieces, sobre et explicite : la meme empreinte que
n'importe quel item tombe (le disque de lumiere au sol et le rayon de
Drop_Common, ceux du systeme "Item" par defaut), a la couleur de la
piece, et au-dessus des pieces du palier qui montent lentement et
s'effacent. Pas de fumee, de vortex ni d'etincelles : ce vocabulaire est
celui des artefacts. Le halo dit « il y a des pieces la, et de quel
metal ».

Un leger increment par palier, dans le meme vocabulaire : plus le metal
est precieux, plus il y a de pieces qui montent (deux a cinq a la fois)
et plus le rayon est franc (de la moitie a la pleine opacite du rayon
vanilla). Le mythril seul ajoute une lueur douce autour de la bourse
(le Glow de Drop_Rare, recolore, a moitie d'opacite), en echo a sa
Light : il luit, c'est tout ce qui le distingue.

Les pieces sont les images de l'API d'Obol (Denomination.texture(),
core/.../Common/UI/Custom/Obol/<Palier>.png), copiees dans le pack sous
Common/Particles/Textures/Obol/, le dossier ou le jeu range toutes ses
textures de particules.

Produit dans addons/purse/src/main/resources/ :
  Server/Particles/Drop/Obol/<Palier>/Drop_Obol_<Palier>.particlesystem
  Server/Particles/Drop/Obol/<Palier>/Spawners/Drop_Obol_<Palier>_Ground.particlespawner
  Server/Particles/Drop/Obol/<Palier>/Spawners/Drop_Obol_<Palier>_Ray.particlespawner
  Server/Particles/Drop/Obol/<Palier>/Spawners/Drop_Obol_<Palier>_Coins.particlespawner
  Server/Particles/Drop/Obol/Mythril/Spawners/Drop_Obol_Mythril_Glow.particlespawner
  Common/Particles/Textures/Obol/<Palier>.png

Le disque, le rayon et la lueur sont ceux de Drop_Common et Drop_Rare, lus
dans Assets.zip, leurs couleurs remplacees par celle de la piece, un peu
eclaircie, leurs opacites mises a l'echelle du palier. Deterministe,
sans dependance. Les couleurs des
pieces sont celles de Denomination.color() dans l'API d'Obol.
"""

import json
import os
import re
import shutil
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "addons", "purse", "src", "main", "resources")
OUT = os.path.join(RES, "Server", "Particles", "Drop", "Obol")
TEXTURES = os.path.join(RES, "Common", "Particles", "Textures", "Obol")
COINS = os.path.join(ROOT, "core", "src", "main", "resources", "Common", "UI", "Custom", "Obol")
ASSETS_ZIP = os.path.expanduser(
    "~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/install/release/package/game/latest/Assets.zip")

# L'argent de l'API (#C0C0C0) est un gris : en lumiere ajoutee, du gris
# est du blanc, le rayon serait celui de n'importe quel item. Une pointe de
# froid pour qu'il se lise « metal ». Le texte et le HUD gardent le gris.
SILVER_LIGHT = "#B8CCE0"

# Palier -> (couleur de la piece, pieces a la fois, pieces par seconde,
#            opacite du rayon par rapport au rayon vanilla)
TIERS = {
    "Copper": ("#B87333", 2, 0.6, 0.5),
    "Silver": (SILVER_LIGHT, 3, 0.8, 0.65),
    "Gold": ("#FFD700", 4, 1.0, 0.8),
    "Mythril": ("#7FDBFF", 5, 1.3, 1.0),
}
GLOW_OPACITY = 0.5   # la lueur du mythril, par rapport au Glow de Drop_Rare

HEX = re.compile(r"^#([0-9a-fA-F]{6})$")


def hex_to_rgb(text):
    return tuple(int(text[i:i + 2], 16) / 255 for i in (1, 3, 5))


def rgb_to_hex(rgb):
    return "#%02x%02x%02x" % tuple(round(max(0.0, min(1.0, c)) * 255) for c in rgb)


def recolor(source, tier_color):
    """La couleur de la piece, un peu eclaircie. Le disque vanilla est
    presque blanc (#e3faff) : garder sa luminosite effacerait la teinte,
    on prend celle de la piece a la place, la source ne compte plus."""
    r, g, b = hex_to_rgb(tier_color)
    return rgb_to_hex(tuple(c + (1.0 - c) * 0.25 for c in (r, g, b)))


def recolor_tree(node, tier_color):
    if isinstance(node, dict):
        return {k: (recolor(v, tier_color) if k == "Color" and isinstance(v, str) and HEX.match(v)
                    else recolor_tree(v, tier_color))
                for k, v in node.items()}
    if isinstance(node, list):
        return [recolor_tree(v, tier_color) for v in node]
    return node


def scale_opacity(node, factor):
    """`node` avec toutes ses cles Opacity multipliees par `factor`."""
    if isinstance(node, dict):
        return {k: (round(v * factor, 3) if k == "Opacity" and isinstance(v, (int, float))
                    else scale_opacity(v, factor))
                for k, v in node.items()}
    if isinstance(node, list):
        return [scale_opacity(v, factor) for v in node]
    return node


def coins_spawner(tier, at_once, per_second):
    """Des pieces du palier qui montent doucement au-dessus de la bourse.
    Sprite plein (BlendLinear, face a la camera), sans filtrage pour
    garder le pixel art net, de 12 a 16 centiemes de bloc, une seconde et
    demie a deux et demie de vie, fondu a l'apparition et a la fin."""
    scale = {"X": {"Min": 0.12, "Max": 0.16}, "Y": {"Min": 0.12, "Max": 0.16}}
    return {
        "RenderMode": "BlendLinear",
        "EmitOffset": {
            "X": {"Min": -0.25, "Max": 0.25},
            "Y": {"Min": 0.0, "Max": 0.1},
            "Z": {"Min": -0.25, "Max": 0.25},
        },
        "ParticleRotationInfluence": "Billboard",
        "MaxConcurrentParticles": at_once,
        "ParticleLifeSpan": {"Min": 1.6, "Max": 2.4},
        "ParticleRotateWithSpawner": False,
        "TrailSpawnerPositionMultiplier": 1,
        "TrailSpawnerRotationMultiplier": 1,
        "SpawnRate": {"Min": round(per_second * 0.8, 2), "Max": round(per_second * 1.2, 2)},
        "InitialVelocity": {
            "Pitch": {"Min": 90.0, "Max": 90.0},
            "Speed": {"Min": 0.12, "Max": 0.2},
        },
        "Particle": {
            "Texture": "Particles/Textures/Obol/%s.png" % tier,
            "ScaleRatioConstraint": "OneToOne",
            "UVOption": "None",
            "Animation": {
                "0": {"Opacity": 0.0},
                "15": {"Opacity": 1.0},
                "65": {"Opacity": 1.0},
                "100": {"Opacity": 0.0},
            },
            "InitialAnimationFrame": {"Scale": scale, "Opacity": 0.0, "Color": "#ffffff"},
        },
        "LinearFiltering": False,
    }


def system(tier, glow):
    """Le systeme : disque, rayon, pieces, et la lueur pour le mythril.
    Offsets et delais de depart de Drop_Common et Drop_Rare."""
    prefix = "Drop_Obol_%s" % tier
    spawners = [
        {"SpawnerId": prefix + "_Ground", "PositionOffset": {"Y": 0.1},
         "FixedRotation": True, "StartDelay": 0.5},
        {"SpawnerId": prefix + "_Ray", "PositionOffset": {"Y": 0.6}, "StartDelay": 0.2},
        {"SpawnerId": prefix + "_Coins", "PositionOffset": {"Y": 0.25}, "StartDelay": 0.8},
    ]
    if glow:
        spawners.insert(1, {"SpawnerId": prefix + "_Glow", "StartDelay": 0.5})
    return {"CullDistance": 30.0, "Spawners": spawners}


def dump(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    shutil.rmtree(OUT, ignore_errors=True)
    os.makedirs(TEXTURES, exist_ok=True)
    with zipfile.ZipFile(ASSETS_ZIP) as z:
        ground = json.loads(z.read("Server/Particles/Drop/Common/Spawners/Drop_Common_Ground.particlespawner"))
        ray = json.loads(z.read("Server/Particles/Drop/Common/Spawners/Drop_Common_Ray.particlespawner"))
        glow = json.loads(z.read("Server/Particles/Drop/Rare/Spawners/Drop_Rare_Glow.particlespawner"))
    for tier, (color, at_once, per_second, ray_opacity) in TIERS.items():
        prefix = "Drop_Obol_%s" % tier
        spawners = os.path.join(OUT, tier, "Spawners")
        dump(os.path.join(spawners, prefix + "_Ground.particlespawner"), recolor_tree(ground, color))
        dump(os.path.join(spawners, prefix + "_Ray.particlespawner"),
             scale_opacity(recolor_tree(ray, color), ray_opacity))
        dump(os.path.join(spawners, prefix + "_Coins.particlespawner"),
             coins_spawner(tier, at_once, per_second))
        is_mythril = tier == "Mythril"
        if is_mythril:
            dump(os.path.join(spawners, prefix + "_Glow.particlespawner"),
                 scale_opacity(recolor_tree(glow, color), GLOW_OPACITY))
        dump(os.path.join(OUT, tier, prefix + ".particlesystem"), system(tier, is_mythril))
        shutil.copyfile(os.path.join(COINS, tier + ".png"), os.path.join(TEXTURES, tier + ".png"))
        print("%s: %d coins at once, %.1f/s, ray %.0f%%%s" % (
            tier, at_once, per_second, ray_opacity * 100, ", glow" if is_mythril else ""))


if __name__ == "__main__":
    main()
