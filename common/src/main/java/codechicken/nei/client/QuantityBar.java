package codechicken.nei.client;

import codechicken.nei.NEIConfig;
import codechicken.nei.item.QuantityMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * NEI's item quantity field ({@code ItemQuantityField} with the {@code +} and {@code -} buttons) under the item list:
 * how many items a click on a list item gives (empty = one stack). Typing digits, Up / Down, the wheel over the field
 * and the buttons change it; Shift steps by 10 and Ctrl by 64.
 *
 * Own small text field instead of {@code EditBox}: NEI's field centres its text and only takes digits.
 * shortcut: no cursor movement or selection (Backspace and digits only); upgrade if anyone needs to edit the middle.
 */
final class QuantityBar {

    static final int H = 20;
    private static final int GLFW_BACKSPACE = 259;
    private static final int GLFW_ENTER = 257;
    private static final int GLFW_KP_ENTER = 335;
    private static final int GLFW_UP = 265;
    private static final int GLFW_DOWN = 264;

    private int x;
    private int y;
    private int w;
    private boolean focused;
    private String text = "";

    /** The amount of one click on {@code maxStack}-sized item (NEI's {@code getScrollMultiplier}). */
    static int multiplier(int maxStack) {
        return QuantityMath.multiplier(NEIConfig.QUANTITY_SHOW.getAsBoolean(), NEIConfig.QUANTITY.getAsInt(), maxStack);
    }

    static boolean enabled() {
        return NEIConfig.QUANTITY_SHOW.getAsBoolean();
    }

    void layout(int x, int y, int w) {
        this.x = x;
        this.y = y;
        this.w = w;
    }

    boolean contains(double mx, double my) {
        return Draw.in(mx, my, x, y, w, H);
    }

    boolean focused() {
        return focused;
    }

    private boolean overLess(double mx, double my) {
        return Draw.in(mx, my, x, y, H, H);
    }

    private boolean overMore(double mx, double my) {
        return Draw.in(mx, my, x + w - H, y, H, H);
    }

    private boolean overField(double mx, double my) {
        return Draw.in(mx, my, x + H + 1, y, w - 2 * H - 2, H);
    }

    private void set(int value) {
        NEIConfig.QUANTITY.set(value);
        NEIConfig.changed();
        if (focused) text = value == 0 ? "" : Integer.toString(value);
    }

    private void shift(int sign, boolean ctrl, boolean shift) {
        set(QuantityMath.step(NEIConfig.QUANTITY.getAsInt(), sign * QuantityMath.stepSize(ctrl, shift)));
    }

    void focus() {
        focused = true;
        final int q = NEIConfig.QUANTITY.getAsInt();
        text = q == 0 ? "" : Integer.toString(q); // NEI: gaining focus clears a "0"
    }

    /** NEI's {@code loseFocus}: the field shows the stored quantity again (typed text that was not a number is dropped). */
    void blur() {
        focused = false;
    }

    /** Returns true if the click was on the bar. */
    boolean click(double mx, double my, int button, boolean ctrl, boolean shift) {
        if (!contains(mx, my)) {
            blur();
            return false;
        }
        if (button == 0 && overLess(mx, my)) shift(-1, ctrl, shift);
        else if (button == 0 && overMore(mx, my)) shift(1, ctrl, shift);
        else if (overField(mx, my)) {
            if (button == 1) set(0); // right click clears, as the search field does
            else if (!focused) focus();
        }
        return true;
    }

    boolean scrolled(double mx, double my, double dy, boolean ctrl, boolean shift) {
        if (!overField(mx, my)) return false;
        shift(dy > 0 ? 1 : -1, ctrl, shift);
        return true;
    }

    /** Keys while the field has focus; returns true if the key was used. */
    boolean key(int glfwKey) {
        if (!focused) return false;
        if (glfwKey == GLFW_BACKSPACE) {
            if (!text.isEmpty()) {
                text = text.substring(0, text.length() - 1);
                NEIConfig.QUANTITY.set(Math.max(0, QuantityMath.parse(text)));
                NEIConfig.changed();
            }
            return true;
        }
        if (glfwKey == GLFW_UP || glfwKey == GLFW_DOWN) {
            set(QuantityMath.step(NEIConfig.QUANTITY.getAsInt(), glfwKey == GLFW_UP ? 1 : -1));
            return true;
        }
        if (glfwKey == GLFW_ENTER || glfwKey == GLFW_KP_ENTER) {
            blur();
            return true;
        }
        return false;
    }

    boolean typed(char c) {
        if (!focused) return false;
        if (c >= '0' && c <= '9') {
            final String next = text.equals("0") ? String.valueOf(c) : text + c;
            final int value = QuantityMath.parse(next);
            if (value >= 0) {
                text = next;
                NEIConfig.QUANTITY.set(value);
                NEIConfig.changed();
            }
        }
        return true; // letters are swallowed too, so they do not reach the inventory
    }

    void render(GuiGraphics g, int mx, int my) {
        final Font font = Minecraft.getInstance().font;
        Draw.button(g, x, y, H, H, overLess(mx, my));
        Draw.button(g, x + w - H, y, H, H, overMore(mx, my));
        Gfx.text(g, font, Component.literal("-"), x + (H - font.width("-")) / 2, y + (H - 8) / 2, 0xFFFFFFFF, true);
        Gfx.text(g, font, Component.literal("+"), x + w - H + (H - font.width("+")) / 2, y + (H - 8) / 2, 0xFFFFFFFF, true);

        final int fx = x + H + 1;
        final int fw = w - 2 * H - 2;
        Gfx.fill(g, fx, y, fx + fw, y + H, focused ? 0xFFFFFFFF : 0xFFA0A0A0);
        Gfx.fill(g, fx + 1, y + 1, fx + fw - 1, y + H - 1, 0xFF000000);

        final int q = NEIConfig.QUANTITY.getAsInt();
        final String shown = focused ? text : q == 0 ? "" : Integer.toString(q);
        final int ty = y + (H - 8) / 2;
        if (shown.isEmpty() && !focused) {
            final String hint = Component.translatable("nei.itempanel.quantity.default").getString();
            Gfx.text(g, font, Component.literal(hint), fx + (fw - font.width(hint)) / 2, ty, 0xFF808080, false);
        } else {
            final boolean caret = focused && (System.currentTimeMillis() / 500) % 2 == 0;
            final String drawn = caret ? shown + "_" : shown;
            Gfx.text(g, font, Component.literal(drawn), fx + (fw - font.width(shown)) / 2, ty, 0xFFE0E0E0, false);
        }
    }
}
