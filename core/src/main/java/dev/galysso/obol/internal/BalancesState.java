package dev.galysso.obol.internal;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import java.util.HashMap;
import java.util.Map;

/**
 * The on-disk shape of {@code balances.json}, and its codec.
 *
 * <p>A mutable bag of fields, as {@link BuilderCodec} wants: the server
 * instantiates it through {@link #BalancesState()} and fills it setter by
 * setter. The file carries a {@code Version} (1) so that a future format
 * change can be migrated; a file from a newer Obol is refused by the server,
 * not misread. Unknown keys are ignored on read (the {@code HudEnabled} list
 * written before 0.1.0 disappears at the next save).</p>
 */
public final class BalancesState {

    public static final BuilderCodec<BalancesState> CODEC = BuilderCodec
            .builder(BalancesState.class, BalancesState::new)
            .append(new KeyedCodec<>("Balances", new MapCodec<>(Codec.LONG, HashMap::new)),
                    (s, m) -> s.balances = m, s -> s.balances)
            .documentation("Balance in copper, keyed by wallet id (kind:key).")
            .add()
            .versioned()
            .codecVersion(1)
            .build();

    /** Keyed by {@code WalletId.storageKey()}, values in copper. */
    Map<String, Long> balances = new HashMap<>();

    public BalancesState() {
    }
}
