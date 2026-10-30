package codechicken.nei.client;

import com.mojang.blaze3d.platform.InputConstants;

import codechicken.nei.NEIConfig;
import codechicken.nei.item.ZoomMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * NEI's item zoom ({@code ItemZoom}): the hovered item drawn big in the free area beside the window, with its name and
 * a hint for the toggle key below it. All the placement maths is in {@link ZoomMath}.
 *
 * Differences from NEI: "only solid" means a block that occludes light (1.7.10 had material.isSolid); the zoomed item
 * is drawn from the stack itself, not a copy loaded from NBT.
 */
final class ItemZoom {

    private static ItemStack stack = ItemStack.EMPTY;
    private static ZoomMath.Placement place;
    private static boolean holdDown;
    private static int screenWidth;
    private static int xSize;

    private ItemZoom() {}

    static void reset() {
        stack = ItemStack.EMPTY;
        place = null;
        holdDown = false;
    }

    static boolean hasItem() {
        return !stack.isEmpty() && place != null;
    }

    /**
     * NEI's {@code resize}: decides what is zoomed this frame.
     *
     * @param hovered    the item under the mouse, or empty
     * @param leftPanel  items in the left (bookmark) panel, 0 if it is not shown
     * @param rightPanel items in the right (item list) panel
     */
    static void update(AbstractContainerScreen<?> screen, ItemStack hovered, int mouseX, int leftPanel, int rightPanel) {
        stack = ItemStack.EMPTY;
        place = null;
        screenWidth = screen.width;
        xSize = screen.imageWidth;
        if (hovered.isEmpty()) return;
        if (NEIConfig.ZOOM_ONLY_SOLID.getAsBoolean()
                && !(hovered.getItem() instanceof BlockItem b && b.getBlock().defaultBlockState().canOcclude())) return;

        final int side = NEIConfig.ZOOM_SIDE.getAsInt();
        final boolean preferLeft = side == 1 || side == 0 && leftPanel <= rightPanel;
        place = ZoomMath.place(screen.width, screen.height, xSize, NEIConfig.ZOOM_AMOUNT.getAsInt(), mouseX, preferLeft);
        if (place != null) stack = hovered;
    }

    static void render(GuiGraphics g, AbstractContainerScreen<?> screen) {
        if (!hasItem() || !(NEIConfig.ZOOM_ENABLED.getAsBoolean() || holdDown)) return;
        // NEI: no zoom while something is held on the cursor (GuiContainerManager#shouldShowTooltip)
        if (!screen.getMenu().getCarried().isEmpty()) return;

        final Font font = Minecraft.getInstance().font;
        final float size = place.size();
        final int cx = (int) (place.x + size / 2);
        final int below = (int) (place.y + size);

        g.pose().pushPose();
        g.pose().translate(place.x, place.y, 0f);
        g.pose().scale(place.scale, place.scale, 1f);
        Draw.stack(g, stack, 0, 0);
        g.pose().popPose();

        int shift = 10;
        if (NEIConfig.ZOOM_SHOW_NAME.getAsBoolean()) {
            final String name = ZoomMath.crop(stack.getHoverName().getString(), place.availableWidth, font::width);
            int color = NEIConfig.color(NEIConfig.ZOOM_NAME_COLOR);
            if (color >>> 24 == 0) color |= 0xFF000000;
            Gfx.text(g, font, Component.literal(name), cx - font.width(name) / 2, below + shift, color, true);
            shift += font.lineHeight;
        }

        if (NEIConfig.ZOOM_ENABLED.getAsBoolean() && NEIConfig.ZOOM_HELP.getAsBoolean()
                && !NEIKeys.ZOOM_TOGGLE.isUnbound()) {
            final String help = Component.translatable("nei.itemzoom.toggle", "Shift + " + NEIKeys.name(NEIKeys.ZOOM_TOGGLE))
                    .getString();
            int y = below + 10 + shift;
            for (String line : ZoomMath.wrap(help, place.availableWidth, font::width)) {
                Gfx.text(g, font, Component.literal(line), cx - font.width(line) / 2, y, 0x66555555, false);
                y += font.lineHeight;
            }
        }
    }

    /** Shift + the toggle key. */
    static void toggle() {
        NEIConfig.ZOOM_ENABLED.set(!NEIConfig.ZOOM_ENABLED.getAsBoolean());
        NEIConfig.changed();
    }

    private static boolean matches(net.minecraft.client.KeyMapping mapping, InputConstants.Key pressed) {
        return NEIKeys.is(mapping, pressed);
    }

    /** The hold, zoom in and zoom out keys; they only act while an item is zoomed. Returns true if the key was used. */
    static boolean key(InputConstants.Key pressed) {
        if (!hasItem()) return false;
        if (matches(NEIKeys.ZOOM_HOLD, pressed)) {
            holdDown = true;
            return true;
        }
        final int now = NEIConfig.ZOOM_AMOUNT.getAsInt();
        if (matches(NEIKeys.ZOOM_IN, pressed)) {
            NEIConfig.ZOOM_AMOUNT.set(ZoomMath.zoomIn(screenWidth, xSize, now));
            NEIConfig.changed();
            return true;
        }
        if (matches(NEIKeys.ZOOM_OUT, pressed)) {
            NEIConfig.ZOOM_AMOUNT.set(ZoomMath.zoomOut(screenWidth, xSize, now));
            NEIConfig.changed();
            return true;
        }
        return false;
    }

    static void released(InputConstants.Key key) {
        if (matches(NEIKeys.ZOOM_HOLD, key)) holdDown = false;
    }

    static boolean toggleKey(InputConstants.Key pressed, boolean shift) {
        return shift && matches(NEIKeys.ZOOM_TOGGLE, pressed);
    }
}
