# API publique cible d'Obol et chemin pour l'atteindre

## Contexte

L'API `obol-api` (16 types, ~60 membres) est plus large que ce qu'un mod
consommateur utilise. Le relevé des usages montre que `core` lui-même vit avec
une douzaine de membres, que plusieurs membres publics n'ont aucun appelant en
dehors de leurs propres tests, et qu'aucun `Wallet` à stockage propre n'existe
hors d'un helper de test. Surtout, l'API a deux portes d'entrée incohérentes :
`ObolApi.get()` (service locator explicite) et `new PlayerWallet(uuid)` (façade
statique déguisée, qui retourne au runtime par `api.internal.ObolApiHolder`).
Cette porte dérobée est ce qui oblige `Wallet` à porter du comportement dans
le jar API, et ce qui justifie `ObolRuntime`.

Objectif : une seule voie, claire et naturelle, pour un mod qui veut de la
monnaie. Une façade statique `Obol`, rien à récupérer ni à stocker :
`Obol.playerWallet(uuid).withdraw(price)` depuis n'importe où. Le jar API ne
contient plus que cette façade, des interfaces et des types valeur. Rien de
spéculatif : ce qui n'a pas de cas d'usage énoncé sort.

Pourquoi un registre à part plutôt qu'un composant sur l'entité : on ne peut
pas faire implémenter `Wallet` aux classes du serveur, et un wallet de guilde
ou de boutique n'est attaché à aucune entité. Il faut donc un registre à part
de toute façon, autant n'en avoir qu'un. `Wallet` est une vue sans état sur
ce registre, ce qui rend la façade statique naturelle : `Obol.wallet(id)` ne
crée rien, il nomme une entrée.

Le point d'injection pour les tests d'un mod (sans serveur) est le holder
interne : `ObolBackendHolder.install(faux backend)`. Il n'est pas documenté
comme API mais il est stable, c'est ce que les tests d'Obol utilisent.

Rupture assumée : `obol-api` passe en `0.2.0` (`gradle.properties`, le jar
n'est pas encore sur un dépôt public).

## API publique finale (`dev.galysso.obol.api`)

Ce qu'un mod écrit, et rien d'autre :

```java
void sell(PlayerRef p, Coins price) {
    Wallet buyer = Obol.playerWallet(p.getUuid());   // rien à récupérer avant, la dépendance manifeste garantit Obol
    if (!buyer.withdraw(price)) { ...; return; }
    giveItem(p);
}
```

La surface complète, signatures seules :

```java
package dev.galysso.obol.api;

public final class Obol {                          // façade statique, IllegalStateException si Obol n'est pas chargé
    public static Wallet wallet(WalletId id);
    public static Wallet playerWallet(UUID player);  // = wallet(WalletId.player(player))
    public static CoinsOverlay show(UUID viewer, ScreenPosition position, Coins coins);    // montant fixe, modifiable par la poignée
    public static CoinsOverlay track(UUID viewer, ScreenPosition position, Wallet wallet); // suit le wallet, avec le feed des changements
    public static void addListener(CoinsListener listener);
    public static boolean removeListener(CoinsListener listener);
}

public interface Wallet {                          // handle sans état, égalité sur id()
    WalletId id();
    Coins balance();
    boolean canAfford(Coins amount);               // = balance().covers(amount), indicatif
    Coins deposit(Coins amount);                   // nouveau solde. ArithmeticException si débordement, rien écrit
    boolean withdraw(Coins amount);                // false = fonds insuffisants, rien n'a bougé
    boolean transferTo(Wallet to, Coins amount);   // false = fonds insuffisants, rien n'a bougé
    void clear();                                  // solde à zéro, Obol oublie l'entrée
}

public record WalletId(String kind, String key) {  // chacun [a-z0-9_-]+
    public static final String PLAYER_KIND = "player";
    public static WalletId player(UUID player);
    public String toString();                      // kind:key
}

public record Coins(long copper) implements Comparable<Coins> {   // jamais négatif
    public static final Coins ZERO;
    public static Coins ofCopper(long copper);
    public static Coins of(Denomination denomination, long count);
    public static Coins parse(String text) throws CoinsParseException;   // "2g 35s 4c", "2g35s", "250s", "250"
    public Coins plus(Coins other);
    public Optional<Coins> minus(Coins other);     // vide si other > this
    public Coins times(long factor);
    public boolean covers(Coins other);            // this >= other
    public EnumMap<Denomination, Long> breakdown();
    public int compareTo(Coins other);
    public String toString();                      // "2g 35s 4c", "0c"
}

public enum Denomination {
    COPPER, SILVER, GOLD, MYTHRIL;                 // ordre croissant, x100 entre deux tiers
    public long valueInCopper();
    public String symbol();                        // c s g m
    public String color();                         // #RRGGBB
    public String texture();                       // Obol/Gold.png, sous Common/UI/Custom/
}

public class CoinsParseException extends Exception {
    public String input();
}

@FunctionalInterface
public interface CoinsListener {
    void onCoinsChanged(CoinsChangedEvent event);
}

public interface CoinsOverlay {                    // poignée sur un overlay posé par Obol.show / Obol.track
    void update(Coins coins);                      // sans effet sur un overlay issu de track()
    void hide();                                   // idempotent
}

public record ScreenPosition(Corner corner, int offsetX, int offsetY) {
    public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    public static ScreenPosition topLeft(int offsetX, int offsetY);
    public static ScreenPosition topRight(int offsetX, int offsetY);
    public static ScreenPosition bottomLeft(int offsetX, int offsetY);
    public static ScreenPosition bottomRight(int offsetX, int offsetY);
}

package dev.galysso.obol.api.event;

public record CoinsChangedEvent(WalletId wallet, Coins before, Coins after) {
    public boolean increased();
}
```

Hors API mais dans le jar, package `dev.galysso.obol.api.internal` :

```java
public interface ObolBackend {                     // ce que la façade délègue, implémenté par core
    Wallet wallet(WalletId id);
    CoinsOverlay show(UUID viewer, ScreenPosition position, Coins coins);
    CoinsOverlay track(UUID viewer, ScreenPosition position, Wallet wallet);
    void addListener(CoinsListener listener);
    boolean removeListener(CoinsListener listener);
}

public final class ObolBackendHolder {             // le singleton, installé par ObolPlugin
    public static void install(ObolBackend backend);   // IllegalStateException si déjà installé
    public static void uninstall();
    public static ObolBackend require();               // IllegalStateException si absent, message "declare the dependency"
}
```

`Obol.x(...)` = `ObolBackendHolder.require().x(...)`, résolu à chaque appel,
donc un backend désinstallé (plugin arrêté ou rechargé) fait échouer l'appel
suivant au lieu de parler à une instance morte.

Contrat inchangé pour `Wallet` : chaque opération sous le verrou de son id,
transferts all-or-nothing avec prise des deux verrous dans un ordre global,
un `CoinsChangedEvent` par écriture acceptée publié hors verrou, aucun
événement pour une écriture qui ne change rien.

## Ce qui change

| Type | Changement |
|---|---|
| `ObolApi` → `Obol` | L'interface avec `get()`/`find()` devient une façade statique. `find()` disparaît (inutile : Obol absent = classe absente, corrigé dans le README). `balances()` retiré. `wallet`/`playerWallet` ajoutés. Ce que la façade délègue est l'interface interne `ObolBackend`. |
| `Wallet` | Classe abstraite → interface, implémentée dans `core`. La clause "stockage négatif → `IllegalStateException`" disparaît : le store valide au chargement et `Coins` est non négatif, le cas n'existe plus. `clear()` (solde à zéro, entrée retirée du store, événement vers `ZERO` si le solde n'était pas nul) remplace `BalanceStore.delete`. `canAfford` gardé. |
| `WalletId` | `player(UUID)` et `PLAYER_KIND` ajoutés. `storageKey()` et `parse()` retirés (usage interne à `core`). |
| `Coins` | `parse` et `toString` absorbent `CoinsFormat.STANDARD`. Retirés : `of(m, g, s, c)`, `isZero`, `amountOf`. |
| `Denomination` | `largest()` retiré. |
| `CoinsDisplay` → `Obol.show`/`Obol.track` | Le type disparaît, ses deux méthodes deviennent statiques sur la façade. Paramètre `format` retiré (une seule valeur légale). |
| `CoinsOverlay` | `move`, `isVisible` retirés. |
| `ScreenPosition` | `Corner.isRight/isBottom` déplacés dans `core`. |
| `CoinsParseException`, `CoinsListener`, `CoinsChangedEvent` | inchangés |

Retirés du jar API : `ObolApi`, `StoredWallet`, `PlayerWallet`,
`BalanceStore`, `CoinsFormat`, `internal.ObolRuntime`, `internal.ObolApiHolder`
(remplacés par `Obol`, `internal.ObolBackend`, `internal.ObolBackendHolder`).

10 types publics, une trentaine de membres. Le module `api` ne contient plus
aucune logique de verrouillage ni d'événement.

Gardés malgré zéro usage dans `core`, parce qu'ils répondent à un cas énoncé
ou évitent de repasser par du copper brut : `times` (prix × quantité),
`color`/`texture` (pages UI d'un mod tiers, promesse du README),
`show`/`update` (étiquette de prix), `increased()`.

## Chemin

Cinq étapes, un seul changement logique. Pas de compatibilité ascendante à
maintenir. Notes d'exécution :

- Les étapes 1 à 4 ne compilent qu'ensemble : couper `api` casse `core`
  jusqu'à la fin de l'étape 4. `./gradlew build` vert est le critère de fin
  de l'étape 4, pas de chaque étape.
- Les numéros de ligne cités (`ObolPlugin.java:49`, `:109-123`…) sont ceux
  du code avant modification. Lire chaque fichier avant de le toucher.
- Avant de supprimer `CoinsFormat.java`, en lire le code et la javadoc : la
  sémantique de `parse` (formats acceptés, insensible à la casse, tiers dans
  n'importe quel ordre, chacun au plus une fois, nombre nu = copper,
  `CoinsParseException` sinon) et de `format` (tiers à zéro omis, `0c` pour
  zéro) passe telle quelle dans `Coins`, tests compris.
- La javadoc de `Wallet` (interface) reprend le contrat de l'actuelle classe
  abstraite : c'est elle que lit un modder.
- Le README reste en anglais. Ce plan et les échanges sont en français.
- Le commit est fait par l'auteur du projet, pas par l'assistant.

### 1. Le module `api` : couper

- `Wallet.java` : réécrire en interface (javadoc du contrat conservée, sans
  la partie "template method" ni les hooks `loadCopper`/`saveCopper`,
  avec `clear()` en plus).
- Supprimer `StoredWallet.java`, `PlayerWallet.java`, `BalanceStore.java`,
  `CoinsFormat.java`, `internal/ObolRuntime.java`.
- `Coins.java` : ajouter `static parse(String)` et déplacer `toString()`
  (le code de `CoinsFormat.STANDARD.format` et `CoinsFormat.parse`, avec
  `TOKEN`, `UNITS`, `tierOf`). Retirer `of(m, g, s, c)`, `isZero`, `amountOf`.
- `WalletId.java` : ajouter `player(UUID)` et `PLAYER_KIND`. Retirer
  `storageKey()` et `parse()`, `toString()` reste `kind:key`.
- `Denomination.java` : retirer `largest()`. Sa javadoc (classe et
  `symbol()`) cite `CoinsFormat`, à reformuler vers `Coins.toString()`.
- `ObolApi.java` → `Obol.java` : classe finale non instanciable, cinq
  méthodes statiques qui délèguent chacune à
  `ObolBackendHolder.require()`. Javadoc : la dépendance manifeste garantit
  qu'Obol est chargé avant le mod, l'`IllegalStateException` signale une
  dépendance manquante ou mal ordonnée.
- `CoinsDisplay.java` : supprimer, `show`/`track` passent dans `Obol` (et
  `ObolBackend`) sans le paramètre `format`. Leur javadoc (viewer connecté
  sinon `IllegalArgumentException`, un overlay par appel, mort à la
  déconnexion) suit, sans "closed set of formats", sans
  `{@link BalanceStore#set}`, sans `IllegalStateException` pour stockage
  négatif.
- `CoinsOverlay.java` : retirer `move`, `isVisible`.
- `ScreenPosition.java` : retirer `Corner.isRight/isBottom`.
- `internal/ObolRuntime.java` → `internal/ObolBackend.java` : les quatre
  méthodes de la façade, sans `static`.
- `internal/ObolApiHolder.java` → `internal/ObolBackendHolder.java` :
  `AtomicReference<ObolBackend>`, `install(ObolBackend)`, `uninstall()`,
  `require()`. Retirer `runtime()`, `find()`, le record `Installed`.
- Tests `api/src/test` : supprimer `WalletTest`, `StoredWalletTest`,
  `FieldWallet`, `InMemoryRuntime` (la logique part dans `core`, voir 4).
  `CoinsFormatTest` devient une partie de `CoinsTest` (format + parse).
  Adapter `CoinsTest`, `DenominationTest`, `WalletIdTest`, `ScreenPositionTest`
  aux membres retirés. `ObolApiHolderTest` → `ObolTest` : la façade lève
  sans backend, délègue au backend installé, double `install` refusé.
- `api/build.gradle.kts` : le commentaire "wallet rules are unit-tested here"
  n'est plus vrai, le remplacer par "value types are unit-tested here".

### 2. Le module `core` : le wallet devient une implémentation

Nouveau `core/src/main/java/dev/galysso/obol/internal/WalletImpl.java`
(`final class`, constructeur package-private) :

- Champs : `WalletId id`, `BalanceStoreImpl store`, `WalletLocks locks`,
  `Listeners listeners`.
- `balance`, `deposit`, `withdraw`, `transferTo`, `canAfford`, `clear` : reprendre le
  corps actuel de `Wallet.java` (api) en remplaçant `runtime().lockFor` par
  `locks.lockFor`, `load()`/`saveCopper()` par `store.balance(id)` /
  `store.set(id, coins)`, `runtime.publish` par `listeners.publish`.
  Le rollback dans `transferLocked` disparaît (le store est une map en
  mémoire, `set` ne lève pas). L'ordre global des verrous se fait sur
  `id.toString()` (`kind:key`, même ordre qu'avant).
  Factoriser `deposit`/`withdraw` avec un helper privé qui gère verrou,
  lecture, écriture et publication (la seule différence entre les deux est
  le calcul de `after` et le cas "rien à écrire").
- `clear()` : corps actuel de `GuardedBalanceStore.delete`.
- `set(Coins)` public sur `WalletImpl` (pas sur l'interface `Wallet`, donc
  invisible via `Obol.wallet`), corps actuel de `GuardedBalanceStore.set`,
  pour `/obol set` uniquement. `ObolCommand` est dans `command`, pas dans
  `internal`, package-private ne suffirait pas.
- `equals`/`hashCode` sur `id`, `toString()` = `Wallet[kind:key]`.

`ObolApiImpl.java` → `ObolBackendImpl.java`, `implements ObolBackend` :
- Retirer `lockFor` et `publish`.
  `storedBalances()` reste, comme méthode propre (plus un override) : c'est
  par elle que `ObolPlugin.java:49` alimente `BalancesPersistence`, qui
  dépend déjà de `BalanceStoreImpl` en concret (`load`, `snapshot`,
  `isDirty`, `markDirty`).
- Retirer `balances()` et le champ `GuardedBalanceStore`.
- Ajouter `wallet(WalletId)` → `new WalletImpl(id, store, locks, listeners)`,
  avec le type de retour covariant `WalletImpl` pour que les commandes
  atteignent `set`.
- `display()` (covariant `CoinsDisplayImpl`) reste pour `ObolPlugin`
  (`onDisconnect`), mais n'est plus dans l'interface. `show`/`track` de
  `ObolBackend` délèguent à `CoinsDisplayImpl`, qui ne implémente plus
  aucune interface publique.

Supprimer `GuardedBalanceStore.java` et son test. `BalanceStoreImpl` reste
mais n'implémente plus `BalanceStore` (l'interface n'existe plus) : garder
`balance`, `set`, `delete`, `exists` (utilisé par `BalancesPersistenceTest`),
`load`, `snapshot`, `isDirty`, `markDirty`. Sa clé reste `id.toString()`, donc
le format de `balances.json` ne bouge pas.

`WalletLocks.java` : inchangé (sa javadoc cite `storageKey`, à corriger).

### 3. `core` : commandes, HUD, plugin

- `ObolPlugin.java` : `ObolBackendHolder.install(backend)` au même endroit
  (constructeur), `uninstall()` aux deux mêmes endroits. Passer `backend`
  aux constructeurs de `ObolCommand` et `PlayerHuds` : `core` n'appelle
  jamais la façade `Obol`, il parle à son implémentation directement.
- `command/ObolCommand.java` : `backend.wallet(WalletId.player(target))`
  partout, et `Set` appelle `.set(amount)` dessus (d'où le retour
  covariant).
  `Commands.java` : `Coins.parse` et `coins.toString()` à la place de
  `CoinsFormat`, `amount.equals(Coins.ZERO)` à la place de `isZero`. La
  javadoc de `Commands` qui dit "through `ObolApi.get()`" à corriger.
- `ui/CoinsDisplayImpl.java`, `ui/Overlay.java`, `ui/CoinsHud.java`,
  `ui/OverlayHuds.java`, `ui/ServerHuds.java`, `ui/HudTemplates.java` :
  retirer le paramètre `CoinsFormat` de bout en bout (`OverlayHuds.open`,
  `ServerHuds`, le constructeur de `CoinsHud`). `HudTemplates` n'a plus
  qu'un template : `document(ScreenPosition)`, plus de `supports` ni de
  `BY_FORMAT_ID`. `Overlay.move` disparaît, `Overlay.isVisible` devient
  package-private (pour `CoinsDisplayImplTest`).
- `ui/PlayerHuds.java` : reçoit `ObolBackend` et appelle
  `backend.track(player, POSITION, backend.wallet(WalletId.player(player)))`. Sans
  `isVisible`, `onReady` ne re-suit que si `shown` n'a pas d'entrée pour le
  joueur : `onDisconnect` la retire, et personne d'autre ne cache cet
  overlay, donc le comportement (un overlay par session, idempotent sur un
  changement de monde) est le même. `PlayerHudsTest` remplace son
  `FakeDisplay` (qui implémentait `CoinsDisplay`) par un faux `ObolBackend`
  dont seuls `wallet` et `track` sont utiles.
- `Corner.isRight/isBottom` : deux méthodes statiques privées dans
  `HudTemplates` (seul appelant, six sites : `document`, `feedRow`,
  `anchor`).
- `ui/HudTemplates.java` : la javadoc cite `CoinsFormat.STANDARD` pour le
  format `+2g 50s` du feed : dire "le format de `Coins.toString()`".

### 4. Tests `core`

- Nouveau `core/src/test/.../internal/WalletImplTest.java` : reprendre les
  cas de `WalletTest` et `StoredWalletTest` (api) : dépôt, retrait couvert
  et non couvert, transfert all-or-nothing, transfert vers soi-même,
  débordement sans écriture, événements publiés hors verrou dans l'ordre
  débit puis crédit, absence d'événement pour un dépôt de zéro, `clear`
  publie vers `ZERO`, deux handles de même id sont égaux. Le test
  s'instancie avec `BalanceStoreImpl`, `WalletLocks`, `Listeners` réels,
  pas de fake.
- `ObolApiImplTest` → `ObolBackendImplTest` : wallets via `backend.wallet(WalletId.player(...))`, les cas admin
  `set`/`clear` via `backend.wallet(id).set(...)` et `.clear()`, `show` sans
  format.
- `CoinsDisplayImplTest` : sans format, sans `move`. Les deux `Wallet`
  anonymes (`:109-123` le wallet "taxe" qui reçoit un transfert imbriqué
  depuis un listener, `:183-197` le wallet corrompu) deviennent : un vrai
  `backend.wallet(new WalletId("tax", ...))` pour le premier, et le second
  disparaît avec la clause "stockage négatif". Le test "format rejeté"
  (`:172`) disparaît aussi. `isVisible` via l'accès package-private de
  `Overlay`.
- `HudTemplatesTest` : `Coins.parse("2g 5s 4c")` à la place de
  `Coins.of(m, g, s, c)` (six sites), `Denomination.MYTHRIL` à la place de
  `largest()`, plus de test `supports`.
- `RecordingHuds` : `coins.toString()` à la place de `format.format(coins)`.
- `WalletLocksTest` : `new WalletId("player", ...)` à la place de
  `WalletId.parse`.
- `PlayerHudsTest`, `BalancesPersistenceTest`, `BalanceStoreImplTest` :
  adapter aux signatures.
- Supprimer `GuardedBalanceStoreTest`.

### 5. README

Réécrire la section "Modders" pour qu'elle raconte une seule voie :

- Setup : inchangé, plus la phrase corrigée sur l'intégration optionnelle :
  `PluginManager.get().hasPlugin(...)` dans une classe qui ne cite aucun type
  Obol, puis `Obol.x()` normalement. Retirer la mention de `find()`.
- "Charging a player" : rien à récupérer ni à stocker,
  `Obol.playerWallet(uuid).withdraw(price)`.
- "Giving anything a wallet" : `Obol.wallet(new WalletId("guild", id))`,
  trois lignes, `clear()` quand le propriétaire disparaît. Plus de
  sous-classes, plus de `MerchantWallet`.
- "Showing coins" et "Listening" : `Obol.track(...)`, `Obol.show(...)`,
  `Obol.addListener(...)`, sans paramètre `format`, sans `move`.
- Référence API : refaire le tableau à partir de la section "API publique
  finale" ci-dessus. Retirer `ObolApi`, `CoinsDisplay`, `BalanceStore`,
  `CoinsFormat`, `StoredWallet`, `PlayerWallet`.
- Section "Guarantees" : garder, retirer "whatever the storage".
- Section "Building" : la phrase "one jar that also contains `api`" reste
  vraie.

`gradle.properties` : `version = 0.2.0`. Le README cite `0.1.0` à deux
endroits (setup), à mettre à jour.

## Anticipé, hors périmètre 0.2

Trois extensions de l'affichage, chacune ajoutable sans rien casser. Aucune
n'entre avant qu'un consommateur réel la demande.

- **Style facultatif.** Un record `CoinsStyle(background, zeroTiers, …)` avec
  `DEFAULT` et des `withX(...)`, et une surcharge `Obol.show(viewer,
  position, coins, style)` / `Obol.track(..., style)`. Les trois arguments de 0.2 restent
  l'appel par défaut. Premier cas probable : "sans fond" pour une étiquette
  de prix posée sur un autre élément. C'est ce que `CoinsFormat` essayait
  d'être avec une seule valeur légale.
- **Prix dans la page d'un autre mod** (une grille d'articles, un prix
  sous chacun). L'UI Hytale n'a pas de coordonnées : un document est un
  arbre de `Group` empilés (`LayoutMode: Left`/`Top`) avec des tailles
  (`Anchor: (Width, Height)`), et on le construit en envoyant des commandes
  à un `UICommandBuilder` (`appendInline(selector, markup)`,
  `append(selector, "Obol/Gold.ui")`, `set("#Gold #Count.Text", n)`). Une
  page (`CustomUIPage.build`) reçoit le même builder que le HUD, et le mod
  l'a donc en main. La "position" d'un prix est le sélecteur de sa cellule.
  D'où :

  ```java
  ObolUi.price(builder, "#Row2 #Cell3 #Price", item.price());          // dans la double boucle du mod
  ObolUi.price(builder, selector, coins, style);                        // avec CoinsStyle, plus tard
  ```

  qui rejoue ce que `CoinsHud.build` fait dans le document d'Obol
  (conteneur inline, `append` d'un `Obol/<Tier>.ui` par tier, `set` des
  compteurs), réutilisant `HudTemplates`. `ObolUi` est une classe à part du
  jar API qui référence `UICommandBuilder` : le module `api` gagne une
  dépendance `compileOnly` au serveur, ce qui ne coûte rien aux modders
  (ils compilent déjà contre lui) et laisse `Obol` et les tests de `api`
  sans serveur tant qu'ils ne touchent pas `ObolUi`. À vérifier au moment
  de le faire : si le client a un mode de grille natif (retour à la ligne),
  et si `appendInline` peut cibler un nœud arbitraire de la page (c'est ce
  que `CoinsHud` fait avec `#Pill`, donc a priori oui). D'ici là,
  `Denomination.texture()`/`color()`.
- **Emplacement du HUD natif.** Deux niveaux. Propriétaire du serveur : un
  fichier de config d'Obol (`hud.enabled`, `hud.position`) lu par
  `PlayerHuds`, via `withConfig` comme `balances.json`. Addon :
  `Obol.playerHud(ScreenPosition)` et `Obol.disablePlayerHud()`, réglage global appliqué
  aux joueurs connectés et aux suivants. Pas de poignée sur l'overlay natif :
  un addon qui devrait le cacher à chaque `PlayerReadyEvent` dépendrait de
  l'ordre des handlers entre plugins.

Le modèle de position ne change pas : le client ancre par distance aux bords
nommés (`HudTemplates.anchor`), donc `ScreenPosition` garde ses quatre coins.
Un coin droit fait déborder vers la gauche, un coin bas fait empiler le feed
vers le haut. Le serveur ne connaît pas la taille de l'écran, un seul coin de
référence ne permettrait pas de placer quelque chose en bas ou à gauche.

## Vérification

1. `./gradlew build` : compile les deux modules, exécute les tests `api`
   (types valeur) et `core` (wallet, persistance, HUD).
2. `./gradlew :api:javadoc` sans avertissement : garantit qu'aucune javadoc
   ne cite un type retiré (`{@link CoinsFormat}`, `{@link BalanceStore}`…).
3. `grep -rn 'CoinsFormat\|StoredWallet\|PlayerWallet\|BalanceStore\b\|ObolRuntime\|ObolApi\|CoinsDisplay\b\|storageKey\|find()' api/src core/src README.md`
   ne renvoie rien (seul `matcher.find()` dans `Coins.parse` reste, faux positif).
4. `./gradlew runServer`, puis `/obol give <moi> 2g`, `/obol set <moi> 0`,
   `/obol transfer` entre deux comptes : le HUD suit, le feed liste les
   changements, restart et le solde revient (le format JSON de
   `balances.json` est inchangé, clé `kind:key`).