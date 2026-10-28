package codechicken.nei.bookmark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * One page ("namespace") of bookmarks: items with an amount and a group, plus the groups' settings. Plain Java; the
 * item list is only touched through the {@code visible} predicate so this class can be tested without the game.
 *
 * Ported from GTNH NEI's {@code BookmarkGrid}: new items go to the end in the default group (id 0); a group's items stay
 * next to each other; {@link #createGroup} is NEI's drag-to-group; groups that lose all their items disappear.
 * Not ported yet: recipe bookmarks and crafting-chain groups.
 */
public final class BookmarkGrid {

    public static final int MAX_AMOUNT = 99999;

    public static final int DEFAULT_GROUP_ID = BookmarkLayout.DEFAULT_GROUP;

    /** One bookmarked item. */
    public static final class Item {

        public final String id;
        public int amount;
        public int groupId;
        /** 0 = plain item, 1 = result of a bookmarked recipe, 2 = ingredient of one. */
        public int type;
        /** Which recipe this belongs to, or null for plain items. */
        public String recipeId;
        /** How many of the item one run of the recipe makes / uses; amount = factor * multiplier. */
        public int factor = 1;
        /** Every item this slot of the recipe accepts (an ingredient that is a tag), or null for just {@link #id}. */
        public List<String> options;

        public java.util.Set<String> optionSet() {
            final java.util.Set<String> set = new HashSet<>();
            set.add(id);
            if (options != null) set.addAll(options);
            return set;
        }

        public Item(String id, int amount, int groupId) {
            this.id = id;
            this.amount = amount;
            this.groupId = groupId;
        }

        public boolean inRecipe(String recipe, int group) {
            return type != 0 && recipeId != null && recipeId.equals(recipe) && groupId == group;
        }
    }

    /** One item of a recipe handed to {@link #addRecipe}. */
    public record RecipeItem(String id, int count, List<String> options) {

        public RecipeItem(String id, int count) {
            this(id, count, null);
        }
    }

    /** A group's settings. */
    public static final class Group {

        /** Todo list view: every item of the group on its own row. */
        public boolean todo;
        public boolean collapsed;
        /** Crafting chain: the recipes of the group are worked out together (NEI's {@code RecipeChainDetails}). */
        public boolean crafting;
        /** Recipes folded into one slot row (NEI's {@code collapsedRecipes}); only used while {@link #crafting}. */
        public final Set<String> collapsedRecipes = new java.util.LinkedHashSet<>();

        public Group(boolean todo) {
            this.todo = todo;
        }
    }

    public final List<Item> items = new ArrayList<>();
    public final Map<Integer, Group> groups = new HashMap<>();
    /** Page shown the last time, only used by the UI. */
    public int page;

    private BookmarkLayout layout;
    private int layoutColumns = -1;
    private List<Item> layoutItems = new ArrayList<>();

    public BookmarkGrid() {
        groups.put(DEFAULT_GROUP_ID, new Group(false));
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public Group group(int id) {
        return groups.computeIfAbsent(id, k -> new Group(false));
    }

    /** The items of one group, in list order. */
    public List<Item> groupItems(int groupId) {
        final List<Item> list = new ArrayList<>();
        for (Item i : items) if (i.groupId == groupId) list.add(i);
        return list;
    }

    /** NEI's {@code BookmarkGrid#indexOf(group, stack, recipeId, false)}: any non-ingredient item of the recipe. */
    public Item find(String id, String recipeId) {
        for (Item i : items) {
            if (i.groupId == DEFAULT_GROUP_ID && i.type != 2 && java.util.Objects.equals(recipeId, i.recipeId)
                    && i.id.equals(id)) {
                return i;
            }
        }
        return null;
    }

    public boolean contains(String id) {
        for (Item i : items) if (i.type != 2 && i.id.equals(id)) return true;
        return false;
    }

    public void add(String id, int amount) {
        add(id, amount, null);
    }

    /** A plain item, optionally remembering the recipe it was bookmarked from (NEI's {@code addItem}). */
    public void add(String id, int amount, String recipeId) {
        final Item item = new Item(id, amount, DEFAULT_GROUP_ID);
        item.recipeId = recipeId;
        items.add(item);
        changed();
    }

    // ---- quantity (NEI shiftItemAmount / shiftRecipeAmount / shiftGroupAmount) ----

    private static long shiftMultiplier(long multiplier, long shift, int min) {
        final long current = ((multiplier + shift) / shift) * shift;
        return Math.min(Integer.MAX_VALUE, current <= 0 && multiplier > 1 ? 1 : Math.max(min, current));
    }

    private static long multiplierOf(Item i) {
        return i.factor <= 0 ? 0 : (i.amount + i.factor - 1) / i.factor;
    }

    private static int clampAmount(long v) {
        return (int) Math.max(0, Math.min(MAX_AMOUNT, v));
    }

    /** Ctrl + wheel over a slot. {@code shift} is +-1 times the step (the item's stack size with Alt). */
    public void shiftItemAmount(Item target, long shift) {
        if (group(target.groupId).collapsed) {
            shiftGroupAmount(target.groupId, shift);
        } else if (target.recipeId != null && target.type != 0) {
            shiftRecipeAmount(target.groupId, target.recipeId, shift);
        } else {
            target.amount = clampAmount(shiftMultiplier(target.amount, shift, 0));
        }
        changed();
    }

    public void shiftRecipeAmount(int groupId, String recipeId, long shift) {
        final int min = isMiddleRecipe(groupId, recipeId) ? 1 : 0;
        long multiplier = Integer.MAX_VALUE;
        for (Item i : items) {
            if (i.inRecipe(recipeId, groupId) && i.factor > 0) {
                multiplier = Math.min(shiftMultiplier(Math.max(min, multiplierOf(i)), shift, min), multiplier);
            }
        }
        for (Item i : items) {
            if (i.inRecipe(recipeId, groupId) && i.factor > 0) i.amount = clampAmount(i.factor * multiplier);
        }
        changed();
    }

    /** Ctrl + wheel over a group's strip, for groups without a crafting chain. */
    public void shiftGroupAmount(int groupId, long shift) {
        final List<Item> list = new ArrayList<>();
        final Set<Long> sizes = new HashSet<>();
        for (Item i : items) {
            if (i.groupId == groupId && i.amount > 0) {
                sizes.add((long) Math.max(i.amount, i.factor));
                list.add(i);
            }
        }
        long gcd = 0;
        for (long v : sizes) gcd = gcd(gcd, v);
        if (gcd <= 0) return;

        long multiplier = Integer.MAX_VALUE;
        for (Item i : list) {
            final long customFactor = (i.amount + gcd - 1) / gcd;
            final long customMultiplier = customFactor > 0 ? (i.amount + customFactor - 1) / customFactor : 0;
            multiplier = Math.min(shiftMultiplier(customMultiplier, shift, 1), multiplier);
        }
        for (Item i : list) {
            final long customFactor = (i.amount + gcd - 1) / gcd;
            final long target = customFactor * multiplier;
            i.amount = clampAmount(i.factor <= 0 ? 0 : ((target + i.factor - 1) / i.factor) * i.factor);
        }
        changed();
    }

    private static long gcd(long a, long b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    public boolean existsRecipe(String recipeId, int groupId) {
        for (Item i : items) if (i.inRecipe(recipeId, groupId)) return true;
        return false;
    }

    /** NEI's {@code BookmarkGrid#addRecipe}: results first, then ingredients; amount = count * multiplier. */
    public void addRecipe(String recipeId, List<RecipeItem> results, List<RecipeItem> ingredients, int multiplier,
            int groupId) {
        for (int pass = 0; pass < 2; pass++) {
            final List<RecipeItem> list = pass == 0 ? results : ingredients;
            final Set<String> seen = new HashSet<>();
            for (RecipeItem r : list) {
                if (!seen.add(r.id())) continue;
                final Item item = new Item(r.id(), r.count() * multiplier, groupId);
                item.type = pass == 0 ? 1 : 2;
                item.recipeId = recipeId;
                item.factor = r.count();
                if (pass == 1 && r.options() != null && r.options().size() > 1) item.options = r.options();
                items.add(item);
            }
        }
        changed();
    }

    /** One recipe handed to {@link #addGroup}. */
    public record RecipeSpec(String recipeId, List<RecipeItem> results, List<RecipeItem> ingredients) {}

    /**
     * NEI's {@code BookmarkPanel#addGroup(recipes, TODO_LIST, crafting)}: a new group with every recipe at multiplier 1.
     *
     * @return the new group's id
     */
    public int addGroup(List<RecipeSpec> recipes, boolean todo, boolean crafting) {
        final Group group = new Group(todo);
        group.crafting = crafting;
        final int id = addGroup(group);
        final Set<String> unique = new HashSet<>();
        for (RecipeSpec r : recipes) {
            if (unique.add(r.recipeId())) addRecipe(r.recipeId(), r.results(), r.ingredients(), 1, id);
        }
        changed();
        return id;
    }

    /** @return true if the recipe was bookmarked (and is gone now) */
    public boolean removeRecipe(String recipeId, int groupId) {
        final boolean removed = items.removeIf(i -> i.inRecipe(recipeId, groupId));
        if (removed) changed();
        return removed;
    }

    /** Removing one slot: a recipe result takes its whole recipe along (NEI removeRecipe(index, false)). */
    public void removeSlot(Item item, boolean wholeRecipe) {
        // NEI: a recipe folded into a collapsed one only goes together with all the recipes folded with it
        final Set<String> folded = item.type != 0 && item.recipeId != null && group(item.groupId).crafting
                ? relations.getOrDefault(item.groupId, Map.of()).get(collapseOwner(item.groupId, item.recipeId))
                : null;
        if (folded != null) {
            if (wholeRecipe) for (String r : new ArrayList<>(folded)) removeRecipe(r, item.groupId);
            return;
        }

        if (item.recipeId != null && item.type != 0) {
            boolean full = wholeRecipe;
            if (!full && item.type == 1) {
                full = true;
                for (Item o : items) if (o != item && o.type == 1 && o.inRecipe(item.recipeId, item.groupId)) full = false;
            }
            if (full) {
                removeRecipe(item.recipeId, item.groupId);
                return;
            }
        }
        remove(item);
    }

    public void remove(Item item) {
        items.remove(item);
        changed();
    }

    public void toggleTodo(int groupId) {
        final Group g = group(groupId);
        g.todo = !g.todo;
        changed();
    }

    public void toggleCrafting(int groupId) {
        final Group g = group(groupId);
        g.crafting = !g.crafting;
        changed();
    }

    public void toggleCollapsed(int groupId) {
        final Group g = group(groupId);
        g.collapsed = !g.collapsed;
        changed();
    }

    /** Call after anything that changes what the layout looks like. */
    public void changed() {
        normalize();
        layout = null;
    }

    /** What the chain math says about each item of a crafting group (identity keys). */
    public final Map<Item, ChainMath.Calc> calc = new java.util.IdentityHashMap<>();
    private final Map<Integer, Set<String>> middleRecipes = new HashMap<>();
    private final Map<Integer, Set<String>> outputRecipes = new HashMap<>();
    // collapsed recipes, per crafting group: what they fold, what is hidden, what a todo list shows instead
    private final Map<Integer, Map<String, Set<String>>> relations = new HashMap<>();
    private final Set<Item> hidden = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private final Map<Integer, Map<String, List<Item>>> shadowRows = new HashMap<>();
    /** The first slot of a collapsed recipe's todo row stands for every item folded into it (identity keys). */
    private final Map<Item, List<Item>> foldedInto = new java.util.IdentityHashMap<>();

    public boolean isOutputRecipe(int groupId, String recipeId) {
        return recipeId != null && outputRecipes.getOrDefault(groupId, Set.of()).contains(recipeId);
    }

    public boolean isMiddleRecipe(int groupId, String recipeId) {
        return recipeId != null && middleRecipes.getOrDefault(groupId, Set.of()).contains(recipeId);
    }

    /**
     * Keeps each group's items together (at the place of the group's first item), puts a recipe's results before its
     * ingredients, turns half recipes back into plain items, drops groups with no items and recalculates the crafting
     * chains. This is NEI's {@code BookmarkGrid#sortIngredients}.
     */
    private void normalize() {
        for (int i = 0; i < items.size(); i++) {
            final Item item = items.get(i);
            if (item.groupId == DEFAULT_GROUP_ID) continue;

            final List<Item> same = new ArrayList<>();
            for (Item other : items) if (other.groupId == item.groupId) same.add(other);
            items.removeAll(same);
            items.addAll(i, same);
            i += same.size() - 1;
        }

        for (Item item : items) group(item.groupId);

        for (Map.Entry<Integer, Group> entry : new ArrayList<>(groups.entrySet())) {
            final int groupId = entry.getKey();
            final Group group = entry.getValue();
            final Map<String, Integer> recipeState = new HashMap<>();

            if (group.todo || group.crafting) sortInsideGroup(groupId);

            for (Item item : items) {
                if (item.recipeId == null && item.type != 0) {
                    item.type = 0;
                } else if (item.groupId == groupId && item.recipeId != null && item.type != 0) {
                    recipeState.merge(item.recipeId, item.type == 2 ? 1 : 2, (x, y) -> x | y);
                }
            }

            for (Item item : items) {
                if (item.groupId == groupId && item.type != 0 && recipeState.getOrDefault(item.recipeId, 0) != 3) {
                    if (item.type == 2) item.recipeId = null;
                    recipeState.remove(item.recipeId);
                    item.type = 0;
                }
            }
        }

        final Set<Integer> used = new HashSet<>();
        for (Item item : items) used.add(item.groupId);
        groups.keySet().removeIf(k -> k != DEFAULT_GROUP_ID && !used.contains(k));

        calc.clear();
        middleRecipes.clear();
        outputRecipes.clear();
        relations.clear();
        hidden.clear();
        shadowRows.clear();
        foldedInto.clear();
        for (Map.Entry<Integer, Group> entry : groups.entrySet()) {
            final Group group = entry.getValue();
            if (!group.crafting) continue;
            final List<Item> inGroup = new ArrayList<>();
            final Set<String> recipes = new HashSet<>();
            for (Item item : items) {
                if (item.groupId != entry.getKey()) continue;
                inGroup.add(item);
                if (item.type != 0) recipes.add(item.recipeId);
            }
            group.collapsedRecipes.retainAll(recipes); // a recipe that is gone cannot stay collapsed

            final ChainMath math = ChainMath.of(inGroup, group.collapsedRecipes);
            calc.putAll(math.calc);
            middleRecipes.put(entry.getKey(), math.recipeInMiddle);
            outputRecipes.put(entry.getKey(), math.outputRecipes);
            relations.put(entry.getKey(), math.relations);
            hidden.addAll(math.hidden);

            final Map<String, List<Item>> rows = new HashMap<>();
            for (Map.Entry<String, List<ChainMath.Calc>> shadow : math.shadows.entrySet()) {
                final List<Item> row = new ArrayList<>();
                for (ChainMath.Calc c : shadow.getValue()) row.add(c.item);
                if (row.isEmpty()) continue;
                rows.put(shadow.getKey(), row);

                final List<Item> folded = new ArrayList<>();
                for (Item i : inGroup) if (i.type != 0 && math.relations.get(shadow.getKey()).contains(i.recipeId)) folded.add(i);
                foldedInto.put(row.get(0), folded);
            }
            shadowRows.put(entry.getKey(), rows);
        }
    }

    /**
     * NEI's {@code borderGrid}: slot to the id of the border drawn around it. Neighbouring slots with the same id are
     * joined. A collapsed group is one border, a collapsed recipe of a crafting group another. Call after
     * {@link #layout}.
     */
    public Map<Integer, String> borderKeys(BookmarkLayout lay) {
        final Map<Integer, String> keys = new HashMap<>();
        for (Map.Entry<Integer, Integer> slot : lay.slotToItem.entrySet()) {
            final Item i = layoutItems.get(slot.getValue());
            final Group g = group(i.groupId);
            if (g.collapsed) {
                keys.put(slot.getKey(), "group:" + i.groupId);
            } else if (g.crafting && i.recipeId != null && relations.getOrDefault(i.groupId, Map.of()).containsKey(i.recipeId)) {
                keys.put(slot.getKey(), "recipe:" + i.groupId + ":" + i.recipeId);
            }
        }
        return keys;
    }

    // ---- collapsed recipes (NEI's toggle_collapse on a recipe) ----

    /** The collapsed recipe that has {@code recipeId} folded into it (the recipe itself when it is the collapsed one). */
    private String collapseOwner(int groupId, String recipeId) {
        for (Map.Entry<String, Set<String>> e : relations.getOrDefault(groupId, Map.of()).entrySet()) {
            if (e.getValue().contains(recipeId)) return e.getKey();
        }
        return recipeId;
    }

    /**
     * Alt + click on a slot of a crafting chain group: folds the recipe (and what is made only for it) into one row, or
     * unfolds it. A recipe in the middle of the chain has its stored multiplier moved between 1 and 0, as NEI does.
     */
    public void toggleCollapsedRecipe(int groupId, String recipeId) {
        final Group g = group(groupId);
        final String owner = collapseOwner(groupId, recipeId);
        final boolean nowCollapsed = g.collapsedRecipes.add(owner);
        if (!nowCollapsed) g.collapsedRecipes.remove(owner);

        if (isMiddleRecipe(groupId, owner)) {
            for (Item i : items) {
                if (i.groupId != groupId || !owner.equals(i.recipeId) || i.factor <= 0) continue;
                final long multiplier = i.amount / i.factor;
                if (nowCollapsed && multiplier == 1) i.amount = 0;
                else if (!nowCollapsed && multiplier == 0) i.amount = i.factor;
            }
        }
        changed();
    }

    /** NEI's {@code sortItemsInsideGroup}: the items of one recipe sit together, results first. */
    private void sortInsideGroup(int groupId) {
        final int size = items.size();
        int index = 0;

        while (index < size) {
            final Item item = items.get(index);
            if (item.groupId == groupId && item.recipeId != null) {
                final List<Item> sorted = new ArrayList<>();
                final List<Item> same = new ArrayList<>();
                for (int k = index; k < size; k++) {
                    final Item o = items.get(k);
                    if (o.groupId == groupId && item.recipeId.equals(o.recipeId)) same.add(o);
                }
                same.sort((x, y) -> (x.type == 2 ? 1 : -1) - (y.type == 2 ? 1 : -1));
                sorted.addAll(same);
                items.removeAll(sorted);
                items.addAll(index, sorted);
                index += sorted.size();
            } else {
                index++;
            }
        }
    }

    /**
     * The layout for this many columns. {@code visible} says which items exist in the item list; items for mods that
     * are not installed keep their place in the file but take no slot.
     */
    public BookmarkLayout layout(int columns, Predicate<Item> visible) {
        if (layout == null || layoutColumns != columns) {
            layoutItems = new ArrayList<>();
            // in a todo list a collapsed recipe is one row of slots; the first of them is a result, the rest count as
            // ingredients (NEI's generateCollapsedRecipe)
            final Map<Item, Integer> rowType = new java.util.IdentityHashMap<>();
            final Set<String> expanded = new HashSet<>();
            for (Item i : items) {
                final Group g = group(i.groupId);
                final List<Item> row = g.todo && !g.collapsed && i.type != 0
                        ? shadowRows.getOrDefault(i.groupId, Map.of()).get(i.recipeId)
                        : null;
                if (row != null) {
                    if (!expanded.add(i.groupId + " " + i.recipeId)) continue;
                    for (Item shadow : row) {
                        if (!visible.test(shadow)) continue;
                        rowType.put(shadow, shadow == row.get(0) ? 1 : 2);
                        layoutItems.add(shadow);
                    }
                } else if (!hidden.contains(i) && visible.test(i)) {
                    layoutItems.add(i);
                }
            }

            final int[] groupIds = new int[layoutItems.size()];
            for (int i = 0; i < groupIds.length; i++) groupIds[i] = layoutItems.get(i).groupId;

            final int[] types = new int[groupIds.length];
            final String[] recipes = new String[groupIds.length];
            for (int i = 0; i < groupIds.length; i++) {
                types[i] = rowType.getOrDefault(layoutItems.get(i), layoutItems.get(i).type);
                recipes[i] = types[i] == 0 ? null : layoutItems.get(i).recipeId;
            }

            layout = BookmarkLayout.generate(columns, groupIds, types, recipes, id -> group(id).todo,
                    id -> group(id).collapsed, id -> group(id).crafting);
            layoutColumns = columns;
        }
        return layout;
    }

    /** Items that were laid out (visible ones), indexed like {@link BookmarkLayout#slotToItem}. */
    public List<Item> layoutItems() {
        return layoutItems;
    }

    public void invalidateLayout() {
        layout = null;
    }

    /**
     * Applies a finished drag on the group strip (NEI's {@code BookmarkGrid#createGroup}).
     *
     * @return true if anything changed
     */
    public boolean createGroup(int columns, Predicate<Item> visible, int groupId, int startRow, int endRow) {
        final BookmarkLayout lay = layout(columns, visible);
        final List<Item> laid = layoutItems;
        final Map<Integer, List<Item>> rowItems = collectItemsToRows(lay, laid);
        final Map<Integer, Integer> newGroups = new HashMap<>();
        boolean change = false;

        for (Map.Entry<Integer, Integer> entry : BookmarkLayout.prepareGroups(lay.rowToGroup, groupId, startRow, endRow)
                .entrySet()) {
            final Integer before = lay.rowToGroup.get(entry.getKey());
            if (before != null && before.equals(entry.getValue())) continue;

            int target = entry.getValue();
            if (target == Integer.MIN_VALUE) {
                target = newGroups.computeIfAbsent(target, k -> addGroup(new Group(group(DEFAULT_GROUP_ID).todo)));
            } else if (target < 0) {
                final int source = Math.abs(target);
                target = newGroups.computeIfAbsent(target, k -> addGroup(new Group(group(source).todo)));
            }

            for (Item item : rowItems.getOrDefault(entry.getKey(), new ArrayList<>())) item.groupId = target;
            change = true;
        }

        if (change) {
            final List<Item> reordered = new ArrayList<>();
            for (List<Item> row : rowItems.values()) reordered.addAll(row);
            items.clear();
            items.addAll(reordered);
            changed();
        }
        return change;
    }

    // ---- sorting by Shift + drag (NEI's SortableGroup / SortableItem) ----

    /**
     * NEI's {@code moveGroup}: the dragged group takes the place of the row under the mouse. Dragging up puts it before
     * that row, dragging down after it; a row in the middle of another group does nothing until the mouse reaches the
     * group's edge.
     *
     * @return true if the order changed
     */
    public boolean moveGroup(int columns, Predicate<Item> visible, int groupId, int overRow) {
        if (groupId == DEFAULT_GROUP_ID) return false;
        final BookmarkLayout lay = layout(columns, visible);
        final int rows = lay.rowCount();
        if (overRow < 0 || overRow >= rows || lay.groupOfRow(overRow) == groupId) return false;

        boolean dragDown = false; // the group has a row above the hovered one
        for (int r = 0; r < overRow && !dragDown; r++) dragDown = lay.groupOfRow(r) == groupId;

        final int target = overRow + (dragDown ? 1 : 0);
        final List<Item> moving = groupItems(groupId);
        final List<Item> rest = new ArrayList<>(items);
        rest.removeAll(moving);

        final int at;
        if (target >= rows) { // after the last row
            if (lay.groupOfRow(rows - 1) == groupId) return false;
            at = rest.size();
        } else {
            final int group = lay.groupOfRow(target);
            if (group != DEFAULT_GROUP_ID && group == lay.groupOfRow(target - 1)) return false; // inside another group
            final Item anchor = collectItemsToRows(lay, layoutItems).getOrDefault(target, List.of()).stream()
                    .filter(i -> !moving.contains(i)).findFirst().orElse(null);
            if (anchor == null) return false;
            at = rest.indexOf(anchor);
        }

        rest.addAll(at, moving);
        if (rest.equals(items)) return false;
        items.clear();
        items.addAll(rest);
        changed();
        return true;
    }

    /** The item of the list a slot stands for: a collapsed recipe's stand-in slot stands for that recipe's first item. */
    public Item real(Item slot) {
        if (slot == null || items.contains(slot)) return slot;
        for (Item o : items) if (o.inRecipe(slot.recipeId, slot.groupId)) return o;
        return null;
    }

    /** True if {@code slot} is, or stands for, one of {@code unit}. */
    public boolean inUnit(List<Item> unit, Item slot) {
        return unit.contains(slot) || unit.contains(real(slot));
    }

    private boolean isCollapsedRecipe(Item i) {
        return i.recipeId != null && relations.getOrDefault(i.groupId, Map.of()).containsKey(i.recipeId);
    }

    /** What moves with a dragged item: a recipe's result takes its whole recipe along in a todo or crafting group. */
    public List<Item> sortUnit(Item dragged) {
        final Group g = group(dragged.groupId);
        if (g.crafting && isCollapsedRecipe(dragged)) {
            // a collapsed recipe moves together with everything folded into it
            final Set<String> folded = relations.get(dragged.groupId).get(dragged.recipeId);
            final List<Item> unit = new ArrayList<>();
            for (Item i : items) if (i.groupId == dragged.groupId && i.type != 0 && folded.contains(i.recipeId)) unit.add(i);
            return unit;
        }
        if (dragged.type == 1 && dragged.recipeId != null && (g.todo || g.crafting)) {
            final List<Item> unit = new ArrayList<>();
            for (Item i : items) if (i.inRecipe(dragged.recipeId, dragged.groupId)) unit.add(i);
            return unit;
        }
        return List.of(dragged);
    }

    /**
     * NEI's {@code moveItem(sortableItem, itemIndex)}: the dragged item (or recipe) goes next to the item under the
     * mouse, before it when dragging up and after it when dragging down. Only inside one group: moving between groups
     * is the strip's job, and NEI's todo list "insert between groups" drop is not ported.
     *
     * @return true if the order changed
     */
    public boolean moveItem(Item dragged, Item over) {
        final Group g = group(dragged.groupId);
        if (g.collapsed || over.groupId != dragged.groupId) return false;
        dragged = real(dragged);
        over = real(over);
        if (dragged == null || over == null) return false;

        final List<Item> unit = sortUnit(dragged);
        if (unit.contains(over)) return false;
        // an ingredient of a todo list only moves among the items of its own recipe
        if (g.todo && dragged.type == 2 && !(over.type != 0 && Objects.equals(over.recipeId, dragged.recipeId))) {
            return false;
        }

        // the item under the mouse stands for its whole recipe when the dragged one is moving recipes
        List<Item> block = List.of(over);
        if (((g.crafting && !g.todo) || (g.todo && dragged.type == 1)) && over.type != 0 && over.recipeId != null) {
            block = new ArrayList<>();
            for (Item i : items) if (i.inRecipe(over.recipeId, over.groupId)) block.add(i);
        }

        int firstUnit = Integer.MAX_VALUE;
        for (Item i : unit) firstUnit = Math.min(firstUnit, items.indexOf(i));
        final boolean after = items.indexOf(over) > firstUnit;

        final List<Item> ordered = new ArrayList<>();
        for (Item i : items) if (unit.contains(i)) ordered.add(i);

        final List<Item> rest = new ArrayList<>(items);
        rest.removeAll(unit);
        int at = Integer.MAX_VALUE;
        int last = -1;
        for (Item i : block) {
            at = Math.min(at, rest.indexOf(i));
            last = Math.max(last, rest.indexOf(i));
        }
        rest.addAll(after ? last + 1 : at, ordered);

        if (rest.equals(items)) return false;
        items.clear();
        items.addAll(rest);
        changed();
        return true;
    }

    // ---- todo lists: dragging an item between groups (NEI's SortableItem#mouseDragged, crossGroup) ----

    /** Results and plain items of a todo list may be dropped into other todo lists. */
    public boolean crossGroup(Item dragged) {
        final Group g = group(dragged.groupId);
        return !g.collapsed && g.todo && dragged.type != 2;
    }

    private boolean canInsert(int groupId) {
        return group(groupId).todo && !group(groupId).collapsed;
    }

    private boolean recipeElsewhere(String recipeId, int groupId, List<Item> unit) {
        if (recipeId == null) return false;
        for (Item i : items) if (!unit.contains(i) && recipeId.equals(i.recipeId) && i.groupId == groupId) return true;
        return false;
    }

    /**
     * NEI's drag of a result / plain item over a todo list: the row under the mouse decides. In the upper quarter of a
     * row the item goes to the end of the group above, in the lower quarter to the start of the one below, in between
     * it joins the neighbouring group only at the edge between two groups. Over a row of its own group it just moves.
     *
     * @param row  absolute row under the mouse
     * @param frac how far down that row the mouse is, 0..1
     * @return true if anything changed
     */
    public boolean dragAcrossGroups(int columns, Predicate<Item> visible, Item dragged, int row, double frac) {
        final BookmarkLayout lay = layout(columns, visible);
        final Integer first = row < 0 ? null : lay.slotToItem.get(row * columns);
        final List<Item> unit = sortUnit(dragged);

        if (first == null) {
            // below the last row: only the default group (no group) follows the mouse there
            if (row < 0 || lay.groupOfRow(row) != dragged.groupId) return false;
            int draggedSlot = -1;
            for (int k = 0; k < layoutItems.size() && draggedSlot < 0; k++) {
                if (unit.contains(layoutItems.get(k))) draggedSlot = lay.itemToSlot.getOrDefault(k, -1);
            }
            return moveToRow(columns, visible, dragged, row, dragged.groupId, row * columns < draggedSlot);
        }

        final Item overItem = layoutItems.get(first);
        final int overGroup = overItem.groupId;
        int before = lay.groupOfRow(row - 1);
        int after = lay.groupOfRow(row + 1);

        if (overGroup == dragged.groupId && !inUnit(unit, overItem)) return moveItem(dragged, overItem);

        if (frac <= 0.25) {
            if (before != overGroup && canInsert(before) && !recipeElsewhere(dragged.recipeId, before, unit)) {
                return moveToRow(columns, visible, dragged, row, before, false);
            }
        } else if (frac <= 0.5) {
            if (before == overGroup) return false;
            if (overGroup != DEFAULT_GROUP_ID && before != DEFAULT_GROUP_ID) before = DEFAULT_GROUP_ID;
            if (overGroup == DEFAULT_GROUP_ID && after != DEFAULT_GROUP_ID) return false;
            if (canInsert(before) && !recipeElsewhere(dragged.recipeId, before, unit)) {
                return moveToRow(columns, visible, dragged, row, before, false);
            }
        } else if (frac < 0.75) {
            if (after == overGroup) return false;
            if (overGroup != DEFAULT_GROUP_ID && after != DEFAULT_GROUP_ID) after = DEFAULT_GROUP_ID;
            if (overGroup == DEFAULT_GROUP_ID && before != DEFAULT_GROUP_ID) return false;
            if (canInsert(after) && !recipeElsewhere(dragged.recipeId, after, unit)) {
                return moveToRow(columns, visible, dragged, row, after, true);
            }
        } else if (after != overGroup && canInsert(after) && !recipeElsewhere(dragged.recipeId, after, unit)) {
            return moveToRow(columns, visible, dragged, row, after, true);
        }
        return false;
    }

    /** NEI's {@code moveItem(sortableItem, overRowIndex, overGroupId, moveAfter)}. */
    private boolean moveToRow(int columns, Predicate<Item> visible, Item dragged, int row, int overGroupId, boolean after) {
        if (isCollapsedRecipe(dragged) && dragged.groupId != overGroupId || !canInsert(overGroupId)) return false;

        final List<Item> unit = sortUnit(dragged);
        boolean moved = false;
        if (dragged.groupId != overGroupId) {
            for (Item i : unit) i.groupId = overGroupId;
            dragged.groupId = overGroupId;
            moved = true;
            changed();
        }

        final BookmarkLayout lay = layout(columns, visible);
        final int slot = (row + (after ? 1 : 0)) * columns;

        if (after && slot >= lay.maxSlot) {
            final Integer last = lay.slotToItem.get(lay.maxSlot - 1);
            if (last == null || inUnit(unit, layoutItems.get(last))) return moved;
            moveItems(null, unit);
            return true;
        }

        final Integer index = lay.slotToItem.get(slot);
        if (index == null) return moved;

        final List<Integer> bySlot = new ArrayList<>(new java.util.TreeMap<>(lay.slotToItem).values());
        final int sortIndex = bySlot.indexOf(index);
        Item target = layoutItems.get(index);
        final Item prev = sortIndex > 0 ? layoutItems.get(bySlot.get(sortIndex - 1)) : null;

        if (sortIndex == -1 || inUnit(unit, target) || prev != null && inUnit(unit, prev)) return moved;

        if (group(target.groupId).crafting && prev != null && target.recipeId != null) {
            // the slots of a collapsed recipe are one block
            if (isCollapsedRecipe(prev) && target.recipeId.equals(prev.recipeId)) return moved;
            for (Item i : items) {
                if (i.groupId == target.groupId && target.recipeId.equals(i.recipeId)) {
                    target = i;
                    break;
                }
            }
        }

        moveItems(real(target), unit);
        return true;
    }

    private void moveItems(Item target, List<Item> unit) {
        items.removeAll(unit);
        final int at = target == null ? -1 : items.indexOf(target);
        items.addAll(at < 0 ? items.size() : at, unit);
        changed();
    }

    public int addGroup(Group group) {
        int max = DEFAULT_GROUP_ID;
        for (Item i : items) max = Math.max(max, i.groupId);
        for (int k : groups.keySet()) max = Math.max(max, k);
        final int id = max + 1;
        groups.put(id, group);
        return id;
    }

    /**
     * Items by row, in row order. A collapsed group's hidden items travel with its visible row. Items that were not
     * laid out (uninstalled mods) go last, so nothing is lost.
     */
    private Map<Integer, List<Item>> collectItemsToRows(BookmarkLayout lay, List<Item> laid) {
        final Map<Integer, List<Item>> results = new TreeMap<>();
        final List<Item> all = new ArrayList<>(items);
        final int rows = lay.rowCount();

        for (int row = 0; row < rows; row++) {
            final int first = lay.firstItemInRow(row);
            if (first < 0) continue;

            final Item firstItem = laid.get(first);
            final List<Item> inRow = new ArrayList<>();

            if (group(firstItem.groupId).collapsed || (group(firstItem.groupId).crafting && !group(firstItem.groupId).todo)) {
                for (Item i : all) if (i.groupId == firstItem.groupId) inRow.add(i);
            } else {
                for (int c = 0; c < lay.columns; c++) {
                    final Integer idx = lay.slotToItem.get(row * lay.columns + c);
                    if (idx == null) continue;
                    final Item slot = laid.get(idx);
                    if (all.contains(slot)) inRow.add(slot);
                    // a collapsed recipe's items travel with its first slot
                    for (Item folded : foldedInto.getOrDefault(slot, List.of())) {
                        if (all.contains(folded) && !inRow.contains(folded)) inRow.add(folded);
                    }
                }
            }

            results.put(row, inRow);
            all.removeAll(inRow);
        }

        results.put(rows, all);
        return results;
    }
}
