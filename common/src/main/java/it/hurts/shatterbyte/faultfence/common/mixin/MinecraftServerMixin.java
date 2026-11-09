package it.hurts.shatterbyte.faultfence.common.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.hurts.shatterbyte.faultfence.common.report.ReportNotifications;
import it.hurts.shatterbyte.faultfence.common.report.Reports;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
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
