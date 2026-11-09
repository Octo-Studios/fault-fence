package it.hurts.shatterbyte.faultfence.common.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import it.hurts.shatterbyte.faultfence.common.recovery.AdvancementRecovery;
import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

@Mixin(SimpleCriterionTrigger.class)
public abstract class SimpleCriterionTriggerMixin {
    @WrapOperation(method = "trigger", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/predicates/ContextAwarePredicate;matches(Lnet/minecraft/world/level/storage/loot/LootContext;)Z"))
    private boolean faultfence$useRecoveredRequirement(ContextAwarePredicate condition, LootContext context, Operation<Boolean> original, @Local Map.Entry<PlayerAdvancements.TriggerInstanceKey, ?> entry) {
        return original.call(AdvancementRecovery.condition(context.getLevel().getServer().getAdvancements(), entry.getKey(), condition), context);
    }
}
