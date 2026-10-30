package codechicken.nei;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkPayload;

/**
 * The player's bookmarks, as in GTNH NEI: a list of pages ("namespaces"), each a {@link BookmarkGrid} of items with an
 * amount and an optional group. Only the active page is shown in the panel, and {@link #toggle}, {@link #amount} etc.
 * work on it.
 *
 * Pages: NEI keeps one empty page after the last used one and drops empty pages as you move off them, so there is
 * always somewhere to start a new page and never a pile of empty ones. {@link #namespaceCount()} does that bookkeeping,
 * exactly as NEI's {@code BookmarkStorage#getNamespaceSize}.
 *
 * File ({@code config/notenoughitems-bookmarks.txt}):
 *
 * <pre>
 * #active 0
 * #space
 * #grp 1 todo collapsed          (group 1 is in todo view and collapsed; flags are optional)
 * #collapse 1 &lt;recipe key&gt;       (a recipe of crafting group 1 folded into one row)
 * minecraft:stone 64 0           (registry id, amount, group id; 0 = no group)
 * minecraft:dirt 1 1
 * </pre>
 *
 * Older files load too: plain {@code id amount} lines go to the first page, and the named groups of the previous
 * version of this port ({@code #group name}) each become a page.
 *
 * Ids are resolved against the item list when shown, so a bookmark for an uninstalled mod's item just stays hidden
 * until the mod comes back.
 *
 * Not yet ported from NEI: bookmarking whole recipes, crafting-chain groups.
 */
public final class Bookmarks {

    public static final int MAX_AMOUNT = BookmarkGrid.MAX_AMOUNT;

    private static final List<BookmarkGrid> SPACES = new ArrayList<>();
    private static int active;
    private static boolean loaded;
    private static Path loadedFile;

    private Bookmarks() {}

    private static Path file() {
        return HistoryFile.configFile("bookmarks", NEIConfig.BOOKMARKS_WORLD.getAsBoolean());
    }

    private static synchronized void ensureLoaded() {
        final Path file = file();
        // NEI's inventory.bookmarks.worldSpecific: another world (or the option switched) loads another file. Every
        // change is saved at once, so nothing is lost by dropping what is in memory.
        if (loaded && file.equals(loadedFile)) return;
        loaded = true;
        loadedFile = file;
        SPACES.clear();
        boolean seeded = false;
        int wantedActive = 0;
        try {
            Path f = file;
            if (!Files.exists(f)) { // a new world starts with a copy of the global bookmarks (NEI copies the file)
                f = HistoryFile.plainFile("bookmarks");
                seeded = !f.equals(file);
            }
            if (Files.exists(f)) {
                BookmarkGrid grid = null;
                for (String raw : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    final String line = raw.trim();
                    if (line.isEmpty()) continue;
                    final String[] parts = line.split("\\s+");

                    if (parts[0].equals("#active")) {
                        wantedActive = parseInt(parts, 1, 0);
                    } else if (parts[0].equals("#space") || parts[0].equals("#group")) {
                        // "#group name" is the previous version's named group: it becomes a page
                        if (grid == null || !grid.isEmpty()) {
                            grid = new BookmarkGrid();
                            SPACES.add(grid);
                        }
                    } else if (parts[0].equals("#grp")) {
                        if (grid == null) {
                            grid = new BookmarkGrid();
                            SPACES.add(grid);
                        }
                        final int id = parseInt(parts, 1, 0);
                        if (id != BookmarkGrid.DEFAULT_GROUP_ID) {
                            final BookmarkGrid.Group g = grid.group(id);
                            for (int i = 2; i < parts.length; i++) {
                                if (parts[i].equals("todo")) g.todo = true;
                                if (parts[i].equals("collapsed")) g.collapsed = true;
                                if (parts[i].equals("crafting")) g.crafting = true;
                            }
                        }
                    } else if (parts[0].equals("#collapse") && parts.length > 2 && grid != null) {
                        grid.group(parseInt(parts, 1, 0)).collapsedRecipes.add(parts[2]);
                    } else if (!parts[0].startsWith("#")) {
                        if (grid == null) {
                            grid = new BookmarkGrid();
                            SPACES.add(grid);
                        }
                        final int type = parseInt(parts, 3, 0);
                        final String linked = parts.length > 4 ? parts[4] : null;
                        if (type == 0 && grid.find(parts[0], linked) != null) continue;
                        final int amount = Math.max(0, Math.min(MAX_AMOUNT, parseInt(parts, 1, 1)));
                        final int group = Math.max(0, parseInt(parts, 2, 0));
                        final BookmarkGrid.Item item = new BookmarkGrid.Item(parts[0], amount, group);
                        if (linked != null) {
                            item.type = type == 2 ? 2 : type == 1 ? 1 : 0;
                            item.recipeId = linked;
                            item.factor = Math.max(1, parseInt(parts, 5, 1));
                            if (parts.length > 6) item.options = List.of(parts[6].split(","));
                        }
                        grid.items.add(item);
                    }
                }
            }
        } catch (IOException e) {
            NotEnoughItems.LOGGER.warn("Could not read bookmarks: {}", e.toString());
        }
        if (SPACES.isEmpty()) SPACES.add(new BookmarkGrid());
        for (BookmarkGrid g : SPACES) g.changed();
        active = Math.max(0, Math.min(wantedActive, SPACES.size() - 1));
        if (seeded && Files.exists(HistoryFile.plainFile("bookmarks"))) save();
    }

    private static int parseInt(String[] parts, int index, int fallback) {
        if (index >= parts.length) return fallback;
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static synchronized void save() {
        ensureLoaded();
        final List<String> lines = new ArrayList<>();
        lines.add("#active " + active);
        for (BookmarkGrid g : SPACES) {
            lines.add("#space");
            for (var entry : g.groups.entrySet()) {
                if (entry.getKey() == BookmarkGrid.DEFAULT_GROUP_ID) continue;
                lines.add("#grp " + entry.getKey() + (entry.getValue().todo ? " todo" : "")
                        + (entry.getValue().collapsed ? " collapsed" : "")
                        + (entry.getValue().crafting ? " crafting" : ""));
                for (String recipe : entry.getValue().collapsedRecipes) lines.add("#collapse " + entry.getKey() + " " + recipe);
            }
            for (BookmarkGrid.Item e : g.items) {
                lines.add(e.id + " " + e.amount + " " + e.groupId
                        + (e.recipeId != null ? " " + e.type + " " + e.recipeId + " " + e.factor
                                + (e.options != null ? " " + String.join(",", e.options) : "") : ""));
            }
        }
        try {
            Files.write(file(), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            NotEnoughItems.LOGGER.warn("Could not save bookmarks: {}", e.toString());
        }
    }

    // ---- pages (NEI: namespaces) ----

    /** The page shown in the panel. */
    public static synchronized BookmarkGrid grid() {
        ensureLoaded();
        return SPACES.get(active);
    }

    public static synchronized int activeIndex() {
        ensureLoaded();
        return active;
    }

    /**
     * Number of pages. Like NEI, this also adds one empty page after a used last page and removes the empty last page
     * when you are on the one before it.
     */
    public static synchronized int namespaceCount() {
        ensureLoaded();
        if (!SPACES.get(SPACES.size() - 1).isEmpty()) {
            SPACES.add(new BookmarkGrid());
        } else if (active == SPACES.size() - 2 && SPACES.get(active).isEmpty()) {
            SPACES.remove(SPACES.size() - 1);
        }
        return SPACES.size();
    }

    /** True when there is a single page and it is empty (NEI hides the page buttons then). */
    public static synchronized boolean isEmpty() {
        ensureLoaded();
        return SPACES.size() <= 1 && SPACES.get(active).isEmpty();
    }

    private static boolean removeEmptyPages() {
        if (active != SPACES.size() - 1 && SPACES.get(active).isEmpty()) {
            SPACES.remove(active);
            active = Math.min(active, SPACES.size() - 1);
            return true;
        }
        return false;
    }

    public static synchronized void prevNamespace() {
        ensureLoaded();
        namespaceCount();
        removeEmptyPages();
        active = active == 0 ? namespaceCount() - 1 : active - 1;
        save();
    }

    public static synchronized void nextNamespace() {
        ensureLoaded();
        if (removeEmptyPages()) {
            save();
            return;
        }
        active = active == namespaceCount() - 1 ? 0 : active + 1;
        save();
    }

    // ---- items of the active page ----

    public static synchronized boolean contains(String registryId) {
        return grid().contains(registryId);
    }

    /**
     * NEI's plain bookmark key: removes the item if it is bookmarked (for this recipe), otherwise adds it. NEI adds it
     * with amount 0 (no number); Ctrl adds it with amount 1.
     *
     * @param recipeId the recipe under the mouse in the recipe window, or null
     * @return true if it is now bookmarked
     */
    public static synchronized boolean toggle(String registryId, String recipeId, boolean withCount) {
        final BookmarkGrid g = grid();
        final BookmarkGrid.Item existing = g.find(registryId, recipeId);
        final boolean added = existing == null;
        if (added) g.add(registryId, withCount ? 1 : 0, recipeId);
        else g.removeSlot(existing, false);
        save();
        return added;
    }

    public static synchronized boolean toggle(String registryId) {
        return toggle(registryId, null, false);
    }

    public static synchronized boolean existsRecipe(String recipeId) {
        return grid().existsRecipe(recipeId, BookmarkGrid.DEFAULT_GROUP_ID);
    }

    /**
     * NEI's SHIFT + bookmark key over a recipe: removes the recipe if it is bookmarked, otherwise adds it.
     *
     * @param withCount true for CTRL + SHIFT (amounts start at the recipe's counts), else amounts start at 0 (no numbers)
     * @return true if the recipe is now bookmarked
     */
    public static synchronized boolean toggleRecipe(String recipeId, List<BookmarkGrid.RecipeItem> results,
            List<BookmarkGrid.RecipeItem> ingredients, boolean withCount) {
        final BookmarkGrid g = grid();
        final boolean added = !g.removeRecipe(recipeId, BookmarkGrid.DEFAULT_GROUP_ID);
        if (added) g.addRecipe(recipeId, results, ingredients, withCount ? 1 : 0, BookmarkGrid.DEFAULT_GROUP_ID);
        save();
        return added;
    }

    /**
     * NEI's "Save Recipe Tree" (Shift + bookmark key on the favorite button): a new crafting chain group in todo list
     * view with every recipe of the tree at multiplier 1.
     *
     * @return the new group's id, or -1 if there was nothing to save
     */
    public static synchronized int addRecipeTree(List<BookmarkGrid.RecipeSpec> recipes) {
        if (recipes.isEmpty()) return -1;
        final int id = grid().addGroup(recipes, true, true);
        save();
        return id;
    }

    /** NEI's {@code /nei_bookmark}: adds a shared group, recipe or item (see {@link BookmarkPayload}) to the active page. */
    public static synchronized void importPayload(String payload) {
        BookmarkPayload.addTo(grid(), payload);
        save();
    }

    /** Removes one slot of the bookmark panel (a recipe result takes its recipe along). */
    public static synchronized void removeSlot(BookmarkGrid.Item item, boolean wholeRecipe) {
        grid().removeSlot(item, wholeRecipe);
        save();
    }

    /** NEI's {@code bookmark.remove_recipe}: removes a whole group with all its items. */
    public static synchronized void removeGroup(int groupId) {
        final BookmarkGrid g = grid();
        g.items.removeIf(i -> i.groupId == groupId);
        g.groups.remove(groupId);
        g.changed();
        save();
    }

    /** Ctrl + wheel over a bookmark slot: {@code steps} is +1 / -1; Alt makes a step the item's stack size. */
    public static synchronized void shiftItem(BookmarkGrid.Item item, int steps, long stack) {
        grid().shiftItemAmount(item, steps * Math.max(1, stack));
        save();
    }

    /** Ctrl + wheel over a group's strip. */
    public static synchronized void shiftGroup(int groupId, int steps, long stack) {
        grid().shiftGroupAmount(groupId, steps * Math.max(1, stack));
        save();
    }
}
