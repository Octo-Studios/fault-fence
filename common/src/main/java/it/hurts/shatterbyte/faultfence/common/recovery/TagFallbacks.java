package it.hurts.shatterbyte.faultfence.common.recovery;

import it.hurts.shatterbyte.faultfence.common.report.ReportAction;
import it.hurts.shatterbyte.faultfence.common.report.Reports;
import it.hurts.shatterbyte.faultfence.common.report.ResourceOrigin;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TagFallbacks {
    private final Map<Identifier, List<Layer>> layers = new HashMap<>();
    private final Map<Identifier, Pending> pending = new HashMap<>();

    public void clear() {
        layers.clear();
        pending.clear();
    }

    public void remember(Identifier id, ResourceOrigin origin, TagFile file, List<TagLoader.EntryWithSource> contents) {
        var previous = pending.put(id, new Pending(origin, file.replace(), contents.size()));

        if (previous != null)
            finish(id, previous, contents);
    }

    private void finish(Identifier id, Pending pending, List<TagLoader.EntryWithSource> contents) {
        layers.computeIfAbsent(id, key -> new ArrayList<>()).add(new Layer(pending.origin(), pending.replace(),
                List.copyOf(contents.subList(pending.replace() ? 0 : pending.offset(), contents.size()))));
    }

    public <T> void apply(Map<Identifier, List<TagLoader.EntryWithSource>> builders, TagLoader.ElementLookup<T> lookup) {
        pending.forEach((id, pending) -> finish(id, pending, builders.get(id)));

        for (var entry : layers.entrySet()) {
            var contents = new ArrayList<TagLoader.EntryWithSource>();
            var valid = true;

            ResourceOrigin lower = null;

            for (var layer : entry.getValue()) {
                var missing = layer.entries().stream()
                        .filter(source -> !source.entry().verifyIfPresent(id -> lookup.get(id, true).isPresent(), builders::containsKey)).toList();

                if (!missing.isEmpty() && lower != null) {
                    PackFallbacks.used(layer.origin(), lower, null);

                    for (var source : missing)
                        Reports.record(layer.origin(), ReportAction.SKIPPED_ENTRY, "Missing required tag reference: " + source.entry(), null, List.of());

                    continue;
                }

                if (layer.replace()) {
                    contents.clear();
                    valid = true;
                }

                contents.addAll(layer.entries());
                valid &= missing.isEmpty();

                if (valid)
                    lower = layer.origin();
            }

            builders.put(entry.getKey(), contents);
        }

        clear();
    }

    private record Pending(ResourceOrigin origin, boolean replace, int offset) {
    }

    private record Layer(ResourceOrigin origin, boolean replace, List<TagLoader.EntryWithSource> entries) {
    }
}
