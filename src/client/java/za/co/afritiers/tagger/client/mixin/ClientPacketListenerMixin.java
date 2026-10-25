package za.co.afritiers.tagger.client.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import za.co.afritiers.tagger.client.AfriTiersClient;

/** Proxy transfers and same-world practice teleports need not establish a new connection. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleRespawn", at = @At("TAIL"))
    private void afritiers$respawn(ClientboundRespawnPacket packet, CallbackInfo callback) {
        AfriTiersClient mod = AfriTiersClient.instance();
        if (mod != null) mod.requestWorldRefresh();
    }

    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void afritiers$teleport(ClientboundPlayerPositionPacket packet, CallbackInfo callback) {
        AfriTiersClient mod = AfriTiersClient.instance();
        if (mod != null) mod.requestWorldRefresh();
    }
}
