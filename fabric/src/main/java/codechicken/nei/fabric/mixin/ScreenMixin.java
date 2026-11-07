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
 * before its slots, tooltips and potion effect boxes, so those end up over the panels. On 1.20.1 at the end of {@code Screen#renderBackground}.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

    /** 1.20.1: {@code renderBackground} draws the in-world dimming gradient (or the dirt) itself, so the panels go at its end. */
    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("TAIL"))
    private void nei$background(GuiGraphics graphics, CallbackInfo ci) {
        ClientHooks.renderBackground((Screen) (Object) this, graphics);
    }
}
