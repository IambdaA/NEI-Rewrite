package codechicken.nei.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import codechicken.nei.client.ClientHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

/** NeoForge's cancellable {@code InputEvent.MouseScrollingEvent}: Ctrl + wheel with no screen open cycles creative+ rows. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void nei$scroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && window == mc.getWindow().handle() && ClientHooks.worldScroll(yOffset)) ci.cancel();
    }
}
