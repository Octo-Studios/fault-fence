package it.hurts.shatterbyte.faultfence.common.report;

import it.hurts.shatterbyte.faultfence.common.config.RecoveryCategory;
import it.hurts.shatterbyte.faultfence.common.mixin.TagEntryAccessor;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagLoader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TagDiagnostics {
    public static void inspect(LoadReport report, String directory, FileToIdConverter files, Map<Identifier, List<TagLoader.EntryWithSource>> builders) {
        for (var entry : builders.entrySet()) {
            var targets = new ArrayList<String>();

            for (var source : entry.getValue()) {
                var origin = origin(report, directory, files, entry.getKey(), source.source());

                report.describe(origin);

                var reference = (TagEntryAccessor) (Object) source.entry();

                targets.add((reference.faultfence$tag() ? directory : "element") + " " + reference.faultfence$id());
            }

            report.references(origin(report, directory, files, entry.getKey(), ""), targets);
        }

        var visited = new HashSet<Identifier>();
        var path = new ArrayList<Identifier>();

        builders.keySet().stream().sorted().forEach(id -> visit(report, directory, files, builders, id, visited, path));
    }

    private static void visit(LoadReport report, String directory, FileToIdConverter files, Map<Identifier, List<TagLoader.EntryWithSource>> builders, Identifier id, Set<Identifier> visited, List<Identifier> path) {
        var cycleStart = path.indexOf(id);

        if (cycleStart >= 0) {
            var cycle = new ArrayList<>(path.subList(cycleStart, path.size()));

            cycle.add(id);

            var description = "Cyclic tag reference: " + String.join(" -> ", cycle.stream().map(value -> "#" + value).toList());

            for (var i = 0; i + 1 < cycle.size(); i++) {
                var from = cycle.get(i);
                var to = cycle.get(i + 1);

                for (var source : builders.get(from)) {
                    var reference = (TagEntryAccessor) (Object) source.entry();

                    if (reference.faultfence$tag() && reference.faultfence$id().equals(to))
                        Reports.record(origin(report, directory, files, from, source.source()), ReportAction.OBSERVED, description, null, List.of(directory + " " + to));
                }
            }

            return;
        }

        if (!visited.add(id))
            return;

        path.add(id);

        for (var source : builders.get(id)) {
            var reference = (TagEntryAccessor) (Object) source.entry();

            if (reference.faultfence$tag() && builders.containsKey(reference.faultfence$id()))
                visit(report, directory, files, builders, reference.faultfence$id(), visited, path);
        }

        path.removeLast();
    }

    public static ResourceOrigin origin(LoadReport report, String directory, FileToIdConverter files, Identifier id, String pack) {
        return new ResourceOrigin(report, RecoveryCategory.TAGS, directory, id.toString(), files.idToFile(id).toString(), pack);
    }
}
