package za.co.afritiers.tagger.client.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import za.co.afritiers.tagger.client.TagFormatter;
import za.co.afritiers.tagger.client.ServerNameLabels;

@Mixin(EntityRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;", at = @At("RETURN"))
    private void afritiers$decorateName(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> callback) {
        // Run after every renderer has extracted/finalized its state, preserving visibility.
        EntityRenderState state = callback.getReturnValue();
        if (state.nameTag != null) {
            state.nameTag = entity instanceof Player player
                    ? TagFormatter.decorate(player.getGameProfile().name(), state.nameTag, false)
                    : ServerNameLabels.decorate(entity, state.nameTag);
        }
        ServerNameLabels.decorateTextDisplay(entity, state);
    }
}
