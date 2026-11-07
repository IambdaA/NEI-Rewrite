package codechicken.nei.client;

import codechicken.nei.NEIConfig;
import net.minecraft.client.gui.screens.Screen;

/**
 * NEI's {@code AutoFocusWidget}: with the option on, a vanilla container screen that has just opened sends the keys you type to
 * the search field without a click. It lets go as soon as the mouse moves (judged from the position it settled at after the
 * screen opened), a button or the wheel is used, Enter or Escape is pressed, or the recipe window or another text field takes
 * over. The first typing key focuses the search field and is typed into it (the inventory key too, unless the option is
 * Extended, where the inventory key lets go and closes the screen).
 */
final class AutoFocus {

    /** What a key press means to a running autofocus. */
    enum Key {
        /** Not a typing key (a modifier, an arrow ...): nothing happens. */
        IGNORE,
        /** Autofocus ended; the key goes on as usual. */
        CANCEL,
        /** The key types into the search field: focus it and swallow the key press. */
        TYPE
    }

    private static final int GLFW_ESCAPE = 256;
    private static final int GLFW_ENTER = 257;
    private static final int GLFW_KP_ENTER = 335;
    /** NEI waits for two ticks with the mouse in the same place. */
    private static final long SETTLE_NANOS = 50_000_000L;

    private static boolean active;
    private static boolean settled;
    private static int pendingX = -1;
    private static int pendingY = -1;
    private static long pendingAt;

    private AutoFocus() {}

    /** NEI's {@code isAllowedGuiAutoSearchFocus}: its default list holds the vanilla screens ({@code net.minecraft.client.gui.}). */
    private static boolean allowed(Screen screen) {
        return screen.getClass().getName().startsWith("net.minecraft.client.gui.");
    }

    /** A screen was opened (or resized). */
    static void load(Screen screen) {
        active = NEIConfig.SEARCH_AUTOFOCUS.getAsInt() != 0 && allowed(screen);
        settled = false;
        pendingX = -1;
        pendingY = -1;
    }

    static boolean active() {
        return active;
    }

    static void cancel() {
        active = false;
    }

    /** NEI's {@code guiTick}: called every frame with the mouse position in GUI pixels. */
    static void frame(int mx, int my) {
        if (!active) return;
        final long now = System.nanoTime();
        if (!settled) {
            if (mx == pendingX && my == pendingY) {
                if (now - pendingAt >= SETTLE_NANOS) settled = true;
            } else {
                pendingX = mx;
                pendingY = my;
                pendingAt = now;
            }
        } else if (mx != pendingX || my != pendingY) {
            cancel();
        }
    }

    /** Keys that put a character into a text field: the printable keys of the keyboard and the keypad (GLFW key codes). */
    private static boolean printable(int key) {
        return key >= 32 && key <= 96 || key == 161 || key == 162 || key >= 320 && key <= 334 || key == 336;
    }

    /** A key was pressed while autofocus runs. */
    static Key onKey(int key, boolean ctrl, boolean alt, boolean inventoryKey) {
        if (!active) return Key.IGNORE;
        if (NEIConfig.SEARCH_AUTOFOCUS.getAsInt() == 2 && inventoryKey) {
            cancel();
            return Key.CANCEL;
        }
        if (key == GLFW_ENTER || key == GLFW_KP_ENTER || key == GLFW_ESCAPE) {
            cancel();
            return Key.CANCEL;
        }
        if (!printable(key) || ctrl || alt) return Key.IGNORE; // NEI: a character that cannot be typed does nothing
        cancel(); // from now on the search field has the focus itself
        return Key.TYPE;
    }
}
