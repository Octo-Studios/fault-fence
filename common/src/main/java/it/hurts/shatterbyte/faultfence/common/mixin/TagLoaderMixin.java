package it.hurts.shatterbyte.faultfence.common.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.DataResult;
import it.hurts.shatterbyte.faultfence.common.FaultFence;
import it.hurts.shatterbyte.faultfence.common.config.RecoveryCategory;
import it.hurts.shatterbyte.faultfence.common.recovery.TagFallbacks;
import it.hurts.shatterbyte.faultfence.common.report.LoadReport;
import it.hurts.shatterbyte.faultfence.common.report.ReportAction;
import it.hurts.shatterbyte.faultfence.common.report.Reports;
import it.hurts.shatterbyte.faultfence.common.report.ResourceOrigin;
import it.hurts.shatterbyte.faultfence.common.report.TagDiagnostics;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.DependencySorter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Mixin(TagLoader.class)
public abstract class TagLoaderMixin<T> {
    @Shadow
    @Final
    private String directory;

    @Shadow
    @Final
    private TagLoader.ElementLookup<T> elementLookup;

    @Unique
    private LoadReport faultfence$report;

    @Unique
    private FileToIdConverter faultfence$files;

    @Unique
    private final TagFallbacks faultfence$fallbacks = new TagFallbacks();

    @WrapMethod(method = "load")
    private Map<Identifier, List<TagLoader.EntryWithSource>> faultfence$trackLoad(ResourceManager manager, Operation<Map<Identifier, List<TagLoader.EntryWithSource>>> original) {
        faultfence$report = Reports.get(manager);
        faultfence$files = FileToIdConverter.json(directory);
        faultfence$fallbacks.clear();

        return Reports.within(new ResourceOrigin(faultfence$report, RecoveryCategory.TAGS, directory, "", "", ""), () -> original.call(manager));
    }

    @WrapMethod(method = "build")
    private Map<Identifier, List<T>> faultfence$inspectTags(Map<Identifier, List<TagLoader.EntryWithSource>> builders, Operation<Map<Identifier, List<T>>> original) {
        if (RecoveryCategory.TAGS.enabled()) {
            if (FaultFence.CONFIG.fallbackToLowerPacks)
                faultfence$fallbacks.apply(builders, elementLookup);

            if (faultfence$report != null)
                TagDiagnostics.inspect(faultfence$report, directory, faultfence$files, builders);
        }

        return original.call(builders);
    }

    @WrapOperation(method = "build", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/DependencySorter;orderByDependencies(Ljava/util/function/BiConsumer;)V"))
    private <E extends DependencySorter.Entry<Identifier>> void faultfence$identifyTag(DependencySorter<Identifier, E> sorter, BiConsumer<Identifier, E> output, Operation<Void> original) {
        if (faultfence$report == null) {
            original.call(sorter, output);

            return;
        }

        original.call(sorter, (BiConsumer<Identifier, E>) (id, entry) -> Reports.within(TagDiagnostics.origin(faultfence$report, directory, faultfence$files, id, ""), () -> {
            output.accept(id, entry);

            return null;
        }));
    }

    @WrapOperation(method = "load", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StrictJsonParser;parse(Ljava/io/Reader;)Lcom/google/gson/JsonElement;"))
    private JsonElement faultfence$reportSyntax(Reader reader, Operation<JsonElement> original, @Local Resource resource, @Local(ordinal = 1) Identifier id) {
        var origin = Reports.origin(resource, RecoveryCategory.TAGS, directory, id.toString(), "");

        origin.report().describe(origin);

        try {
            return original.call(reader);
        } catch (RuntimeException error) {
            Reports.record(origin, ReportAction.SKIPPED_RESOURCE, error);

            throw error;
        }
    }

    @WrapOperation(method = "tryBuildTag", at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z"))
    private boolean faultfence$keepValidEntries(List<?> missing, Operation<Boolean> original) {
        if (!RecoveryCategory.TAGS.enabled())
            return original.call(missing);

        if (!original.call(missing))
            FaultFence.LOGGER.error("Kept valid tag contents; skipped missing references: {}", missing);

        return true;
    }

    @WrapOperation(method = "load", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/DataResult;getOrThrow()Ljava/lang/Object;"))
    private Object faultfence$reportInvalidFile(DataResult<?> result, Operation<Object> original, @Local Resource resource, @Local(ordinal = 1) Identifier id, @Local List<TagLoader.EntryWithSource> contents) {
        try {
            var file = (TagFile) original.call(result);

            if (FaultFence.CONFIG.fallbackToLowerPacks && RecoveryCategory.TAGS.enabled())
                faultfence$fallbacks.remember(id, Reports.origin(resource, RecoveryCategory.TAGS, directory, id.toString(), ""), file, contents);

            return file;
        } catch (RuntimeException error) {
            Reports.record(Reports.origin(resource, RecoveryCategory.TAGS, directory, id.toString(), ""), ReportAction.SKIPPED_RESOURCE, error);

            throw error;
        }
    }

    @WrapOperation(method = "tryBuildTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/tags/TagEntry;build(Lnet/minecraft/tags/TagEntry$Lookup;Ljava/util/function/Consumer;)Z"))
    private boolean faultfence$reportMissingEntry(TagEntry entry, TagEntry.Lookup<T> lookup, Consumer<T> output, Operation<Boolean> original, @Local TagLoader.EntryWithSource source) {
        var built = original.call(entry, lookup, output);

        if (!built && faultfence$report != null) {
            var current = Reports.current(RecoveryCategory.TAGS, directory, "");
            var origin = new ResourceOrigin(current.report(), current.category(), current.type(), current.id(), current.file(), source.source());
            var reference = (TagEntryAccessor) (Object) entry;
            var dependency = (reference.faultfence$tag() ? directory : "element") + " " + reference.faultfence$id();

            Reports.record(origin, RecoveryCategory.TAGS.enabled() ? ReportAction.SKIPPED_ENTRY : ReportAction.OBSERVED,
                    "Missing required tag reference: " + entry, null, List.of(dependency));
        }

        return built;
    }
}
