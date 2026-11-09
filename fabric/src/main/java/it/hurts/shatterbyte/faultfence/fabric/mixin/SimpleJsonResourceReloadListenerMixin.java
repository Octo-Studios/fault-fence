package it.hurts.shatterbyte.faultfence.fabric.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import it.hurts.shatterbyte.faultfence.common.recovery.JsonResourceRecovery;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.StrictJsonParser;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Map;
import java.util.Optional;

@Mixin(SimpleJsonResourceReloadListener.class)
public abstract class SimpleJsonResourceReloadListenerMixin {
    @WrapMethod(method = "scanDirectory(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/FileToIdConverter;Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;Ljava/util/Map;)V")
    private static <T> void faultfence$keepValidResources(ResourceManager manager, FileToIdConverter lister, DynamicOps<JsonElement> ops, Codec<T> codec, Map<Identifier, T> result, Operation<Void> original) {
        JsonResourceRecovery.scan(manager, lister, ops, codec.map(Optional::of), result, StrictJsonParser::parse, null);
    }
}
