# Obol on CurseForge

The project page's text, kept here so it evolves with the mod. Paste the
part under the rule into the description editor (Markdown mode). The
summary and categories above it go in the project settings.

**Title:** Obol - Minimalist RPG Currency

**Summary (236/256):** Minimalist RPG-style currency and economy core for
Hytale: four coins, a balance per player, and nothing you didn't ask for.
Built to be extended: purses, lootbags, trades, shops and compatibility
with other mods are add-ons you pick à la carte.

**Categories:** Library (primary), Gameplay, Utility.

**Logo:** `docs/curseforge.png` (`python3 tools/curseforge.py docs/icon.png`).

---

# Obol

**A minimalist RPG-style currency for Hytale, made to be built on.**

Obol gives every player a balance, in four coins from copper to mythril,
and shows it on screen. That is all it does on its own: no shops, no
taxes, no jobs, no commands for players. What Obol does to your server is
what the mods built on it do. You install it because another mod asks for
it, or because you want to pick your economy feature by feature.

## What you get

- **Four coins.** Copper, silver, gold, mythril, ×100 from one to the
  next, so `2g 35s 4c` reads at a glance. Amounts are always whole coins,
  never negative.
- **A balance for every player,** and for anything else that should hold
  coins: a guild, a shop, a purse, a bounty. Persisted by Obol, atomic
  under the hood, no duplication exploits.
- **A HUD.** Your balance in the top right corner. When it moves, the
  counts roll to the new amount, green for a gain and red for a loss, and
  the change stays listed under it for a few seconds. Every mod built on
  Obol shows prices with the same coins, so money looks the same
  everywhere.
- **Admin commands.** `/obol give`, `take`, `set` and `transfer`, by
  player name or UUID, online or not. Amounts read `2g 35s 4c`, `250s` or
  a bare number in copper.

## Add-ons, à la carte

Each one is a separate mod that requires Obol. Install the ones you want.

- **Obol Purse.** A craftable purse that carries coins from hand to hand,
  chest or floor. It shows what it holds: cord, slot frame, label and glow
  on the ground take the colour of the largest coin inside. Hand it to a
  player face to face, they accept or decline, nobody receives coins
  without saying yes.
- **Obol Lootbag.** Bags of coins that are never crafted, only found, in
  dungeon chests and on the creatures you kill, in the game's five
  rarities. Open one and the coins go into your balance. Where they fall
  and how much they hold is the server's to set, or a pack's.
- **Obol Trade.** Look at a player, press F, trade items and coins on one
  page. Both accept, everything changes hands at once.

Compatibility mods bring other mods' money into Obol. The first one,
for Aetherhaven, is in progress.

## Server owners

Drop `Obol-<version>.jar` into the server's `mods/` folder (or into
`UserData/Mods` for a single-player world). Nothing to configure.
Balances live in `mods/Galysso_obol/balances.json`, with the previous
version kept in `balances.json.bak`.

Admin commands need `obol.admin`, given to `hytale:Admin` by default. A
balance never goes below zero: a `take` or `transfer` it cannot cover is
refused, nothing else happens.

## Modders

Obol is small on purpose so that the mod you have in mind is easy to
write. A wallet is a name, not an object to create or look up. Every write
is atomic, transfers move both balances or neither, and you never draw a
coin yourself.

```java
Wallet buyer = Obol.playerWallet(player.getUuid());
Coins price = Coins.of(Denomination.GOLD, 2);
if (!buyer.withdraw(price)) {          // false: nothing moved
    player.sendMessage(Message.raw("Not enough coins"));
    return;
}
giveItem(player);
```

```java
Wallet guild = Obol.wallet(new WalletId("guild", guildId));   // any wallet you like
buyer.transferTo(guild, fee);
ObolUi.show(commands, "#Price", price);                        // coins drawn in your own page
Obol.addListener(e -> log(e.wallet() + ": " + e.before() + " -> " + e.after()));
```

`obol-api` is a plain Java library, versioned independently of the
plugin. A mod can also depend on Obol optionally and fall back when it is
absent. The [README](https://github.com/galysso/obol#modders) has the
setup and the reference, [MODDING.md](https://github.com/galysso/obol/blob/main/MODDING.md)
the patterns for add-ons and compatible mods.

## Links

- Source and issues: [github.com/galysso/obol](https://github.com/galysso/obol)
- License: MIT
