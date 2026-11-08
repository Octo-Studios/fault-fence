package it.hurts.shatterbyte.faultfence.common.report;

import com.google.gson.GsonBuilder;
import it.hurts.shatterbyte.faultfence.common.FaultFence;
import it.hurts.shatterbyte.faultfence.common.config.RecoveryCategory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class LoadReport {
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("Fault Fence reports").daemon().factory());

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(WRITER::close, "Fault Fence report shutdown"));
    }

    private final String startedAt = Instant.now().toString();
    private final String id = startedAt.replace(':', '-');
    private final String scope;
    private final Map<IssueKey, Issue> issues = new LinkedHashMap<>();
    private final Map<String, Set<Source>> sources = new LinkedHashMap<>();
    private final Map<String, Set<String>> references = new LinkedHashMap<>();
    private String status = "LOADING";
    private String finishedAt = "";
    private String failure = "";
    private boolean saveQueued;

    public LoadReport(String scope) {
        this.scope = scope;
    }

    public synchronized void describe(ResourceOrigin origin) {
        if (origin.file().isEmpty() && origin.pack().isEmpty())
            return;

        sources.computeIfAbsent(origin.node(), key -> new LinkedHashSet<>()).add(new Source(origin.file(), origin.pack()));
    }

    public synchronized void references(ResourceOrigin origin, List<String> targets) {
        references.computeIfAbsent(origin.node(), key -> new LinkedHashSet<>()).addAll(targets);
    }

    public synchronized void add(ResourceOrigin origin, ReportAction action, String message, Throwable error, List<String> causedBy) {
        describe(origin);

        var causes = List.copyOf(causedBy);
        var key = new IssueKey(origin.node(), origin.file(), origin.pack(), action, message, causes);

        if (issues.containsKey(key))
            return;

        var stack = new StringWriter();

        if (error != null)
            error.printStackTrace(new PrintWriter(stack));

        issues.put(key, new Issue(origin.category(), origin.type(), origin.id(), origin.file(), origin.pack(), action,
                message, causes, stack.toString()));

        if (!finishedAt.isEmpty())
            save();
    }

    public synchronized void finish(Throwable error) {
        if (status.equals("FAILED") || (!finishedAt.isEmpty() && error == null))
            return;

        finishedAt = Instant.now().toString();

        if (error != null)
            failure = error.toString();

        status = error != null ? "FAILED" : "COMPLETED";
        save();
    }

    public synchronized int issueCount() {
        return issues.size();
    }

    public synchronized String status() {
        return status;
    }

    public String scope() {
        return scope;
    }

    public synchronized List<String> ids() {
        return issues.values().stream().map(Issue::id).distinct().sorted().toList();
    }

    public synchronized Map<RecoveryCategory, Long> counts() {
        return issues.values().stream().collect(Collectors.groupingBy(Issue::category, LinkedHashMap::new, Collectors.counting()));
    }

    public synchronized List<String> explain(String id) {
        var lines = new ArrayList<String>();
        var selected = issues.values().stream().filter(issue -> issue.id().equals(id) || issue.node().equals(id)).toList();

        for (var issue : selected) {
            lines.add(issue.node() + " [" + issue.action() + "]");

            if (!issue.file().isEmpty() || !issue.pack().isEmpty())
                lines.add("File: " + issue.file() + "; pack: " + issue.pack());
            else
                for (var source : sources.getOrDefault(issue.node(), Set.of()))
                    lines.add("Contributing file: " + source.file() + "; pack: " + source.pack());

            lines.add(issue.message());
            explainCauses(issue, "", new LinkedHashSet<>(), lines);
        }

        return lines;
    }

    private void explainCauses(Issue issue, String indent, Set<String> path, List<String> lines) {
        if (!path.add(issue.node())) {
            lines.add(indent + "Cycle: " + String.join(" -> ", path) + " -> " + issue.node());

            return;
        }

        for (var cause : issue.causedBy()) {
            lines.add(indent + "Depends on: " + cause);

            for (var source : sources.getOrDefault(cause, Set.of()))
                lines.add(indent + "  File: " + source.file() + "; pack: " + source.pack());

            issues.values().stream().filter(candidate -> candidate.node().equals(cause)).forEach(candidate -> {
                lines.add(indent + "  " + candidate.action() + ": " + candidate.message());
                explainCauses(candidate, indent + "  ", path, lines);
            });
        }

        path.remove(issue.node());
    }

    public Path jsonFile() {
        return FaultFence.reportDirectory.resolve(scope + "-" + id + ".json");
    }

    public synchronized void save() {
        if (!FaultFence.CONFIG.writeReports || saveQueued)
            return;

        saveQueued = true;

        try {
            WRITER.execute(this::write);
        } catch (RuntimeException error) {
            saveQueued = false;

            FaultFence.LOGGER.error("Couldn't queue Fault Fence report", error);
        }
    }

    private synchronized Snapshot snapshot() {
        saveQueued = false;

        var affected = new LinkedHashSet<String>();

        issues.values().forEach(issue -> {
            affected.add(issue.node());
            affected.addAll(issue.causedBy());
        });

        var relevantSources = new LinkedHashMap<String, Set<Source>>();
        var relevantReferences = new LinkedHashMap<String, Set<String>>();

        affected.forEach(node -> {
            relevantSources.put(node, Set.copyOf(sources.getOrDefault(node, Set.of())));
            relevantReferences.put(node, Set.copyOf(references.getOrDefault(node, Set.of())));
        });

        return new Snapshot(scope, startedAt, finishedAt, status, failure, List.copyOf(issues.values()),
                Map.copyOf(relevantSources), Map.copyOf(relevantReferences));
    }

    private void write() {
        try {
            var json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(snapshot());

            Files.createDirectories(FaultFence.reportDirectory);
            Files.writeString(jsonFile(), json, StandardCharsets.UTF_8);

            if (Reports.latest() == this)
                Files.writeString(FaultFence.reportDirectory.resolve("latest-" + scope + ".json"), json, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException error) {
            FaultFence.LOGGER.error("Couldn't write Fault Fence report", error);
        }
    }

    private record IssueKey(String node, String file, String pack, ReportAction action, String message, List<String> causedBy) {
    }

    public record Issue(RecoveryCategory category, String type, String id, String file, String pack, ReportAction action,
            String message, List<String> causedBy, String exception) {
        public String node() {
            return type + " " + id;
        }
    }

    private record Source(String file, String pack) {
    }

    private record Snapshot(String scope, String startedAt, String finishedAt, String status, String failure, List<Issue> issues,
            Map<String, Set<Source>> sources, Map<String, Set<String>> references) {
    }
}
