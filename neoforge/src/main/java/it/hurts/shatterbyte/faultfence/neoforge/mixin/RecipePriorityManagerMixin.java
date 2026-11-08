package it.hurts.shatterbyte.faultfence.neoforge.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import it.hurts.shatterbyte.faultfence.common.FaultFence;
import it.hurts.shatterbyte.faultfence.common.config.RecoveryCategory;
import it.hurts.shatterbyte.faultfence.common.recovery.PackFallbacks;
import it.hurts.shatterbyte.faultfence.common.report.ReportAction;
import it.hurts.shatterbyte.faultfence.common.report.Reports;
import it.hurts.shatterbyte.faultfence.common.report.ResourceOrigin;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.common.crafting.RecipePriorityManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(value = RecipePriorityManager.class, remap = false)
public abstract class RecipePriorityManagerMixin {
    @Unique
    private String faultfence$lowerPack;

    @WrapOperation(method = "prepare", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/ResourceManager;getResourceStack(Lnet/minecraft/resources/Identifier;)Ljava/util/List;"))
    private List<Resource> faultfence$beginLayers(ResourceManager manager, Identifier id, Operation<List<Resource>> original) {
        faultfence$lowerPack = null;

        return original.call(manager, id);
    }

    @WrapOperation(method = "prepare", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/GsonHelper;getAsBoolean(Lcom/google/gson/JsonObject;Ljava/lang/String;Z)Z"))
    private boolean faultfence$keepLowerPriorities(JsonObject json, String name, boolean defaultValue, Operation<Boolean> original, @Local Resource resource) {
        var replace = original.call(json, name, defaultValue);

        if (replace && RecoveryCategory.RECIPE_PRIORITIES.enabled() && FaultFence.CONFIG.fallbackToLowerPacks) {
            try {
                for (var entry : GsonHelper.getAsJsonObject(json, "entries").entrySet()) {
                    Identifier.parse(entry.getKey());
                    entry.getValue().getAsInt();
                }
            } catch (RuntimeException error) {
                if (faultfence$lowerPack != null) {
                    var origin = Reports.origin(resource, RecoveryCategory.RECIPE_PRIORITIES, "recipe_priority", "neoforge:recipe_priorities", "neoforge:recipe_priorities.json");

                    PackFallbacks.used(origin, new ResourceOrigin(origin.report(), origin.category(), origin.type(), origin.id(), origin.file(), faultfence$lowerPack), error);

                    throw error;
                }
            }
        }

        return replace;
    }

    @WrapOperation(method = "prepare", at = @At(value = "INVOKE", target = "Lcom/google/gson/JsonObject;entrySet()Ljava/util/Set;"))
    private Set<Map.Entry<String, JsonElement>> faultfence$keepValidPriorities(JsonObject entries, Operation<Set<Map.Entry<String, JsonElement>>> original, @Local Resource resource) {
        if (!RecoveryCategory.RECIPE_PRIORITIES.enabled())
            return original.call(entries);

        var valid = new LinkedHashSet<Map.Entry<String, JsonElement>>();

        for (var entry : original.call(entries)) {
            try {
                Identifier.parse(entry.getKey());
                entry.getValue().getAsInt();
                valid.add(entry);
            } catch (RuntimeException error) {
                Reports.record(Reports.origin(resource, RecoveryCategory.RECIPE_PRIORITIES, "recipe_priority", entry.getKey(), "neoforge:recipe_priorities.json"),
                        ReportAction.SKIPPED_ENTRY, error);
                FaultFence.LOGGER.error("Skipped recipe priority {} from pack {}", entry.getKey(), resource.sourcePackId(), error);
            }
        }

        faultfence$lowerPack = resource.sourcePackId();

        return valid;
    }
}
