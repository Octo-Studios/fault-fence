package it.hurts.shatterbyte.faultfence.common.mixin;

import it.hurts.shatterbyte.faultfence.common.report.ReportNotifications;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "placeNewPlayer", at = @At("RETURN"))
    private void faultfence$notifyAdmin(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        ReportNotifications.notify(player);
    }
}
