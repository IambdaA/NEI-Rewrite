package codechicken.nei.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import codechicken.nei.client.ClientHooks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * NeoForge's {@code ContainerScreenEvent.Render.Background}: NEI's panels are drawn right after the screen's background and
 * before its slots, tooltips and potion effect boxes, so those end up over the panels. Placed like JEI's own hook: at the end
 * of the menu background and right after the in-world dimming gradient.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "renderMenuBackground(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("TAIL"))
    private void nei$menuBackground(GuiGraphics graphics, CallbackInfo ci) {
        ClientHooks.renderBackground((Screen) (Object) this, graphics);
    }

    @Inject(method = "renderTransparentBackground(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fillGradient(IIIIII)V", ordinal = 0, shift = At.Shift.AFTER))
    private void nei$transparentBackground(GuiGraphics graphics, CallbackInfo ci) {
        ClientHooks.renderBackground((Screen) (Object) this, graphics);
    }
}
