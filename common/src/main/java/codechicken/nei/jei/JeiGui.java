package codechicken.nei.jei;

import codechicken.nei.NotEnoughItems;

/**
 * Turns JEI's item list and bookmark panels off, so they do not sit on top of NEI's (NEI is the GUI, JEI only supplies the
 * recipes). JEI's API for this ({@code IJeiFeatures.disableJeiGui}) only exists from JEI 30 on, so for older JEI the same
 * switch Ctrl+O flips is flipped here, by name: a JEI that looks different only costs a log line.
 * shortcut: JEI's own recipe keys (R / U over an item) are not switched off; rebind them in the controls if both open.
 */
final class JeiGui {

    private JeiGui() {
    }

    /**
     * JEI 19 draws its config, bookmark and history buttons whenever it has a valid screen, whatever the toggle says. A
     * screen handler that reports the whole screen as the GUI leaves JEI no room for any of it, so nothing is drawn.
     * JEI's own handler for container screens is registered by its own plugin, in an order we do not control, so the
     * handler map of its screen helper is changed once JEI is running.
     */
    @SuppressWarnings("unchecked")
    static void hide(Object screenHelper) {
        hide();
        try {
            final var field = screenHelper.getClass().getDeclaredField("guiScreenHandlers");
            field.setAccessible(true);
            final java.util.Map<Class<?>, Object> handlers = (java.util.Map<Class<?>, Object>) field.get(screenHelper);
            final mezz.jei.api.gui.handlers.IScreenHandler<net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>> whole = screen ->
                    screen.width <= 0 || screen.height <= 0 ? null
                            : new WholeScreen(screen.getClass(), screen.width, screen.height);
            handlers.put(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class, whole);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            NotEnoughItems.LOGGER.warn("NEI could not take JEI's buttons off the screen ({}); they may show behind NEI's", e.toString());
        }
    }

    /** A GUI as big as the screen: no room beside it for JEI's panels and buttons. */
    private record WholeScreen(Class<? extends net.minecraft.client.gui.screens.Screen> screenClass, int screenWidth, int screenHeight)
            implements mezz.jei.api.gui.handlers.IGuiProperties {

        @Override
        public int guiLeft() {
            return 0;
        }

        @Override
        public int guiTop() {
            return 0;
        }

        @Override
        public int guiXSize() {
            return screenWidth;
        }

        @Override
        public int guiYSize() {
            return screenHeight;
        }
    }

    private static void hide() {
        try {
            final Class<?> internal = Class.forName("mezz.jei.common.Internal");
            final Object state = internal.getMethod("getClientToggleState").invoke(null);
            final Class<?> api = Class.forName("mezz.jei.common.config.IClientToggleState");
            api.getMethod("setOverlayEnabled", boolean.class).invoke(state, false);
            api.getMethod("setBookmarkEnabled", boolean.class).invoke(state, false);
            NotEnoughItems.LOGGER.info("NEI turned JEI's item list and bookmark panels off");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            NotEnoughItems.LOGGER.warn("NEI could not turn JEI's panels off ({}); press Ctrl+O to hide JEI's item list", e.toString());
        }
    }
}
