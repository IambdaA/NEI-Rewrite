package codechicken.nei.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import codechicken.nei.recipe.ClientRecipes;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;

/** Forge's {@code RecipesUpdatedEvent}: the server sent its recipes (joining, /reload). */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleUpdateRecipes", at = @At("TAIL"))
    private void nei$recipesUpdated(ClientboundUpdateRecipesPacket packet, CallbackInfo ci) {
        ClientRecipes.recipesUpdated();
    }
}
