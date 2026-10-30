package codechicken.nei.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import codechicken.nei.client.ClientHooks;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** NeoForge's cancellable {@code ScreenEvent.CharacterTyped.Pre} (Fabric API has no typed-character event). */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void nei$charTyped(long window, int codePoint, int modifiers, CallbackInfo ci) {
        final Minecraft mc = Minecraft.getInstance();
        final Screen screen = mc.screen;
        if (screen != null && window == mc.getWindow().getWindow() && ClientHooks.charTyped(screen, codePoint, modifiers)) {
            ci.cancel();
        }
    }
}
