package codechicken.nei.bookmark;

import java.util.ArrayList;
import java.util.List;

/**
 * What the chat link buttons send: a bookmark group, a recipe or an item as text, and how a receiver adds it to their own
 * bookmarks (NEI's {@code BookmarkPayload}, which uses NBT; this is lines of text). Plain Java.
 *
 * <pre>
 * #label Bookmark Group             shown in the chat message
 * #grp todo crafting collapsed      only for a group: its settings
 * #collapse &lt;recipe key&gt;           only for a group: a folded recipe
 * minecraft:stick 8 1 r1 4 -        id, amount, type, recipe key, factor, options (- = none)
 * </pre>
 */
public final class BookmarkPayload {

    /** Most items a payload may hold. */
    public static final int MAX_ITEMS = 500;
    private static final String NONE = "-";

    private BookmarkPayload() {}

    public static String ofGroup(BookmarkGrid grid, int groupId, String label) {
        final BookmarkGrid.Group group = grid.group(groupId);
        final StringBuilder out = new StringBuilder("#label ").append(label).append('\n');
        out.append("#grp").append(group.todo ? " todo" : "").append(group.crafting ? " crafting" : "")
                .append(group.collapsed ? " collapsed" : "").append('\n');
        for (String r : group.collapsedRecipes) out.append("#collapse ").append(r).append('\n');
        items(out, grid.groupItems(groupId));
        return out.toString();
    }

    /** A recipe's slots, or one plain item: they are added to the receiver's default group. */
    public static String ofItems(List<BookmarkGrid.Item> items, String label) {
        final StringBuilder out = new StringBuilder("#label ").append(label).append('\n');
        items(out, items);
        return out.toString();
    }

    private static void items(StringBuilder out, List<BookmarkGrid.Item> items) {
        for (BookmarkGrid.Item i : items.subList(0, Math.min(items.size(), MAX_ITEMS))) {
            out.append(i.id).append(' ').append(i.amount).append(' ').append(i.type).append(' ')
                    .append(i.recipeId == null ? NONE : i.recipeId).append(' ').append(i.factor).append(' ')
                    .append(i.options == null ? NONE : String.join(",", i.options)).append('\n');
        }
    }

    public static String label(String payload) {
        for (String line : payload.split("\n")) {
            if (line.startsWith("#label ")) return line.substring(7).trim();
        }
        return "Bookmarks";
    }

    /** @return the group the items went into (the default group for a recipe or an item) */
    public static int addTo(BookmarkGrid grid, String payload) {
        BookmarkGrid.Group settings = null;
        final List<String> collapsed = new ArrayList<>();
        final List<BookmarkGrid.Item> items = new ArrayList<>();

        for (String raw : payload.split("\n")) {
            final String[] p = raw.trim().split("\\s+");
            if (p[0].equals("#grp")) {
                settings = new BookmarkGrid.Group(java.util.Arrays.asList(p).contains("todo"));
                settings.crafting = java.util.Arrays.asList(p).contains("crafting");
                settings.collapsed = java.util.Arrays.asList(p).contains("collapsed");
            } else if (p[0].equals("#collapse") && p.length > 1) {
                collapsed.add(p[1]);
            } else if (!p[0].startsWith("#") && p.length >= 6 && items.size() < MAX_ITEMS) {
                final BookmarkGrid.Item item = new BookmarkGrid.Item(p[0], clamp(p[1]), 0);
                item.type = Math.max(0, Math.min(2, (int) parse(p[2])));
                item.recipeId = p[3].equals(NONE) ? null : p[3];
                item.factor = (int) Math.max(1, Math.min(BookmarkGrid.MAX_AMOUNT, parse(p[4])));
                item.options = p[5].equals(NONE) ? null : List.of(p[5].split(","));
                if (item.recipeId == null) item.type = 0;
                items.add(item);
            }
        }

        int groupId = BookmarkGrid.DEFAULT_GROUP_ID;
        if (settings != null) {
            groupId = grid.addGroup(settings);
            settings.collapsedRecipes.addAll(collapsed);
        }
        // which recipes the grid had before: asked up front, or the recipe's result would hide its own ingredients
        final java.util.Set<String> present = new java.util.HashSet<>();
        for (BookmarkGrid.Item item : items) if (item.type != 0 && grid.existsRecipe(item.recipeId, groupId)) present.add(item.recipeId);
        for (BookmarkGrid.Item item : items) {
            item.groupId = groupId;
            if (settings == null && (item.type == 0 ? grid.find(item.id, item.recipeId) != null
                    : present.contains(item.recipeId))) continue; // already there
            grid.items.add(item);
        }
        grid.changed();
        return groupId;
    }

    private static long parse(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int clamp(String s) {
        return (int) Math.max(0, Math.min(BookmarkGrid.MAX_AMOUNT, parse(s)));
    }
}
