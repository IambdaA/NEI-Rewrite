package codechicken.nei.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/** A window drawn over the inventory that takes all input while it is open (presets, enchantments, potions). */
interface ModalWindow {

    Screen screen();

    boolean wantsClose();

    void render(GuiGraphics g, int mx, int my, int sw, int sh);

    void click(double mx, double my, int button, boolean ctrl, boolean shift);

    void scrolled(double mx, double my, double dy);

    /** The mouse button was let go (windows with dragging stop here). */
    default void released() {}

    /** Escape: leave the text box, the sub view, or close. */
    void escape();

    /** Up / Down while a search field with a text history has focus. True if it was used. */
    default boolean historyKey(boolean older) {
        return false;
    }

    /** The text box that has focus, or null. */
    EditBox typingBox();
}
