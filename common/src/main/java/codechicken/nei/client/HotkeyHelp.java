package codechicken.nei.client;

import java.util.ArrayList;
import java.util.List;

import codechicken.nei.Bookmarks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The "hold Alt over an item to see its hotkeys" box. While Alt is held it replaces the item's normal tooltip, with
 * the item's name on the first line, so the two never fight over the same screen space.
 */
final class HotkeyHelp {

    /** Where the hovered item is, which decides which hotkeys apply. */
    enum Where {
        LIST, BOOKMARK, RECIPE, SLOT,
        /** An icon row of the Item Subsets menu. */
        SUBSET,
        /** A box of the crafting tree window; {@code TREE_ROOT} is a root, whose recipe can be scaled. */
        TREE, TREE_ROOT
    }

    private static boolean altDown;
    private static boolean shiftDown;
    private static boolean ctrlDown;

    private HotkeyHelp() {}

    static boolean isAltDown() {
        return altDown;
    }

    static boolean isShiftDown() {
        return shiftDown;
    }

    static boolean isCtrlDown() {
        return ctrlDown;
    }

    /** Forget modifier state, e.g. when a screen opens (a release may have happened while no screen was listening). */
    static void reset() {
        altDown = false;
        shiftDown = false;
        ctrlDown = false;
    }

    /** Called for every key press and release on a container screen so modifier state stays current. */
    static void track(int glfwKey, boolean pressed) {
        if (glfwKey == 342 || glfwKey == 346) altDown = pressed; // left / right alt
        if (glfwKey == 340 || glfwKey == 344) shiftDown = pressed; // left / right shift
        if (glfwKey == 341 || glfwKey == 345) ctrlDown = pressed; // left / right control
    }

    static String favoriteKeyName() {
        return "SHIFT + " + NEIKeys.name(NEIKeys.FAVORITE_ITEM);
    }

    /** Set by the recipe window each frame: the item under the mouse is a result of its recipe. */
    static boolean recipeResult;

    private record Line(String key, String text) {}

    /**
     * Draws the help box if Alt is held. Returns true if it did, in which case the caller skips its own tooltip.
     */
    /** Set by the bookmark panel for the slot it asks help for: 0 item, 1 recipe result, 2 ingredient. */
    static int slotType;
    /** True if the hovered result is the only result of its recipe. */
    static boolean slotOnlyResult;
    /** True if Alt + click does something on the hovered bookmark: expands its collapsed group or folds its recipe. */
    static boolean slotAltToggle;
    /** Set by the item list for a hovered collapsible group: how many items it has (0 = not a group). */
    static int listGroupSize;
    static boolean listGroupExpanded;

    static boolean draw(GuiGraphicsExtractor g, int mx, int my, int sw, int sh, ItemStack stack, Where where) {
        if (!altDown || stack.isEmpty()) return false;

        final boolean bookmarked = Bookmarks.contains(codechicken.nei.item.FluidDisplay.idOf(stack));

        // Wording and order follow NEI's ShortcutInputHandler#handleHotkeys and its lang file. Only actions this port
        // has are listed.
        final List<Line> lines = new ArrayList<>();
        if (where == Where.SUBSET) {
            // NEI's SubsetWidget#handleHotkeys for a hovered item
            if (codechicken.nei.NEIConfig.CHEAT_ENABLED.getAsBoolean()) lines.add(new Line("CTRL + LMB", "Give out an Item"));
            if (SubsetMenu.searchByClick()) lines.add(new Line("SHIFT + LMB", "Search by Item"));
            lines.add(new Line("LMB", "Show Item"));
            lines.add(new Line("RMB", "Hide Item"));
        }
        if (where == Where.LIST && listGroupSize > 1) {
            lines.add(new Line("ALT + LMB", (listGroupExpanded ? "Collapse Group (" : "Expand Group (") + listGroupSize + " items)"));
        }
        if (where == Where.BOOKMARK) {
            // As in NEI: plain items only say "Remove Item"; recipe slots offer "Remove Recipe" with Shift
            final String key = NEIKeys.name(NEIKeys.BOOKMARK);
            if (slotType == 0) {
                lines.add(new Line(key, "Remove Item"));
            } else if (slotType == 2 || !slotOnlyResult) {
                lines.add(new Line(key, "Remove Item"));
                lines.add(new Line("SHIFT + " + key, "Remove Recipe"));
            } else {
                lines.add(new Line(key, "Remove Recipe"));
            }
        } else if (where == Where.TREE || where == Where.TREE_ROOT) {
            // the tree window (NEI's GuiCraftingTree#handleHotkeys, then the shortcut handler's item lines)
            if (where == Where.TREE_ROOT) {
                lines.add(new Line("CTRL + Scroll", "Change Quantity"));
                lines.add(new Line("CTRL + ALT + Scroll", "Change Quantity with Multiplier"));
            }
            final String key = NEIKeys.name(NEIKeys.BOOKMARK);
            lines.add(new Line(key, "Add Bookmark"));
            lines.add(new Line("CTRL + " + key, "Add Bookmark with Count"));
        } else {
            // NEI always offers "Add Bookmark" outside the bookmark panel, then the count / recipe variants
            final String key = NEIKeys.name(NEIKeys.BOOKMARK);
            lines.add(new Line(key, "Add Bookmark"));
            lines.add(new Line("CTRL + " + key, "Add Bookmark with Count"));
            if (where == Where.LIST) chatLine(lines, "Send Item to Chat");
            if (where == Where.RECIPE) {
                lines.add(new Line("CTRL + SHIFT + " + key, "Add Bookmark with Recipe & Count"));
                lines.add(new Line("SHIFT + " + key, "Add Bookmark with Recipe"));
                craftLines(lines);
                chatLine(lines, "Send Item to Chat");
                lines.add(new Line("CTRL + SHIFT + " + NEIKeys.name(NEIKeys.CHAT_LINK), "Send Recipe to Chat"));
                if (recipeResult) lines.add(new Line("SHIFT + " + NEIKeys.name(NEIKeys.FAVORITE_ITEM), "Favorite Recipe for this Result"));
            }
        }
        if (where == Where.BOOKMARK) {
            lines.add(new Line("CTRL + Scroll", "Change Quantity"));
            lines.add(new Line("CTRL + ALT + Scroll", "Change Quantity with Multiplier"));
            chatLine(lines, slotType == 0 ? "Send Item to Chat" : "Send Recipe to Chat");
            pullLines(lines);
            if (slotType != 2 && slotType != 0) craftLines(lines);
            lines.add(new Line("SHIFT + LMB + Drag", "Move Position"));
            if (slotAltToggle) lines.add(new Line("ALT + LMB", "Toggle Collapse/Expand"));
        }
        lines.add(new Line(NEIKeys.name(NEIKeys.RECIPE), "Recipe to make this item"));
        lines.add(new Line(NEIKeys.name(NEIKeys.USAGE), "Recipes that use this item"));
        if (TerminalSearchSync.isTerminal(net.minecraft.client.Minecraft.getInstance().gui.screen())) {
            lines.add(new Line(NEIKeys.name(NEIKeys.TERMINAL_NAME), "Search this in the terminal"));
        }
        // As in NEI: the three copy lines follow the recipe / uses lines everywhere (oredict is "tags" here)
        lines.add(new Line("CTRL + " + NEIKeys.name(NEIKeys.COPY_NAME), "Copy Item Name"));
        lines.add(new Line("CTRL + " + NEIKeys.name(NEIKeys.COPY_TAGS), "Copy Item Tags"));
        lines.add(new Line("CTRL + " + NEIKeys.name(NEIKeys.COPY_ID), "Copy Item ID"));
        if (where != Where.SLOT && where != Where.SUBSET) {
            // As in NEI: while cheating a plain click gives the item, so recipes / uses move to Shift + click
            final boolean cheating = where != Where.RECIPE && where != Where.TREE && where != Where.TREE_ROOT
                    && codechicken.nei.NEIConfig.CHEAT_ENABLED.getAsBoolean();
            lines.add(new Line(cheating ? "SHIFT + LMB" : "LMB", "Recipe to make this item"));
            lines.add(new Line(cheating ? "SHIFT + RMB" : "RMB", "Recipes that use this item"));
        }

        box(g, mx, my, sw, sh, stack.getHoverName().getString(), lines, where == Where.SLOT);
        return true;
    }

    /**
     * Help for the thin group strip beside the bookmarks. Wording and order follow NEI's {@code BookmarkPanel#handleHotkeys};
     * actions that are not ported yet (group quantity, crafting chain, sorting) are left out.
     */
    static void drawStrip(GuiGraphicsExtractor g, int mx, int my, int sw, int sh, boolean inGroup, boolean crafting) {
        final List<Line> lines = new ArrayList<>();
        lines.add(new Line("LMB + Drag", "Create/Include Group"));
        if (inGroup) {
            lines.add(new Line("CTRL + Scroll", "Change Quantity"));
            lines.add(new Line("CTRL + ALT + Scroll", "Change Quantity with Multiplier"));
            lines.add(new Line("RMB + Drag", "Remove/Exclude Group"));
            lines.add(new Line("LMB", "Toggle Group Mode"));
            lines.add(new Line("RMB", "Toggle Crafting Chain"));
            chatLine(lines, "Send Bookmark Group to Chat");
            pullLines(lines);
            if (crafting) craftLines(lines);
            lines.add(new Line("SHIFT + LMB + Drag", "Move Position"));
            lines.add(new Line("ALT + LMB", "Toggle Collapse/Expand"));
            if (crafting) lines.add(new Line(NEIKeys.name(NEIKeys.OPEN_TREE), "Open Crafting Tree"));
        }
        box(g, mx, my, sw, sh, "Bookmarks Group", lines, false);
    }

    /** Help for the favorite (heart) button of a recipe (NEI's {@code GuiFavoriteButton#handleHotkeys}). */
    static void drawSubset(GuiGraphicsExtractor g, int mx, int my, int sw, int sh, String name) {
        final List<Line> lines = new ArrayList<>();
        lines.add(new Line("Double LMB", "Show only this Subset"));
        if (SubsetMenu.searchByClick()) lines.add(new Line("SHIFT + LMB", "Search by Subset"));
        lines.add(new Line("LMB", "Show Subset"));
        lines.add(new Line("RMB", "Hide Subset"));
        box(g, mx, my, sw, sh, name, lines, false);
    }

    static void drawHeart(GuiGraphicsExtractor g, int mx, int my, int sw, int sh, boolean severalResults) {
        final List<Line> lines = new ArrayList<>();
        lines.add(new Line("SHIFT + " + NEIKeys.name(NEIKeys.BOOKMARK), "Save Recipe Tree"));
        lines.add(new Line("SHIFT + " + NEIKeys.name(NEIKeys.OPEN_TREE), "Open Crafting Tree"));
        if (severalResults) lines.add(new Line("MOUSE WHEEL", "Change favorite result")); // NEI: recipe.favorite.change_result_index
        box(g, mx, my, sw, sh, "Favorite", lines, false);
    }

    private static void chatLine(List<Line> lines, String text) {
        lines.add(new Line("CTRL + " + NEIKeys.name(NEIKeys.CHAT_LINK), text));
    }

    /** NEI lists the pull keys only while the open screen is a container it can pull from. */
    private static void pullLines(List<Line> lines) {
        if (!ContainerPull.available(Minecraft.getInstance().gui.screen())) return;
        lines.add(new Line(NEIKeys.name(NEIKeys.PULL_ITEMS), "Pull all Items"));
        lines.add(new Line("SHIFT + " + NEIKeys.name(NEIKeys.PULL_ITEMS), "Pull missing Items"));
    }

    /** NEI lists the craft keys for recipes while autocrafting is on. */
    private static void craftLines(List<Line> lines) {
        if (!AutoCraft.enabled()) return;
        lines.add(new Line("SHIFT + CTRL + " + NEIKeys.name(NEIKeys.CRAFT_ITEMS), "Craft Missing Items"));
        lines.add(new Line("SHIFT + " + NEIKeys.name(NEIKeys.CRAFT_ITEMS), "Craft Items"));
    }

    private static void box(GuiGraphicsExtractor g, int mx, int my, int sw, int sh, String name, List<Line> lines,
            boolean below) {
        final Font font = Minecraft.getInstance().font;
        int width = font.width(name);
        for (Line l : lines) width = Math.max(width, font.width(l.key()) + 8 + font.width(l.text()));
        final int height = 12 + lines.size() * 10;

        // Normal tooltips sit right and slightly above the cursor; slots in the container screen already have one
        // underneath, so the help goes below it.
        int x = mx + 12;
        int y = below ? my + 70 : my - 12;
        if (x + width + 8 > sw) x = Math.max(4, mx - width - 16);
        if (y + height + 8 > sh) y = Math.max(4, sh - height - 8);

        g.fill(x - 3, y - 3, x + width + 3, y + height + 3, 0xF0100010);
        g.fill(x - 3, y - 3, x + width + 3, y - 2, 0xFF5000FF);
        g.fill(x - 3, y + height + 2, x + width + 3, y + height + 3, 0xFF28007F);
        g.fill(x - 3, y - 2, x - 2, y + height + 2, 0xFF3C00BF);
        g.fill(x + width + 2, y - 2, x + width + 3, y + height + 2, 0xFF3C00BF);

        g.text(font, Component.literal(name), x, y, 0xFFFFFFFF, true);
        int ty = y + 12;
        for (Line l : lines) {
            g.text(font, Component.literal(l.key()), x, ty, 0xFFFFFF55, false);
            g.text(font, Component.literal(l.text()), x + font.width(l.key()) + 8, ty, 0xFFAAAAAA, false);
            ty += 10;
        }
    }
}
