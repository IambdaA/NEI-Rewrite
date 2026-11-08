package codechicken.nei.smoketest.mixin;

import codechicken.nei.smoketest.NeiSmokeTest;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keys held by the driver's {@code press} count as down for vanilla's checks (hasShiftDown and friends). */
@Mixin(InputConstants.class)
public class InputConstantsMixin {
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void nei_smoketest$held(Window window, int key, CallbackInfoReturnable<Boolean> cir) {
        if (codechicken.nei.smoketest.SmokeCore.HELD.contains(key)) cir.setReturnValue(true);
    }
}
