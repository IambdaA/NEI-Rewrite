package codechicken.nei.client;

import codechicken.nei.NEIConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * Where the inventory screens draw the boxes of the active potion effects (NEI's {@code NEIPotionGuiHandler}, which JEI's
 * {@code InventoryEffectRendererGuiHandler} inspired). The item panels leave out the slots that are under them.
 *
 * The rule is vanilla's {@code EffectsInInventory}: the boxes start 2 pixels right of the inventory, are 120 wide (32 when
 * the room is under 120), 32 high and 33 apart (132 / (effects - 1) from the sixth effect on); nothing is drawn when there
 * are no effects or under 32 pixels of room.
 */
final class EffectAreas {

    private EffectAreas() {}

    /** The effect boxes as {@code {x, y, w, h}}; empty if the option ignores them or the screen draws none. */
    static List<int[]> current() {
        if (NEIConfig.IGNORE_POTION_OVERLAP.getAsBoolean()) return List.of();
        return boxes();
    }

    /** The effect boxes whatever the option says. */
    static List<int[]> boxes() {
        final Screen screen = Minecraft.getInstance().screen;
        if (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen)) return List.of();
        final var player = Minecraft.getInstance().player;
        if (player == null) return List.of();

        final AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) screen;
        final int left = container.leftPos + container.imageWidth + 2;
        final int room = container.width - left;
        final int count = player.getActiveEffects().size();
        if (count == 0 || room < 32) return List.of();

        final int w = room >= 120 ? 120 : 32;
        final int step = count > 5 ? 132 / (count - 1) : 33;
        final List<int[]> areas = new ArrayList<>(count);
        for (int i = 0; i < count; i++) areas.add(new int[] { left, container.topPos + i * step, w, 32 });
        return areas;
    }

    /** True if the rectangle touches one of the boxes. */
    static boolean hits(List<int[]> areas, int x, int y, int w, int h) {
        for (int[] a : areas) {
            if (x + w > a[0] && x < a[0] + a[2] && y + h > a[1] && y < a[1] + a[3]) return true;
        }
        return false;
    }
}
