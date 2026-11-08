package it.hurts.shatterbyte.faultfence.common.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.hurts.shatterbyte.faultfence.common.FaultFence;
import it.hurts.shatterbyte.faultfence.common.config.RecoveryCategory;
import it.hurts.shatterbyte.faultfence.common.recovery.AdvancementRecovery;
import it.hurts.shatterbyte.faultfence.common.report.ReportAction;
import it.hurts.shatterbyte.faultfence.common.report.Reports;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceManagerRegistryLoadTask;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;
import java.util.stream.Stream;

@Mixin(RegistryLoadTask.class)
public abstract class RegistryLoadTaskMixin {
    @Shadow
    @Final
    protected Map<ResourceKey<?>, Exception> loadingErrors;

    @Shadow
    @Final
    protected RegistryDataLoader.RegistryData<?> data;

    @Shadow
    @Final
    private WritableRegistry<?> registry;

    @WrapMethod(method = "registerElements")
    private void faultfence$keepValidEntries(Stream<?> elements, Operation<Void> original) {
        original.call(elements);

        var category = RecoveryCategory.registry(data.key());

        if (!category.enabled() || !((Object) this instanceof ResourceManagerRegistryLoadTask<?>))
            return;

        loadingErrors.entrySet().removeIf(entry -> {
            var key = entry.getKey();

            if (!key.isFor(data.key()))
                return false;

            if (category == RecoveryCategory.ADVANCEMENTS)
                AdvancementRecovery.skipped(registry, key.identifier(), entry.getValue());

            Reports.record(Reports.origin(entry.getValue(), category, key.registry().toString(), key.identifier().toString()),
                    ReportAction.SKIPPED_RESOURCE, entry.getValue());
            FaultFence.LOGGER.error("Skipped invalid entry {} in {}; valid entries remain registered", key.identifier(), key.registry(), entry.getValue());

            return true;
        });
    }
}
