package codechicken.nei;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.neoforged.fml.loading.FMLPaths;

/** NEI's "inventory.history.save": the history panel's items (registry ids, newest first) kept between sessions. */
public final class HistoryFile {

    private HistoryFile() {}

    /** The world's name (singleplayer) or the server address, safe as a file name; "global" outside a world. */
    static String worldKey() {
        final var mc = net.minecraft.client.Minecraft.getInstance();
        String name = "global";
        // The save folder is unique, the level name is not.
        if (mc.getSingleplayerServer() != null) {
            name = "world-" + mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .normalize().getFileName();
        }
        else if (mc.getCurrentServer() != null) name = "server-" + mc.getCurrentServer().ip;
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String cachedKey = "global";
    private static long keyAt;

    /** {@link #worldKey()}, at most four times a second (callers ask every frame). */
    static String currentWorld() {
        final long now = System.currentTimeMillis();
        if (now - keyAt > 250) {
            keyAt = now;
            cachedKey = worldKey();
        }
        return cachedKey;
    }

    /**
     * The config file for the bookmarks / favorites: the plain one, or one per world when {@code perWorld}. A world file
     * that does not exist yet is read from the plain one (NEI starts a new world with the global bookmarks).
     */
    static Path configFile(String base, boolean perWorld) {
        final Path plain = FMLPaths.CONFIGDIR.get().resolve("notenoughitems-" + base + ".txt");
        return perWorld ? FMLPaths.CONFIGDIR.get().resolve("notenoughitems-" + base + "-" + currentWorld() + ".txt") : plain;
    }

    static Path plainFile(String base) {
        return FMLPaths.CONFIGDIR.get().resolve("notenoughitems-" + base + ".txt");
    }

    /** The file name part that tells histories apart: the world when world specific, else "all". */
    public static String key() {
        return NEIConfig.HISTORY_WORLD.getAsBoolean() ? worldKey() : "all";
    }

    private static Path file() {
        // NEI's inventory.history.worldSpecific: one list per world / server
        return FMLPaths.CONFIGDIR.get().resolve("notenoughitems-history-" + key() + ".txt");
    }

    public static List<String> read() {
        NotEnoughItems.LOGGER.info("Loading item history from {}", file());
        try {
            return Files.exists(file()) ? Files.readAllLines(file(), StandardCharsets.UTF_8) : List.of();
        } catch (IOException e) {
            NotEnoughItems.LOGGER.warn("Could not read history: {}", e.toString());
            return List.of();
        }
    }

    public static void write(List<String> ids) {
        try {
            Files.write(file(), ids, StandardCharsets.UTF_8);
        } catch (IOException e) {
            NotEnoughItems.LOGGER.warn("Could not save history: {}", e.toString());
        }
    }
}
