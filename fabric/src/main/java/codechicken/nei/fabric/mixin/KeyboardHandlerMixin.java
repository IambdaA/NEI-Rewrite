package codechicken.nei.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import codechicken.nei.client.ClientHooks;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;

/** NeoForge's cancellable {@code ScreenEvent.CharacterTyped.Pre} (Fabric API has no typed-character event). */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void nei$charTyped(long window, CharacterEvent event, CallbackInfo ci) {
        final Minecraft mc = Minecraft.getInstance();
        final Screen screen = mc.gui.screen();
        if (screen != null && window == mc.getWindow().handle() && ClientHooks.charTyped(screen, event)) ci.cancel();
    }
}
