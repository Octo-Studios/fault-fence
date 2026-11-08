package it.hurts.shatterbyte.faultfence.common.mixin;

import com.google.common.collect.ImmutableMap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import it.hurts.shatterbyte.faultfence.common.recovery.AdvancementRecovery;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerAdvancementManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerAdvancementManager.class)
public abstract class ServerAdvancementManagerMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableMap$Builder;buildOrThrow()Lcom/google/common/collect/ImmutableMap;"))
    private ImmutableMap<Identifier, AdvancementHolder> faultfence$recoverChains(ImmutableMap.Builder<Identifier, AdvancementHolder> builder, Operation<ImmutableMap<Identifier, AdvancementHolder>> original, @Local(argsOnly = true) HolderLookup.Provider registries) {
        return AdvancementRecovery.prepare((ServerAdvancementManager) (Object) this, registries, original.call(builder));
    }
}
