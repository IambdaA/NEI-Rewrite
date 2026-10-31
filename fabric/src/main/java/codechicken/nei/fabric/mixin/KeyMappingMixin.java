package codechicken.nei.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import codechicken.nei.fabric.KeyTable;
import net.minecraft.client.KeyMapping;

/**
 * Vanilla keeps one key mapping per key and refills that table in {@code resetMapping} (after the options load and after
 * every rebinding). NEI's GUI-only hotkeys must not be in it, see {@link KeyTable}.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {

    @Inject(method = "resetMapping", at = @At("TAIL"))
    private static void nei$afterReset(CallbackInfo ci) {
        KeyTable.fix();
    }
}
