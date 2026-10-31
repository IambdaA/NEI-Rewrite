package codechicken.nei;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import codechicken.nei.item.ItemEntry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * NEI's item subsets ({@code SubsetWidget}): a tree of named groups of items (Blocks, Items > Tools > Pickaxes, Mod >
 * Minecraft ...) whose items can be hidden from the item list. Hidden items are kept in
 * {@code config/notenoughitems-hidden.txt}, one registry id per line.
 *
 * Differences from NEI: the groups are built from item tags and item classes of this Minecraft version (not NEI's list) and
 * hiding works per registry id (all variants of an item together). As in NEI a menu lists the items its group's own filter
 * accepts as icon rows under the child rows; a click on one hides or shows that item.
 */
public final class Subsets {

    public static final class Tag {

        public final String name;
        Predicate<ItemEntry> filter;
        public final List<Tag> children = new ArrayList<>();
        /** Every item of this tag and all tags below it. */
        final Set<ItemEntry> all = new LinkedHashSet<>();
        /** The items this tag's own filter accepts: NEI lists them as icon rows under the child rows of the tag's menu. */
        public final List<ItemEntry> own = new ArrayList<>();

        Tag(String name, Predicate<ItemEntry> filter) {
            this.name = name;
            this.filter = filter;
        }
    }

    private static volatile Tag root = new Tag("", null);
    private static final Set<String> HIDDEN = ConcurrentHashMap.newKeySet();
    private static final Map<Tag, Integer> STATE = new ConcurrentHashMap<>();
    private static boolean loaded;

    private Subsets() {}

    /** NEI's tag path ({@code Blocks.MobSpawners}): the names from the top down joined with dots. */
    public static String pathOf(Tag tag) {
        final String path = pathFrom(root, tag);
        return path == null ? tag.name : path;
    }

    private static String pathFrom(Tag from, Tag target) {
        for (Tag child : from.children) {
            if (child == target) return child.name;
            final String below = pathFrom(child, target);
            if (below != null) return child.name + "." + below;
        }
        return null;
    }

    /**
     * NEI's {@code %} search ({@code SubsetWidget.DefaultParserProvider}): every item of the subsets whose path (lower case,
     * no spaces) the matcher accepts, with the items of the subsets below them.
     */
    public static java.util.Set<ItemEntry> entriesMatching(Predicate<String> pathMatcher) {
        final java.util.Set<ItemEntry> found = new java.util.HashSet<>();
        collect(root, "", pathMatcher, found);
        return found;
    }

    private static void collect(Tag tag, String parentPath, Predicate<String> matcher, java.util.Set<ItemEntry> found) {
        for (Tag child : tag.children) {
            final String path = parentPath.isEmpty() ? child.name : parentPath + "." + child.name;
            if (matcher.test(path.toLowerCase(java.util.Locale.ROOT).replace(" ", ""))) found.addAll(child.all);
            collect(child, path, matcher, found);
        }
    }

    public static Tag root() {
        return root;
    }

    public static boolean isHidden(ItemEntry entry) {
        ensureLoaded();
        return !HIDDEN.isEmpty() && HIDDEN.contains(entry.registryId);
    }

    /** 0 = every item hidden, 1 = some hidden, 2 = all shown. */
    public static int state(Tag tag) {
        ensureLoaded();
        return STATE.computeIfAbsent(tag, t -> {
            int hidden = 0;
            for (ItemEntry e : t.all) if (HIDDEN.contains(e.registryId)) hidden++;
            return hidden == 0 ? 2 : hidden == t.all.size() ? 0 : 1;
        });
    }

    private static Tag tagged(String name, String path) {
        final TagKey<Item> key = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("minecraft", path));
        return new Tag(name, e -> e.stack.is(key));
    }

    /** Rebuilds the tree from the full item list. Runs on the client thread after the list is loaded. */
    static void rebuild(List<ItemEntry> entries) {
        final Tag newRoot = new Tag("", null);

        final Tag blocks = new Tag("Blocks", e -> e.stack.getItem() instanceof BlockItem);
        blocks.children.add(new Tag("Mob Spawners", e -> e.stack.is(Items.SPAWNER))); // NEI's Blocks.MobSpawners
        newRoot.children.add(blocks);

        final Tag items = new Tag("Items", e -> !(e.stack.getItem() instanceof BlockItem));
        final Tag tools = new Tag("Tools", null);
        tools.children.addAll(List.of(tagged("Pickaxes", "pickaxes"), tagged("Shovels", "shovels"),
                tagged("Axes", "axes"), tagged("Hoes", "hoes")));
        final Tag weapons = new Tag("Weapons", null);
        weapons.children.add(tagged("Swords", "swords"));
        final Tag armor = new Tag("Armor", null);
        armor.children.addAll(List.of(tagged("Helmets", "head_armor"), tagged("Chestplates", "chest_armor"),
                tagged("Leggings", "leg_armor"), tagged("Boots", "foot_armor")));
        items.children.addAll(List.of(tools, weapons, armor));
        items.children.add(new Tag("Food", e -> e.stack.has(DataComponents.FOOD)));
        items.children.add(new Tag("Potions", e -> e.registryId.endsWith("potion")));
        newRoot.children.add(items);

        final Tag mods = new Tag("Mod", null);
        final Map<String, String> modNames = new TreeMap<>();
        for (ItemEntry e : entries) modNames.putIfAbsent(e.modDisplay, e.modId);
        modNames.forEach((display, id) -> mods.children.add(new Tag(display, e -> e.modId.equals(id))));
        newRoot.children.add(mods);

        // NEI's "Presets" subsets: one per enabled Subset preset
        final Tag presets = new Tag("Presets", null);
        for (var preset : Presets.active(codechicken.nei.item.Preset.Mode.SUBSET)) {
            presets.children.add(new Tag(preset.name, e -> preset.items.contains(e.registryId)));
        }
        newRoot.children.add(presets);

        for (var subset : codechicken.nei.api.API.subsets()) insert(newRoot, subset);

        fill(newRoot, entries);
        prune(newRoot);
        sort(newRoot);
        STATE.clear();
        root = newRoot;
    }

    /** NEI's {@code API.addSubset}: a dotted name such as {@code Blocks.MobSpawners} (any case) walks or creates the levels. */
    private static void insert(Tag root, codechicken.nei.api.API.Subset subset) {
        final Predicate<ItemEntry> filter = e -> subset.filter().matches(e);
        Tag at = root;
        final String[] parts = subset.name().split("\\.");
        for (int i = 0; i < parts.length; i++) {
            final String part = parts[i].trim();
            if (part.isEmpty()) continue;
            final boolean last = i == parts.length - 1;
            Tag next = null;
            for (Tag child : at.children) {
                if (child.name.equalsIgnoreCase(part)) {
                    next = child;
                    break;
                }
            }
            if (next == null) {
                next = new Tag(part, last ? filter : null);
                at.children.add(next);
            } else if (last) {
                // the name exists (built in or from another mod): its items join this one
                final Predicate<ItemEntry> old = next.filter;
                next.filter = old == null ? filter : old.or(filter);
            }
            at = next;
        }
    }

    /** Rebuilds the tree after the presets changed. */
    public static void refresh() {
        if (ItemList.isLoaded()) rebuild(ItemList.all());
    }

    private static void fill(Tag tag, List<ItemEntry> entries) {
        if (tag.filter != null) {
            for (ItemEntry e : entries) {
                try {
                    if (tag.filter.test(e)) {
                        tag.all.add(e);
                        tag.own.add(e);
                    }
                } catch (RuntimeException ex) {
                    // a tag that cannot judge an item does not contain it
                }
            }
        }
        for (Tag child : tag.children) {
            fill(child, entries);
            tag.all.addAll(child.all);
        }
    }

    private static void prune(Tag tag) {
        tag.children.removeIf(c -> c.all.isEmpty());
        tag.children.forEach(Subsets::prune);
    }

    private static void sort(Tag tag) {
        // NEI sorts every level by name, but keeps the built-in order here so Blocks and Items stay first
        if (tag.name.equals("Mod") || tag.name.equals("Tools") || tag.name.equals("Armor")) {
            tag.children.sort(Comparator.comparing(c -> c.name));
        }
        tag.children.forEach(Subsets::sort);
    }

    // ---- changing what is hidden ----

    public static void setHidden(Tag tag, boolean hidden) {
        ensureLoaded();
        for (ItemEntry e : tag.all) {
            if (hidden) HIDDEN.add(e.registryId);
            else HIDDEN.remove(e.registryId);
        }
        changed();
    }

    /** NEI's {@code setHidden(ItemStack, boolean)}: one item of a subset's menu (per registry id here). */
    public static void setHidden(ItemEntry entry, boolean hidden) {
        ensureLoaded();
        if (hidden) HIDDEN.add(entry.registryId);
        else HIDDEN.remove(entry.registryId);
        changed();
    }

    /** Hides everything that is in a subset except this one. */
    public static void showOnly(Tag tag) {
        ensureLoaded();
        HIDDEN.clear();
        for (ItemEntry e : root.all) HIDDEN.add(e.registryId);
        for (ItemEntry e : tag.all) HIDDEN.remove(e.registryId);
        changed();
    }

    public static void unhideAll() {
        ensureLoaded();
        HIDDEN.clear();
        changed();
    }

    private static void changed() {
        STATE.clear();
        save();
        ItemList.refilter();
    }

    // ---- file ----

    private static Path file() {
        return NotEnoughItems.platform().configDir().resolve("notenoughitems-hidden.txt");
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            final Path f = file();
            if (Files.exists(f)) {
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) HIDDEN.add(line.trim());
                }
            }
        } catch (IOException e) {
            NotEnoughItems.LOGGER.warn("Could not read hidden items: {}", e.toString());
        }
    }

    private static void save() {
        try {
            Files.write(file(), new ArrayList<>(HIDDEN).stream().sorted().toList(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            NotEnoughItems.LOGGER.warn("Could not save hidden items: {}", e.toString());
        }
    }
}
