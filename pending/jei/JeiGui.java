package codechicken.nei.jei;

import codechicken.nei.NotEnoughItems;

/**
 * Turns JEI's item list and bookmark panels off, so they do not sit on top of NEI's (NEI is the GUI, JEI only supplies the
 * recipes). JEI's API for this ({@code IJeiFeatures.disableJeiGui}) only exists from JEI 30 on, so for older JEI the same
 * switch Ctrl+O flips is flipped here, by name: this class has no JEI imports and a JEI that looks different only
 * costs a log line.
 * shortcut: JEI's own recipe keys (R / U over an item) are not switched off; rebind them in the controls if both open.
 */
final class JeiGui {

    private JeiGui() {
    }

    static void hide() {
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
