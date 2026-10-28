package codechicken.nei.bookmark;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How much of every recipe in a crafting chain group has to be made. Plain Java, no game classes.
 *
 * Ported from GTNH NEI's {@code RecipeChainMath} and {@code RecipeChainDetails} (the part used while no recipe of the
 * group is collapsed). Left out: container items (buckets, damageable tools), chances and fluid cells - none of them
 * exist for the recipes this port reads.
 *
 * <pre>
 * Items of a group are
 *   - plain items (type 0): what the player already has, used up before anything is crafted,
 *   - recipe results (type 1) and recipe ingredients (type 2).
 * An ingredient is "supplied" by the result of another recipe in the group that makes it (its preferred item). The
 * recipes nothing else needs are the output recipes; their multiplier is what the player set. Everything below is
 * derived: how many times each sub recipe must run so the output recipes get what they need.
 * </pre>
 */
public class ChainMath {

    /** What the chain says about one bookmark item. */
    public static final class Calc {

        public static final int RESULT = 0;
        public static final int REMAINDER = 1;
        public static final int INGREDIENT = 2;

        public final BookmarkGrid.Item item;
        /** Multiplier to display: amount = factor * multiplier. */
        public long multiplier;
        /** How much of it still has to be made / gathered. */
        public long shiftAmount;
        public int type;
        /** The multiplier the player set (minus the automatic one for recipes in the middle of the chain). */
        public long realMultiplier;

        Calc(BookmarkGrid.Item item, long multiplier, long shiftAmount, int type) {
            this.item = item;
            this.multiplier = multiplier;
            this.shiftAmount = shiftAmount;
            this.type = type;
        }

        public long amount() {
            return item.factor * multiplier;
        }
    }

    /** An item copy the math may change. Identity is what counts. */
    static final class Node {

        final BookmarkGrid.Item src;
        final String id;
        final Set<String> options;
        final String recipe;
        final int type;
        final long factor;
        long multiplier;

        Node(BookmarkGrid.Item src, long multiplier) {
            this.src = src;
            this.id = src.id;
            this.options = src.optionSet();
            this.recipe = src.recipeId;
            this.type = src.type;
            this.factor = src.factor;
            this.multiplier = multiplier;
        }

        boolean emptyFactor() {
            return factor <= 0;
        }

        long amount() {
            return factor * multiplier;
        }

        long amount(long mult) {
            return factor * mult;
        }

        long multiplierFromAmount(long amount) {
            return factor <= 0 ? 0 : (amount + factor - 1) / factor;
        }

        boolean containsItems(Node other) {
            for (String o : options) if (other.options.contains(o)) return true;
            return false;
        }
    }

    // ---- results ----

    public final Map<BookmarkGrid.Item, Calc> calc = new IdentityHashMap<>();
    public final Set<String> outputRecipes = new HashSet<>();
    public final Set<String> recipeInMiddle = new HashSet<>();

    /** Collapsed recipe to the recipes folded into it (itself included). Filled only when something is collapsed. */
    public final Map<String, Set<String>> relations = new LinkedHashMap<>();
    /**
     * What a collapsed recipe shows instead of its items and the folded recipes' items: results first, then
     * remainders, then raw ingredients, one entry per item. An entry's item is the recipe's own result when it has one
     * for that item, otherwise a stand-in that is not in the group's item list.
     */
    public final Map<String, List<Calc>> shadows = new LinkedHashMap<>();
    /** Items of the group that are not shown while their recipes are folded (identity set). */
    public final Set<BookmarkGrid.Item> hidden = Collections.newSetFromMap(new IdentityHashMap<>());

    // ---- working state ----

    protected final Map<String, Long> outputMultipliers = new HashMap<>();
    protected final List<Node> initialItems = new ArrayList<>();
    protected final List<Node> recipeIngredients = new ArrayList<>();
    protected final List<Node> recipeResults = new ArrayList<>();
    protected final Map<String, List<Node>> ingredientsByRecipe = new HashMap<>();
    protected final Map<String, List<Node>> resultsByRecipe = new HashMap<>();
    protected final Map<Node, Node> preferred = new IdentityHashMap<>();
    protected final Map<Node, Long> required = new IdentityHashMap<>();

    protected ChainMath() {}

    /** Calculates the chain of the given items (all the items of one group, in list order). */
    public static ChainMath of(List<BookmarkGrid.Item> items) {
        return of(items, Set.of());
    }

    /** @param collapsed recipes the player folded (NEI's {@code collapsedRecipes}); see {@link #shadows}. */
    public static ChainMath of(List<BookmarkGrid.Item> items, Set<String> collapsed) {
        final ChainMath m = new ChainMath();
        m.build(items, collapsed);
        m.refresh();
        m.fill(items);
        if (!collapsed.isEmpty()) m.collapse(items, collapsed);
        return m;
    }

    protected void build(List<BookmarkGrid.Item> items) {
        build(items, Set.of());
    }

    protected void build(List<BookmarkGrid.Item> items, Set<String> collapsed) {
        final Map<String, Long> multipliers = new HashMap<>();

        for (BookmarkGrid.Item item : items) {
            final long mult = item.factor <= 0 ? 0 : item.amount / item.factor;
            if (item.recipeId == null || item.type == 0) {
                initialItems.add(new Node(item, mult));
            } else if (item.type == 2) {
                recipeIngredients.add(new Node(item, 0));
            } else {
                recipeResults.add(new Node(item, 0));
                multipliers.merge(item.recipeId, mult, Math::max);
            }
        }

        rebuildRecipeIndex();

        for (Map.Entry<String, Long> entry : multipliers.entrySet()) {
            // a collapsed recipe is always an output recipe, so everything below it is worked out for it alone
            if (entry.getValue() > 1 || collapsed.contains(entry.getKey())) {
                collectPreferred(entry.getKey(), preferred, new HashSet<>());
                removeLoop(entry.getKey(), preferred, new HashSet<>());
                outputMultipliers.put(entry.getKey(), entry.getValue());
            }
        }

        while (true) {
            Map<Node, Node> maxReference = Collections.emptyMap();
            String maxRecipe = null;
            long maxMultiplier = 0;
            int maxDepth = 0;

            for (Map.Entry<String, Long> entry : multipliers.entrySet()) {
                final String recipeId = entry.getKey();
                if (!outputMultipliers.containsKey(recipeId) && preferred.values().stream().noneMatch(n -> n.recipe.equals(recipeId))) {
                    final Map<Node, Node> references = new IdentityHashMap<>(preferred);
                    collectPreferred(recipeId, references, new HashSet<>());
                    removeLoop(recipeId, references, new HashSet<>());
                    final int depth = maxDepth(recipeId, references);

                    if (maxDepth < depth || maxDepth == depth && entry.getValue() > maxMultiplier) {
                        maxMultiplier = entry.getValue();
                        maxReference = references;
                        maxRecipe = recipeId;
                        maxDepth = depth;
                    }
                }
            }

            if (maxReference.isEmpty()) break;

            preferred.putAll(maxReference);
            outputMultipliers.put(maxRecipe, multipliers.get(maxRecipe));
        }

        for (Map.Entry<String, Long> entry : multipliers.entrySet()) {
            final String recipeId = entry.getKey();
            final boolean isOutput = outputMultipliers.containsKey(recipeId);
            final boolean inMiddle = preferred.values().stream().anyMatch(n -> n.recipe.equals(recipeId));

            if (!isOutput && !inMiddle) {
                outputMultipliers.put(recipeId, entry.getValue());
            } else if (isOutput && inMiddle) {
                outputMultipliers.put(recipeId, Math.max(0, entry.getValue() - 1));
            }
        }
    }

    private void rebuildRecipeIndex() {
        ingredientsByRecipe.clear();
        resultsByRecipe.clear();
        for (Node n : recipeIngredients) ingredientsByRecipe.computeIfAbsent(n.recipe, k -> new ArrayList<>()).add(n);
        for (Node n : recipeResults) resultsByRecipe.computeIfAbsent(n.recipe, k -> new ArrayList<>()).add(n);
    }

    private void collectPreferred(String recipeId, Map<Node, Node> prefs, Set<String> visited) {
        visited.add(recipeId);

        for (Node ingr : ingredientsByRecipe.getOrDefault(recipeId, Collections.emptyList())) {
            if (!ingr.emptyFactor() && !prefs.containsKey(ingr)) {
                Node active = null;
                Node pref = null;

                for (Node item : recipeResults) {
                    if (!item.emptyFactor() && !visited.contains(item.recipe) && item.containsItems(ingr)) {
                        if ((active == null || item.amount(1) > active.amount(1)) && item.id.equals(ingr.id)) {
                            active = item;
                        } else if (pref == null || item.amount(1) > pref.amount(1)) {
                            pref = item;
                        }
                    }
                }

                if (active != null) pref = active;

                if (pref != null) {
                    prefs.put(ingr, pref);
                    collectPreferred(pref.recipe, prefs, visited);
                }
            }
        }

        visited.remove(recipeId);
    }

    private int maxDepth(String recipeId, Map<Node, Node> prefs) {
        int max = 0;
        for (Node ingr : prefs.keySet()) {
            if (!ingr.emptyFactor() && recipeId.equals(ingr.recipe)) {
                max = Math.max(max, maxDepth(prefs.get(ingr).recipe, prefs) + 1);
            }
        }
        return max;
    }

    private void removeLoop(String recipeId, Map<Node, Node> prefs, Set<String> visited) {
        visited.add(recipeId);

        for (Node ingr : ingredientsByRecipe.getOrDefault(recipeId, Collections.emptyList())) {
            if (!ingr.emptyFactor() && prefs.containsKey(ingr)) {
                final Node pref = prefs.get(ingr);
                if (visited.contains(pref.recipe)) prefs.remove(ingr);
                else removeLoop(pref.recipe, prefs, visited);
            }
        }

        visited.remove(recipeId);
    }

    private List<String> processingOrder() {
        if (outputMultipliers.size() <= 1) return new ArrayList<>(outputMultipliers.keySet());

        final Map<String, Set<String>> dependents = new HashMap<>();
        final Map<String, Integer> inDegree = new HashMap<>();
        for (String r : outputMultipliers.keySet()) inDegree.put(r, 0);

        for (String r : outputMultipliers.keySet()) {
            final Set<String> deps = new HashSet<>();
            collectBoxDependencies(r, r, deps, new HashSet<>());
            dependents.put(r, deps);
            for (String d : deps) inDegree.merge(d, 1, Integer::sum);
        }

        final List<String> order = new ArrayList<>();
        final ArrayDeque<String> queue = new ArrayDeque<>();
        for (String r : outputMultipliers.keySet()) if (inDegree.get(r) == 0) queue.add(r);

        while (!queue.isEmpty()) {
            final String r = queue.poll();
            order.add(r);
            for (String d : dependents.getOrDefault(r, Collections.emptySet())) {
                if (inDegree.merge(d, -1, Integer::sum) == 0) queue.add(d);
            }
        }

        if (order.size() != outputMultipliers.size()) {
            for (String r : outputMultipliers.keySet()) if (!order.contains(r)) order.add(r);
        }
        return order;
    }

    private void collectBoxDependencies(String boxId, String current, Set<String> deps, Set<String> visited) {
        if (!visited.add(current)) return;

        for (Node ingr : ingredientsByRecipe.getOrDefault(current, Collections.emptyList())) {
            if (ingr.emptyFactor()) continue;
            final Node pref = preferred.get(ingr);
            if (pref == null) continue;

            if (outputMultipliers.containsKey(pref.recipe) && !pref.recipe.equals(boxId)) deps.add(pref.recipe);
            else collectBoxDependencies(boxId, pref.recipe, deps, visited);
        }

        visited.remove(current);
    }

    protected boolean isForeignBoxBoundary(Node ingr) {
        final Node pref = preferred.get(ingr);
        return pref != null && ingr.type != 1 && outputMultipliers.containsKey(pref.recipe);
    }

    protected void refresh() {
        for (Node n : recipeIngredients) n.multiplier = 0;
        for (Node n : recipeResults) n.multiplier = 0;
        preferred.clear();
        required.clear();

        for (String recipeId : outputMultipliers.keySet()) {
            collectPreferred(recipeId, preferred, new HashSet<>());
            removeLoop(recipeId, preferred, new HashSet<>());
        }

        for (String recipeId : processingOrder()) {
            final long prefMultiplier = outputMultipliers.get(recipeId);

            for (Node pref : resultsByRecipe.getOrDefault(recipeId, Collections.emptyList())) {
                if (pref.emptyFactor()) continue;

                preferred.put(pref, pref);
                calculateSuitableRecipe(pref, prefMultiplier, new ArrayList<>());
                preferred.remove(pref);
            }
        }

        for (Node pref : recipeResults) {
            if (!pref.emptyFactor() && outputMultipliers.containsKey(pref.recipe) && required.containsKey(pref)) {
                final long prefAmount = pref.amount(outputMultipliers.get(pref.recipe));
                required.put(pref, required.get(pref) - prefAmount);
            }
        }
    }

    protected void calculateSuitableRecipe(Node ingr, long ingrMultiplier, List<String> visited) {
        final Node pref = preferred.get(ingr);
        long ingrAmount = ingr.amount(ingrMultiplier);
        final long requested = ingrAmount;
        long inventoryAmount = 0;

        // what the player already has (plain items of the group) is used up first
        if (ingrAmount > 0) {
            for (Node item : initialItems) {
                if (item.containsItems(ingr)) {
                    final long left = addRequired(item, ingrAmount, item.amount());
                    inventoryAmount += ingrAmount - left;
                    if ((ingrAmount = left) == 0) break;
                }
            }
        }

        final Node producer = pref != null ? pref : ingr;
        final long beforeShift = required.getOrDefault(producer, 0L);
        addRequired(producer, ingrAmount, Long.MAX_VALUE);

        calculationStepTrigger(ingr, pref, requested, 0, inventoryAmount, ingrAmount,
                required.getOrDefault(producer, 0L) - beforeShift);

        if (pref != null && !isForeignBoxBoundary(ingr) && !visited.contains(pref.recipe)) {
            final long multiplier = Math.max(0, pref.multiplierFromAmount(required.get(pref)) - pref.multiplier);

            if (multiplier > 0) {
                addShift(pref.recipe, multiplier);
                visited.add(pref.recipe);
                prepareIngredients(pref.recipe, multiplier, visited);
                visited.remove(pref.recipe);
            }
        }
    }

    /** NEI's {@code calculationStepTrigger}: called once for every item the calculation walks over. */
    protected void calculationStepTrigger(Node ingr, Node pref, long requested, long containerAmount,
            long inventoryAmount, long ingrAmount, long produced) {}

    protected void prepareIngredients(String recipeId, long multiplier, List<String> visited) {
        for (Node item : ingredientsByRecipe.getOrDefault(recipeId, Collections.emptyList())) {
            if (!item.emptyFactor()) calculateSuitableRecipe(item, multiplier, visited);
        }
    }

    /** Adds to what is required of {@code node}, up to {@code max}; returns what did not fit. */
    protected long addRequired(Node node, long amount, long max) {
        final long have = required.getOrDefault(node, 0L);
        final long add = Math.min(amount, max - have);
        required.put(node, have + add);
        return amount - add;
    }

    protected void addShift(String recipeId, long shift) {
        for (Node n : ingredientsByRecipe.getOrDefault(recipeId, Collections.emptyList())) n.multiplier += shift;
        for (Node n : resultsByRecipe.getOrDefault(recipeId, Collections.emptyList())) n.multiplier += shift;
    }

    // ---- what autocrafting needs ----

    /** How many times the recipe has to run (its results' multiplier, which already counts what the stock covers). */
    public long runs(String recipe) {
        long runs = 0;
        for (Node n : resultsByRecipe.getOrDefault(recipe, Collections.emptyList())) runs = Math.max(runs, n.multiplier);
        return runs;
    }

    /** The recipes in crafting order: a recipe comes after the recipes that make what it uses. */
    public List<String> craftOrder() {
        final List<String> out = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        for (String r : outputMultipliers.keySet().stream().sorted().toList()) order(r, seen, out);
        return out;
    }

    /** The recipe and every recipe below it (the ones that make what it uses, and so on). */
    public Set<String> dependencies(String recipe) {
        final Set<String> seen = new LinkedHashSet<>();
        order(recipe, seen, new ArrayList<>());
        return seen;
    }

    private void order(String recipe, Set<String> seen, List<String> out) {
        if (!seen.add(recipe)) return;
        for (Node ingr : ingredientsByRecipe.getOrDefault(recipe, Collections.emptyList())) {
            final Node pref = ingr.emptyFactor() ? null : preferred.get(ingr);
            if (pref != null) order(pref.recipe, seen, out);
        }
        out.add(recipe);
    }

    // ---- RecipeChainDetails.refresh ----

    private void fill(List<BookmarkGrid.Item> items) {
        outputRecipes.addAll(outputMultipliers.keySet());
        for (Node p : preferred.values()) if (p != null && p.recipe != null) recipeInMiddle.add(p.recipe);

        for (Node n : initialItems) {
            calc.put(n.src, new Calc(n.src, n.multiplier, required.getOrDefault(n, 0L), Calc.INGREDIENT));
        }

        for (Node n : recipeResults) {
            final long amount = n.amount() - required.getOrDefault(n, 0L);
            final int type = outputMultipliers.containsKey(n.recipe) ? Calc.RESULT : Calc.REMAINDER;
            calc.put(n.src, new Calc(n.src, n.multiplier, amount, type));
        }

        for (Node n : recipeIngredients) {
            final Node pref = preferred.get(n);
            final long amount = pref != null && required.containsKey(pref) ? 0 : required.getOrDefault(n, n.amount());
            calc.put(n.src, new Calc(n.src, n.multiplier, amount, Calc.INGREDIENT));
        }

        for (BookmarkGrid.Item item : items) {
            final Calc c = calc.get(item);
            if (c == null) continue;
            final long real = item.factor <= 0 ? 0 : item.amount / item.factor;
            c.realMultiplier = item.recipeId != null && recipeInMiddle.contains(item.recipeId) ? Math.max(0, real - 1) : real;
        }
    }

    // ---- RecipeChainDetails.refresh, the collapsed-recipes branch ----

    private void collapse(List<BookmarkGrid.Item> items, Set<String> collapsedAll) {
        final Set<String> collapsed = new LinkedHashSet<>();
        for (String r : collapsedAll) if (resultsByRecipe.containsKey(r) || ingredientsByRecipe.containsKey(r)) collapsed.add(r);

        final Set<String> top = topLevelRecipes(collapsed);
        final Set<BookmarkGrid.Item> shown = Collections.newSetFromMap(new IdentityHashMap<>());

        for (String recipe : collapsed) {
            final Set<String> rels = relationsOf(recipe, top, new LinkedHashSet<>(List.of(recipe)));
            relations.put(recipe, rels);

            final List<Calc> entries = new ArrayList<>();
            final Map<String, Calc> byId = new LinkedHashMap<>();
            for (Calc c : collectItems(this, recipe, rels, top)) {
                final BookmarkGrid.Item it = c.item;
                final boolean own = it.type != 2 && recipe.equals(it.recipeId);
                if (it.type == 1 && !outputMultipliers.containsKey(it.recipeId)) c.type = Calc.REMAINDER;

                final Calc shown1;
                if (own) {
                    shown1 = c;
                    shown.add(it);
                    final long real = it.factor <= 0 ? 0 : it.amount / it.factor;
                    c.realMultiplier = recipeInMiddle.contains(recipe) ? Math.max(0, real - 1) : real;
                    calc.put(it, c);
                } else {
                    final BookmarkGrid.Item stand = new BookmarkGrid.Item(it.id, 0, it.groupId);
                    stand.type = it.type;
                    stand.recipeId = recipe;
                    stand.factor = it.factor;
                    stand.options = it.options;
                    shown1 = new Calc(stand, c.multiplier, c.shiftAmount, c.type);
                    calc.put(stand, shown1);
                }
                entries.add(shown1);
                byId.put(it.id, shown1);
            }

            // how many times each item's recipe really runs: a chain of just this recipe and what is folded into it
            final List<BookmarkGrid.Item> sub = new ArrayList<>();
            for (BookmarkGrid.Item item : items) {
                if (!rels.contains(item.recipeId)) continue;
                final BookmarkGrid.Item copy = new BookmarkGrid.Item(item.id, recipe.equals(item.recipeId) ? item.amount : 0,
                        item.groupId);
                copy.type = item.type;
                copy.recipeId = item.recipeId;
                copy.factor = item.factor;
                copy.options = item.options;
                sub.add(copy);
            }
            final ChainMath subMath = ChainMath.of(sub);
            for (Calc c : collectItems(subMath, recipe, rels, Set.of())) {
                final Calc target = byId.get(c.item.id);
                // the recipe's own results keep the multiplier worked out above
                if (target != null && !shown.contains(target.item)) {
                    target.realMultiplier = c.item.factor <= 0 ? 0 : (c.shiftAmount + c.item.factor - 1) / c.item.factor;
                }
            }

            // ItemSorter order: the item list is sorted by registry id here, so this is the same order
            entries.sort(Comparator.<Calc>comparingInt(c -> c.type).thenComparing(c -> c.item.id));
            shadows.put(recipe, entries);
        }

        for (Node n : recipeResults) hideUnlessShown(n, collapsed, top, shown);
        for (Node n : recipeIngredients) hideUnlessShown(n, collapsed, top, shown);
    }

    private void hideUnlessShown(Node n, Set<String> collapsed, Set<String> top, Set<BookmarkGrid.Item> shown) {
        if (!(top.contains(n.recipe) && !collapsed.contains(n.recipe)) && !shown.contains(n.src)) hidden.add(n.src);
    }

    /** The recipes that stay visible: outputs, and what is not made only for one collapsed recipe. */
    private Set<String> topLevelRecipes(Set<String> collapsed) {
        final Set<String> result = new HashSet<>(outputMultipliers.keySet());

        for (Node item : recipeResults) {
            if (result.contains(item.recipe)) continue;
            final Set<String> parents = parentsOf(item.recipe, new HashSet<>(), new HashSet<>(List.of(item.recipe)));
            if (parents.size() != 1 || parents.stream().noneMatch(collapsed::contains)) result.add(item.recipe);
        }
        return result;
    }

    /** The output recipes (or dead ends) that need {@code recipe}, through the recipes in between. */
    private Set<String> parentsOf(String recipe, Set<String> parents, Set<String> visited) {
        final Set<String> users = new HashSet<>();
        for (Map.Entry<Node, Node> e : preferred.entrySet()) {
            if (e.getValue() != null && recipe.equals(e.getValue().recipe)) users.add(e.getKey().recipe);
        }

        if (users.isEmpty()) {
            parents.add(recipe);
        } else {
            for (String user : users) {
                if (!visited.add(user)) continue;
                if (outputMultipliers.containsKey(user)) parents.add(user);
                else parentsOf(user, parents, visited);
            }
        }
        return parents;
    }

    private Set<String> relationsOf(String recipe, Set<String> top, Set<String> recipes) {
        for (Node item : recipeIngredients) {
            if (!item.recipe.equals(recipe)) continue;
            final Node pref = preferred.get(item);
            if (pref != null && !top.contains(pref.recipe) && recipes.add(pref.recipe)) relationsOf(pref.recipe, top, recipes);
        }
        return recipes;
    }

    /** NEI's {@code collectItems}: the results and raw ingredients of the recipes, merged per item. */
    private static List<Calc> collectItems(ChainMath m, String recipe, Set<String> rels, Set<String> top) {
        final Map<String, Calc> out = new LinkedHashMap<>();
        final Map<String, Long> multiplier = new HashMap<>();

        for (String rel : rels) {
            for (Node n : m.recipeResults) {
                if (!rel.equals(n.recipe)) continue;
                final long amount = n.amount() - m.required.getOrDefault(n, 0L);
                if (rel.equals(recipe)) {
                    multiplier.put(rel, n.multiplier);
                    append(out, n, Calc.RESULT, amount, n.amount());
                } else if (amount > 0) {
                    append(out, n, Calc.RESULT, amount, n.amount());
                }
            }

            for (Node n : m.recipeIngredients) {
                if (!rel.equals(n.recipe)) continue;
                final Node pref = m.preferred.get(n);
                final boolean supplied = m.required.containsKey(pref);
                final long amount = supplied ? 0 : m.required.getOrDefault(n, n.amount());
                final long ref = pref != null && !top.contains(pref.recipe) ? m.required.getOrDefault(pref, 0L) : 0;

                if (amount != 0 || n.amount() > ref && supplied || recipe.equals(n.recipe) && multiplier.getOrDefault(recipe, -1L) == 0) {
                    append(out, n, Calc.INGREDIENT, amount, n.amount() - ref);
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    private static void append(Map<String, Calc> out, Node n, int type, long amount, long calculatedAmount) {
        final Calc c = out.computeIfAbsent(n.id, k -> new Calc(n.src, 0, 0, type));
        c.shiftAmount += amount;
        // the first item of that name decides the factor, as in NEI
        c.multiplier += c.item.factor <= 0 ? 0 : (calculatedAmount + c.item.factor - 1) / c.item.factor;
    }
}
