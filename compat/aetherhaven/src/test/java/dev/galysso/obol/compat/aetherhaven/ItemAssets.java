package dev.galysso.obol.compat.aetherhaven;

import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.event.IEventBus;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * An empty item asset store, so that a test can make real {@code ItemStack}s
 * without a server: the constructor looks its item up, and an unknown item
 * is fine. Installed in {@code Item}'s static field, and put back after.
 */
final class ItemAssets extends AssetStore<String, Item, DefaultAssetMap<String, Item>> {

    private static Object previous;

    private final EventBus events = new EventBus(false);

    private ItemAssets(Config config) {
        super(config);
    }

    static void install() throws ReflectiveOperationException {
        Field field = field();
        previous = field.get(null);
        field.set(null, new Config().build());
    }

    static void restore() throws ReflectiveOperationException {
        field().set(null, previous);
    }

    private static Field field() throws ReflectiveOperationException {
        Field field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        return field;
    }

    @Override
    protected IEventBus getEventBus() {
        return events;
    }

    @Override
    public void addFileMonitor(String pack, Path path) {
    }

    @Override
    public void removeFileMonitor(Path path) {
    }

    @Override
    protected void handleRemoveOrUpdate(Set<String> removed, Map<String, Item> updated, AssetUpdateQuery query) {
    }

    private static final class Config extends Builder<String, Item, DefaultAssetMap<String, Item>, Config> {
        Config() {
            super(String.class, Item.class, new DefaultAssetMap<>());
            setPath("Items");
            setCodec(Item.CODEC);
            setKeyFunction(Item::getId);
        }

        @Override
        public ItemAssets build() {
            return new ItemAssets(this);
        }
    }
}
