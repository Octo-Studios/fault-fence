package it.hurts.shatterbyte.faultfence.common.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import it.hurts.shatterbyte.faultfence.common.config.RecoveryCategory;
import it.hurts.shatterbyte.faultfence.common.recovery.AdvancementRecovery;
import it.hurts.shatterbyte.faultfence.common.recovery.PackFallbacks;
import it.hurts.shatterbyte.faultfence.common.report.Reports;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.io.Reader;

@Mixin(targets = "net.minecraft.resources.RegistryLoadTask$PendingRegistration")
public abstract class PendingRegistrationMixin {
    @Shadow
    @Final
    private ResourceKey<?> key;

    @WrapMethod(method = "loadFromResource")
    private static <T> Either<T, Exception> faultfence$identifyResource(Decoder<T> decoder, RegistryOps<JsonElement> ops, ResourceKey<T> key, Resource resource, Operation<Either<T, Exception>> original) {
        return PackFallbacks.read(key, resource, candidate -> {
            var origin = Reports.origin(candidate, RecoveryCategory.registry(key), key.registry().toString(), key.identifier().toString(), "");

            origin.report().describe(origin);

            return Reports.within(origin, () -> AdvancementRecovery.read(key, origin, () -> original.call(decoder, ops, key, candidate)))
                    .ifRight(error -> Reports.rememberFailure(origin, error));
        });
    }

    @WrapOperation(method = "loadFromResource", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StrictJsonParser;parse(Ljava/io/Reader;)Lcom/google/gson/JsonElement;"))
    private static JsonElement faultfence$rememberParent(Reader reader, Operation<JsonElement> original) {
        var json = original.call(reader);

        AdvancementRecovery.parent(json);

        return json;
    }

    @WrapOperation(method = "loadFromResource", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/DataResult;getOrThrow()Ljava/lang/Object;"))
    private static Object faultfence$trackDecode(DataResult<?> result, Operation<Object> original) {
        PackFallbacks.decoded(result.isSuccess());

        return original.call(result);
    }

    @ModifyVariable(method = "<init>", at = @At("LOAD"), argsOnly = true)
    private RegistrationInfo faultfence$useSelectedPack(RegistrationInfo original) {
        return PackFallbacks.registrationInfo(key, original);
    }
}
