# Using Obol from your mod

The [README](README.md#modders) has the setup and the API reference. This
guide is about using them well: the model behind the API, the patterns that
fit it, and the mistakes it is designed to make hard.

It is written for two kinds of mods:

- **An addon**: a mod that exists because of Obol. A shop, a bank, bounties,
  a market. It depends on Obol outright and uses the whole API.
- **A compatible mod**: a mod with a purpose of its own that uses Obol when
  it is there. A quest mod paying rewards, a land-claim mod charging a fee,
  a minigame with an entry ticket. It runs without Obol, with a fallback.

Everything applies to both, except loading order, which has one section for
each.

## The model in three sentences

Obol keeps one store of balances, keyed by `WalletId`, persisted by Obol.
A `Wallet` is a name for one entry of that store, not an object you create,
register or keep. Money is a `Coins`, never negative, and every write is
atomic under a lock Obol holds per id.

Everything else follows from this. You never look anything up: you name the
wallet you mean and act on it. You never cache a balance: reading it is a
map lookup under a lock. You never repair a balance: the API cannot produce
a negative one.

## Loading order

Declare the dependency in your `manifest.json`:

```json
"Dependencies": { "Galysso:obol": ">=0.2.0" }
```

With it, the server loads Obol before your plugin and shuts it down after,
so `Obol.*` works from your constructor onwards, and until your `shutdown()`
returns. An `IllegalStateException` saying "Obol is not loaded" means the
dependency is missing or misspelled, never a race to handle.

Never bundle `obol-api` in your jar. At runtime the classes come from Obol's
jar through the plugin class loader, and a second copy would give you types
that are not the ones Obol installed its backend into.

### Compatible mod: Obol as an option

Declare Obol under `OptionalDependencies` instead:

```json
"OptionalDependencies": { "Galysso:obol": ">=0.2.0" }
```

The server then loads Obol first when it is present, and loads your mod
anyway when it is not. Two things follow.

**Keep Obol behind an interface of your own.** Your mod has a handful of
money questions: can this player pay, take the fee, give the reward. Write
them as an interface, with one implementation on Obol and one fallback, and
pick at startup. The rest of the mod never knows which one it got.

```java
interface Fees {
    boolean charge(UUID player, long copper);
    void reward(UUID player, long copper);
}

final class FreeFees implements Fees {           // without Obol: nothing costs anything
    public boolean charge(UUID player, long copper) { return true; }
    public void reward(UUID player, long copper) { }
}

final class ObolFees implements Fees {           // the only class that names Obol
    public boolean charge(UUID player, long copper) {
        return Obol.playerWallet(player).withdraw(Coins.ofCopper(copper));
    }
    public void reward(UUID player, long copper) {
        Obol.playerWallet(player).deposit(Coins.ofCopper(copper));
    }
}
```

The fallback is a design decision, not a technicality: free, disabled, or
a counter of your own. Say which in your README.

**Never name an Obol type outside that implementation.** When Obol is
absent its classes are absent too, so any class that mentions `Obol`,
`Coins` or `Wallet` anywhere fails to load with a `NoClassDefFoundError`,
even if the line is never executed. The class that chooses the
implementation must therefore not mention `ObolFees`'s dependencies
itself: it asks the plugin manager, then loads the class by name.

```java
// Loads fine without Obol: no Obol type appears here.
static Fees pickFees() {
    boolean present = PluginManager.get().hasPlugin(
            new PluginIdentifier("Galysso", "obol"), SemverRange.fromString(">=0.2.0"));
    return present ? new ObolFees() : new FreeFees();
}
```

Naming `ObolFees` here is fine: loading that class does not load Obol's,
the JVM resolves `Obol` and `Coins` when a method of `ObolFees` first runs,
and that only happens on the `present` branch. What must not happen is an
Obol type in a field, a parameter, a local or an import of the choosing
class, or of any class loaded on the other branch.

**What compatible means.** A mod is compatible with Obol when, Obol
present, it behaves like an addon would:

- Player money is the player's Obol wallet. No parallel currency, no
  points of your own that convert to coins.
- Amounts come from a `Coins`, prices in config are parsed with
  `Coins.parse` so that admins write `2g 50s` everywhere.
- Balances are not drawn again: Obol's HUD already shows every player their
  balance, and a change made by your mod appears there with its feed row.
  Draw amounts of your own only: a price, a reward, a pot.
- Reactions to money go through the events, not through polling.

## Naming wallets

`WalletId` is `(kind, key)`, both `[a-z0-9_-]+`. Obol uses `player` for
players. Pick one or more kinds for your mod and treat them as a namespace:
`shop`, `guild`, `quest-escrow`. Two mods choosing the same kind would share
entries, so name it after what it holds, with your mod's name in front if
the word is generic (`myshop-till` rather than `till`).

The key must be stable for the life of the owner and unique inside the
kind: a UUID, a database id, a config name. Do not derive it from something
that can change, like a display name.

```java
Wallet shop  = Obol.wallet(new WalletId("shop", shopId));
Wallet buyer = Obol.playerWallet(player.getUuid());
```

Build the handle at the point of use. Keeping one in a field is harmless
but buys nothing: it holds no state, and two handles with the same id are
equal.

## Moving money

Three operations write: `deposit`, `withdraw`, `transferTo`. Each is one
atomic read-modify-write under the wallet's lock.

**Read the boolean.** `withdraw` and `transferTo` return `false` when the
funds are short, and then nothing has moved. That return value is the whole
contract: insufficient funds are a normal outcome, so there is no exception
to catch, and ignoring the result means giving the item away for free.

```java
if (!buyer.withdraw(price)) {
    tell(player, "You need " + price + ", you have " + buyer.balance());
    return;
}
giveItem(player);
```

**Do not check then act.** `canAfford` is for showing a greyed-out button or
a message, not for deciding. Between `canAfford` and `withdraw` another
thread can move the money. `withdraw` does its own check under the lock,
so call it directly and branch on the result.

**Prefer `transferTo` to a withdraw followed by a deposit.** A transfer moves
both balances or neither, under both locks, and cannot deadlock against a
transfer going the other way. Two separate calls can leave money in flight
if the second one throws, and they publish two unrelated events instead of a
matched pair.

**Deposit cannot fail**, except on overflow of a `long` of copper, which is a
bug in the amount, not a case to handle.

**`clear()` is for owners that are gone.** It sets the balance to zero and
removes the entry from `balances.json`. Use it when a guild is disbanded or
a shop is deleted, so the file does not grow with dead entries. It is not a
way to take money: the coins vanish, they are not moved anywhere.

## Amounts

`Coins` is a value: immutable, comparable, never negative. Arithmetic is
`plus`, `minus` (an `Optional`, empty when the result would be negative),
`times` for a unit price times a quantity, and `covers` for "is this at
least that".

```java
Coins unit  = Coins.of(Denomination.SILVER, 35);
Coins total = unit.times(quantity);
```

Amounts typed by a player go through `Coins.parse`. It accepts what people
write (`2g 35s`, `2g35s`, `250s`, `2 gold`, a bare number in copper) and
throws the checked `CoinsParseException` on anything else. Report the
exception's message to the player. It is written for them.

`toString()` gives `2g 35s 4c`, which is what you put in a chat message,
and which `parse` reads back. Do not format amounts yourself: the symbols
and the rule for zero tiers are Obol's.

Amounts in your own persistence go as `coins.copper()`, one `long`, and come
back through `Coins.ofCopper`.

## Reacting to changes

`Obol.addListener` sees every accepted write on every wallet, whoever made
it: your mod, another mod, an admin command. A transfer gives two events,
debit then credit.

Rules for a listener:

- It runs on the thread that moved the money, right after the lock was
  released. Keep it short.
- Never throw. Obol logs and skips a throwing listener, the caller of the
  wallet operation never sees it, but nothing you did after the throw
  happens.
- It may move money itself, on any wallet including the one of the event.
  The lock is not held, so a tax deducted on deposit is legal.
- The event is a snapshot. When the current balance matters, read
  `Obol.wallet(event.wallet()).balance()`. When the sequence of changes
  matters, use `before()` and `after()`.
- Filter by `event.wallet().kind()` when you only care about your own
  wallets.

Add the listener once, in `setup()`, and remove it in `shutdown()` if your
plugin can be unloaded while Obol stays up.

## Showing coins

Obol already draws every player's balance top right. Use `Obol.show` and
`Obol.track` for amounts of your own: a price next to a stall, a reward
during a quest, a shop's till for its owner.

- `show(viewer, position, coins)` draws a fixed amount you change through
  the handle (`update`).
- `track(viewer, position, wallet)` follows a wallet and lists its recent
  changes under it.

Both need the viewer connected and in a world. Calling them earlier is a
bug, reported as `IllegalArgumentException`. An overlay belongs to the
session: it disappears on disconnect and is not put back on reconnect. For
an overlay that should always be there, create it on `PlayerReadyEvent`,
the way Obol does for its own HUD, and keep one per player so that a world
change within a session does not stack a second one.

Positions are a corner and an offset. A right corner overflows to the left
as the amount grows, a bottom corner stacks the feed upwards. Obol's own HUD
sits at `topRight(20, 20)`: a pill 36 pixels tall, and under it up to five
feed rows of 30 pixels each while changes are recent. Put yours in another
corner, or below `topRight(20, 210)` in that one.

For amounts inside a page of your own, use the coin images and colours:
`Denomination.texture()` and `Denomination.color()`. The README shows the
markup.

## Testing without a server

The `api` jar has no server dependency, so the code of yours that moves
money can run in a plain unit test. `Obol` delegates every call to a backend
installed in `dev.galysso.obol.api.internal.ObolBackendHolder`. That package
is not public API, but the holder is stable and it is what Obol's own tests
use.

Install a fake before the test, uninstall after. A map-backed `Wallet` is a
few lines:

```java
final class FakeBackend implements ObolBackend {
    final Map<WalletId, Coins> store = new HashMap<>();

    @Override public Wallet wallet(WalletId id) {
        return new Wallet() {
            public WalletId id() { return id; }
            public Coins balance() { return store.getOrDefault(id, Coins.ZERO); }
            public boolean canAfford(Coins a) { return balance().covers(a); }
            public Coins deposit(Coins a) { return store.merge(id, a, Coins::plus); }
            public boolean withdraw(Coins a) {
                Optional<Coins> left = balance().minus(a);
                left.ifPresent(c -> store.put(id, c));
                return left.isPresent();
            }
            public boolean transferTo(Wallet to, Coins a) {
                if (!withdraw(a)) return false;
                to.deposit(a);
                return true;
            }
            public void clear() { store.remove(id); }
        };
    }

    @Override public CoinsOverlay show(UUID v, ScreenPosition p, Coins c) { throw new UnsupportedOperationException(); }
    @Override public CoinsOverlay track(UUID v, ScreenPosition p, Wallet w) { throw new UnsupportedOperationException(); }
    @Override public void addListener(CoinsListener l) { }
    @Override public boolean removeListener(CoinsListener l) { return false; }
}

@BeforeEach void install()   { ObolBackendHolder.install(backend); }
@AfterEach  void uninstall() { ObolBackendHolder.uninstall(); }
```

Never call `install` outside tests. At runtime the holder is Obol's.

## Checklist

Do:

- Name wallets at the point of use, with a kind of your own.
- Branch on the result of `withdraw` and `transferTo`.
- Use `transferTo` when money goes from one wallet to another.
- Parse player input with `Coins.parse`, print with `toString()`.
- Keep listeners short and exception-free.
- Recreate overlays on `PlayerReadyEvent`.
- Call `clear()` when an owner is deleted.

Do not:

- Bundle `obol-api`.
- Reference an Obol type from a class that must load without Obol.
- Cache a balance, or check `canAfford` and then write.
- Ignore a `false` from `withdraw` or `transferTo`.
- Format amounts by hand.
- Install a backend outside a unit test.

## Versions

`obol-api` follows semantic versioning on its own. Within a major version,
existing signatures do not change and interfaces gain methods only with a
`default` body, so a mod built against `0.2.0` runs against every later
`0.x`. Pin the lower bound you actually need in the manifest
(`">=0.2.0"`), not an exact version.
