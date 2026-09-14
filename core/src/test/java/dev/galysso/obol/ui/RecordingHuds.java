package dev.galysso.obol.ui;

import dev.galysso.obol.api.CoinsFormat;
import dev.galysso.obol.api.ScreenPosition;

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
            public void show(ScreenPosition position, String text) {
                calls.add(key + " show " + HudTemplates.anchor(position) + " \"" + text + "\"");
            }

            @Override
            public void setText(String text) {
                calls.add(key + " text \"" + text + "\"");
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
