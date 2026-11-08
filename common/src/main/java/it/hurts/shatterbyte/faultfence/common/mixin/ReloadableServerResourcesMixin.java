package it.hurts.shatterbyte.faultfence.common.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.hurts.shatterbyte.faultfence.common.report.Reports;
import net.minecraft.commands.Commands;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.flag.FeatureFlagSet;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesMixin {
    @WrapMethod(method = "loadResources")
    private static CompletableFuture<ReloadableServerResources> faultfence$finishReport(ResourceManager manager, LayeredRegistryAccess<RegistryLayer> context, List<Registry.PendingTags<?>> tags, FeatureFlagSet flags, Commands.CommandSelection selection, PermissionSet permissions, Executor tasks, Executor main, Operation<CompletableFuture<ReloadableServerResources>> original) {
        Reports.activate(manager);

        var report = Reports.get(manager);

        return original.call(manager, context, tags, flags, selection, permissions, tasks, main)
                .whenComplete((result, error) -> report.finish(error));
    }
}
