package codechicken.nei;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import codechicken.nei.api.ItemFilter;
import codechicken.nei.item.ItemEntry;
import codechicken.nei.search.SearchParser;
import net.neoforged.fml.loading.FMLPaths;

/**
 * NEI's collapsible items ({@code CollapsibleItems}): groups of similar items (spawn eggs, music discs ...) that take
 * one slot of the item list until the group is expanded.
 *
 * Groups come from {@code config/notenoughitems-collapsible.txt} (written with defaults on first start): one search
 * expression per line, an item goes into the first group that matches; a {@code ; {"displayName": "...", "expanded":
 * true}} line before an expression sets that group's name and start state. Which groups are expanded is kept in
 * {@code config/notenoughitems-collapsible-state.txt}.
 *
 * Differences from NEI: groups are written in this port's search syntax (NEI's cfg uses the 1.7.10 item syntax) and the
 * default groups are the 26.2 equivalents of NEI's (spawn eggs, records -> music discs, splash potions, lingering
 * potions); the expanded state is global instead of per world.
 */
public final class Collapsible {

    public static final class Group {

        public final String key;
        final ItemFilter filter;
        public String name = "";
        volatile boolean expanded;

        Group(String key, ItemFilter filter) {
            this.key = key;
            this.filter = filter;
        }
    }

    private static final String DEFAULTS = """
            # Collapsible items: one search expression per line, an item goes into the first group that matches.
            # A line "; {"displayName": "Name", "expanded": true}" before an expression names the group.
            # Search syntax: &id  @mod  $tag  #tooltip, plain text searches the name.
            ; {"displayName": "Spawn Eggs"}
            &_spawn_egg
            ; {"displayName": "Music Discs"}
            &music_disc_
            ; {"displayName": "Splash Potions"}
            &splash_potion
            ; {"displayName": "Lingering Potions"}
            &lingering_potion
            """;

    private static volatile List<Group> groups = List.of();
    private static final Map<ItemEntry, Integer> CACHE = new ConcurrentHashMap<>();
    private static volatile int version;
    private static boolean loaded;

    private Collapsible() {}

    // ---- groups ----

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        reload();
    }

    public static synchronized void reload() {
        loaded = true;
        final List<Group> list = new ArrayList<>();
        final SearchParser parser = new SearchParser();
        try {
            final Path file = FMLPaths.CONFIGDIR.get().resolve("notenoughitems-collapsible.txt");
            if (!Files.exists(file)) Files.writeString(file, DEFAULTS, StandardCharsets.UTF_8);
            parse(Files.readAllLines(file, StandardCharsets.UTF_8), parser, list);
        } catch (IOException e) {
            NotEnoughItems.LOGGER.warn("Could not read collapsible items: {}", e.toString());
            parse(DEFAULTS.lines().toList(), parser, list);
        }

        // NEI's Group presets come first, then the groups of the file
        final List<Group> withPresets = new ArrayList<>();
        for (var preset : Presets.active(codechicken.nei.item.Preset.Mode.GROUP)) {
            final Group group = new Group("preset:" + preset.name, e -> preset.items.contains(e.registryId));
            group.name = preset.name;
            withPresets.add(group);
        }
        list.addAll(0, withPresets);

        loadStates(list);
        CACHE.clear();
        groups = List.copyOf(list);
        version++;
    }

    static void parse(List<String> lines, SearchParser parser, List<Group> out) {
        String name = "";
        Boolean expanded = null;

        for (String raw : lines) {
            final String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (line.startsWith(";")) {
                try {
                    final var json = com.google.gson.JsonParser.parseString(line.substring(1).trim()).getAsJsonObject();
                    if (json.has("displayName")) name = json.get("displayName").getAsString();
                    if (json.has("expanded")) expanded = json.get("expanded").getAsBoolean();
                } catch (RuntimeException e) {
                    NotEnoughItems.LOGGER.warn("Bad collapsible items setting: {}", line);
                }
                continue;
            }

            final ItemFilter filter = parser.parsePlain(line);
            if (filter != ItemFilter.EVERYTHING) {
                final Group group = new Group(line, filter);
                group.name = name;
                group.expanded = expanded != null && expanded;
                out.add(group);
            }
            name = "";
            expanded = null;
        }
    }

    // ---- state ----

    public static boolean isEmpty() {
        ensureLoaded();
        return groups.isEmpty() || !NEIConfig.COLLAPSIBLE_ENABLED.getAsBoolean();
    }

    /** Changes whenever the groups or their open / closed state change, so callers can cache what they built. */
    public static int version() {
        return version;
    }

    public static int groupIndex(ItemEntry entry) {
        ensureLoaded();
        if (isEmpty() || entry == null) return -1;
        return CACHE.computeIfAbsent(entry, e -> {
            final List<Group> list = groups;
            for (int i = 0; i < list.size(); i++) {
                try {
                    if (list.get(i).filter.matches(e)) return i;
                } catch (RuntimeException ex) {
                    // a filter that cannot judge an item does not contain it
                }
            }
            return -1;
        });
    }

    public static String displayName(int index) {
        return index >= 0 && index < groups.size() ? groups.get(index).name : "";
    }

    public static boolean isExpanded(int index) {
        return index < 0 || index >= groups.size() || groups.get(index).expanded;
    }

    public static void setExpanded(int index, boolean expanded) {
        if (index < 0 || index >= groups.size()) return;
        groups.get(index).expanded = expanded;
        changed();
    }

    /** NEI's "G" button: null opens everything unless something is already open, then closes everything. */
    public static void toggleAll(Boolean expanded) {
        ensureLoaded();
        final boolean value = expanded != null ? expanded : groups.stream().noneMatch(g -> g.expanded);
        for (Group g : groups) g.expanded = value;
        changed();
    }

    private static void changed() {
        version++;
        saveStates();
    }

    private static Path stateFile() {
        return FMLPaths.CONFIGDIR.get().resolve("notenoughitems-collapsible-state.txt");
    }

    private static void saveStates() {
        final List<String> lines = new ArrayList<>();
        for (Group g : groups) lines.add((g.expanded ? "open " : "closed ") + g.key);
        try {
            Files.write(stateFile(), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            NotEnoughItems.LOGGER.warn("Could not save collapsible items state: {}", e.toString());
        }
    }

    private static void loadStates(List<Group> list) {
        try {
            if (!Files.exists(stateFile())) return;
            final Map<String, Boolean> states = new HashMap<>();
            for (String line : Files.readAllLines(stateFile(), StandardCharsets.UTF_8)) {
                if (line.startsWith("open ")) states.put(line.substring(5), true);
                else if (line.startsWith("closed ")) states.put(line.substring(7), false);
            }
            for (Group g : list) if (states.containsKey(g.key)) g.expanded = states.get(g.key);
        } catch (IOException e) {
            NotEnoughItems.LOGGER.warn("Could not read collapsible items state: {}", e.toString());
        }
    }
}
