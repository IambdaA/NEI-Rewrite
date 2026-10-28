package codechicken.nei.bookmark;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * What the crafting tree's stats panel lists: the numbers behind the tree, in NEI's blocks (Ingredients, Crafting,
 * Results, Handlers). Plain Java. Ported from GTNH NEI's {@code StatsPanel#rebuild} and its {@code collect*} methods.
 *
 * Left out: container items (buckets and the like) in the remainders, and fluid cells.
 */
public final class TreeStats {

    /** The sections the toolbar's menu switches on and off. */
    public enum Section {
        INGREDIENTS_NEEDED, INGREDIENTS_AVAILABLE, CRAFTING_NEEDED, CRAFTING_AVAILABLE, RESULTS, REMAINDERS, HANDLERS
    }

    /** Which slot sprite an entry gets (NEI's SLOT_DEFAULT, SLOT_MISSING, SLOT_CRAFTING, SLOT_DISABLED). */
    public enum Kind {
        DEFAULT, MISSING, CRAFTING, DISABLED
    }

    /** An item with an amount; {@code recipeId} is set for the "Crafting" entries (the recipe that makes the item). */
    public record Entry(String itemId, long amount, Kind kind, String recipeId, long multiplier) {}

    /** How often a recipe type runs in the chain. */
    public record Handler(String name, long iterations, long recipes) {}

    /** One labelled group of slots: entries for item blocks, handlers for the last one. */
    public record Block(String label, List<Entry> entries, List<Handler> handlers) {

        public int size() {
            return entries.size() + handlers.size();
        }
    }

    private TreeStats() {}

    /**
     * @param handlerOf the name of the recipe type of {@code (itemId, recipeId)}, e.g. "Crafting Table"
     */
    public static List<Block> of(TreeMath math, TreeGraph graph, boolean useInventorySnapshot, Set<Section> sections,
            BiFunction<String, String, String> handlerOf) {
        final List<Block> blocks = new ArrayList<>();

        final List<Entry> ingredients = new ArrayList<>();
        if (sections.contains(Section.INGREDIENTS_NEEDED)) ingredients.addAll(inputs(math, useInventorySnapshot));
        if (sections.contains(Section.INGREDIENTS_AVAILABLE)) ingredients.addAll(available(math));
        if (!ingredients.isEmpty()) blocks.add(new Block("Ingredients", ingredients, List.of()));

        final boolean needed = sections.contains(Section.CRAFTING_NEEDED);
        final boolean ready = sections.contains(Section.CRAFTING_AVAILABLE);
        if (needed || ready) {
            final List<Entry> crafting = crafting(math, graph, needed, ready);
            if (!crafting.isEmpty()) blocks.add(new Block("Crafting", crafting, List.of()));
        }

        final List<Entry> results = new ArrayList<>();
        if (sections.contains(Section.RESULTS)) results.addAll(outputs(math, true));
        if (sections.contains(Section.REMAINDERS)) results.addAll(outputs(math, false));
        if (!results.isEmpty()) blocks.add(new Block("Results", results, List.of()));

        if (sections.contains(Section.HANDLERS)) {
            final List<Handler> handlers = handlers(math, handlerOf);
            if (!handlers.isEmpty()) blocks.add(new Block("Handlers", List.of(), handlers));
        }

        return blocks;
    }

    /** Adds up amounts per item, keeping the order they first appear in. */
    private static List<Entry> sum(Map<String, Long> amounts, Kind kind) {
        final List<Entry> list = new ArrayList<>();
        amounts.forEach((id, amount) -> {
            if (amount > 0) list.add(new Entry(id, amount, kind, null, 1));
        });
        return list;
    }

    private static List<Entry> available(TreeMath math) {
        final Map<String, Long> amounts = new LinkedHashMap<>();
        for (ChainMath.Node n : math.initialItems) {
            final long amount = math.required.getOrDefault(n, 0L);
            if (amount > 0) amounts.merge(n.id, amount, Long::sum);
        }
        return sum(amounts, Kind.DEFAULT);
    }

    private static List<Entry> inputs(TreeMath math, boolean useInventorySnapshot) {
        final Map<String, Long> amounts = new LinkedHashMap<>();
        for (ChainMath.Node n : math.recipeIngredients) {
            final long amount = math.required.containsKey(math.preferred.get(n)) ? 0
                    : math.required.getOrDefault(n, n.amount());
            if (amount > 0) amounts.merge(n.id, amount, Long::sum);
        }
        return sum(amounts, useInventorySnapshot ? Kind.MISSING : Kind.DEFAULT);
    }

    /** Results are what the output recipes leave over; remainders what the recipes in the middle leave over. */
    private static List<Entry> outputs(TreeMath math, boolean results) {
        final Map<String, Long> amounts = new LinkedHashMap<>();
        for (ChainMath.Node n : math.recipeResults) {
            final long amount = n.amount() - math.required.getOrDefault(n, 0L);
            if (amount > 0 && math.outputMultipliers.containsKey(n.recipe) == results) amounts.merge(n.id, amount, Long::sum);
        }
        return sum(amounts, results ? Kind.DEFAULT : Kind.DISABLED);
    }

    /** One entry per recipe that has ingredients below it: shallow recipes first, "needed" before "available". */
    private static List<Entry> crafting(TreeMath math, TreeGraph graph, boolean showNeeded, boolean showAvailable) {
        // BookmarkGrid.Item has no equals(), so these maps compare by identity
        final Map<BookmarkGrid.Item, Boolean> missing = new LinkedHashMap<>();
        final Map<BookmarkGrid.Item, Integer> level = new IdentityHashMap<>();

        for (TreeSlot node : graph.childrenOf.keySet()) {
            if (node.prefItem == null) continue;
            final boolean needsCrafting = graph.childrenNeedCraftingCache.contains(node);
            missing.merge(node.prefItem, needsCrafting, (a, b) -> a || b);
            level.merge(node.prefItem, depth(node, graph.parentOf), Math::max);
        }

        // TreeMath does not fill ChainMath.calc, so the amounts come from the result nodes themselves
        final Map<BookmarkGrid.Item, ChainMath.Node> results = new IdentityHashMap<>();
        for (ChainMath.Node n : math.recipeResults) results.put(n.src, n);

        final List<BookmarkGrid.Item> order = new ArrayList<>(missing.keySet());
        order.sort(Comparator.<BookmarkGrid.Item>comparingInt(level::get).thenComparing(i -> i.id));

        final List<Entry> needed = new ArrayList<>();
        final List<Entry> ready = new ArrayList<>();
        for (BookmarkGrid.Item item : order) {
            final ChainMath.Node result = results.get(item);
            if (result == null || result.amount() <= 0) continue;
            final boolean notReady = missing.get(item);
            if (notReady ? !showNeeded : !showAvailable) continue;
            (notReady ? needed : ready).add(new Entry(item.id, result.amount(), notReady ? Kind.DEFAULT : Kind.CRAFTING,
                    item.recipeId, result.multiplier));
        }

        final List<Entry> all = new ArrayList<>(needed);
        all.addAll(ready);
        return all;
    }

    private static int depth(TreeSlot node, Map<TreeSlot, TreeSlot> parentOf) {
        int level = 0;
        for (TreeSlot up = parentOf.get(node); up != null; up = parentOf.get(up)) level++;
        return level;
    }

    private static List<Handler> handlers(TreeMath math, BiFunction<String, String, String> handlerOf) {
        final Map<String, long[]> stats = new LinkedHashMap<>();
        final Set<String> counted = new HashSet<>();

        for (ChainMath.Node n : math.recipeResults) {
            // a recipe adds one result item per output: count it once
            if (n.recipe == null || !counted.add(n.recipe)) continue;
            final String name = handlerOf.apply(n.id, n.recipe);
            final long[] s = stats.computeIfAbsent(name == null ? "Recipe" : name, k -> new long[2]);
            s[0] += n.multiplier;
            s[1]++;
        }

        final List<Handler> list = new ArrayList<>();
        stats.forEach((name, s) -> list.add(new Handler(name, s[0], s[1])));
        return list;
    }
}
