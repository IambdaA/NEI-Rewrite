package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Extra recipe providers next to the synced vanilla recipes: the JEI bridge today, a NEI-native mod API later. A source
 * returns finished {@link RecipeView}s; {@link RecipeIndex} calls it when it (re)builds, on the client thread.
 * Plain Java on purpose, so nothing here loads JEI classes.
 */
public final class RecipeSources {

    private static final Map<String, Supplier<List<RecipeView>>> SOURCES = new ConcurrentHashMap<>();
    /** Ids of sources that cover every modded machine (JEI): only they make result-only vanilla displays redundant. */
    private static final java.util.Set<String> COMPLETE = ConcurrentHashMap.newKeySet();

    private RecipeSources() {}

    /** Adds or replaces a source and makes the index rebuild. */
    public static void set(String id, Supplier<List<RecipeView>> source) {
        set(id, source, false);
    }

    /**
     * @param complete the source has the full recipes of all modded machines, so vanilla displays that only know their
     *                 result are dropped (a mod's own source covers just that mod and must not hide other mods' entries)
     */
    public static void set(String id, Supplier<List<RecipeView>> source, boolean complete) {
        SOURCES.put(id, source);
        if (complete) COMPLETE.add(id);
        else COMPLETE.remove(id);
        RecipeIndex.invalidate();
    }

    public static void remove(String id) {
        COMPLETE.remove(id);
        if (SOURCES.remove(id) != null) RecipeIndex.invalidate();
    }

    /** Makes the index rebuild (the stations of a recipe type changed). */
    public static void invalidate() {
        RecipeIndex.invalidate();
    }

    public static boolean any() {
        return !SOURCES.isEmpty();
    }

    static boolean anyComplete() {
        return !COMPLETE.isEmpty();
    }

    static List<RecipeView> all() {
        final List<RecipeView> out = new ArrayList<>();
        for (Supplier<List<RecipeView>> s : SOURCES.values()) {
            try {
                out.addAll(s.get());
            } catch (RuntimeException | LinkageError e) {
                codechicken.nei.NotEnoughItems.LOGGER.warn("A recipe source failed: {}", e.toString());
            }
        }
        return out;
    }
}
