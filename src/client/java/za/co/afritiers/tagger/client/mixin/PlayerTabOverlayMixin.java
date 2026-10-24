package za.co.afritiers.tagger.client.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import za.co.afritiers.tagger.client.TagFormatter;
import za.co.afritiers.tagger.client.TabColumns;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Shadow private java.util.List<PlayerInfo> getPlayerInfos() { throw new AssertionError(); }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void afritiers$beginColumns(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int width,
            net.minecraft.world.scores.Scoreboard scoreboard, net.minecraft.world.scores.Objective objective, CallbackInfo callback) {
        TabColumns.begin(getPlayerInfos());
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void afritiers$endColumns(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int width,
            net.minecraft.world.scores.Scoreboard scoreboard, net.minecraft.world.scores.Objective objective, CallbackInfo callback) {
        TabColumns.end();
    }

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void afritiers$decorateName(PlayerInfo info, CallbackInfoReturnable<Component> callback) {
        callback.setReturnValue(TagFormatter.decorate(info.getProfile().name(), callback.getReturnValue(), true));
    }
}
