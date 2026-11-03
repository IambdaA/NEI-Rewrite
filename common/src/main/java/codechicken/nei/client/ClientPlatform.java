package codechicken.nei.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.material.Fluid;

/** The client-only things that differ between the mod loaders (see {@link codechicken.nei.Platform}). */
public interface ClientPlatform {

    /** The sprite and ARGB tint a fluid is drawn with. */
    record FluidLook(TextureAtlasSprite sprite, int color) {}

    /**
     * Creates a hotkey. A {@code guiOnly} key only acts while a screen is open, so it must not take the key away from
     * the game's own controls (NeoForge: key conflict context GUI; Fabric: see the key mapping mixin).
     */
    KeyMapping newKey(String name, boolean guiOnly, int glfwKey);

    /** How to draw a fluid, or null when the loader knows nothing about it. */
    FluidLook fluidLook(Fluid fluid);

    /** The container screen's frame (position and size of the background) and the slot under the mouse. */
    int guiLeft(AbstractContainerScreen<?> screen);

    int guiTop(AbstractContainerScreen<?> screen);

    int guiWidth(AbstractContainerScreen<?> screen);

    int guiHeight(AbstractContainerScreen<?> screen);

    Slot hoveredSlot(AbstractContainerScreen<?> screen);

    Holder HOLDER = new Holder();

    /** Static holder, set by the loader's client entry point before any NEI class that needs it loads. */
    final class Holder {

        private ClientPlatform value;

        public void set(ClientPlatform p) {
            value = p;
        }

        public ClientPlatform get() {
            if (value == null) throw new IllegalStateException("NEI Rewrite: the loader module did not set the client platform");
            return value;
        }
    }
}
