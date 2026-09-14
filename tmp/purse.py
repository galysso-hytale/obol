#!/usr/bin/env python3
"""
Generateur de l'item bourse de l'add-on Obol Purse.

Un seul item, Obol_Purse, quel que soit son contenu. Il est construit comme
un item vanilla : un modele 3D .blockymodel (l'objet tenu en main, au sol,
dans un coffre), une texture peinte 64x64 et une icone rendue depuis ce
modele avec la camera des icones vanilla (IconProperties.Rotation
[22.5, 45, 22.5]), comme le fait le pipeline « ItemsGenerated » du jeu.

Produit dans addons/purse/src/main/resources/ :
  Common/Items/Obol/Purse.blockymodel
  Common/Items/Obol/Purse_Texture.png
  Common/Icons/ItemsGenerated/Obol_Purse.png
  Server/Item/Items/Obol/Obol_Purse.json
  Server/Languages/en-US/server.lang

et dans tmp/ :
  _purse_apercu.png     icone x3, icone a 24 px, texture x3, trois vues du modele
  _purse_check.png      la routine de rendu appliquee au Feedbag vanilla, a cote
                        de l'icone Food_Flour livree par le jeu : si les deux se
                        ressemblent, les conventions (faces, rotations, lumiere)
                        sont bonnes.

Sans dependance autre que Pillow. Deterministe (graine fixe).
"""

import json
import math
import os
import random
import zipfile

from PIL import Image, ImageChops, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "addons", "purse", "src", "main", "resources")
MODEL_DIR = os.path.join(RES, "Common", "Items", "Obol")
ICON_DIR = os.path.join(RES, "Common", "Icons", "ItemsGenerated")
ITEM_DIR = os.path.join(RES, "Server", "Item", "Items", "Obol")
LANG_DIR = os.path.join(RES, "Server", "Languages", "en-US")
TMP = os.path.join(ROOT, "tmp")

ASSETS_ZIP = os.path.expanduser(
    "~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/install/release/package/game/latest/Assets.zip")

TEX_SIZE = 64
ICON_SIZE = 64
ICON_ROTATION = (22.5, 30.0, 10.0)   # IconProperties.Rotation : vanilla est
                                      # (22.5, 45, 22.5), ici moins de lacet pour
                                      # voir la face et le tampon, comme le Feedbag

random.seed(0x0B01)


# =============================================================================
# Maths : quaternions (x, y, z, w) et vecteurs
# =============================================================================

def q_mul(a, b):
    ax, ay, az, aw = a
    bx, by, bz, bw = b
    return (aw * bx + ax * bw + ay * bz - az * by,
            aw * by - ax * bz + ay * bw + az * bx,
            aw * bz + ax * by - ay * bx + az * bw,
            aw * bw - ax * bx - ay * by - az * bz)


def q_rot(q, v):
    x, y, z, w = q
    vx, vy, vz = v
    # v' = v + 2 w (q x v) + 2 q x (q x v)
    cx, cy, cz = y * vz - z * vy, z * vx - x * vz, x * vy - y * vx
    return (vx + 2 * (w * cx + y * cz - z * cy),
            vy + 2 * (w * cy + z * cx - x * cz),
            vz + 2 * (w * cz + x * cy - y * cx))


def q_axis(axis, degrees):
    h = math.radians(degrees) / 2
    s = math.sin(h)
    return (axis[0] * s, axis[1] * s, axis[2] * s, math.cos(h))


def q_round(q):
    return {"x": round(q[0], 6), "y": round(q[1], 6), "z": round(q[2], 6), "w": round(q[3], 6)}


def add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


# =============================================================================
# Le modele : des boites, comme les items vanilla
#
# Unites : 1 = 1/16 de bloc. Le modele est pose sur y = 0, centre en x, z.
# Faces : front = +Z, back = -Z, right = +X, left = -X, top = +Y, bottom = -Y
# (convention supposee du jeu, a verifier en jeu : le tampon Obol doit etre
# sur le devant de la bourse quand elle est tenue).
# =============================================================================

class Box:
    def __init__(self, name, size, position, material, orientation=(0, 0, 0, 1)):
        self.name = name
        self.size = size
        self.position = position
        self.orientation = orientation
        self.material = material
        self.uv = {}          # face -> (u0, v0, w, h) dans la texture


FACES = ("front", "back", "right", "left", "top", "bottom")
# Materiaux dont toutes les boites partagent leurs regions de texture.
SHARED_MATERIALS = ("tassel", "cord")


def face_dims(size, face):
    w, h, d = size
    if face in ("front", "back"):
        return w, h
    if face in ("right", "left"):
        return d, h
    return w, d


def purse_boxes():
    """La bourse, a la maniere des items vanilla (l'oeuf est un cube, le sac de
    farine trois boites) : peu de boites, la rondeur dans la texture.

    Son identite, comme celle des pieces, tient a une silhouette et une
    couleur : un ventre a huit pans (deux boites croisees a 45 degres, ce que
    fait Hytale pour ses bouchons de potion, une etoile a huit branches vue de
    dessus comme l'or d'Obol), un cuir lie-de-vin qu'aucun item vanilla ne
    porte, et la cordelette en or. Pas d'emblème : rien a lire, juste a
    reconnaitre."""
    b = []
    b.append(Box("Bottom", (11, 2, 11), (0, 1, 0), "body"))
    b.append(Box("Belly", (13, 8, 13), (0, 6, 0), "body"))
    b.append(Box("Belly_X", (12, 7.5, 12), (0, 6, 0), "body",
                 orientation=q_axis((0, 1, 0), 45)))
    b.append(Box("Shoulder", (9, 2.5, 9), (0, 11.25, 0), "body",
                 orientation=q_axis((0, 1, 0), 45)))
    b.append(Box("Neck", (6, 3, 6), (0, 14, 0), "pleats"))
    b.append(Box("Cord", (7.5, 1.5, 7.5), (0, 13.75, 0), "cord"))
    b.append(Box("Knot", (2.5, 2.5, 1.2), (0, 13.75, 4.2), "cord"))
    b.append(Box("Tassel_L", (1, 5, 1), (-1.0, 10.5, 4.4), "tassel"))
    b.append(Box("Tassel_R", (1, 3.5, 1), (1.2, 11.25, 4.4), "tassel"))
    # le fronce : deux boites croisees, une etoile a huit branches vue de dessus
    b.append(Box("Top", (7, 3.5, 7), (0, 17.25, 0), "pleats",
                 orientation=q_axis((1, 0, 0), 4)))
    b.append(Box("Top_X", (6.5, 3.2, 6.5), (0, 17.35, 0), "pleats",
                 orientation=q_mul(q_axis((0, 1, 0), 45), q_axis((0, 0, 1), -5))))
    return b


def pack_uv(boxes):
    """Range chaque face de chaque boite dans la texture, par etageres, les
    plus hautes d'abord. Les faces identiques d'une meme boite (front/back,
    right/left, top/bottom) partagent une region quand le materiau est
    symetrique, pour garder de la place."""
    wanted = []      # (h, w, box, face, key)
    for box in boxes:
        for face in FACES:
            w, h = (math.ceil(v) for v in face_dims(box.size, face))
            side = {"front": "fb", "back": "fb", "right": "lr", "left": "lr",
                    "top": "tb", "bottom": "tb"}[face]
            if box.material == "pleats" and side == "tb":
                side = face             # dessus fronce, dessous cuir
            owner = box.material if box.material in SHARED_MATERIALS else id(box)
            wanted.append((h, w, box, face, (owner, w, h, side)))
    wanted.sort(key=lambda t: (-t[0], -t[1]))

    shelves = []   # [y, height, x_cursor]

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
            x, y = alloc(w, h)
            placed[key] = (x, y, w, h)
        box.uv[face] = placed[key]


def model_json(boxes):
    """Le .blockymodel : la hierarchie vanilla (R-Attachment > Origin_Projectile
    > Origin_Item) puis les boites en enfants directs d'Origin_Item."""
    next_id = [0]

    def nid():
        next_id[0] += 1
        return str(next_id[0])

    def vec(v):
        return {"x": round(v[0], 4), "y": round(v[1], 4), "z": round(v[2], 4)}

    def empty(name, children):
        return {"id": nid(), "name": name, "children": children,
                "position": vec((0, 0, 0)), "orientation": q_round((0, 0, 0, 1)),
                "shape": {"type": "none", "settings": {"isPiece": False}}}

    def box_node(box):
        layout = {}
        for face in FACES:
            u, v, _, _ = box.uv[face]
            layout[face] = {"offset": {"x": u, "y": v},
                            "mirror": {"x": False, "y": False}, "angle": 0}
        return {
            "id": nid(), "name": box.name, "children": [],
            "position": vec(box.position), "orientation": q_round(box.orientation),
            "shape": {
                "type": "box",
                "offset": vec((0, 0, 0)),
                "stretch": vec((1, 1, 1)),
                "settings": {"size": vec(box.size)},
                "visible": True, "doubleSided": False,
                "shadingMode": "standard", "unwrapMode": "custom",
                "textureLayout": layout,
            },
        }

    root = empty("R-Attachment", [empty("Origin_Projectile",
                                        [empty("Origin_Item", [box_node(b) for b in boxes])])])
    return {"lod": "auto", "nodes": [root]}


# =============================================================================
# La texture : peinte procéduralement, dans l'esprit des textures vanilla
# (aplats chauds, ombres douces, grain leger, pas de contour noir)
# =============================================================================

# Cuir sang-de-boeuf, quatre tons comme les textures vanilla. Rouge chaud
# (teinte ~7 degres) pour s'accorder au cuivre et a l'or, et assez clair
# pour ne pas se fondre dans le bleu nuit des cases d'inventaire (V ~0.3).
LEATHER = {"light": (214, 118, 88), "base": (168, 70, 56), "dark": (112, 42, 38),
           "seam": (60, 20, 20)}
# Cordelette en or, la couleur des pieces d'Obol.
CORD = {"light": (255, 226, 120), "base": (224, 176, 44), "dark": (162, 112, 26)}
HOLE = (48, 16, 18)


def lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def value_noise(w, h, cells, seed):
    """Bruit de valeur lisse en [0, 1], w x h, `cells` cases par cote."""
    rnd = random.Random(seed)
    grid = [[rnd.random() for _ in range(cells + 1)] for _ in range(cells + 1)]
    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        fy = y / h * cells
        iy, ty = int(fy), fy - int(fy)
        ty = ty * ty * (3 - 2 * ty)
        for x in range(w):
            fx = x / w * cells
            ix, tx = int(fx), fx - int(fx)
            tx = tx * tx * (3 - 2 * tx)
            a = grid[iy][ix] + (grid[iy][ix + 1] - grid[iy][ix]) * tx
            b = grid[iy + 1][ix] + (grid[iy + 1][ix + 1] - grid[iy + 1][ix]) * tx
            out[y][x] = a + (b - a) * ty
    return out


def paint_body(px, rect, seed, face):
    """Cuir d'une face du corps, rondeur peinte : clair vers le centre-haut,
    sombre vers les bords (le vignettage des textures vanilla), un grain
    leger."""
    x0, y0, w, h = rect
    noise = value_noise(w, h, 3, seed)
    rnd = random.Random(seed + 1)
    cx, cy = (w - 1) / 2, (h - 1) * 0.38     # le point clair un peu au-dessus du centre
    for y in range(h):
        for x in range(w):
            dx, dy = (x - cx) / max(1, cx), (y - cy) / max(1, (h - 1) / 2)
            d = min(1.0, math.hypot(dx, dy * 0.9))
            if face == "bottom":
                c = lerp(LEATHER["base"], LEATHER["dark"], 0.4 + 0.4 * d)
            elif face == "top":
                c = lerp(LEATHER["base"], LEATHER["dark"], 0.25 + 0.35 * d)
            else:
                c = lerp(LEATHER["light"], LEATHER["base"], 0.25 + 0.75 * d * d)
                c = lerp(c, LEATHER["dark"], max(0.0, d - 0.55) * 1.2)
            c = lerp(c, LEATHER["dark"], noise[y][x] * 0.18)
            g = rnd.randint(-4, 4)
            px[x0 + x, y0 + y] = tuple(max(0, min(255, v + g)) for v in c) + (255,)


def paint_pleats(px, rect, seed):
    """Tissu fronce : colonnes claires et moyennes, qui s'assombrissent vers
    le bas, la ou le cordon serre."""
    x0, y0, w, h = rect
    rnd = random.Random(seed)
    for x in range(w):
        col = (LEATHER["light"], LEATHER["base"], LEATHER["base"])[(x + rnd.randint(0, 1)) % 3]
        for y in range(h):
            c = lerp(col, LEATHER["dark"], 0.5 * (y / max(1, h - 1)))
            px[x0 + x, y0 + y] = c + (255,)


def paint_hole(px, rect):
    """Dessus du fronce : les plis convergent vers un petit trou sombre au
    centre, la bourse est serree, pas ouverte."""
    x0, y0, w, h = rect
    cx, cy = (w - 1) / 2, (h - 1) / 2
    for y in range(h):
        for x in range(w):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            if d <= 1.2:
                c = HOLE
            else:
                sector = int(((math.atan2(dy, dx) + math.pi) / (2 * math.pi)) * 8) % 8
                c = (LEATHER["light"], LEATHER["base"])[sector % 2]
                c = lerp(c, LEATHER["dark"], max(0.0, 1 - d / 3) * 0.7)
            px[x0 + x, y0 + y] = c + (255,)


def paint_cord(px, rect, seed):
    x0, y0, w, h = rect
    rnd = random.Random(seed)
    for y in range(h):
        for x in range(w):
            k = (x + y) % 3
            c = (CORD["light"], CORD["base"], CORD["dark"])[k]
            g = rnd.randint(-5, 5)
            px[x0 + x, y0 + y] = tuple(max(0, min(255, v + g)) for v in c) + (255,)


def paint_tassel(px, rect):
    x0, y0, w, h = rect
    for y in range(h):
        for x in range(w):
            c = CORD["dark"] if y % 2 else CORD["base"]
            if y == h - 1:
                c = CORD["light"]
            px[x0 + x, y0 + y] = c + (255,)


def paint_texture(boxes):
    img = Image.new("RGBA", (TEX_SIZE, TEX_SIZE), (0, 0, 0, 0))
    px = img.load()
    painted = set()
    seed = 7
    for box in boxes:
        for face in FACES:
            rect = box.uv[face]
            if rect in painted:
                continue
            painted.add(rect)
            seed += 13
            m = box.material
            if m == "body":
                paint_body(px, rect, seed, face)
            elif m == "pleats":
                if face == "top":
                    paint_hole(px, rect)
                elif face == "bottom":
                    paint_body(px, rect, seed, "bottom")
                else:
                    paint_pleats(px, rect, seed)
            elif m == "cord":
                paint_cord(px, rect, seed)
            elif m == "tassel":
                paint_tassel(px, rect)
    return img


# =============================================================================
# Rendu d'icone : projection orthographique du modele, camera des icones vanilla
# =============================================================================

FACE_FRAMES = {
    # face: (normale, direction u, direction v) en repere local de la boite.
    # u va vers la droite du texel, v vers le bas.
    "front":  ((0, 0, 1),  (1, 0, 0),  (0, -1, 0)),
    "back":   ((0, 0, -1), (-1, 0, 0), (0, -1, 0)),
    "right":  ((1, 0, 0),  (0, 0, -1), (0, -1, 0)),
    "left":   ((-1, 0, 0), (0, 0, 1),  (0, -1, 0)),
    "top":    ((0, 1, 0),  (1, 0, 0),  (0, 0, 1)),
    "bottom": ((0, -1, 0), (1, 0, 0),  (0, 0, -1)),
}

# Lumiere : par face, ambiante + diffuse d'une source haut / avant / droite,
# comme les icones vanilla (dessus le plus clair, devant moyen, cote sombre).
LIGHT_DIR = (0.35, 0.6, 0.72)


def load_blockymodel(data):
    """Aplatit un .blockymodel en une liste de (boite, quaternion monde,
    position monde, textureLayout)."""
    out = []

    def walk(node, ppos, prot):
        pos = node.get("position", {"x": 0, "y": 0, "z": 0})
        rot = node.get("orientation", {"x": 0, "y": 0, "z": 0, "w": 1})
        lq = (rot["x"], rot["y"], rot["z"], rot["w"])
        wpos = add(ppos, q_rot(prot, (pos["x"], pos["y"], pos["z"])))
        wrot = q_mul(prot, lq)
        shape = node.get("shape") or {}
        if shape.get("type") == "box":
            s = shape["settings"]["size"]
            o = shape.get("offset", {"x": 0, "y": 0, "z": 0})
            st = shape.get("stretch", {"x": 1, "y": 1, "z": 1})
            out.append({
                "size": (s["x"] * st["x"], s["y"] * st["y"], s["z"] * st["z"]),
                "center": add(wpos, q_rot(wrot, (o["x"], o["y"], o["z"]))),
                "rot": wrot,
                "layout": shape.get("textureLayout", {}),
                "visible": shape.get("visible", True),
            })
        for c in node.get("children", []):
            walk(c, wpos, wrot)

    for n in data["nodes"]:
        walk(n, (0, 0, 0), (0, 0, 0, 1))
    return out


def render_icon(model, texture, rotation=ICON_ROTATION, size=ICON_SIZE, fit=0.9,
                supersample=4, perspective=3.0, pitch=15.0):
    """Rend le modele vu par la camera des icones : modele tourne de `rotation`
    (degres X, Y, Z), repere main gauche comme le jeu (X ecran inverse),
    perspective avec la camera a `perspective` fois la taille du modele.
    Faces triees de l'arriere vers l'avant, chaque face un affine de sa region
    de texture (approximation de la perspective, exacte en orthographique).
    Sur-echantillonne puis reduit pour lisser les bords, comme les icones du
    jeu."""
    boxes = load_blockymodel(model)
    # Etabli par comparaison avec les icones livrees (voir _purse_check.png,
    # rendu avec la rotation vanilla (22.5, 45, 22.5) et pitch 20) : le jeu
    # applique X, puis Z, puis Y ; son repere est main gauche, d'ou le signe
    # inverse de Y et Z dans ces maths main droite ; sa camera regarde l'item
    # d'un peu plus haut que la rotation seule ne le dit (`pitch`) et d'assez
    # pres (`perspective`, distance en multiples de la taille du modele).
    rx, ry, rz = rotation
    cam = (0, 0, 0, 1)
    for step in (q_axis((1, 0, 0), rx), q_axis((0, 0, 1), -rz), q_axis((0, 1, 0), -ry),
                 q_axis((1, 0, 0), pitch)):
        cam = q_mul(step, cam)

    faces = []
    for b in boxes:
        if not b["visible"]:
            continue
        hx, hy, hz = (v / 2 for v in b["size"])
        for face, (n, u, v) in FACE_FRAMES.items():
            lay = b["layout"].get(face)
            if lay is None:
                continue
            fw, fh = face_dims(b["size"], face)
            # coin haut-gauche du texel (0,0) de la face, en local. Un stretch
            # negatif donne des dimensions negatives : la boite est retournee
            # et ses faces s'inversent d'elles-memes.
            c = tuple(n[i] * (hx, hy, hz)[i] - u[i] * fw / 2 - v[i] * fh / 2 for i in range(3))
            pts_local = [c,
                         tuple(c[i] + u[i] * fw for i in range(3)),
                         tuple(c[i] + u[i] * fw + v[i] * fh for i in range(3)),
                         tuple(c[i] + v[i] * fh for i in range(3))]
            world = [q_rot(cam, add(b["center"], q_rot(b["rot"], p))) for p in pts_local]
            depth = sum(p[2] for p in world) / 4
            faces.append((depth, world, (lay["offset"]["x"], lay["offset"]["y"], abs(fw), abs(fh)),
                          lay.get("mirror", {})))

    # projection : perspective depuis une camera sur +Z, puis cadrage sur la
    # boite englobante des points projetes
    pts = [p for f in faces for p in f[1]]
    zs = [p[2] for p in pts]
    span = max(max(p[0] for p in pts) - min(p[0] for p in pts),
               max(p[1] for p in pts) - min(p[1] for p in pts))
    cam_z = (min(zs) + max(zs)) / 2 + span * perspective if perspective else None

    cz = (min(zs) + max(zs)) / 2

    def project(p):
        k = (cam_z - cz) / (cam_z - p[2]) if perspective else 1
        return (-p[0] * k, p[1] * k)     # X inverse : repere main gauche

    proj = [project(p) for p in pts]
    xs = [p[0] for p in proj]
    ys = [p[1] for p in proj]
    S = size * supersample
    scale = S * fit / max(max(xs) - min(xs), max(ys) - min(ys))
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2

    def screen(p):
        x, y = project(p)
        return ((x - cx) * scale + S / 2, -(y - cy) * scale + S / 2)

    canvas = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    faces.sort(key=lambda f: f[0])
    for _, world, (u0, v0, fw, fh), mirror in faces:
        p0, p1, p2, p3 = (screen(p) for p in world)
        ax, ay = p1[0] - p0[0], p1[1] - p0[1]
        bx, by = p3[0] - p0[0], p3[1] - p0[1]
        det = ax * by - ay * bx
        if det >= -1e-6:
            continue          # face tournee vers l'arriere (ou vue par la tranche)
        # normale geometrique en monde (camera), pour la lumiere
        e1 = tuple(world[1][i] - world[0][i] for i in range(3))
        e2 = tuple(world[3][i] - world[0][i] for i in range(3))
        nrm = (-(e1[1] * e2[2] - e1[2] * e2[1]),
               -(e1[2] * e2[0] - e1[0] * e2[2]),
               -(e1[0] * e2[1] - e1[1] * e2[0]))
        ln = math.sqrt(sum(v * v for v in nrm)) or 1
        nrm = tuple(v / ln for v in nrm)
        shade = 0.68 + 0.32 * max(0.0, sum(nrm[i] * LIGHT_DIR[i] for i in range(3)))
        # affine ecran -> texture : p0 -> (u0,v0), p1 -> (u0+fw,v0), p3 -> (u0,v0+fh)
        ia, ib = by / det, -bx / det
        ic, id_ = -ay / det, ax / det
        mu0, mu1 = (u0 + fw, -fw) if mirror.get("x") else (u0, fw)
        mv0, mv1 = (v0 + fh, -fh) if mirror.get("y") else (v0, fh)
        a = ia * mu1
        bb = ib * mu1
        c = mu0 - (a * p0[0] + bb * p0[1])
        d = ic * mv1
        e = id_ * mv1
        f = mv0 - (d * p0[0] + e * p0[1])
        warped = texture.transform((S, S), Image.AFFINE, (a, bb, c, d, e, f), Image.NEAREST)
        r, g, b_, al = warped.split()
        r = r.point(lambda v: int(v * shade))
        g = g.point(lambda v: int(v * shade))
        b_ = b_.point(lambda v: int(v * shade))
        mask = Image.new("L", (S, S), 0)
        ImageDraw.Draw(mask).polygon([p0, p1, p2, p3], fill=255)
        warped = Image.merge("RGBA", (r, g, b_, ImageChops.multiply(al, mask)))
        canvas = Image.alpha_composite(canvas, warped)
    return canvas.resize((size, size), Image.LANCZOS)


# =============================================================================
# L'item et sa langue
# =============================================================================

def item_json():
    """Obol_Purse.json, calque sur les items a modele vanilla (quiver, sacs).
    Un seul item : la bourse est une cle vers un wallet d'Obol, le stack ne
    porte que cette cle et un tooltip (ItemDisplayMetadata) qui dit le
    montant. Le clic droit ouvre la page "ObolPurse", fournie par le plugin
    (PursePageSupplier) : page de la bourse, ou popup de don si un joueur est
    vise, comme le kit de reparation vanilla (Tool_Repair_Kit_Crude.json)."""
    return {
        "$Comment": "Genere par tmp/purse.py, ne pas editer a la main.",
        "TranslationProperties": {
            "Name": "server.items.Obol_Purse.name",
            "Description": "server.items.Obol_Purse.description",
        },
        "Categories": ["Items.Tools"],
        "Icon": "Icons/ItemsGenerated/Obol_Purse.png",
        "Model": "Items/Obol/Purse.blockymodel",
        "Texture": "Items/Obol/Purse_Texture.png",
        "Scale": 0.8,
        "Quality": "Common",
        "ItemLevel": 5,
        "MaxStack": 10,
        "PlayerAnimationsId": "Item",
        # Les memes que les sacs vanilla ; l'icone livree est rendue par ce
        # script avec la meme rotation.
        "IconProperties": {
            "Scale": 0.6,
            "Rotation": list(ICON_ROTATION),
            "Translation": [0.0, -15.0],
        },
        "Recipe": {
            "Input": [
                {"ItemId": "Ingredient_Leather_Light", "Quantity": 2},
                {"ItemId": "Ingredient_Fabric_Scrap_Wool", "Quantity": 1},
            ],
            # A trancher en jeu : a la main ou sur un etabli.
            "BenchRequirement": [],
        },
        "Tags": {"Type": ["Utility"], "Family": ["Leather"]},
        "ItemSoundSetId": "ISS_Items_Leather",
        "DropOnDeath": True,
        "Interactions": {
            "Secondary": {
                "Interactions": [
                    {"Type": "OpenCustomUI", "Page": {"Id": "ObolPurse"}},
                ],
            },
        },
    }


def lang():
    return ("items.Obol_Purse.name = Purse\n"
            "items.Obol_Purse.description = A leather purse. Right-click to put coins in "
            "or take them out, right-click a player to hand them what it holds.\n")


# =============================================================================
# Planches de controle
# =============================================================================

def zoom(img, k):
    return img.resize((img.width * k, img.height * k), Image.NEAREST)


def preview(icon, texture, model):
    views = [render_icon(model, texture, rotation=r, size=96, perspective=0, pitch=0)
             for r in ((0, 0, 0), (0, 90, 0), (90, 0, 0))]
    w = 64 * 3 + 8 + 24 + 8 + 64 * 3 + 8 + len(views) * (96 + 8) + 8
    sheet = Image.new("RGBA", (w, 64 * 3 + 16), (28, 30, 36, 255))
    x = 8
    sheet.paste(zoom(icon, 3), (x, 8), zoom(icon, 3))
    x += 64 * 3 + 8
    small = icon.resize((24, 24), Image.LANCZOS)
    sheet.paste(small, (x, 8), small)
    x += 24 + 8
    sheet.paste(zoom(texture, 3), (x, 8), zoom(texture, 3))
    x += 64 * 3 + 8
    for v in views:
        sheet.paste(v, (x, 8), v)
        x += 96 + 8
    sheet.save(os.path.join(TMP, "_purse_apercu.png"))


def check_against_vanilla():
    """Rend le Feedbag vanilla avec notre routine, a cote de l'icone livree."""
    if not os.path.exists(ASSETS_ZIP):
        print("Assets.zip introuvable, pas de planche de verification")
        return
    with zipfile.ZipFile(ASSETS_ZIP) as z:
        model = json.loads(z.read("Common/Items/Tools/Feedbag/Feedbag.blockymodel"))
        with z.open("Common/Items/Tools/Feedbag/Flourbag_Texture.png") as f:
            tex = Image.open(f).convert("RGBA")
        with z.open("Common/Icons/ItemsGenerated/Food_Flour.png") as f:
            ref = Image.open(f).convert("RGBA")
    mine = render_icon(model, tex, rotation=(22.5, 45.0, 22.5), pitch=20.0)
    sheet = Image.new("RGBA", (64 * 3 * 2 + 24, 64 * 3 + 16), (28, 30, 36, 255))
    sheet.paste(zoom(ref, 3), (8, 8), zoom(ref, 3))
    sheet.paste(zoom(mine, 3), (64 * 3 + 16, 8), zoom(mine, 3))
    sheet.save(os.path.join(TMP, "_purse_check.png"))


# =============================================================================

if __name__ == "__main__":
    for path in (MODEL_DIR, ICON_DIR, ITEM_DIR, LANG_DIR):
        os.makedirs(path, exist_ok=True)

    boxes = purse_boxes()
    pack_uv(boxes)
    texture = paint_texture(boxes)
    model = model_json(boxes)
    icon = render_icon(model, texture)

    texture.save(os.path.join(MODEL_DIR, "Purse_Texture.png"))
    with open(os.path.join(MODEL_DIR, "Purse.blockymodel"), "w") as f:
        json.dump(model, f, indent=2)
        f.write("\n")
    icon.save(os.path.join(ICON_DIR, "Obol_Purse.png"))
    with open(os.path.join(ITEM_DIR, "Obol_Purse.json"), "w") as f:
        json.dump(item_json(), f, indent=2)
        f.write("\n")
    with open(os.path.join(LANG_DIR, "server.lang"), "w") as f:
        f.write(lang())

    preview(icon, texture, model)
    check_against_vanilla()
    print("ok")
