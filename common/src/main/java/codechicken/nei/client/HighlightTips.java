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
    public static void onHud(net.minecraft.client.gui.GuiGraphicsExtractor g) {
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
        final int w = font.width(text) + 29;
        final int h = 24;
        final int x = (sw - w - 1) * NEIConfig.HUD_X.getAsInt() / 10000;
        final int y = (sh - h - 1) * NEIConfig.HUD_Y.getAsInt() / 10000;

        // vanilla tooltip look: dark box with a purple frame
        g.fill(x, y, x + w, y + h, 0xF0100010);
        g.fill(x, y, x + w, y + 1, 0xFF3A1A80);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF3A1A80);
        g.fill(x, y, x + 1, y + h, 0xFF3A1A80);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF3A1A80);
        g.text(font, Component.literal(text), x + 24, y + (h - 8) / 2, 0xFFA0A0A0, true);
        if (!stack.isEmpty()) Draw.stack(g, stack, x + 5, y + h / 2 - 8);
    }
}
