package codechicken.nei.item;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * NEI's fluid display items ({@code ItemFluidDisplay}): every fluid gets an item of its own so fluids can sit in the item
 * panel, be searched, and be looked up like items (recipes that make or use them). GTNH NEI uses one item with the fluid in
 * its damage value; here every source fluid gets its own item (ids {@code notenoughitems:fluid/<mod>/<fluid>}), so every
 * place that keys by item works unchanged. They exist only on the client (see {@code client.FluidRegistration}): a server
 * that sent them would be refused by clients without the mod. They are never obtainable.
 */
public final class FluidDisplay {

    private static final Map<Item, Fluid> FLUIDS = new HashMap<>();
    private static final Map<Fluid, Item> ITEMS = new HashMap<>();
    /** The id each display item was registered under: kept here so nothing depends on the item registry still holding it. */
    private static final Map<Item, String> IDS = new HashMap<>();

    /**
     * What tells stacks of one item apart ("subtype identity"), as text; "" for a plain stack. Set by the JEI bridge from
     * JEI's own subtype interpreters, because some mods (NTM: {@code hbm:fluid_icon}) use one item for many fluids and
     * JEI is the one that knows which component says which. Without JEI nothing is a subtype.
     */
    private static volatile java.util.function.Function<ItemStack, String> subtypes = s -> "";

    public static void setSubtypes(java.util.function.Function<ItemStack, String> f) {
        subtypes = f == null ? s -> "" : f;
    }

    public static String subtypeOf(ItemStack stack) {
        try {
            final String s = subtypes.apply(stack);
            return s == null ? "" : s;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Separates the registry id from JEI's subtype identity in an id: {@code hbm:fluid_icon~<base64url>}. */
    public static final char MARK = '~';
    private static final java.util.Set<String> FLUID_ITEM_IDS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Ids of subtyped item stacks whose tooltip names an amount of fluid ("20mB"): fluids that are items (NTM - Continuum). */
    private static final java.util.Set<String> AMOUNT_IDS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Map<net.minecraft.core.component.DataComponentPatch, Integer> TOOLTIP_AMOUNTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.regex.Pattern MB = java.util.regex.Pattern.compile("(\\d[\\d,]*)\\s*mB", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final Map<String, ItemStack> KNOWN = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile java.util.function.Supplier<? extends Iterable<ItemStack>> scanner;
    private static volatile boolean scanned;

    /** Called by the JEI bridge when its runtime (re)starts: all ingredient stacks, to rebuild a stack from a saved id. */
    public static void setScanner(java.util.function.Supplier<? extends Iterable<ItemStack>> all) {
        scanner = all;
        scanned = false;
        KNOWN.clear();
        AMOUNT_IDS.clear();
        TOOLTIP_AMOUNTS.clear();
    }

    /**
     * The id of a stack: its registry id, plus {@code ~} and JEI's subtype identity when JEI tells stacks of this item
     * apart (NTM's {@code hbm:fluid_icon}: one item, a fluid in a component). Two stacks have the same id exactly when
     * JEI treats them as the same ingredient, whatever else they carry (an amount). No tab, comma or | in it.
     */
    public static String idOf(ItemStack stack) {
        final String plain = idOf(stack.getItem());
        if (stack.isEmpty() || stack.getComponentsPatch().isEmpty()) return plain;
        final String sub = subtypeOf(stack);
        if (sub.isEmpty()) return plain;
        final String id = plain + MARK + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sub.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        KNOWN.putIfAbsent(id, stack.copyWithCount(1));
        return id;
    }

    /** Is this id a fluid: one of NEI's fluid display items, or an item whose tooltip gave an amount of fluid? */
    public static boolean isFluidId(String id) {
        return id != null && (FLUID_ITEM_IDS.contains(id) || AMOUNT_IDS.contains(id));
    }

    /** A fluid's display stack carrying the amount in millibuckets as its count (the count is what recipes and bookmarks add up). */
    public static ItemStack stackOf(Fluid fluid, long millibuckets) {
        final ItemStack stack = stackOf(fluid);
        if (!stack.isEmpty() && millibuckets > 1) stack.setCount((int) Math.min(Integer.MAX_VALUE, millibuckets));
        return stack;
    }

    /**
     * The millibuckets a fluid-as-item stack stands for, read from its tooltip ("20mB": NTM - Continuum draws every fluid as
     * one item whose amount is in a component only that mod can read), or 0. Only stacks JEI says have a subtype are asked.
     */
    public static int itemFluidAmount(ItemStack stack) {
        if (stack.isEmpty() || stack.getComponentsPatch().isEmpty() || subtypeOf(stack).isEmpty()) return 0;
        final int amount = TOOLTIP_AMOUNTS.computeIfAbsent(stack.getComponentsPatch(), k -> {
            try {
                for (var line : stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.EMPTY,
                        net.minecraft.client.Minecraft.getInstance().player, net.minecraft.world.item.TooltipFlag.NORMAL)) {
                    final var m = MB.matcher(line.getString());
                    if (m.find()) return Integer.parseInt(m.group(1).replace(",", ""));
                }
            } catch (RuntimeException e) {
                // a broken tooltip of a mod: no amount
            }
            return 0;
        });
        if (amount > 0) AMOUNT_IDS.add(idOf(stack));
        return amount;
    }

    /** An amount of fluid as short text that fits a slot: {@code 250mB}, {@code 1B}, {@code 2.5B}, {@code 12B}. */
    public static String amountLabel(long mb) {
        if (mb < 1000 || codechicken.nei.NEIConfig.FLUID_MB_ONLY.getAsBoolean()) return mb + "mB";
        if (mb % 1000 == 0) return (mb / 1000) + "B";
        if (mb < 10_000) return String.format(java.util.Locale.ROOT, "%.1fB", mb / 1000.0).replace(".0B", "B");
        return (mb / 1000) + "B";
    }

    /** The first "<n>mB" in some lines of text (a recipe slot's tooltip), or 0. */
    public static int amountIn(java.util.List<? extends net.minecraft.network.chat.Component> lines) {
        for (var line : lines) {
            final var m = MB.matcher(line.getString());
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1).replace(",", ""));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    /** Remembers that this stack's id is a fluid (an amount was read for it). */
    public static void markFluid(ItemStack stack) {
        AMOUNT_IDS.add(idOf(stack));
    }

    /** The registry id inside an id (without the subtype part). */
    public static String plainId(String id) {
        final int at = id.indexOf(MARK);
        return at < 0 ? id : id.substring(0, at);
    }

    /** The stack of a subtyped id (seen in recipes so far, else found in JEI's ingredient list), or empty. */
    public static ItemStack stackOf(String id) {
        ItemStack seen = KNOWN.get(id);
        if (seen == null && !scanned && scanner != null) {
            scanned = true;
            try {
                for (ItemStack listed : scanner.get()) idOf(listed);
            } catch (RuntimeException e) {
                // JEI not ready: the id stays unknown for now
                scanned = false;
            }
            seen = KNOWN.get(id);
        }
        return seen == null ? ItemStack.EMPTY : seen.copy();
    }

    private FluidDisplay() {}

    /** Records a display item; called while the client registers them. */
    public static void add(Item item, Fluid fluid, String id) {
        FLUIDS.put(item, fluid);
        ITEMS.put(fluid, item);
        IDS.put(item, id);
        FLUID_ITEM_IDS.add(id);
    }

    /** Every display item made so far. */
    public static java.util.Collection<Item> items() {
        return java.util.List.copyOf(FLUIDS.keySet());
    }

    /**
     * The id of an item as text, e.g. {@code minecraft:stone}. A fluid display item answers with the id it was made with
     * even if the item registry has dropped it (a registry sync with a server that does not know it), where the registry
     * itself would say {@code minecraft:air}.
     */
    public static String idOf(Item item) {
        final String own = IDS.get(item);
        return own != null ? own : BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** How many display items the item registry still holds (a diagnostic: it should be all of them). */
    public static int stillRegistered() {
        int n = 0;
        for (Item item : FLUIDS.keySet()) {
            if (BuiltInRegistries.ITEM.getKey(item).toString().equals(IDS.get(item))) n++;
        }
        return n;
    }

    public static boolean isFluidItem(Item item) {
        return FLUIDS.containsKey(item);
    }

    /** A fluid: one of NEI's display items, or an item whose tooltip names an amount of fluid. */
    public static boolean isFluid(ItemStack stack) {
        return is(stack) || (!stack.isEmpty() && !stack.getComponentsPatch().isEmpty() && isFluidId(idOf(stack)));
    }

    public static boolean is(ItemStack stack) {
        return FLUIDS.containsKey(stack.getItem());
    }

    public static Fluid fluidOf(ItemStack stack) {
        return FLUIDS.get(stack.getItem());
    }

    /** The display stack of a fluid, or empty if it has none (flowing or unregistered). */
    public static ItemStack stackOf(Fluid fluid) {
        Item item = ITEMS.get(fluid);
        // a recipe may name the flowing variant; the display item belongs to the source
        if (item == null && fluid instanceof net.minecraft.world.level.material.FlowingFluid flowing) {
            item = ITEMS.get(flowing.getSource());
        }
        return item == null ? ItemStack.EMPTY : named(new ItemStack(item));
    }

    /** Gives a fluid display stack the fluid's name (not italic, as a custom name would be); other stacks come back as they are. */
    public static ItemStack named(ItemStack stack) {
        final Fluid fluid = fluidOf(stack);
        if (fluid != null) {
            stack.set(DataComponents.CUSTOM_NAME,
                    codechicken.nei.NotEnoughItems.platform().fluidName(fluid).copy().withStyle(s -> s.withItalic(false)));
        }
        return stack;
    }

    /** The mod that owns the stack: the fluid's mod for a display item, else the item's. */
    public static String ownerNamespace(ItemStack stack) {
        final Fluid fluid = fluidOf(stack);
        return (fluid != null ? BuiltInRegistries.FLUID.getKey(fluid) : BuiltInRegistries.ITEM.getKey(stack.getItem()))
                .getNamespace();
    }
}
