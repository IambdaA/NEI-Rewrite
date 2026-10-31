package codechicken.nei.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import codechicken.nei.client.ClientHooks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * NeoForge's cancellable {@code ScreenEvent.Render.Pre} (lets NEI's tree window replace the screen) and the "after the
 * screen's own click" part of {@code MouseButtonPressed.Post}; Fabric has neither.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void nei$renderPre(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (ClientHooks.renderPre((Screen) (Object) this, graphics, mouseX, mouseY, partialTick)) ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void nei$clicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        ClientHooks.mousePressedPost((Screen) (Object) this, event);
    }
}
