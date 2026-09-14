package dev.galysso.obol.ui;

import dev.galysso.obol.api.Coins;
import dev.galysso.obol.api.CoinsFormat;
import dev.galysso.obol.api.ScreenPosition;
import dev.galysso.obol.api.event.CoinsChangedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * An {@link OverlayHuds} with no screen: every call on every overlay is
 * written down as {@code "<key> <call>"}, in order, across all overlays.
 * Amounts are written in the overlay's format, for readable expectations.
 */
final class RecordingHuds implements OverlayHuds {

    final List<String> calls = new CopyOnWriteArrayList<>();
    /** Viewers the fake server knows; others get no overlay. */
    final Set<UUID> online = new CopyOnWriteArraySet<>();
    final List<String> keys = new ArrayList<>();

    @Override
    public Optional<OverlayHud> open(UUID viewer, String key, CoinsFormat format) {
        if (!online.contains(viewer)) {
            return Optional.empty();
        }
        keys.add(key);
        return Optional.of(new OverlayHud() {
            @Override
            public void show(ScreenPosition position, Coins coins) {
                calls.add(key + " show " + HudTemplates.anchor(position) + " \"" + format.format(coins) + "\"");
            }

            @Override
            public void setCoins(Coins coins) {
                calls.add(key + " text \"" + format.format(coins) + "\"");
            }

            @Override
            public void log(CoinsChangedEvent change) {
                Coins amount = change.increased()
                        ? change.after().minus(change.before()).orElseThrow()
                        : change.before().minus(change.after()).orElseThrow();
                calls.add(key + " log " + (change.increased() ? "+" : "-") + format.format(amount));
            }

            @Override
            public void move(ScreenPosition position) {
                calls.add(key + " move " + HudTemplates.anchor(position));
            }

            @Override
            public void hide() {
                calls.add(key + " hide");
            }
        });
    }
}
