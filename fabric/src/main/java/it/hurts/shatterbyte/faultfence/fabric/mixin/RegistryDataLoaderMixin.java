package it.hurts.shatterbyte.faultfence.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.hurts.shatterbyte.faultfence.common.report.Reports;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(RegistryDataLoader.class)
public abstract class RegistryDataLoaderMixin {
    @WrapMethod(method = "load(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Ljava/util/List;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;")
    private static CompletableFuture<RegistryAccess.Frozen> faultfence$reportRegistries(ResourceManager manager, List<HolderLookup.RegistryLookup<?>> context, List<RegistryDataLoader.RegistryData<?>> registries, Executor executor, Operation<CompletableFuture<RegistryAccess.Frozen>> original) {
        Reports.activate(manager);

        var report = Reports.get(manager);

        return original.call(manager, context, registries, executor).whenComplete((loaded, error) -> {
            if (error != null)
                report.finish(error);
        });
    }
}
