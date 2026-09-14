package dev.galysso.obol.internal;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The on-disk shape of {@code balances.json}, and its codec.
 *
 * <p>A mutable bag of fields, as {@link BuilderCodec} wants: the server
 * instantiates it through {@link #BalancesState()} and fills it setter by
 * setter. The HUD preference lives in the same file as the balances, under
 * its own key, rather than in a second file. The file carries a
 * {@code Version} (1) so that a future format change can be migrated; a file
 * from a newer Obol is refused by the server, not misread.</p>
 */
public final class BalancesState {

    public static final BuilderCodec<BalancesState> CODEC = BuilderCodec
            .builder(BalancesState.class, BalancesState::new)
            .append(new KeyedCodec<>("Balances", new MapCodec<>(Codec.LONG, HashMap::new)),
                    (s, m) -> s.balances = m, s -> s.balances)
            .documentation("Balance in copper, keyed by wallet id (kind:key).")
            .add()
            .append(new KeyedCodec<>("HudEnabled", new ArrayCodec<>(Codec.UUID_STRING, UUID[]::new)),
                    (s, a) -> s.hudEnabled = new HashSet<>(Arrays.asList(a)),
                    s -> s.hudEnabled.toArray(UUID[]::new))
            .documentation("Players who turned the coins HUD on.")
            .add()
            .versioned()
            .codecVersion(1)
            .build();

    /** Keyed by {@code WalletId.storageKey()}, values in copper. */
    Map<String, Long> balances = new HashMap<>();

    /** Players who turned the coins HUD on. */
    Set<UUID> hudEnabled = new HashSet<>();

    public BalancesState() {
    }
}
