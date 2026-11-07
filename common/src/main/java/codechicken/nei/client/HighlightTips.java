package codechicken.nei.client;

import codechicken.nei.NEIConfig;
import codechicken.nei.NotEnoughItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * NEI's highlight tooltips ({@code HUDRenderer}): a small box with the item and name of the block you are looking at,
 * placed by the "highlightTipsX / Y" options (0 - 10000 across the free area, NEI's {@code world.highlight_tips.x / y}).
 * The numpad 0 key (rebindable) switches it on and off.
 *
 * Differences from NEI: the name only (NEI's default handler adds nothing else either, but other mods could add lines),
 * the block's own item instead of its pick-block item, and the position is set by the two numbers instead of by dragging a
 * sample box.
 */
final class HighlightTips {

    /** A screen drew within this time: the player is not looking at the world, so the box stays away (NEI: currentScreen == null). */
    private static final long SCREEN_GRACE_NANOS = 200_000_000L;

    private static long screenSeen;

    private HighlightTips() {}

    public static void onScreenFrame(ScreenEv event) {
        screenSeen = System.nanoTime();
    }

    public static void onTick() {
        while (NEIKeys.HUD_TOGGLE.consumeClick()) {
            NEIConfig.HUD_ENABLED.set(!NEIConfig.HUD_ENABLED.getAsBoolean());
            NEIConfig.changed();
        }
        NEIConfig.flush();
    }

    public static void onHud(net.minecraft.client.gui.GuiGraphics g) {
        if (!NEIConfig.HUD_ENABLED.getAsBoolean() || System.nanoTime() - screenSeen < SCREEN_GRACE_NANOS) return;

        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        final BlockState state = mc.level.getBlockState(hit.getBlockPos());
        final ItemStack stack = new ItemStack(state.getBlock().asItem()); // shortcut: NEI asks the block for its pick-block item
        final String text = stack.isEmpty() ? state.getBlock().getName().getString() : stack.getHoverName().getString();
        if (text.isEmpty()) return;

        final Font font = mc.font;
        final int sw = mc.getWindow().getGuiScaledWidth();
        final int sh = mc.getWindow().getGuiScaledHeight();
        final int w = boxWidth(font, text, null);
        final int x = (sw - w - 1) * NEIConfig.HUD_X.getAsInt() / 10000;
        final int y = (sh - BOX_H - 1) * NEIConfig.HUD_Y.getAsInt() / 10000;
        drawBox(g, font, stack, text, null, x, y);
    }

    static final int BOX_H = 24;

    static int boxWidth(Font font, String text, String extra) {
        return Math.max(font.width(text), extra == null ? 0 : font.width(extra)) + 29;
    }

    /** The box itself; {@code extra} is a second (red) line, used by the sample on the position screen. */
    static void drawBox(net.minecraft.client.gui.GuiGraphics g, Font font, ItemStack stack, String text, String extra,
            int x, int y) {
        final int w = boxWidth(font, text, extra);
        final int h = BOX_H;
        // vanilla tooltip look: dark box with a purple frame
        Gfx.fill(g, x, y, x + w, y + h, 0xF0100010);
        Gfx.fill(g, x, y, x + w, y + 1, 0xFF3A1A80);
        Gfx.fill(g, x, y + h - 1, x + w, y + h, 0xFF3A1A80);
        Gfx.fill(g, x, y, x + 1, y + h, 0xFF3A1A80);
        Gfx.fill(g, x + w - 1, y, x + w, y + h, 0xFF3A1A80);
        if (extra == null) {
            Gfx.text(g, font, Component.literal(text), x + 24, y + (h - 8) / 2, 0xFFA0A0A0, true);
        } else {
            Gfx.text(g, font, Component.literal(text), x + 24, y + 3, 0xFFA0A0A0, true);
            Gfx.text(g, font, Component.literal(extra), x + 24, y + 13, 0xFFFF5555, true);
        }
        if (!stack.isEmpty()) Draw.stack(g, stack, x + 5, y + h / 2 - 8);
    }
}
