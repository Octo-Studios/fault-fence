package it.hurts.shatterbyte.faultfence.common.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.DataResult;
import it.hurts.shatterbyte.faultfence.common.FaultFence;
import it.hurts.shatterbyte.faultfence.common.config.RecoveryCategory;
import it.hurts.shatterbyte.faultfence.common.report.ReportAction;
import it.hurts.shatterbyte.faultfence.common.report.Reports;
import net.minecraft.server.PlayerAdvancements;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Shadow
    @Final
    private Path playerSavePath;

    @WrapOperation(method = "load", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/DataResult;getOrThrow(Ljava/util/function/Function;)Ljava/lang/Object;"))
    private Object faultfence$keepValidProgress(DataResult<?> result, Function<String, RuntimeException> exception, Operation<Object> original) {
        if (!RecoveryCategory.ADVANCEMENTS.enabled() || result.error().isEmpty() || result.error().get().partialValue().isEmpty())
            return original.call(result, exception);

        return original.call(result.promotePartial(message -> {
            var origin = Reports.current(RecoveryCategory.ADVANCEMENTS, "advancement_progress", playerSavePath.toString());

            Reports.record(origin, ReportAction.SKIPPED_ENTRY, message, null, List.of());
            FaultFence.LOGGER.error("Kept readable advancement progress from {}: {}", playerSavePath, message);
        }), exception);
    }
}
