package codechicken.nei.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/**
 * The container screen's frame and the slot under the mouse. In 1.20.1 these fields are protected; Forge patches public
 * getters in, Fabric widens the fields, so each loader answers (see {@link ClientPlatform}).
 */
final class Frame {

    private Frame() {}

    static int left(AbstractContainerScreen<?> screen) {
        return ClientPlatform.HOLDER.get().guiLeft(screen);
    }

    static int top(AbstractContainerScreen<?> screen) {
        return ClientPlatform.HOLDER.get().guiTop(screen);
    }

    static int width(AbstractContainerScreen<?> screen) {
        return ClientPlatform.HOLDER.get().guiWidth(screen);
    }

    static int height(AbstractContainerScreen<?> screen) {
        return ClientPlatform.HOLDER.get().guiHeight(screen);
    }

    static Slot hovered(AbstractContainerScreen<?> screen) {
        return ClientPlatform.HOLDER.get().hoveredSlot(screen);
    }
}
