package codechicken.nei.client;

import java.util.ArrayList;
import java.util.List;

import codechicken.nei.NotEnoughItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * NEI's own item tooltip, drawn by hand so it can add the "Hold ALT for hotkeys" hint and the mod name:
 *
 * <pre>
 * Stone
 * Hold ALT for hotkeys
 *
 * (the item's normal tooltip lines, e.g. enchantments)
 * Minecraft
 * </pre>
 *
 * Panels and the recipe window only {@link #request} a tooltip while they draw; the tooltip is drawn once, last, by
 * {@link #flush}, so nothing can paint over it. While Alt is held the hotkey help box ({@link HotkeyHelp}) is drawn
 * instead.
 */
final class ItemTooltip {

    private static ItemStack pending = ItemStack.EMPTY;
    /** 0 = none, 1 = group strip outside a group, 2 = inside a group, 3 = inside a crafting chain group. */
    private static int pendingStrip;
    private static boolean pendingHeart;
    private static boolean pendingHeartMulti;
    private static HotkeyHelp.Where pendingWhere = HotkeyHelp.Where.LIST;
    /** Extra lines right under the item's name (the crafting tree's "Still needed: 4" and the like). */
    private static List<Component> pendingState = List.of();
    /** A plain text box (toolbar buttons). */
    private static List<Component> pendingPlain = List.of();
    /** Name of the subset row under the mouse, or null. */
    private static String pendingSubset;
    /** NEI's "Accepts following" list under a cycling ingredient's tooltip, or empty; and the stack the slot shows now. */
    private static List<ItemStack> pendingAccepts = List.of();
    private static ItemStack pendingActive = ItemStack.EMPTY;
    /** NEI's RecipeTooltipLineHandler: a recipe drawn under the item's tooltip (bookmarks, favorites), or null. */
    private static codechicken.nei.recipe.RecipeView pendingRecipe;
    /** NEI's RecipeChainTooltipLineHandler lines under the "Bookmarks Group" tooltip, or empty. */
    private static List<ItemLine> pendingChain = List.of();

    private ItemTooltip() {}

    /** Call at the start of a frame. */
    static void begin() {
        pending = ItemStack.EMPTY;
        pendingStrip = 0;
        pendingHeart = false;
        pendingState = List.of();
        pendingPlain = List.of();
        pendingSubset = null;
        pendingAccepts = List.of();
        pendingRecipe = null;
        pendingChain = List.of();
    }

    /** Adds the recipe to this frame's item tooltip (NEI's recipe tooltips of bookmarks and favorites). */
    static void requestRecipe(codechicken.nei.recipe.RecipeView view) {
        pendingRecipe = view;
    }

    /** The crafting chain lines of a group's strip tooltip. */
    static void requestChain(List<ItemLine> lines) {
        pendingChain = lines;
    }

    /** Adds NEI's "Accepts following" item grid to this frame's item tooltip (a recipe slot that cycles through {@code items}). */
    static void requestAccepts(List<ItemStack> items, ItemStack active) {
        pendingAccepts = items;
        pendingActive = active;
    }

    /** Asks for a plain text tooltip (no item), e.g. for a toolbar button. */
    static void requestPlain(List<Component> lines) {
        pendingPlain = lines;
        pending = ItemStack.EMPTY;
    }

    /** Asks for the tooltip of a row of the Item Subsets menu (Alt lists its mouse actions). */
    static void requestSubset(String name) {
        pendingSubset = name;
        pending = ItemStack.EMPTY;
    }

    /** Asks for the "Favorite" tooltip of a recipe's heart button. */
    private static List<Component> pendingHeartState = List.of();

    static void requestHeart(boolean severalResults, List<Component> stateLines) {
        pendingHeartState = stateLines;
        pendingHeart = true;
        pendingHeartMulti = severalResults;
        pending = ItemStack.EMPTY;
    }

    /** Asks for the "Bookmarks Group" tooltip of the strip beside the bookmarks. */
    static void requestStrip(boolean inGroup, boolean crafting) {
        pendingStrip = !inGroup ? 1 : crafting ? 3 : 2;
        pending = ItemStack.EMPTY;
    }

    /** Asks for a tooltip for this item at the end of the frame. The last request of a frame wins. */
    static void request(ItemStack stack, HotkeyHelp.Where where) {
        request(stack, where, List.of());
    }

    /** Like {@link #request(ItemStack, HotkeyHelp.Where)} with extra lines under the name. */
    static void request(ItemStack stack, HotkeyHelp.Where where, List<Component> stateLines) {
        if (stack.isEmpty()) return;
        pending = stack;
        pendingWhere = where;
        pendingState = stateLines;
    }

    /** Call at the end of a frame, after everything else is drawn. */
    static void flush(GuiGraphicsExtractor g, int mx, int my, int sw, int sh) {
        if (pendingSubset != null) {
            if (HotkeyHelp.helpDown()) {
                HotkeyHelp.drawSubset(g, mx, my, sw, sh, pendingSubset);
            } else {
                box(g, mx, my, sw, sh, hinted(Component.literal(pendingSubset)), 0, 0);
            }
            return;
        }
        if (pendingHeart) {
            if (HotkeyHelp.helpDown()) {
                HotkeyHelp.drawHeart(g, mx, my, sw, sh, pendingHeartMulti);
            } else {
                final List<Component> heartLines = new java.util.ArrayList<>();
                heartLines.add(Component.literal("Favorite"));
                heartLines.addAll(pendingHeartState);
                if (hintShown()) heartLines.add(hintLine());
                box(g, mx, my, sw, sh, heartLines, 0, 0);
            }
            return;
        }
        if (pendingStrip != 0) {
            if (HotkeyHelp.helpDown()) {
                HotkeyHelp.drawStrip(g, mx, my, sw, sh, pendingStrip >= 2, pendingStrip == 3);
            } else {
                box(g, mx, my, sw, sh, hinted(Component.literal("Bookmarks Group")), 0, 0,
                        pendingChain.isEmpty() ? null : new Stack(new ArrayList<>(pendingChain)));
            }
            return;
        }
        if (!pendingPlain.isEmpty()) {
            box(g, mx, my, sw, sh, pendingPlain, 0, 0);
            return;
        }
        if (pending.isEmpty()) return;

        if (HotkeyHelp.helpDown()) {
            HotkeyHelp.draw(g, mx, my, sw, sh, pending, pendingWhere);
        } else if (pendingWhere != HotkeyHelp.Where.SLOT) {
            // slots of the container screen already get vanilla's own tooltip
            draw(g, mx, my, sw, sh, pending);
        }
    }

    /** The display name of the mod that owns the item, e.g. "Minecraft". */
    static String modName(ItemStack stack) {
        final String namespace = codechicken.nei.item.FluidDisplay.ownerNamespace(stack);
        return NotEnoughItems.platform().modName(namespace);
    }

    /** The item's own tooltip lines after its name (enchantments, lore ...), for the Alt help box. */
    static List<Component> extraLines(ItemStack stack) {
        final List<Component> lines = normalLines(stack);
        return lines.size() > 1 ? lines.subList(1, lines.size()) : List.of();
    }

    private static List<Component> normalLines(ItemStack stack) {
        try {
            return SpawnerLook.recolored(stack, withoutOpWarning(stack,
                    stack.getTooltipLines(Item.TooltipContext.EMPTY, Minecraft.getInstance().player, TooltipFlag.NORMAL)));
        } catch (RuntimeException e) {
            return List.of(stack.getHoverName()); // a broken third-party tooltip must not break the panel
        }
    }

    /**
     * Vanilla warns on items with block data (the mob spawners of the item list): a bold red "Warning:" and two red lines
     * ("Use of this item might lead to command execution ..."). NEI's list has no such text, so those lines are dropped.
     * They are found by their translation key and, in case the key is not what it was, by their look: a bold red line and the
     * red lines right after it.
     */
    private static List<Component> withoutOpWarning(ItemStack stack, List<Component> lines) {
        final List<Component> result = new ArrayList<>(lines.size());
        int redTail = 0; // red lines after a dropped header that are still dropped
        for (Component line : lines) {
            final boolean byKey = line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().contains("op_block");
            final var color = line.getStyle().getColor();
            final boolean red = color != null && color.getValue() == 0xFF5555;
            final boolean header = red && line.getStyle().isBold() && line.getString().endsWith(":");
            if (byKey || header || redTail > 0 && red) {
                redTail = byKey || header ? 2 : redTail - 1;
                if (!result.isEmpty() && result.get(result.size() - 1).getString().isEmpty()) result.remove(result.size() - 1);
                continue;
            }
            redTail = 0;
            result.add(line);
        }
        return result;
    }

    /** "SHIFT + F: Favorite recipe for this result" with the keys highlighted. */
    private static Component favoriteLine() {
        return Component.literal(HotkeyHelp.favoriteKeyName()).withStyle(s -> s.withColor(0xFFFF55))
                .append(Component.literal(": Favorite recipe for this result").withStyle(s -> s.withColor(0xAAAAAA)));
    }

    /** NEI's inventory.hotkeysHelpText: whether tooltips end with the "Hold ALT for hotkeys" line. */
    static boolean hintShown() {
        return codechicken.nei.NEIConfig.HOTKEYS_HELP_TEXT.getAsBoolean();
    }

    /** The lines, plus the hint line when it is switched on. */
    static List<Component> hinted(Component... lines) {
        final List<Component> list = new ArrayList<>(List.of(lines));
        if (hintShown()) list.add(hintLine());
        return list;
    }

    /** "Hold ALT for hotkeys" with ALT highlighted. */
    static Component hintLine() {
        return Component.literal("Hold ").withStyle(s -> s.withColor(0xAAAAAA))
                .append(Component.literal("ALT").withStyle(s -> s.withColor(0xFFFF55)))
                .append(Component.literal(" for hotkeys").withStyle(s -> s.withColor(0xAAAAAA)));
    }

    private static void draw(GuiGraphicsExtractor g, int mx, int my, int sw, int sh, ItemStack stack) {
        final List<Component> source = normalLines(stack);
        Component name = source.isEmpty() ? stack.getHoverName() : source.get(0);
        final List<Component> extra = new ArrayList<>(source.size() > 1 ? source.subList(1, source.size()) : List.of());
        // a fluid: first what its mod says about it through the loader's fluid tooltip hook (NTM's traits, help, the id); only
        // when no mod says anything, NEI's ItemFluidDisplay#addInformation lines: temperature (red) and state (green)
        final net.minecraft.world.level.material.Fluid fluid = codechicken.nei.item.FluidDisplay.fluidOf(stack);
        java.util.List<Component> modLines = java.util.List.of();
        if (fluid != null) {
            try {
                modLines = NotEnoughItems.platform().fluidTooltip(fluid, Minecraft.getInstance().options.advancedItemTooltips);
            } catch (RuntimeException | LinkageError e) {
                modLines = java.util.List.of(); // a mod's broken tooltip handler must not break ours
            }
            extra.addAll(0, modLines);
        }
        // no hook lines: the item the fluid's mod uses to stand for it (NTM - Continuum's fluid icon) says what it is
        final ItemStack represented = fluid != null && modLines.isEmpty() ? codechicken.nei.item.FluidDisplay.representative(fluid)
                : ItemStack.EMPTY;
        if (!represented.isEmpty()) {
            final List<Component> own = normalLines(represented);
            if (!own.isEmpty()) {
                name = own.get(0);
                extra.addAll(0, own.subList(1, own.size()));
            }
        }
        // NEI's own temperature / state lines: only on vanilla fluids (water, lava); a mod's fluid shows what its mod says, or
        // nothing, as in JEI (the user's choice)
        if (fluid != null && modLines.isEmpty() && represented.isEmpty()
                && "minecraft".equals(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid).getNamespace())) {
            int at = 0;
            if (codechicken.nei.NEIConfig.FLUID_TEMPERATURE.getAsBoolean()) {
                extra.add(at++, Component.literal("Temperature: " + String.format("%,d", NotEnoughItems.platform().fluidTemperature(fluid)) + "K")
                        .withStyle(net.minecraft.ChatFormatting.RED));
            }
            if (codechicken.nei.NEIConfig.FLUID_STATE.getAsBoolean()) {
                extra.add(at, Component.literal("State: " + (NotEnoughItems.platform().fluidGaseous(fluid) ? "Gas" : "Liquid"))
                        .withStyle(net.minecraft.ChatFormatting.GREEN));
            }
        }

        final Component hint = hintLine();
        final Component mod = Component.literal(modName(stack)).withStyle(s -> s.withColor(0x5555FF).withItalic(true));

        final List<Component> lines = new ArrayList<>();
        lines.add(name);
        lines.addAll(pendingState);
        if (hintShown()) lines.add(hint);
        lines.addAll(extra);
        // a result of a recipe: the key that favorites the recipe for it, in the same box just above the mod name
        final boolean favoriteHint = pendingWhere == HotkeyHelp.Where.RECIPE && HotkeyHelp.recipeResult;
        if (favoriteHint) lines.add(favoriteLine());
        lines.add(mod);

        final int gapAfterName = 2;
        final int gapBeforeMod = extra.isEmpty() && !favoriteHint ? 0 : 4;
        final List<Block> blocks = new ArrayList<>();
        if (pendingAccepts.size() > 1) blocks.add(new ItemLine("Accepts following", net.minecraft.ChatFormatting.GRAY, pendingAccepts,
                List.of(), pendingActive, 4, 0, true));
        if (pendingRecipe != null) blocks.add(new RecipeBlock(pendingRecipe));
        box(g, mx, my, sw, sh, lines, gapAfterName, gapBeforeMod, blocks.isEmpty() ? null : new Stack(blocks));
    }

    /** Something drawn inside a tooltip box under its text lines (NEI's ITooltipLineHandler). */
    interface Block {
        int width();

        int height();

        void draw(GuiGraphicsExtractor g, Font font, int x, int y);
    }

    /** Several blocks one under the other. */
    private record Stack(List<? extends Block> blocks) implements Block {

        public int width() {
            int w = 0;
            for (Block b : blocks) w = Math.max(w, b.width());
            return w;
        }

        public int height() {
            int h = 0;
            for (Block b : blocks) h += b.height();
            return h;
        }

        public void draw(GuiGraphicsExtractor g, Font font, int x, int y) {
            for (Block b : blocks) {
                b.draw(g, font, x, y);
                y += b.height();
            }
        }
    }

    /** NEI's RecipeTooltipLineHandler: the recipe card, 4 px in from the box edges as NEI's background is. */
    private record RecipeBlock(codechicken.nei.recipe.RecipeView view) implements Block {

        public int width() {
            return RecipeOverlay.cardWidth(view);
        }

        public int height() {
            return RecipeOverlay.cardHeight(view) + 4 + 4;
        }

        public void draw(GuiGraphicsExtractor g, Font font, int x, int y) {
            RecipeOverlay.drawCard(g, view, x, y + 4);
        }
    }

    /**
     * NEI's {@code ItemsTooltipLineHandler}: a coloured label, then the items: first up to {@code maxNamedRows} rows of one item
     * with its name, then a grid of up to 11 a row, at most {@code maxRows} rows in all; what does not fit is counted as "+n" in
     * the last row, and the grid moves so the {@code active} item is in view (it gets a grey background). With
     * {@code favorites} a manual favorite item carries a yellow "F" (NEI's {@code AcceptsFollowingTooltipLineHandler}, which also
     * lists those first). An amount above 0 is drawn on its item.
     */
    static final class ItemLine implements Block {

        static final int SLOT = 18;
        static final int MAX_COLUMNS = 11;

        final String label;
        final net.minecraft.ChatFormatting color;
        final List<ItemStack> items;
        final List<Long> amounts;
        final boolean favorites;
        final int active;
        final List<String> names = new ArrayList<>();
        int nameRows;
        int columns;
        int rows;
        int count;
        int width;

        ItemLine(String label, net.minecraft.ChatFormatting color, List<ItemStack> items, List<Long> amounts, ItemStack activeStack,
                int maxRows, int maxNamedRows, boolean favorites) {
            final Font font = Minecraft.getInstance().font;
            this.label = label;
            this.color = color;
            this.items = items;
            this.amounts = amounts;
            this.favorites = favorites;
            int a = -1;
            for (int i = 0; activeStack != null && !activeStack.isEmpty() && i < items.size(); i++) {
                if (ItemStack.isSameItemSameComponents(items.get(i), activeStack)) {
                    a = i;
                    break;
                }
            }
            active = a;
            if (items.isEmpty()) return;
            if (!layoutWithNames(font, Math.max(1, maxRows), maxNamedRows)) layoutAsGrid(font, Math.max(1, maxRows));
        }

        private int labelWidth(Font font) {
            return font.width(label) + 15;
        }

        private boolean layoutWithNames(Font font, int maxRows, int maxNamedRows) {
            final int length = items.size();
            final int maxNames = Math.max(0, Math.min(maxNamedRows, length <= maxRows ? length : maxRows - 1));
            final int[] lineWidth = new int[maxNames + 1];
            lineWidth[0] = labelWidth(font);
            for (int i = 0; i < maxNames; i++) {
                final String name = items.get(i).getHoverName().getString();
                names.add(name);
                lineWidth[i + 1] = Math.max(lineWidth[i], SLOT + 2 + font.width(name));
            }
            for (int named = maxNames; named >= 0; named--) {
                final int rest = length - named;
                final int w = Math.max(lineWidth[named], Math.min(rest, MAX_COLUMNS) * SLOT);
                final int cols = Math.max(1, w / SLOT);
                final int restRows = (rest + cols - 1) / cols;
                if (named + restRows <= maxRows) {
                    nameRows = named;
                    columns = cols;
                    count = rest;
                    rows = named + restRows;
                    width = w;
                    return true;
                }
            }
            names.clear();
            return false;
        }

        private void layoutAsGrid(Font font, int maxRows) {
            final int length = items.size();
            nameRows = 0;
            columns = Math.min(MAX_COLUMNS, length);
            rows = Math.min(maxRows, (length + columns - 1) / columns);
            count = Math.min(length, columns * rows);
            width = Math.max(columns * SLOT, labelWidth(font));
            if (count < length) count -= (font.width("+" + (length - count)) - 2 + SLOT - 1) / SLOT; // NEI's reservedColumns
        }

        public int width() {
            return items.isEmpty() ? 0 : width;
        }

        public int height() {
            return items.isEmpty() ? 0 : 2 + 9 + 2 + rows * SLOT; // NEI: MARGIN_TOP + the label + 2, then the rows
        }

        private void slot(GuiGraphicsExtractor g, Font font, int sx, int sy, int index) {
            if (index == active) g.fill(sx, sy, sx + SLOT, sy + SLOT, 0x66555555);
            final ItemStack stack = items.get(index);
            Draw.stack(g, stack, sx + 1, sy + 1);
            final long amount = index < amounts.size() ? amounts.get(index) : 0;
            if (amount > 0) { // NEI's TotalAmountRenderer: items bottom right, fluids bottom left
                final String id = codechicken.nei.item.FluidDisplay.idOf(stack);
                final boolean fluid = codechicken.nei.item.FluidDisplay.isFluidId(id);
                final String text = fluid ? codechicken.nei.item.FluidDisplay.amountLabel(amount)
                        : amount < 10_000 ? String.valueOf(amount) : amount < 1_000_000 ? amount / 1000 + "K" : amount / 1_000_000 + "M";
                Draw.smallText(g, font, text, fluid ? sx + 1 : sx + 17 - font.width(text) * 0.5f, sy + 17 - 4.5f, 0xFFFFFF);
            }
            if (favorites && RecipeBookmarks.isFavoriteItem(codechicken.nei.item.FluidDisplay.idOf(stack))) {
                Draw.smallText(g, font, "F", sx + 1, sy + 1, 0xFDD835);
            }
        }

        public void draw(GuiGraphicsExtractor g, Font font, int x, int y) {
            if (items.isEmpty()) return;
            y += 2;
            g.text(font, Component.literal(label + ":").withStyle(color), x, y, 0xFFFFFFFF, true);
            final int gy = y + 9 + 2;
            final int textShift = 1 + 4; // NEI: ICON_OFFSET + round((16 - FONT_HEIGHT) / 2)
            for (int i = 0; i < nameRows; i++) {
                slot(g, font, x, gy + i * SLOT, i);
                g.text(font, Component.literal(names.get(i)).withStyle(net.minecraft.ChatFormatting.GRAY), x + SLOT + 2,
                        gy + i * SLOT + textShift, 0xFFFFFFFF, true);
            }
            final int shift = nameRows > 0 || active == -1 ? 0 : Math.max(0, Math.min(items.size() - count, active - count + 2));
            for (int i = 0; i < count; i++) {
                slot(g, font, x + (i % columns) * SLOT, gy + (nameRows + i / columns) * SLOT, nameRows + shift + i);
            }
            final int hidden = items.size() - nameRows - count;
            if (hidden > 0) {
                final String text = "+" + hidden;
                g.text(font, Component.literal(text).withStyle(net.minecraft.ChatFormatting.GRAY), x + width - font.width(text) - 2,
                        gy + (rows - 1) * SLOT + textShift, 0xFFFFFFFF, true);
            }
        }
    }

    /** A plain tooltip drawn now (for screens that are not a container screen, such as the settings screen). */
    static void drawPlain(GuiGraphicsExtractor g, int mx, int my, int sw, int sh, List<Component> lines) {
        box(g, mx, my, sw, sh, lines, 0, 0);
    }

    /** Draws vanilla's purple tooltip box around the lines; {@code gapAfterName} / {@code gapBeforeMod} add spacing. */
    private static void box(GuiGraphicsExtractor g, int mx, int my, int sw, int sh, List<Component> lines,
            int gapAfterName, int gapBeforeMod) {
        box(g, mx, my, sw, sh, lines, gapAfterName, gapBeforeMod, null);
    }

    private static void box(GuiGraphicsExtractor g, int mx, int my, int sw, int sh, List<Component> lines,
            int gapAfterName, int gapBeforeMod, Block accepts) {
        final Font font = Minecraft.getInstance().font;

        int width = 0;
        for (Component c : lines) width = Math.max(width, font.width(c));
        if (accepts != null) width = Math.max(width, accepts.width());
        final int textHeight = lines.size() * 10 + gapAfterName + gapBeforeMod;
        final int height = textHeight + (accepts != null ? accepts.height() : 0);

        int x = mx + 12;
        int y = my - 12;
        if (x + width + 8 > sw) x = Math.max(4, mx - width - 16);
        if (y + height + 8 > sh) y = Math.max(4, sh - height - 8);
        if (y < 4) y = 4;

        // vanilla's dark purple box
        g.fill(x - 3, y - 3, x + width + 3, y + height + 3, 0xF0100010);
        g.fill(x - 3, y - 3, x + width + 3, y - 2, 0xFF5000FF);
        g.fill(x - 3, y + height + 2, x + width + 3, y + height + 3, 0xFF28007F);
        g.fill(x - 3, y - 2, x - 2, y + height + 2, 0xFF3C00BF);
        g.fill(x + width + 2, y - 2, x + width + 3, y + height + 2, 0xFF3C00BF);

        int ty = y;
        for (int i = 0; i < lines.size(); i++) {
            g.text(font, lines.get(i), x, ty, 0xFFFFFFFF, true);
            ty += 10;
            if (i == 0) ty += gapAfterName;
            if (i == lines.size() - 2) ty += gapBeforeMod;
        }
        if (accepts != null) accepts.draw(g, font, x, y + textHeight);
    }
}
