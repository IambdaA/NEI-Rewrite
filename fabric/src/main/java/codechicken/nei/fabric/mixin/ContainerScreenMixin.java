package codechicken.nei.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import codechicken.nei.client.ClientHooks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/** NeoForge's cancellable {@code ScreenEvent.Render.Pre} (Fabric has none): lets NEI's tree window replace the screen. */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void nei$renderPre(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (ClientHooks.renderPre((Screen) (Object) this, graphics, mouseX, mouseY, partialTick)) ci.cancel();
    }
}
