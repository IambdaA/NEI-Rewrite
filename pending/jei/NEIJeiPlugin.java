package codechicken.nei.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import codechicken.nei.NotEnoughItems;
import codechicken.nei.item.FluidDisplay;
import codechicken.nei.recipe.RecipeSources;
import codechicken.nei.recipe.RecipeView;
import codechicken.nei.recipe.RecipeView.SlotView;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Reads every recipe JEI has registered (all mods' machines) and hands them to NEI as {@link RecipeView}s. JEI finds this
 * class itself ({@link JeiPlugin}); nothing else in NEI refers to it, so NEI runs fine without JEI.
 *
 * Each recipe draws itself with JEI's own layout ({@link RecipeView.Drawn}): frame, arrow, fluids, energy text, exactly as
 * JEI shows it. The item slots are still listed in the view (inputs row by row, outputs in a column) so lookups, "uses",
 * bookmarks and the recipe search work; the machine icon (crafting station / render-only slots) is not an input.
 * JEI's own GUI is turned off: NEI is the GUI.
 * shortcut: a layout wider than the window (176) is not scaled; upgrade if a category looks cut off.
 */
@JeiPlugin
public final class NEIJeiPlugin implements IModPlugin {

    private static final String SOURCE = "jei";
    private static final int MAX_OPTIONS = 100;
    private static final int MAX_INPUTS = 9;
    private static final int[] OUTPUT_ROWS = { 1, 0, 2 };

    /** The types NEI already reads from the synced recipes (and JEI's fuel / info pseudo recipes NEI has no use for). */
    private static final Set<String> NEI_COVERS = Set.of("minecraft:crafting", "minecraft:smelting", "minecraft:blasting",
            "minecraft:smoking", "minecraft:campfire_cooking", "minecraft:stonecutting", "minecraft:smithing",
            "minecraft:smelting_fuel", "minecraft:blasting_fuel", "minecraft:smoking_fuel", "jei:information");

    private static volatile List<RecipeView> cache;
    private static Bridge bridge;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(NotEnoughItems.MODID, "jei_bridge");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        cache = null;
        JeiGui.hide(); // NEI draws the item list, bookmarks and recipe window
        bridge = new Bridge(runtime);
        codechicken.nei.recipe.CraftingBridge.set(bridge);
        // built on first use (the recipe index builds on the client thread), not here on JEI's loading thread
        RecipeSources.set(SOURCE, () -> {
            List<RecipeView> views = cache;
            if (views == null) cache = views = read(runtime);
            return views;
        }, true);
    }

    @Override
    public void onRuntimeUnavailable() {
        cache = null;
        if (bridge != null) codechicken.nei.recipe.CraftingBridge.clear(bridge);
        bridge = null;
        RecipeSources.remove(SOURCE);
    }

    private static List<RecipeView> read(IJeiRuntime runtime) {
        final List<RecipeView> out = new ArrayList<>();
        final IRecipeManager manager = runtime.getRecipeManager();
        final IFocusGroup noFocus = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        final long start = System.currentTimeMillis();
        int categories = 0;
        fluidSlots = 0;
        MISSED.clear();

        for (IRecipeCategory<?> category : manager.createRecipeCategoryLookup().get().toList()) {
            if (NEI_COVERS.contains(category.getRecipeType().getUid().toString())) continue;
            try {
                final int before = out.size();
                category(manager, runtime.getIngredientManager(), runtime.getRecipeTransferManager(), category, noFocus, out);
                if (out.size() > before) categories++;
            } catch (RuntimeException | LinkageError e) {
                NotEnoughItems.LOGGER.warn("NEI could not read JEI category {}: {}", category.getRecipeType().getUid(),
                        e.toString());
            }
        }
        NotEnoughItems.LOGGER.info("NEI read {} recipes in {} categories from JEI in {} ms", out.size(), categories,
                System.currentTimeMillis() - start);
        NotEnoughItems.LOGGER.info("NEI JEI bridge: {} fluid ingredients became display items; not converted: {}", fluidSlots,
                MISSED);
        return out;
    }

    private static <T> void category(IRecipeManager manager, IIngredientManager ingredients, IRecipeTransferManager transfers,
            IRecipeCategory<T> category,
            IFocusGroup noFocus,
            List<RecipeView> out) {
        final String title = category.getTitle().getString();
        final List<ItemStack> stations = manager.createRecipeCatalystLookup(category.getRecipeType()).getItemStack()
                .filter(s -> !s.isEmpty()).map(ItemStack::copy).toList();
        final ItemStack station = stations.isEmpty() ? ItemStack.EMPTY : stations.get(0);

        for (T recipe : manager.createRecipeLookup(category.getRecipeType()).get().toList()) {
            try {
                final var layout = manager.createRecipeLayoutDrawable(category, recipe, noFocus);
                if (layout.isEmpty()) continue;
                final var area = layout.get().getRectWithBorder();
                final int pad = (area.getWidth() - layout.get().getRect().getWidth()) / 2; // JEI's border, 4 or 0
                final RecipeView view = view(title, station, stations, layout.get().getRecipeSlotsView().getSlotViews(),
                        new JeiDrawn<>(manager, ingredients, transfers, category, recipe, noFocus, area.getWidth(), area.getHeight(), pad));
                if (view != null) out.add(view);
            } catch (RuntimeException | LinkageError e) {
                // a broken recipe of a mod must not hide the rest
            }
        }
    }

    /** An item as itself, a fluid as its display item (so fluids are looked up like items), anything else as nothing. */
    private static ItemStack asStack(ITypedIngredient<?> typed) {
        final Object ingredient = typed.getIngredient();
        if (ingredient instanceof ItemStack stack) return stack.copy();
        final net.minecraft.world.level.material.Fluid fluid = fluidOf(ingredient);
        if (fluid != null) {
            final ItemStack stack = FluidDisplay.stackOf(fluid);
            if (stack.isEmpty()) missed("fluid without display item: " + fluid);
            else fluidSlots++;
            return stack;
        }
        missed(ingredient.getClass().getName());
        return ItemStack.EMPTY;
    }

    /**
     * The fluid of a JEI fluid ingredient. The ingredient is NeoForge's FluidStack or Fabric's IJeiFluidIngredient; both have
     * {@code getFluid()}, which is called by name so this class needs neither loader's fluid API.
     */
    private static net.minecraft.world.level.material.Fluid fluidOf(Object ingredient) {
        final String name = ingredient.getClass().getName();
        if (!name.contains("Fluid")) return null;
        try {
            final Object fluid = ingredient.getClass().getMethod("getFluid").invoke(ingredient);
            return fluid instanceof net.minecraft.world.level.material.Fluid f ? f : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Like {@link #asStack} but without the diagnostics counters (called while drawing). */
    private static ItemStack displayStack(ITypedIngredient<?> typed) {
        final Object ingredient = typed.getIngredient();
        if (ingredient instanceof ItemStack stack) return stack;
        final net.minecraft.world.level.material.Fluid fluid = fluidOf(ingredient);
        return fluid == null ? ItemStack.EMPTY : FluidDisplay.stackOf(fluid);
    }

    // diagnostics for the log: what could not be turned into an item, and how many fluid ingredients were
    private static int fluidSlots;
    private static final java.util.Map<String, Integer> MISSED = new java.util.TreeMap<>();

    private static void missed(String what) {
        MISSED.merge(what, 1, Integer::sum);
    }

    private static RecipeView view(String title, ItemStack station, List<ItemStack> stations, List<IRecipeSlotView> slots,
            RecipeView.Drawn drawn) {
        final List<SlotView> out = new ArrayList<>();
        int inputs = 0;
        int outputs = 0;
        boolean anyItem = false;

        for (IRecipeSlotView slot : slots) {
            final RecipeIngredientRole role = slot.getRole();
            // the machine icon in the arrow is a crafting station / render-only slot: drawn by the layout, not an input
            if (role != RecipeIngredientRole.INPUT && role != RecipeIngredientRole.OUTPUT) continue;
            final boolean output = role == RecipeIngredientRole.OUTPUT;
            final List<ItemStack> options = slot.getAllIngredients().map(NEIJeiPlugin::asStack)
                    .filter(s -> !s.isEmpty()).limit(MAX_OPTIONS).toList();

            if (options.isEmpty()) continue; // other ingredient types (gases, energy ...): the layout draws them
            anyItem = true;

            // grid positions only matter to lookups and the transfer; extra slots share the last cell
            if (output) out.add(new SlotView(5, OUTPUT_ROWS[Math.min(outputs++, OUTPUT_ROWS.length - 1)], options, true, true));
            else {
                out.add(new SlotView(Math.min(inputs, MAX_INPUTS - 1) % 3, Math.min(inputs, MAX_INPUTS - 1) / 3, options,
                        false, true));
                inputs++;
            }
        }
        if (!anyItem) return null;

        return new RecipeView(title, station, List.copyOf(out), "", RecipeView.Crafting.NONE, drawn, stations);
    }

    /**
     * NEI's vanilla crafting recipes are not read from JEI, so they have no JEI recipe object. To let a mod's crafting
     * screen place one, the JEI crafting recipe that makes the same result from the same ingredients is looked up and
     * its transfer handling reused ({@link JeiDrawn}).
     */
    private static final class Bridge implements codechicken.nei.recipe.CraftingBridge.Provider {

        private final IJeiRuntime runtime;
        private final java.util.Map<RecipeView, RecipeView.Drawn> matches = new java.util.IdentityHashMap<>();
        private IRecipeCategory<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> category;

        Bridge(IJeiRuntime runtime) {
            this.runtime = runtime;
        }

        @Override
        public RecipeView.Drawn.Transfer state(AbstractContainerMenu menu, RecipeView view) {
            final RecipeView.Drawn d = find(view);
            return d == null ? RecipeView.Drawn.Transfer.NONE : d.transferState(menu);
        }

        @Override
        public String transfer(AbstractContainerMenu menu, Player player, RecipeView view, boolean max) {
            final RecipeView.Drawn d = find(view);
            return d == null ? "This recipe cannot be placed here" : d.transfer(menu, player, max);
        }

        private RecipeView.Drawn find(RecipeView view) {
            if (matches.containsKey(view)) return matches.get(view);
            RecipeView.Drawn found = null;
            try {
                found = search(view);
            } catch (RuntimeException | LinkageError e) {
                NotEnoughItems.LOGGER.warn("NEI could not match a crafting recipe to JEI: {}", e.toString());
            }
            if (matches.size() > 512) matches.clear();
            matches.put(view, found);
            return found;
        }

        @SuppressWarnings("unchecked")
        private RecipeView.Drawn search(RecipeView view) {
            ItemStack result = ItemStack.EMPTY;
            final List<String> mine = new ArrayList<>();
            for (SlotView slot : view.slots()) {
                if (slot.output()) {
                    if (result.isEmpty() && !slot.options().isEmpty()) result = slot.options().get(0);
                } else if (!slot.options().isEmpty()) {
                    mine.add(signature(slot.options().stream().map(s -> idOf(s)).toList(), slot.options().size() >= MAX_OPTIONS));
                }
            }
            if (result.isEmpty()) return null;
            java.util.Collections.sort(mine);

            final IRecipeManager manager = runtime.getRecipeManager();
            if (category == null) {
                category = (IRecipeCategory<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>>) manager
                        .createRecipeCategoryLookup().limitTypes(List.of(RecipeTypes.CRAFTING)).get().findFirst().orElse(null);
            }
            if (category == null) return null;

            final var focuses = runtime.getJeiHelpers().getFocusFactory();
            final IFocusGroup noFocus = focuses.getEmptyFocusGroup();
            final var candidates = manager.createRecipeLookup(RecipeTypes.CRAFTING)
                    .limitFocus(List.of(focuses.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, result))).get()
                    .toList();
            for (var recipe : candidates) {
                final var layout = manager.createRecipeLayoutDrawable(category, recipe, noFocus);
                if (layout.isEmpty()) continue;
                final List<String> theirs = new ArrayList<>();
                for (IRecipeSlotView slot : layout.get().getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT)) {
                    final List<String> ids = slot.getIngredients(VanillaTypes.ITEM_STACK).map(Bridge::idOf).toList();
                    if (!ids.isEmpty()) theirs.add(signature(ids, ids.size() >= MAX_OPTIONS));
                }
                java.util.Collections.sort(theirs);
                if (theirs.equals(mine)) {
                    return new JeiDrawn<>(manager, runtime.getIngredientManager(), runtime.getRecipeTransferManager(), category,
                            recipe, noFocus, 0, 0, 0);
                }
            }
            return null;
        }

        private static String idOf(ItemStack stack) {
            return FluidDisplay.idOf(stack.getItem());
        }

        /** A slot's options as text, in a fixed order; very large tags (capped on one side only) become one word. */
        private static String signature(List<String> ids, boolean big) {
            if (big) return "BIG";
            return ids.stream().distinct().sorted().collect(java.util.stream.Collectors.joining(","));
        }
    }

    /** A recipe drawn by JEI's own layout; the layout is made on first draw (thousands exist, few are ever shown). */
    private static final class JeiDrawn<T> implements RecipeView.Drawn {

        private final IRecipeManager manager;
        private final IIngredientManager ingredients;
        private final IRecipeTransferManager transfers;
        private final IRecipeCategory<T> category;
        private final T recipe;
        private final IFocusGroup focus;
        private final int width;
        private final int height;
        private final int pad;
        private IRecipeLayoutDrawable<T> layout;
        private long lastTick;

        JeiDrawn(IRecipeManager manager, IIngredientManager ingredients, IRecipeTransferManager transfers, IRecipeCategory<T> category, T recipe, IFocusGroup focus, int width, int height,
                int pad) {
            this.manager = manager;
            this.ingredients = ingredients;
            this.transfers = transfers;
            this.category = category;
            this.recipe = recipe;
            this.focus = focus;
            this.width = width;
            this.height = height;
            this.pad = pad;
        }

        private IRecipeLayoutDrawable<T> layout() {
            if (layout == null) layout = manager.createRecipeLayoutDrawable(category, recipe, focus).orElse(null);
            return layout;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public void draw(GuiGraphics g, int x, int y, int mx, int my) {
            final var l = layout();
            if (l == null) return;
            l.setPosition(x + pad, y + pad); // the rect JEI positions is inside its border
            final long now = System.currentTimeMillis();
            if (now - lastTick >= 50) { // JEI ticks 20 times a second
                lastTick = now;
                l.tick();
            }
            l.drawRecipe(g, mx, my);
        }

        @Override
        // the list is plain components, which NEI's tooltip can draw
        public List<Component> tooltip(int mx, int my) {
            if (layout == null) return List.of();
            return layout.getSlotUnderMouse(mx, my).map(s -> withMod(s.slot())).orElse(List.of());
        }

        /** The slot's tooltip plus the owning mod's name in blue italics, the way NEI ends item tooltips. */
        private List<Component> withMod(IRecipeSlotDrawable slot) {
            final List<Component> lines = new ArrayList<>(slot.getTooltip());
            slot.getDisplayedIngredient().ifPresent(typed -> {
                final String ns = modId(typed);
                final String mod = NotEnoughItems.platform().modName(ns);
                lines.add(Component.literal(mod).withStyle(st -> st.withColor(0x5555FF).withItalic(true)));
            });
            return lines;
        }

        private <V> String modId(ITypedIngredient<V> typed) {
            return ingredients.getIngredientHelper(typed.getType()).getDisplayModId(typed.getIngredient());
        }

        /** The item under the mouse; a fluid is its display item, so fluids can be looked up (R / U) like items. */
        @Override
        public ItemStack itemAt(int mx, int my) {
            if (layout == null) return ItemStack.EMPTY;
            final var slot = layout.getSlotUnderMouse(mx, my);
            if (slot.isPresent()) {
                final var shown = slot.get().slot().getDisplayedIngredient();
                if (shown.isPresent()) {
                    final ItemStack stack = displayStack(shown.get());
                    if (!stack.isEmpty()) return stack;
                }
            }
            return layout.getItemStackUnderMouse(mx, my).orElse(ItemStack.EMPTY);
        }

        @Override
        public void highlightResult(GuiGraphics g, ItemStack result, int color) {
            if (layout == null) return;
            for (IRecipeSlotView slot : layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT)) {
                if (slot.getAllIngredients().anyMatch(t -> displayStack(t).getItem() == result.getItem())) slot.drawHighlight(g, color);
            }
        }

        @Override
        public void overlays(GuiGraphics g, int mx, int my) {
            if (layout != null) layout.drawOverlays(g, mx, my);
        }

        @Override
        public boolean contains(int mx, int my) {
            return layout != null && layout.isMouseOver(mx, my);
        }

        // JEI's recipe transfer: the handler a mod registered for its machine's menu moves the items.
        private AbstractContainerMenu checkedMenu;
        private long checkedAt;
        private Transfer checked = Transfer.NONE;

        private java.util.Optional<IRecipeTransferHandler<AbstractContainerMenu, T>> handler(AbstractContainerMenu menu) {
            return transfers.getRecipeTransferHandler(menu, category);
        }

        @Override
                public Transfer transferState(AbstractContainerMenu menu) {
            final long now = System.currentTimeMillis();
            if (menu == checkedMenu && now - checkedAt < 250) return checked;
            checkedMenu = menu;
            checkedAt = now;
            try {
                final var handler = handler(menu);
                final Player player = Minecraft.getInstance().player;
                if (handler.isEmpty() || player == null) return checked = Transfer.NONE;
                if (layout() == null) return checked = Transfer.NONE;
                final IRecipeTransferError error = handler.get().transferRecipe(menu, recipe, layout().getRecipeSlotsView(), player,
                        false, false);
                return checked = error == null || error.getType().allowsTransfer ? Transfer.OK : Transfer.BLOCKED;
            } catch (RuntimeException | LinkageError e) {
                return checked = Transfer.NONE;
            }
        }

        @Override
        public String transfer(AbstractContainerMenu menu, Player player, boolean max) {
            try {
                final var handler = handler(menu);
                if (handler.isEmpty()) return "This recipe cannot be placed here";
                if (layout() == null) return "Could not place the recipe";
                final var slots = layout().getRecipeSlotsView();
                IRecipeTransferError error = handler.get().transferRecipe(menu, recipe, slots, player, max, false);
                if (error != null && !error.getType().allowsTransfer) return message(error);
                error = handler.get().transferRecipe(menu, recipe, slots, player, max, true);
                return error == null ? null : message(error);
            } catch (RuntimeException | LinkageError e) {
                NotEnoughItems.LOGGER.warn("JEI recipe transfer failed: {}", e.toString());
                return "Could not place the recipe";
            }
        }

        private static String message(IRecipeTransferError error) {
            return error.getType() == IRecipeTransferError.Type.USER_FACING ? "Missing ingredients or no room" : "This recipe cannot be placed here";
        }
    }
}
