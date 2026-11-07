package codechicken.nei.fabric.mixin;

import codechicken.nei.client.ClientHooks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's {@code ScreenEvent.Render.Background}: NEI's panels are drawn right after the screen's background and before
 * its slots, tooltips and potion effect boxes, so those end up over the panels. (Placed like JEI's own hook.)
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", ordinal = 0, shift = At.Shift.AFTER))
    private void nei$background(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ClientHooks.renderBackground((Screen) (Object) this, graphics, mouseX, mouseY, partialTick);
    }
}
