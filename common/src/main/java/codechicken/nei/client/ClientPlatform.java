package codechicken.nei.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;

/** The client-only things that differ between the mod loaders (see {@link codechicken.nei.Platform}). */
public interface ClientPlatform {

    /** The controls screen section the hotkeys go under. */
    KeyMapping.Category category(Identifier id);

    /**
     * Creates a hotkey. A {@code guiOnly} key only acts while a screen is open, so it must not take the key away from
     * the game's own controls (NeoForge: key conflict context GUI; Fabric: see the key mapping mixin).
     */
    KeyMapping newKey(String name, boolean guiOnly, int glfwKey);

    /** The key a mapping is bound to (vanilla has no accessor; NeoForge and Fabric API each add one). */
    com.mojang.blaze3d.platform.InputConstants.Key boundKey(KeyMapping mapping);

    /** The ARGB tint a fluid's still sprite is drawn with. */
    int fluidColor(Fluid fluid, FluidModel model);

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
