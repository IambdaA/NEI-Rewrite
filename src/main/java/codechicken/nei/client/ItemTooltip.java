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
import net.neoforged.fml.ModList;

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
    private static HotkeyHelp.Where pendingWhere = HotkeyHelp.Where.LIST;
    /** Extra lines right under the item's name (the crafting tree's "Still needed: 4" and the like). */
    private static List<Component> pendingState = List.of();
    /** A plain text box (toolbar buttons). */
    private static List<Component> pendingPlain = List.of();
    /** Name of the subset row under the mouse, or null. */
    private static String pendingSubset;

    private ItemTooltip() {}

    /** Call at the start of a frame. */
    static void begin() {
        pending = ItemStack.EMPTY;
        pendingStrip = 0;
        pendingHeart = false;
        pendingState = List.of();
        pendingPlain = List.of();
        pendingSubset = null;
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
    static void requestHeart() {
        pendingHeart = true;
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
            if (HotkeyHelp.isAltDown()) {
                HotkeyHelp.drawSubset(g, mx, my, sw, sh, pendingSubset);
            } else {
                box(g, mx, my, sw, sh, List.of(Component.literal(pendingSubset), hintLine()), 0, 0);
            }
            return;
        }
        if (pendingHeart) {
            if (HotkeyHelp.isAltDown()) {
                HotkeyHelp.drawHeart(g, mx, my, sw, sh);
            } else {
                box(g, mx, my, sw, sh, List.of(Component.literal("Favorite"), hintLine()), 0, 0);
            }
            return;
        }
        if (pendingStrip != 0) {
            if (HotkeyHelp.isAltDown()) {
                HotkeyHelp.drawStrip(g, mx, my, sw, sh, pendingStrip >= 2, pendingStrip == 3);
            } else {
                final Component hint = hintLine();
                box(g, mx, my, sw, sh, List.of(Component.literal("Bookmarks Group"), hint), 0, 0);
            }
            return;
        }
        if (!pendingPlain.isEmpty()) {
            box(g, mx, my, sw, sh, pendingPlain, 0, 0);
            return;
        }
        if (pending.isEmpty()) return;

        if (HotkeyHelp.isAltDown()) {
            HotkeyHelp.draw(g, mx, my, sw, sh, pending, pendingWhere);
        } else if (pendingWhere != HotkeyHelp.Where.SLOT) {
            // slots of the container screen already get vanilla's own tooltip
            draw(g, mx, my, sw, sh, pending);
        }
    }

    /** The display name of the mod that owns the item, e.g. "Minecraft". */
    static String modName(ItemStack stack) {
        final String namespace = codechicken.nei.item.FluidDisplay.ownerNamespace(stack);
        return ModList.get().getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName()).orElse(namespace);
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

    /** "Hold ALT for hotkeys" with ALT highlighted. */
    static Component hintLine() {
        return Component.literal("Hold ").withStyle(s -> s.withColor(0xAAAAAA))
                .append(Component.literal("ALT").withStyle(s -> s.withColor(0xFFFF55)))
                .append(Component.literal(" for hotkeys").withStyle(s -> s.withColor(0xAAAAAA)));
    }

    private static void draw(GuiGraphicsExtractor g, int mx, int my, int sw, int sh, ItemStack stack) {
        final List<Component> source = normalLines(stack);
        final Component name = source.isEmpty() ? stack.getHoverName() : source.get(0);
        final List<Component> extra = source.size() > 1 ? source.subList(1, source.size()) : List.of();

        final Component hint = hintLine();
        final Component mod = Component.literal(modName(stack)).withStyle(s -> s.withColor(0x5555FF).withItalic(true));

        final List<Component> lines = new ArrayList<>();
        lines.add(name);
        lines.addAll(pendingState);
        lines.add(hint);
        lines.addAll(extra);
        lines.add(mod);

        final int gapAfterName = 2;
        final int gapBeforeMod = extra.isEmpty() ? 0 : 4;
        box(g, mx, my, sw, sh, lines, gapAfterName, gapBeforeMod);
    }

    /** Draws vanilla's purple tooltip box around the lines; {@code gapAfterName} / {@code gapBeforeMod} add spacing. */
    private static void box(GuiGraphicsExtractor g, int mx, int my, int sw, int sh, List<Component> lines,
            int gapAfterName, int gapBeforeMod) {
        final Font font = Minecraft.getInstance().font;

        int width = 0;
        for (Component c : lines) width = Math.max(width, font.width(c));
        final int height = lines.size() * 10 + gapAfterName + gapBeforeMod;

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
    }
}
