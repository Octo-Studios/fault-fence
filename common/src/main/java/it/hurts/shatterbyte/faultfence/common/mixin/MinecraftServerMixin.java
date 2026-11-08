package it.hurts.shatterbyte.faultfence.common.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.hurts.shatterbyte.faultfence.common.report.ReportNotifications;
import it.hurts.shatterbyte.faultfence.common.report.Reports;
import it.hurts.shatterbyte.faultfence.common.viewer.ReportViewer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "stopServer", at = @At("HEAD"))
    private void faultfence$stopViewer(CallbackInfo ci) {
        ReportViewer.stop();
    }

    @WrapMethod(method = "reloadResources")
    private CompletableFuture<Void> faultfence$notifyReload(Collection<String> packs, Operation<CompletableFuture<Void>> original) {
        var server = (MinecraftServer) (Object) this;
        var previous = Reports.latest();

        return original.call(packs).whenComplete((result, error) -> {
            var report = Reports.latest();

            if (report == previous)
                return;

            if (report != null && error != null)
                report.finish(error);

            server.execute(() -> server.getPlayerList().getPlayers().forEach(ReportNotifications::notify));
        });
    }
}
