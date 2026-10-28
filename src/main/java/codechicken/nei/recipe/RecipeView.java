package codechicken.nei.recipe;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * A recipe reduced to what the lookup screen draws: a title, a list of slots on a grid of 18px cells, and an info
 * line. Built once from the synced recipe data so the screen never touches recipe classes.
 *
 * @param title   usually the name of the crafting station, e.g. "Crafting Table"
 * @param station the station's icon, may be empty
 * @param slots   input and output slots; a slot with several options cycles through them (tags, fuels)
 * @param info    extra text such as smelting time and experience, may be empty
 * @param crafting whether this is a crafting-table recipe that can be transferred into a crafting grid
 * @param drawn   how it draws itself, or null for NEI's own grid drawing
 * @param stations every block that can make this recipe (shown in the station column; the "uses" of such a block lists them)
 */
public record RecipeView(String title, ItemStack station, List<SlotView> slots, String info, Crafting crafting,
        Drawn drawn, List<ItemStack> stations) {

    public RecipeView(String title, ItemStack station, List<SlotView> slots, String info, Crafting crafting) {
        this(title, station, slots, info, crafting, null, List.of());
    }

    /**
     * A recipe that draws itself (a modded machine's JEI layout: frame, arrow, fluids, energy text). The window gives it
     * the room it asks for and calls these every frame; {@code slots} still describe its items for lookups and bookmarks.
     * Plain interface so nothing here loads JEI classes.
     */
    public interface Drawn {

        /** The tooltip lines of the slot under the mouse (fluids etc.), empty if none. */
        List<net.minecraft.network.chat.Component> tooltip(int mx, int my);

        /** Size including its border. */
        int width();

        int height();

        /** Draws the recipe with its top-left corner at (x, y). */
        void draw(GuiGraphicsExtractor g, int x, int y, int mx, int my);

        /** The item stack under the mouse as of the last draw, or empty. */
        ItemStack itemAt(int mx, int my);

        /** Tooltips of what the mouse is over (fluids, bars); items are left to NEI's own tooltip. */
        void overlays(GuiGraphicsExtractor g, int mx, int my);

        boolean contains(int mx, int my);

        /** Whether a "+" can place this recipe into the open screen's menu: none, possible, or known to be impossible right now. */
        enum Transfer {
            NONE, OK, BLOCKED
        }

        /** JEI's recipe transfer: whether the open menu has a handler for this recipe (checked at most a few times a second). */
        default Transfer transferState(net.minecraft.world.inventory.AbstractContainerMenu menu) {
            return Transfer.NONE;
        }

        /** Places the recipe's ingredients into the open menu ({@code max}: as many sets as possible). Null on success, else why not. */
        default String transfer(net.minecraft.world.inventory.AbstractContainerMenu menu, net.minecraft.world.entity.player.Player player,
                boolean max) {
            return "This recipe cannot be placed here";
        }
    }

    /** Shaped recipes keep their slot positions; shapeless ones fill the grid in order. */
    public enum Crafting {
        NONE, SHAPED, SHAPELESS
    }

    /**
     * @param gx      column on the recipe grid
     * @param gy      row on the recipe grid
     * @param options every stack this slot can show (never empty)
     * @param output  true for result slots
     * @param indexed false for slots that should not make the recipe show up in "uses" (fuel slots list hundreds of items)
     */
    public record SlotView(int gx, int gy, List<ItemStack> options, boolean output, boolean indexed) {}
}
