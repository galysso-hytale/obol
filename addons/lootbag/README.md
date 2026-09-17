# Obol Lootbag

<img src="src/main/resources/Common/Icons/ItemsGenerated/Obol_Lootbag_Epic.png" alt="" width="64" align="right">

Lootbags for [Obol](../../README.md): bags of coins that are never
crafted, only found, in dungeon chests and on the creatures you kill.
Open one and the coins go into your balance.

- **Five rarities**, the game's own: Common, Uncommon, Rare, Epic,
  Legendary. The rarity is the whole look of a bag (its slot and tooltip
  frame, its label, its glow on the ground) and says how much is inside.
- **The tooltip says the range.** "Holds 5 to 50 silver": the amount is
  rolled when the bag opens, so bags of one rarity stack, wherever they
  came from.
- **Right-click** with a bag in hand to open one. **Crouch and
  right-click** to open the whole stack. One sound, one line in Obol's
  HUD feed with the sum. The sound says the rarity: a chime of coins for
  every bag, and from Rare up a layer over it, up to the legendary
  chest's for a Legendary bag. Bags opened together make one sound, that
  of the best bag.
- A server can choose instead that a bag **opens itself** the moment you
  pick it up or take it out of a chest, even with a full inventory, or
  that each bag **says its exact amount** (then bags do not stack, and
  crouching opens every bag you carry).

Requires Obol 0.2 or later. Drop `ObolLootbag-<version>.jar` next to
Obol's jar in the server's `mods/` folder. Bags then fall from every
dungeon chest and from the undead, goblins, trorks, outlanders and the
Void guardian, at the rates below.

## Server owners

Two files in `mods/Galysso_obol-lootbag/`, both written with their
defaults on first start: `lootbag.json` says what a bag is,
`drops.json` says where bags fall.

### `lootbag.json`

| Key | Default | Effect |
|---|---|---|
| `OpenOn` | `"Use"` | `Use`: a bag is an item, opened by a right-click (crouch for the stack). `Pickup`: a bag is credited the moment it is picked up from the ground or taken out of a chest (click or shift-click), even with a full inventory. It never sits in an inventory. |
| `RevealAmount` | `false` | `true` rolls the amount when the bag drops and writes it on the bag ("Holds 1 gold, 47 silver"). Every bag is then its own stack, and crouch + right-click opens every bag you carry. |
| `Rarities` | see below | The amount law of each rarity: what a bag of that rarity gives when opened. |

Two settings make sense together. `Use` with `RevealAmount: false` (the
defaults): bags stack, the range is a promise, opening is a small
moment. `Pickup` with `RevealAmount: true`: a chest shows exact piles of
coins that vanish into your balance as you take them.

A law under `Rarities` is written with these keys, amounts as text
(`"5c"`, `"1s 20c"`, `"2g"`, or `"250"` for copper):

| Key | Meaning |
|---|---|
| `Distribution` | `Uniform` (every amount as likely), `Triangular` (climbs to `Mode`, then falls: often little, sometimes a lot), `LogUniform` (every tier as likely, for a range that spans several tiers), `Fixed` (one amount). May be left out: `Amount` alone means `Fixed`, `Mode` means `Triangular`, anything else `Uniform`. |
| `Min`, `Max` | The range, both included. |
| `Mode` | `Triangular` only: the most likely amount. |
| `Amount` | `Fixed` only: the amount. |
| `Step` | Optional. A roll is rounded down to a multiple of it. Default: one coin of the tier below the largest tier of `Max`, so an epic bag gives "1 gold, 47 silver" and not "1 gold, 47 silver, 83 copper". |

The defaults go up by ten per rarity:

```json
"Rarities": {
  "Common":    { "Distribution": "Triangular", "Min": "5c",  "Max": "50c", "Mode": "15c" },
  "Uncommon":  { "Distribution": "Triangular", "Min": "50c", "Max": "5s",  "Mode": "1s 20c" },
  "Rare":      { "Distribution": "Triangular", "Min": "5s",  "Max": "50s", "Mode": "12s" },
  "Epic":      { "Distribution": "Triangular", "Min": "50s", "Max": "5g",  "Mode": "1g 20s" },
  "Legendary": { "Distribution": "Triangular", "Min": "5g",  "Max": "50g", "Mode": "12g" }
}
```

A rarity left out or written wrong keeps its default, with a line in the
log. A law is written into each bag when it drops: changing `Rarities`
changes the bags that drop from then on, not the ones already in
circulation, which keep what their tooltip promised.

### `drops.json`

The game's loot is a set of drop tables (`Server/Drops/**/<id>.json` in
the game's assets): one per creature, one per dungeon chest. `drops.json`
adds bags to those tables, on top of what they give:

```json
{
  "Rules": [
    { "Droplists": ["Prefabs/Zone*_Encounters_Tier1"], "Chance": 30, "Bags": { "Common": 70, "Uncommon": 25, "Rare": 5 } },
    { "Droplists": ["Prefabs/Zone*_Encounters_Tier2"], "Chance": 35, "Bags": { "Common": 40, "Uncommon": 40, "Rare": 17, "Epic": 3 } },
    { "Droplists": ["Prefabs/Zone*_Encounters_Tier3"], "Chance": 40, "Bags": { "Uncommon": 40, "Rare": 40, "Epic": 17, "Legendary": 3 } },
    { "Droplists": ["Prefabs/Zone*_Encounters_Tier4"], "Chance": 50, "Bags": { "Rare": 45, "Epic": 40, "Legendary": 15 } },
    { "Droplists": ["NPCs/Undead/*", "NPCs/Intelligent/Goblin/*", "NPCs/Intelligent/Trork/*", "NPCs/Intelligent/Outlander/*"],
      "Chance": 8, "Bags": { "Common": 85, "Uncommon": 15 } },
    { "Droplists": ["NPCs/Boss/*"], "Chance": 100, "Rarity": "Legendary", "Distribution": "Uniform", "Min": "20g", "Max": "50g" }
  ]
}
```

A rule has three parts:

- **`Droplists`**: which tables. A pattern with a `/` is matched against
  the table's folder and name under `Server/Drops`, which is how the
  game sorts its tables. A pattern without one is matched against the
  table's id alone. `*` stands for anything but a `/`, `**` for anything,
  case does not count. Useful targets:
  - `Prefabs/Zone<1..4>_Encounters_Tier<1..4>`: every dungeon chest of
    that zone and tier goes through that table. Sixteen of them cover
    every chest in the game. (The chests' own tables,
    `Zone1_Trork_Tier2` and the like, include the Encounters table: name
    one or the other, not both, or the chest gets two chances.)
  - `NPCs/Undead/*`, `NPCs/Intelligent/<Race>/*` (Goblin, Trork,
    Outlander, Feran, Kweebec, Klops, Tuluk), `NPCs/Boss/*`,
    `NPCs/Elemental/*`, `NPCs/Void/*`, `NPCs/Beast/*`,
    `NPCs/Wildlife/*`, `NPCs/Livestock/*`: creatures, by folder. A
    creature's table is named after it, `Drop_Skeleton_Knight`,
    `Drop_Trork_Warrior`.
  - `/lootbag droplists --filter=NPCs/Intelligent/**` lists what a
    pattern matches on your server, other mods' tables included, and
    what the rules do to each.
- **`Chance`**: the percentage of drops of a matched table that get the
  rule's bags, independent of what the table gives. `100` if left out.
- **The bags**, one of three forms. **`Bags`**: one bag drawn among
  rarities at relative weights (`70, 25, 5` is 70 %, 25 %, 5 %).
  **`Rarity`**: one bag of that rarity, with its law from `lootbag.json`
  unless one is written next to it with the keys above. **`Droplist`**:
  whatever another table gives, one of the four shipped
  (`Obol_Lootbag_Tier1` to `Tier4`, the mixes of the four chest rules
  above) or any table of yours.

Several rules on one table stack. A rule written wrong is skipped, with
the reason in the log. A rule that matches no table says so at startup.
Rules apply to each table as it loads, vanilla and packs alike, and
again when a table is reloaded, so they compose with anything that
changes the tables.

`"Rules": []` puts no bag anywhere. That is the setting for a server that
places its bags itself, with [Patchly](https://modtale.net/mod/patchly)
or in its own packs: the bags are then items like any other in a table,
with a container type of their own,

```json
{ "Type": "ObolLootbag", "Weight": 10, "Rarity": "Rare" }
{ "Type": "ObolLootbag", "Weight": 5,  "Rarity": "Epic", "Distribution": "LogUniform", "Min": "1g", "Max": "9g" }
{ "Type": "Droplist",    "Weight": 35, "DroplistId": "Obol_Lootbag_Tier2" }
```

and a Patchly file such as `Server/Drops/Prefabs/Zone1_Encounters_Tier2.patch.json`
in any pack adds one to a vanilla table without copying it:

```json
{ "Container": { "Containers+": [
    { "Type": "Droplist", "Weight": 35, "DroplistId": "Obol_Lootbag_Tier2" }
] } }
```

### Commands

Admins (`obol.lootbag.debug`, given to `hytale:Admin` by default):

- `/lootbag give <rarity> [--count=n] [--law=...]`: bags in your
  inventory, with the rarity's law or the one written (`5g`,
  `uniform 1g 5g`, `triangular 5s 50s 12s`, `... step 1s`).
- `/lootbag roll <table> [--count=n]`: rolls a drop table and gives you
  what falls, to see a table's bags with their tooltip.
- `/lootbag rarities`: the law of each rarity, as `lootbag.json` has it.
- `/lootbag droplists [--filter=pattern]`: the tables the rules apply
  to, or those matching a pattern. Works from the console.

## How it works

A bag carries its law in its metadata, written when it drops. Opening
rolls the law once per bag and makes one deposit of the sum into the
player's balance through Obol's API, which shows it in the HUD feed. The
bags leave the inventory before the coins exist, and come back if the
deposit fails. In `Pickup` mode, a bag on the ground runs its own pickup
interaction instead of going into the inventory, a bag clicked in a chest
is credited by the move itself, and a bag that reaches an inventory by
any other way (a harvest, a command, another mod) is opened on arrival.

Rules do not write files or load assets: when a table loads, its
container is wrapped in a vanilla `Multiple` holding the original and
the rule's bags, on the loaded object. A tool that patches the JSON
(Patchly, a pack) does its work first, and a reload gives a fresh object
that is wrapped again.

## Modders

`dev.galysso.obol.lootbag.api.LootbagItem` is the way another mod makes
or reads a bag: `LootbagItem.stack(Rarity.Epic, LootLaw.fixed(Coins.parse("5g")), 1, OpenOn.Use)`
for a quest reward, `isLootbag`, `rarity` and `law` to read one. A table
of yours drops bags with the `ObolLootbag` container above, and the
`ObolOpenLootbag` interaction (`"All": true` for the stack) can be put on
an item of your own.

## Building

`./gradlew :addons:lootbag:build` makes `build/libs/ObolLootbag-<version>.jar`
and runs the unit tests of the laws and the rules.
`./gradlew :addons:lootbag:runServer` starts a server in
`addons/lootbag/run/` with Obol and the lootbag, the asset pack read live
from `src/main/resources`, where `lootbag.json` and `drops.json` are
then written. `./gradlew runAllMods`, from the root, runs the whole
workspace in one server.

The items, the three models, the five textures and icons, and the
pickup interaction are generated by `tools/lootbag.py` (Pillow only): edit
the script, not the files.
