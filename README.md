# Obol

<img src="docs/icon.png" alt="" width="96" align="right">

Minimalist currency mod for Hytale, with an RPG feel.

Obol is the base other mods build on, not a feature set: a balance for
every player (and anything else that should hold coins), an API to move
them, and a readable display of the result.

- Just a currency. No shops, taxes or jobs: what Obol does to your server
  is what the mods built on it do; gameplay belongs to add-ons. The first
  one, [Obol Purse](addons/purse/README.md), is a craftable purse that
  carries coins from hand to hand, chest or floor.
- Four coins, copper to mythril, so 1 <img src="core/src/main/resources/Common/UI/Custom/Obol/Mythril.png" alt="mythril" height="16" align="absmiddle"> 2 <img src="core/src/main/resources/Common/UI/Custom/Obol/Gold.png" alt="gold" height="16" align="absmiddle"> 35 <img src="core/src/main/resources/Common/UI/Custom/Obol/Silver.png" alt="silver" height="16" align="absmiddle"> 4 <img src="core/src/main/resources/Common/UI/Custom/Obol/Copper.png" alt="copper" height="16" align="absmiddle"> reads at a glance.

## Players and server owners

Drop `Obol-<version>.jar` into the server's `mods/` folder, or, for a
single-player world, into the game's `UserData/Mods`:
- Windows: `%APPDATA%\Hytale\UserData\Mods`
- macOS: `~/Library/Application Support/Hytale/UserData/Mods`
- Linux (Flatpak launcher): `~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/UserData/Mods`

Nothing to configure. Every player sees their balance top right of the
screen. When it moves, the counts roll to the new amount, green for a gain
and red for a loss, and the change stays listed under it for a few seconds.

Players have no commands: coins come and go through the mods built on Obol
(shops, quests, trades…). Admins (`obol.admin`, given to `hytale:Admin` by
default) have:

| Command | Effect |
|---|---|
| `/obol give <player> <amount>` | Adds coins. |
| `/obol take <player> <amount>` | Removes coins. |
| `/obol set <player> <amount>` | Sets the balance. |
| `/obol transfer <from> <to> <amount>` | Moves coins from one player to another. |

Players are given by name or UUID, online or not. Amounts read `2g 35s 4c`
(`c s g m` for copper, silver, gold, mythril, ×100 from one to the next),
also `2g35s`, `250s` or a bare number in copper. A balance never goes below
zero: a `take` or `transfer` it cannot cover is refused, nothing else.

Balances are in `mods/Galysso_obol/balances.json` (previous version in
`balances.json.bak`). A file Obol cannot read stops the plugin, and the mods
depending on it, rather than starting an empty economy.

## Modders

Setup and reference below. [MODDING.md](MODDING.md) is the guide: the model
behind the API and the patterns that fit it.

### Setup

```kotlin
dependencies {
    compileOnly("dev.galysso.obol:obol-api:0.2.0")   // server types only in ObolUi
}
```

```json
"Dependencies": { "Galysso:obol": ">=0.2.0" }
```

`obol-api` is not on a public repository yet: run
`./gradlew :api:publishToMavenLocal` from this repository and add
`mavenLocal()` to your repositories. Never bundle it: at runtime the classes
come from Obol's own jar.

For an optional integration, put Obol under `OptionalDependencies` and check
`PluginManager.get().hasPlugin(...)` from a class that names no Obol type
before touching one: when Obol is absent its classes are absent too, so any
class that mentions `Obol` fails to load.

### Charging a player

```java
import dev.galysso.obol.api.*;

Wallet buyer = Obol.playerWallet(player.getUuid());   // nothing to fetch or keep: call it where needed
Coins price = Coins.of(Denomination.GOLD, 2);
if (!buyer.withdraw(price)) {                         // atomic: false means nothing moved
    player.sendMessage(Message.raw("Not enough coins: " + buyer.balance() + " / " + price));
    return;
}
giveItem(player);
```

### Giving anything a wallet

```java
Wallet guild = Obol.wallet(new WalletId("guild", guildId));   // stored under "guild:<id>"
buyer.transferTo(guild, fee);
guild.clear();                                                // when the guild is disbanded
```

A wallet is a stateless handle on an entry of Obol's store: an id nobody has
written to holds zero, and an entry is never removed on its own. `kind` is
your mod's namespace. Every wallet gets the same rules, transfers, events and
displays as a player's.

That is all [Obol Purse](addons/purse/README.md) needs: a purse is a
`WalletId("purse", <uuid>)` whose key travels in the item's metadata, and
filling, emptying or handing it over are three `transferTo` calls. The
add-on writes no money code of its own.

### Showing coins

```java
// Follows the wallet until hide() or disconnect, whoever moves the money.
CoinsOverlay hud = Obol.track(viewer, ScreenPosition.topRight(20, 20), Obol.playerWallet(viewer));

// A fixed amount (a price), changed by hand.
CoinsOverlay tag = Obol.show(viewer, ScreenPosition.bottomLeft(20, 80), price);
tag.update(Coins.of(Denomination.GOLD, 3));
tag.hide();
```

On screen an amount is a pill of counts and coin icons: tiers above the
largest one holding coins are omitted, every tier below it is shown even at
zero, and counts sit in fixed two-digit columns — `2 [gold] 5 [silver]
4 [copper]` for `2g 5s 4c`, `1 [gold] 0 [silver] 5 [copper]` for `1g 5c`. A
change of amount, from `update` or the tracked wallet, rolls the counts to the
new value over about half a second, the ones that move tinted green (gain) or
red (loss) until just after the roll settles; a change during a roll retargets
it from the value on screen. The last frame is always the exact amount. A
tracking overlay also lists the wallet's changes under the pill (over it, in
a bottom corner): one row per change, newest first, at most five, each fully
shown for 4 s then fading out over 1 s; nothing is merged, the oldest row
goes when a sixth arrives. A fixed overlay has no feed.

Inside a page of your own (a `CustomUIPage`), Obol draws the coins for
you, so that a price looks the same in every mod and in the HUD:

```java
@Override
public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
    commands.append("MyShop/Page.ui");
    for (Article article : articles) {
        ObolUi.show(commands, "#" + article.id() + " #Price", article.price());   // an empty group of your document
    }
    ObolUi.show(commands, "#Cart #Gold", Denomination.GOLD, 12, CoinsStyle.DEFAULT.withAlignment(CoinsStyle.Alignment.END));
}
```

`ObolUi.show(builder, selector, coins)` fills the group at `selector` with
the same pill as the HUD, centred (a `CoinsStyle` moves it to the start or
the end of the group); `show(builder, selector, tier, count)` draws one tier
alone, even at zero, for a page that lays tiers out itself. The group takes
the width of its content and the height you give it (the coins are 24
pixels high). Fill a group once, and `clear` it before filling it again.
The pictures and colours are Obol's; a mod never has to draw a coin.

The coin images themselves are also part of the API, for the rare document
that needs one outside an amount: `Denomination.texture()` gives the path of
each one relative to `Common/UI/Custom/` (`Obol/Gold.png`, a 48×48 PNG
shipped by Obol), so a document of yours in `Common/UI/Custom/<YourMod>/`
draws it with `Background: (TexturePath: "../Obol/Gold.png");` and
`Denomination.color()` gives the matching text colour. Paths and sizes
follow `obol-api` versioning, the drawings themselves may change. They are
UI textures, not item icons: a mod giving coins a physical form needs its
own item assets.

### Listening

```java
Obol.addListener(e -> log(e.wallet() + ": " + e.before() + " -> " + e.after()));
```

### API reference

Package `dev.galysso.obol.api`. No method accepts `null`
(`NullPointerException`). `dev.galysso.obol.api.internal` is not API.

**`Obol`** — static entry point, `IllegalStateException` on every method if
Obol is not loaded (missing or misordered manifest dependency).
`wallet(WalletId)`, `playerWallet(UUID)` (= `wallet(WalletId.player(uuid))`),
`show(viewer, position, coins)` and `track(viewer, position, wallet)` →
`CoinsOverlay`, `addListener(l)` (twice = called twice), `removeListener(l)`
→ `boolean`.

**`ObolUi`** — static, `IllegalStateException` if Obol is not loaded.
`show(builder, selector, coins[, style])` and `show(builder, selector, tier,
count[, style])` queue the commands that draw the coins into the group at
`selector` (count then coin per tier, largest first, the HUD's rule for
which tiers appear). The only class of the API that names a server type
(`UICommandBuilder`).

**`CoinsStyle`** — a record, `DEFAULT` (centred) and
`withAlignment(START | CENTER | END)`. Options may be added, each with a
default.

**`Wallet`** — a stateless handle, equal to any other with the same `id()`.
`balance()`, `canAfford(c)` (read-only hint; trust `withdraw` instead),
`deposit(c)` → new balance, `withdraw(c)` → `boolean`, `transferTo(wallet, c)`
→ `boolean`, `clear()` (balance to zero, entry removed, idempotent).
`withdraw` and `transferTo` return `false` and write nothing when the funds
are short — **never ignore the result**. A transfer moves both balances or
neither; to itself it writes nothing. Overflow → `ArithmeticException`,
nothing written.

**`WalletId`** — record `(kind, key)`, both `[a-z0-9_-]+`
(`IllegalArgumentException`); `kind` is your mod's namespace. `PLAYER_KIND =
"player"`, `static player(UUID)`, `toString()` = `kind:key`.

**`Coins`** — immutable record over a non-negative `long copper()`,
`Comparable`. `ZERO`, `ofCopper(long)`, `of(Denomination, long)`, `static
parse(text)` (`2g 35s 4c`, `2g35s`, `250s` = 2g 50s, `2 gold`, bare copper;
case-insensitive, tiers in any order, each at most once; throws the checked
`CoinsParseException`, `input()`); `plus(c)`, `times(long)`
(`ArithmeticException` on overflow), `minus(c)` → `Optional`, empty below
zero; `covers(c)`, `breakdown()` → `EnumMap<Denomination, Long>`.
`toString()` is `2g 35s 4c`, zero tiers omitted, `0c` for zero, and parses
back.

**`Denomination`** — `COPPER, SILVER, GOLD, MYTHRIL`, declared in ascending
value. `valueInCopper()`, `symbol()` (`c s g m`), `color()` (`#RRGGBB`, the
on-screen palette), `texture()` (`Obol/<Tier>.png` under `Common/UI/Custom/`,
48×48).

**`CoinsOverlay`** — `update(coins)` (ignored on a tracking overlay), `hide()`
(idempotent, releases a tracking listener). The viewer (`UUID`) must be
connected and in a world or `Obol.show`/`track` throw
`IllegalArgumentException`. Any number of overlays per player. `track`
re-reads the wallet on every change, so it never shows a stale value, and
lists the recent changes under the balance. Bound to the viewer's session:
gone on disconnect, not recreated on reconnect (put it back on the server's
`PlayerReadyEvent`), after which the handle is dead. Every method may be
called from any thread; changes reach the client in call order.

**`ScreenPosition`** — record `(Corner, offsetX, offsetY)` in UI pixels, never
negative; `topLeft`, `topRight`, `bottomLeft`, `bottomRight(x, y)`. `Corner`:
`TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT`.

**`CoinsListener`** — `onCoinsChanged(CoinsChangedEvent)`.
**`event.CoinsChangedEvent`** — record `(wallet: WalletId, before, after)`,
`increased()`; `before` and `after` always differ. Published for every
accepted write on every wallet (a transfer gives two: debit, then credit),
synchronously on the thread that moved the money, after the lock was
released, in subscription order. A listener that throws is logged and skipped;
the others still run and the caller never sees it. The event is a snapshot:
read `wallet.balance()` when the current value matters.

### Guarantees

Every operation runs under a lock Obol keeps per `WalletId`, so concurrent
callers never see a torn read-modify-write, and transfers lock in a global
order and cannot deadlock. Wallets carry no state: nothing is cached, nothing
to register or look up.

`obol-api` follows semantic versioning independently of the plugin. Within a
major version, interfaces gain methods only with a `default` body.

## Building

Two modules: `api` (`java-library`, published as
`dev.galysso.obol:obol-api`, JDK-only except `ObolUi`, which sees the server
jar at compile time only) and `core` (`com.azuredoom.hytale-tools`: the
plugin, `manifest.json`, one jar that also contains `api`). `core/src/main/
resources` is the asset pack (`Common/UI/Custom/Obol/<Tier>.{ui,png}` for
the HUD, `Obol/Coins/<Tier>.ui` for `ObolUi`; the 64×64 originals are in
`tmp/`). Identity and versions live in
`gradle.properties`; `manifest.json` is generated from it. Add-ons are
sub-projects under `addons/` (`addons/purse`), each a plugin of its own with
its own version, built and run alone or with everything else
(`./gradlew runAllMods`).

Requires **JDK 25** for the Gradle daemon itself; Gradle provisions it on
first run (`gradle/gradle-daemon-jvm.properties`).

```bash
./gradlew setupHytaleDev      # once: fetches the game's assets (OAuth device flow, or set
                              #   hytaleHomeOverride=/path/to/Assets.zip in ~/.gradle/gradle.properties)
./gradlew build               # tests + core/build/libs/Obol-<version>.jar
./gradlew runServer           # dev server in core/run/, reading the asset pack live
./gradlew updateAllPluginManifests
```

The dev server links `core/run/mods/Galysso_obol` to
`core/src/main/resources`, so its `balances.json` lands in the sources
(git-ignored, excluded from the jar; delete it to reset). Smoke test: give
yourself `obol.admin` (`core/run/permissions.json`,
`users.<uuid>.groups = ["hytale:Admin"]`), `/obol give <you> 2g`: the HUD
updates at once; restart: it comes back with the same amount.
