# Obol

<img src="docs/icon.png" alt="" width="96" align="right">

Minimalist currency mod for Hytale, with an RPG feel.

Obol is the base other mods build on, not a feature set: a balance for
every player (and anything else that should hold coins), an API to move
them, and a readable display of the result.

- Just a currency. No shops, taxes or jobs: what Obol does to your server
  is what the mods built on it do; gameplay belongs to add-ons.
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

### Setup

```kotlin
dependencies {
    compileOnly("dev.galysso.obol:obol-api:0.1.0")   // JDK-only, no server types
}
```

```json
"Dependencies": { "Galysso:obol": ">=0.1.0" }
```

`obol-api` is not on a public repository yet: run
`./gradlew :api:publishToMavenLocal` from this repository and add
`mavenLocal()` to your repositories. Never bundle it: at runtime the classes
come from Obol's own jar. For an optional integration, put Obol under
`OptionalDependencies` and use `ObolApi.find()` instead of `ObolApi.get()`.

### Charging a player

```java
import dev.galysso.obol.api.*;

Wallet buyer = new PlayerWallet(player.getUuid());   // a stateless handle: create it anywhere
Coins price = Coins.of(Denomination.GOLD, 2);
if (!buyer.withdraw(price)) {                        // atomic: false means nothing moved
    player.sendMessage(Message.raw("Not enough coins: " + buyer.balance() + " / " + price));
    return;
}
giveItem(player);
```

### Giving anything a wallet

```java
// Balance stored by Obol, under "guild:<id>" in balances.json.
final class GuildWallet extends StoredWallet {
    private final String guildId;
    GuildWallet(String guildId) { this.guildId = guildId; }
    @Override public WalletId id() { return new WalletId("guild", guildId); }
}

// Balance stored by you (a persistent ECS component, your own file…).
final class MerchantWallet extends Wallet {
    private final MerchantComponent c;               // holds a `long copper`
    MerchantWallet(MerchantComponent c) { this.c = c; }
    @Override public WalletId id()              { return new WalletId("merchant", c.merchantId()); }
    @Override protected long loadCopper()       { return c.copper(); }
    @Override protected void saveCopper(long v) { c.setCopper(v); }
}
```

Both get the same rules, transfers, events and displays as a player. The two
hooks run under the wallet lock, on the thread that moves the money. A
`StoredWallet` entry is never removed on its own: call
`ObolApi.get().balances().delete(id)` when its owner is gone for good.

### Showing coins

```java
CoinsDisplay display = ObolApi.get().display();

// Follows the wallet until hide() or disconnect, whoever moves the money.
CoinsOverlay hud = display.track(viewer, ScreenPosition.topRight(20, 20),
                                 new PlayerWallet(viewer), CoinsFormat.STANDARD);

// A fixed amount (a price), changed by hand.
CoinsOverlay tag = display.show(viewer, ScreenPosition.bottomLeft(20, 80),
                                price, CoinsFormat.STANDARD);
tag.update(Coins.of(Denomination.GOLD, 3));
tag.move(ScreenPosition.bottomRight(20, 80));
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

The coin images are part of the API, for pages of your own that show
amounts: `Denomination.texture()` gives the path of each one relative to
`Common/UI/Custom/` (`Obol/Gold.png`, a 48×48 PNG shipped by Obol), so a
document of yours in `Common/UI/Custom/<YourMod>/` draws it with
`Background: (TexturePath: "../Obol/Gold.png");` and `Denomination.color()`
gives the matching text colour. Paths and sizes follow `obol-api`
versioning, the drawings themselves may change. They are UI textures, not
item icons: a mod giving coins a physical form needs its own item assets.

### Listening

```java
ObolApi.get().addListener(e -> log(e.wallet() + ": " + e.before() + " -> " + e.after()));
```

### API reference

Package `dev.galysso.obol.api`. No method accepts `null`
(`NullPointerException`). `dev.galysso.obol.api.internal` is not API.

**`ObolApi`** — `static get()` (`IllegalStateException` if Obol is not
loaded: missing or misordered manifest dependency), `static find()` →
`Optional`. `balances()`, `display()`, `addListener(l)` (twice = called
twice), `removeListener(l)` → `boolean`.

**`Coins`** — immutable record over a non-negative `long copper()`,
`Comparable`. `ZERO`, `ofCopper(long)`, `of(Denomination, long)`,
`of(mythril, gold, silver, copper)`; `plus(c)`, `times(long)`
(`ArithmeticException` on overflow), `minus(c)` → `Optional`, empty below
zero; `isZero()`, `covers(c)`, `breakdown()` → `EnumMap<Denomination, Long>`,
`amountOf(tier)`. `toString()` is `CoinsFormat.STANDARD`.

**`Denomination`** — `COPPER, SILVER, GOLD, MYTHRIL`, declared in ascending
value. `valueInCopper()`, `symbol()` (`c s g m`), `color()` (`#RRGGBB`, the
on-screen palette), `texture()` (`Obol/<Tier>.png` under `Common/UI/Custom/`,
48×48), `static largest()`.

**`CoinsFormat`** — `STANDARD` (`2g 35s 4c`, zero tiers omitted, `0c` for
zero), `LONG` (`2 gold, 35 silver, 4 copper`); `id()`, `format(coins)`.
`static parse(text)` reads every format's output plus `2g35s`, `250s`
(= 2g 50s) and bare copper; case-insensitive, tiers in any order, each at most
once; throws the checked `CoinsParseException` (`input()`).

**`Wallet`** (abstract) — `id()`, `protected loadCopper()`,
`protected saveCopper(long)`. Final: `balance()`, `canAfford(c)` (read-only
hint; trust `withdraw` instead), `deposit(c)` → new balance, `withdraw(c)` →
`boolean`, `transferTo(wallet, c)` → `boolean`. `withdraw` and `transferTo`
return `false` and write nothing when the funds are short — **never ignore
the result**. A transfer moves both balances or neither, including when the
receiving `saveCopper` throws; to itself it writes nothing. Storage holding a
negative value → `IllegalStateException`; overflow → `ArithmeticException`,
nothing written. `equals`/`hashCode` are on `id()`.

**`StoredWallet`** (abstract) — a `Wallet` whose balance Obol stores and
persists; only `id()` is left to write. **`PlayerWallet`** — `new
PlayerWallet(UUID)`, `playerId()`, id `player:<uuid>`, `KIND = "player"`.

**`WalletId`** — record `(kind, key)`, both `[a-z0-9_-]+`
(`IllegalArgumentException`); `kind` is your mod's namespace. `storageKey()` =
`kind:key`, `static parse(storageKey)`.

**`BalanceStore`** (`ObolApi.balances()`) — Obol's own storage, for
administration and migrations; day-to-day code goes through a `Wallet`.
`balance(id)` (`ZERO` if unknown), `set(id, coins)` → previous balance (zero
kept, not removed), `exists(id)`, `delete(id)` → `boolean`. `set` and
`delete` take the wallet lock and publish an event when the balance changes.
Calls are thread-safe individually; read-modify-write is not atomic here.

**`CoinsDisplay`** (`ObolApi.display()`) — `show(viewer, position, coins,
format)` and `track(viewer, position, wallet, format)` → `CoinsOverlay`. The
viewer (`UUID`) must be connected and in a world, and the format must have an
on-screen template — only `STANDARD` in this version — or
`IllegalArgumentException`. Any number of overlays per player. `track`
re-reads the wallet on every change, so it never shows a stale value, and
lists the recent changes under the balance.

**`CoinsOverlay`** — `update(coins)` (ignored on a tracking overlay),
`move(position)`, `hide()` (idempotent, releases a tracking listener),
`isVisible()`. Bound to the viewer's session: gone on disconnect, not
recreated on reconnect (put it back on the server's `PlayerReadyEvent`), after
which the handle is dead and `isVisible()` stays `false`. Every method may be
called from any thread; changes reach the client in call order.

**`ScreenPosition`** — record `(Corner, offsetX, offsetY)` in UI pixels, never
negative; `topLeft`, `topRight`, `bottomLeft`, `bottomRight(x, y)`. `Corner`:
`isRight()`, `isBottom()`.

**`CoinsListener`** — `onCoinsChanged(CoinsChangedEvent)`.
**`event.CoinsChangedEvent`** — record `(wallet: WalletId, before, after)`,
`increased()`; `before` and `after` always differ. Published for every
accepted write on every kind of wallet (a transfer gives two: debit, then
credit), synchronously on the thread that moved the money, after the lock was
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

Two modules: `api` (`java-library`, JDK-only, published as
`dev.galysso.obol:obol-api`) and `core` (`com.azuredoom.hytale-tools`: the
plugin, `manifest.json`, one jar that also contains `api`). `core/src/main/
resources` is the asset pack (`Common/UI/Custom/Obol/<Tier>.{ui,png}`; the
64×64 originals are in `tmp/`). Identity and versions live in
`gradle.properties`; `manifest.json` is generated from it.

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
