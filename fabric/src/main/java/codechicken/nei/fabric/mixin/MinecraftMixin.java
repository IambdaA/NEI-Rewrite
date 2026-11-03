package codechicken.nei.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import codechicken.nei.client.ClientHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** NeoForge's cancellable {@code ScreenEvent.Opening}: the creative inventory is replaced by the extended one. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void nei$opening(Screen screen, CallbackInfo ci) {
        if (screen != null && ClientHooks.screenOpening(screen)) ci.cancel();
    }
}
