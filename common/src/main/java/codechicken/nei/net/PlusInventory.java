package codechicken.nei.net;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import codechicken.nei.NotEnoughItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

/**
 * NEI's extended creative inventory ("creative+"): 54 extra slots that belong to the player, not to the world's
 * inventory, kept on the server and opened as a 6 row chest named "Extended Creative". Because it is a vanilla chest
 * menu, a client without the mod can even open it; the client half only adds the buttons and the item list.
 *
 * Saved as {@code <world>/nei/creative_plus_<uuid>.json} on every change (NEI saves with the player data).
 * shortcut: a small file written on each change instead of hooking the player save.
 */
public final class PlusInventory {

    public static final String TITLE = "Extended Creative"; // NEI's inventory name; the client finds its screen by it
    private static final int SIZE = 54;
    private static final Map<UUID, SimpleContainer> STORES = new HashMap<>();

    private PlusInventory() {}

    /** The chest menu, a class of its own so the packets can tell it from any other open menu. */
    private static final class Menu extends ChestMenu {

        Menu(int id, Inventory inventory, Container store) {
            super(MenuType.GENERIC_9x6, id, inventory, store, 6);
        }
    }

    /** A container that tells when it changed (SimpleContainer's own listener list is not there in 26.2). */
    private static final class Store extends SimpleContainer {

        Runnable onChange = () -> {};

        Store() {
            super(SIZE);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            onChange.run();
        }
    }

    public static void onServerStopped() {
        STORES.clear(); // the next world may be a different one for the same player
    }

    static void open(ServerPlayer player) {
        if (!player.isCreative()) return;
        final Container store = store(player);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new Menu(id, inventory, store), Component.literal(TITLE)));
    }

    static void setSlot(ServerPlayer player, int slot, ItemStack stack) {
        if (!player.isCreative() || !(player.containerMenu instanceof Menu) || slot < 0 || slot >= SIZE) return;
        store(player).setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(Math.min(stack.getCount(), stack.getMaxStackSize())));
    }

    static void cycle(ServerPlayer player, int steps) {
        if (!player.isCreative()) return;
        final SimpleContainer store = store(player);
        final Inventory inventory = player.getInventory();
        final List<ItemStack> extra = new ArrayList<>();
        final List<ItemStack> own = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) extra.add(store.getItem(i));
        for (int i = 0; i < 36; i++) own.add(inventory.getItem(i));
        PlusRows.cycle(extra, own, steps);
        for (int i = 0; i < 36; i++) inventory.setItem(i, own.get(i));
        for (int i = 0; i < SIZE; i++) store.setItem(i, extra.get(i));
    }

    private static SimpleContainer store(ServerPlayer player) {
        return STORES.computeIfAbsent(player.getUUID(), id -> load(player.level().getServer(), id));
    }

    private static Path file(MinecraftServer server, UUID id) {
        return server.getWorldPath(LevelResource.ROOT).resolve("nei").resolve("creative_plus_" + id + ".json");
    }

    private static SimpleContainer load(MinecraftServer server, UUID id) {
        final Store store = new Store();
        final Path file = file(server, id);
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                final var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
                final List<ItemStack> list = ItemStack.OPTIONAL_CODEC.listOf().parse(ops, JsonParser.parseReader(reader)).result()
                        .orElse(List.of());
                for (int i = 0; i < Math.min(SIZE, list.size()); i++) store.setItem(i, list.get(i));
            } catch (IOException | RuntimeException e) {
                NotEnoughItems.LOGGER.warn("Could not read the extended creative inventory {}: {}", file, e.toString());
            }
        }
        store.onChange = () -> save(server, id, store);
        return store;
    }

    private static void save(MinecraftServer server, UUID id, Container store) {
        final Path file = file(server, id);
        try {
            final List<ItemStack> list = new ArrayList<>();
            for (int i = 0; i < SIZE; i++) list.add(store.getItem(i));
            final var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            final JsonElement json = ItemStack.OPTIONAL_CODEC.listOf().encodeStart(ops, list).getOrThrow();
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                new Gson().toJson(json, writer);
            }
        } catch (IOException | RuntimeException e) {
            NotEnoughItems.LOGGER.warn("Could not save the extended creative inventory {}: {}", file, e.toString());
        }
    }
}
