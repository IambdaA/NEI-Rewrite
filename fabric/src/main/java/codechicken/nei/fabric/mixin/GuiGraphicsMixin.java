package codechicken.nei.fabric.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import codechicken.nei.client.ClientHooks;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;

/** NeoForge's {@code RenderTooltipEvent.Pre} cancel: while NEI's Alt box is up, vanilla's slot tooltip is dropped. */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {

    @Inject(method = "renderTooltipInternal", at = @At("HEAD"), cancellable = true)
    private void nei$oneTooltip(Font font, List<ClientTooltipComponent> lines, int x, int y, ClientTooltipPositioner positioner,
            CallbackInfo ci) {
        if (ClientHooks.suppressVanillaTooltip()) ci.cancel();
    }
}
