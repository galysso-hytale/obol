# tools

Generators for the assets that are drawn or written by script rather than
by hand. Python 3 and Pillow only. Each script is deterministic and writes
straight into the module's `src/main/resources`, so the committed asset is
always the script's output: edit the script, run it, commit both. Never
edit a generated file by hand (the JSON ones say so in `$Comment`).

Run from anywhere, paths are resolved from the repository root. Control
sheets (`_*_apercu.png`, `_*_check.png`) go to `tmp/`, which is ignored.

| Script | Module | Produces |
|---|---|---|
| `purse.py` | addons/purse | The purse item: model, textures, icons, item JSON, `server.lang`. |
| `qualities.py` | addons/purse | One `ItemQuality` per tier, vanilla qualities recoloured. |
| `halos.py` | addons/purse | The halo particle systems on the ground, one per tier. |
| `lootbag.py` | addons/lootbag | The five lootbags: model, textures, icons, items, sounds, pickup interaction. Imports `purse.py`. |
| `backpack_tag.py` | addons/trade | The "from the backpack" mark on offer slots. |
| `offer_panel.py` | addons/trade | The 9-patch behind "what I give". |
| `offer_slot.py` | addons/trade | The tinted slot background of my offer. |
| `curseforge.py <icon.png> [--tint <addon>]` | any | The mod page logo: the icon on the shared opaque plate, `curseforge.png` next to the icon. |
| `coins_strip.py` | docs | The four coins in a row on the same plate, `docs/coins.png`, for the CurseForge description (`docs/curseforge.md`). |

`purse.py`, `qualities.py`, `halos.py` and `offer_slot.py` read vanilla
references from the game's `Assets.zip`, `backpack_tag.py` from the
client's interface files. Their paths are at the top of each script and
point at the Flatpak install.
