package codechicken.nei;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import codechicken.nei.item.ItemEntry;
import codechicken.nei.item.Preset;
import codechicken.nei.item.PresetFile;
import codechicken.nei.recipe.RecipeIndex;
import net.neoforged.fml.loading.FMLPaths;

/**
 * NEI's presets ({@code PresetsList}), kept in {@code config/notenoughitems-presets.txt}.
 *
 * <ul>
 * <li>Hide: the items are not in the item list.</li>
 * <li>Remove: as Hide, and the recipes that need or only make those items are not shown either.</li>
 * <li>Subset: a subset "Presets &gt; name" in the subset menu.</li>
 * <li>Group: a collapsible group of the item list with the preset's name.</li>
 * </ul>
 * Differences from NEI: items are registry ids (all variants of an item together), and the editor is a window over the
 * inventory ({@code PresetsWindow}) instead of a settings screen.
 */
public final class Presets {

    private static final List<Preset> PRESETS = new ArrayList<>();
    private static volatile Set<String> hidden = Set.of();
    private static volatile Set<String> removed = Set.of();
    private static boolean loaded;

    private Presets() {}

    /** The live list; edit it, then call {@link #save()}. */
    public static synchronized List<Preset> list() {
        ensureLoaded();
        return PRESETS;
    }

    public static boolean hides(ItemEntry entry) {
        ensureLoaded();
        return !hidden.isEmpty() && hidden.contains(entry.registryId);
    }

    /** Registry ids of the items whose recipes are removed (enabled Remove presets). */
    public static Set<String> removedIds() {
        ensureLoaded();
        return removed;
    }

    /** Enabled presets of one mode, in list order. */
    public static List<Preset> active(Preset.Mode mode) {
        final List<Preset> out = new ArrayList<>();
        for (Preset p : list()) if (p.enabled && p.mode == mode) out.add(p);
        return out;
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("notenoughitems-presets.txt");
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(file())) PRESETS.addAll(PresetFile.parse(Files.readAllLines(file(), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            NotEnoughItems.LOGGER.warn("Could not read presets: {}", e.toString());
        }
        refreshSets();
    }

    private static void refreshSets() {
        final Set<String> hide = new HashSet<>();
        final Set<String> remove = new HashSet<>();
        for (Preset p : PRESETS) {
            if (!p.enabled) continue;
            if (p.mode == Preset.Mode.HIDE || p.mode == Preset.Mode.REMOVE) hide.addAll(p.items);
            if (p.mode == Preset.Mode.REMOVE) remove.addAll(p.items);
        }
        hidden = hide;
        removed = remove;
    }

    /** Writes the file and applies the presets everywhere (NEI's {@code savePresets}). */
    public static synchronized void save() {
        ensureLoaded();
        try {
            Files.write(file(), PresetFile.lines(PRESETS), StandardCharsets.UTF_8);
        } catch (IOException e) {
            NotEnoughItems.LOGGER.warn("Could not save presets: {}", e.toString());
        }
        refreshSets();
        Subsets.refresh();
        Collapsible.reload();
        RecipeIndex.invalidate();
        ItemList.refilter();
    }
}
