package it.hurts.shatterbyte.faultfence.common.recovery;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import it.hurts.shatterbyte.faultfence.common.FaultFence;
import it.hurts.shatterbyte.faultfence.common.config.RecoveryCategory;
import it.hurts.shatterbyte.faultfence.common.report.ReportAction;
import it.hurts.shatterbyte.faultfence.common.report.Reports;
import it.hurts.shatterbyte.faultfence.common.report.ResourceOrigin;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class JsonResourceRecovery {
    public static <T> void scan(ResourceManager manager, FileToIdConverter lister, DynamicOps<JsonElement> ops, Decoder<Optional<T>> decoder, Map<Identifier, T> result, Function<Reader, JsonElement> parser, Consumer<Map<Identifier, JsonElement>> modifier) {
        var resources = lister.listMatchingResources(manager);

        if (modifier == null) {
            resources.forEach((file, resource) -> load(manager, lister, ops, decoder, result, parser, lister.fileToId(file), resource, null));

            return;
        }

        var jsons = new HashMap<Identifier, JsonElement>();
        var failed = new HashMap<Identifier, Resource>();

        resources.forEach((file, resource) -> {
            var id = lister.fileToId(file);

            try (var reader = resource.openAsReader()) {
                jsons.put(id, parser.apply(reader));
            } catch (Exception error) {
                failed.put(id, resource);
            }
        });

        modifier.accept(jsons);

        jsons.forEach((id, json) -> load(manager, lister, ops, decoder, result, parser, id, resources.get(lister.idToFile(id)), json));

        failed.forEach((id, resource) -> {
            if (!jsons.containsKey(id))
                load(manager, lister, ops, decoder, result, parser, id, resource, null);
        });
    }

    private static <T> void load(ResourceManager manager, FileToIdConverter lister, DynamicOps<JsonElement> ops, Decoder<Optional<T>> decoder, Map<Identifier, T> result, Function<Reader, JsonElement> parser, Identifier id, Resource resource, JsonElement json) {
        var registry = ResourceKey.<T>createRegistryKey(Identifier.withDefaultNamespace(lister.prefix()));
        var key = ResourceKey.create(registry, id);
        var category = RecoveryCategory.registry(key);

        Function<Resource, Either<Optional<T>, Exception>> read = candidate -> {
            var origin = candidate == null
                    ? new ResourceOrigin(Reports.get(manager), category, registry.identifier().toString(), id.toString(), lister.idToFile(id).toString(), "")
                    : Reports.origin(candidate, category, registry.identifier().toString(), id.toString(), "");

            origin.report().describe(origin);

            return Reports.within(origin, () -> AdvancementRecovery.read(key, origin, () -> {
                try {
                    JsonElement input;

                    if (candidate == resource && json != null)
                        input = json;
                    else {
                        try (var reader = candidate.openAsReader()) {
                            input = parser.apply(reader);
                        }
                    }

                    AdvancementRecovery.parent(input);

                    var decoded = decoder.parse(ops, input);

                    PackFallbacks.decoded(decoded.isSuccess());

                    return Either.<Optional<T>, Exception>left(decoded.getOrThrow());
                } catch (Exception error) {
                    return Either.<Optional<T>, Exception>right(new IllegalStateException("Failed to parse " + id + " from pack " + origin.pack(), error));
                }
            })).ifRight(error -> Reports.rememberFailure(origin, error));
        };
        var loaded = resource == null ? read.apply(null) : PackFallbacks.read(key, resource, read);

        loaded.ifLeft(value -> value.ifPresent(parsed -> {
            if (result.putIfAbsent(id, parsed) != null)
                throw new IllegalStateException("Duplicate data file ignored with ID " + id);
        })).ifRight(error -> {
            if (key.isFor(Registries.ADVANCEMENT) && category.enabled())
                AdvancementRecovery.skipped(manager, id, error);

            Reports.record(Reports.origin(error, category, registry.identifier().toString(), id.toString()), ReportAction.SKIPPED_RESOURCE, error);
            FaultFence.LOGGER.error("Skipped invalid entry {} in {}; valid entries remain loaded", id, registry.identifier(), error);
        });
    }
}
