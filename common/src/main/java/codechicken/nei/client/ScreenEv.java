package codechicken.nei.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * One screen event (render, mouse, key, character) in a form that does not depend on the mod loader. Each loader module
 * builds one from its own event and calls {@link ClientHooks}; a handler that sets {@link #setCanceled} stops the
 * screen (or the other mods) from seeing the event, as with NeoForge's cancellable screen events. The input events are
 * Minecraft's own objects (26.x passes those to every loader's callbacks).
 */
public final class ScreenEv {

    private final Screen screen;
    private GuiGraphicsExtractor graphics;
    private double mouseX;
    private double mouseY;
    private float partialTick;
    private MouseButtonEvent mouse;
    private double scrollY;
    private KeyEvent key;
    private CharacterEvent character;
    private boolean canceled;

    private ScreenEv(Screen screen) {
        this.screen = screen;
    }

    public static ScreenEv init(Screen screen) {
        return new ScreenEv(screen);
    }

    public static ScreenEv render(Screen screen, GuiGraphicsExtractor graphics, double mx, double my, float partialTick) {
        final ScreenEv e = new ScreenEv(screen);
        e.graphics = graphics;
        e.mouseX = mx;
        e.mouseY = my;
        e.partialTick = partialTick;
        return e;
    }

    public static ScreenEv mouse(Screen screen, MouseButtonEvent mouse) {
        final ScreenEv e = new ScreenEv(screen);
        e.mouse = mouse;
        e.mouseX = mouse.x();
        e.mouseY = mouse.y();
        return e;
    }

    public static ScreenEv scroll(Screen screen, double mx, double my, double scrollY) {
        final ScreenEv e = new ScreenEv(screen);
        e.mouseX = mx;
        e.mouseY = my;
        e.scrollY = scrollY;
        return e;
    }

    public static ScreenEv key(Screen screen, KeyEvent key) {
        final ScreenEv e = new ScreenEv(screen);
        e.key = key;
        return e;
    }

    public static ScreenEv chr(Screen screen, CharacterEvent character) {
        final ScreenEv e = new ScreenEv(screen);
        e.character = character;
        return e;
    }

    public Screen getScreen() {
        return screen;
    }

    public GuiGraphicsExtractor getGuiGraphics() {
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
        return mouse.button();
    }

    public MouseButtonEvent getMouseButtonEvent() {
        return mouse;
    }

    public double getScrollDeltaY() {
        return scrollY;
    }

    public KeyEvent getKeyEvent() {
        return key;
    }

    public CharacterEvent getCharacterEvent() {
        return character;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    public boolean isCanceled() {
        return canceled;
    }
}
