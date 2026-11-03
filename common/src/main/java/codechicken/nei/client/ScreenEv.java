package codechicken.nei.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * One screen event (render, mouse, key, character) in a form that does not depend on the mod loader. Each loader module
 * builds one from its own event and calls {@link ClientHooks}; a handler that sets {@link #setCanceled} stops the
 * screen (or the other mods) from seeing the event, as with NeoForge's cancellable screen events.
 */
public final class ScreenEv {

    /** A key press or release. */
    public record Key(int key, int scan, int modifiers) {

        public InputConstants.Key asKey() {
            return InputConstants.getKey(key, scan);
        }
    }

    private final Screen screen;
    private GuiGraphics graphics;
    private double mouseX;
    private double mouseY;
    private float partialTick;
    private int button;
    private double scrollY;
    private Key keyEvent;
    private int codePoint;
    private int modifiers;
    private boolean canceled;

    private ScreenEv(Screen screen) {
        this.screen = screen;
    }

    public static ScreenEv init(Screen screen) {
        return new ScreenEv(screen);
    }

    public static ScreenEv render(Screen screen, GuiGraphics graphics, double mx, double my, float partialTick) {
        final ScreenEv e = new ScreenEv(screen);
        e.graphics = graphics;
        e.mouseX = mx;
        e.mouseY = my;
        e.partialTick = partialTick;
        return e;
    }

    public static ScreenEv mouse(Screen screen, double mx, double my, int button) {
        final ScreenEv e = new ScreenEv(screen);
        e.mouseX = mx;
        e.mouseY = my;
        e.button = button;
        return e;
    }

    public static ScreenEv scroll(Screen screen, double mx, double my, double scrollY) {
        final ScreenEv e = new ScreenEv(screen);
        e.mouseX = mx;
        e.mouseY = my;
        e.scrollY = scrollY;
        return e;
    }

    public static ScreenEv key(Screen screen, int key, int scan, int modifiers) {
        final ScreenEv e = new ScreenEv(screen);
        e.keyEvent = new Key(key, scan, modifiers);
        e.modifiers = modifiers;
        return e;
    }

    public static ScreenEv chr(Screen screen, int codePoint, int modifiers) {
        final ScreenEv e = new ScreenEv(screen);
        e.codePoint = codePoint;
        e.modifiers = modifiers;
        return e;
    }

    public Screen getScreen() {
        return screen;
    }

    public GuiGraphics getGuiGraphics() {
        return graphics;
    }

    public double getMouseX() {
        return mouseX;
    }

    public double getMouseY() {
        return mouseY;
    }

    public float getPartialTick() {
        return partialTick;
    }

    public int getButton() {
        return button;
    }

    public double getScrollDeltaY() {
        return scrollY;
    }

    public Key getKeyEvent() {
        return keyEvent;
    }

    /** The typed character (a UTF-16 code unit, as the screen's {@code charTyped} gets it). */
    public char getCodePoint() {
        return (char) codePoint;
    }

    public int getModifiers() {
        return modifiers;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    public boolean isCanceled() {
        return canceled;
    }
}
