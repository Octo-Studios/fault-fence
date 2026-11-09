package it.hurts.shatterbyte.faultfence.common.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.hurts.shatterbyte.faultfence.common.FaultFence;
import it.hurts.shatterbyte.faultfence.common.config.RecoveryCategory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoadReport {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
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

    public synchronized Summary summary() {
        var actions = new EnumMap<ReportAction, Integer>(ReportAction.class);
        var fallbackFiles = new LinkedHashSet<String>();
        var replacedSources = new LinkedHashSet<Source>();

        for (var action : ReportAction.values())
            actions.put(action, 0);

        for (var issue : issues.values()) {
            actions.merge(issue.action(), 1, Integer::sum);

            if (issue.action() == ReportAction.USED_LOWER_RESOURCE) {
                fallbackFiles.add(issue.file());
                replacedSources.add(new Source(issue.file(), issue.pack()));
            }
        }

        var modified = issues.values().stream().anyMatch(issue -> switch (issue.action()) {
            case SKIPPED_RESOURCE -> !fallbackFiles.contains(issue.file());
            case SKIPPED_ENTRY, USED_EMPTY_ENTRY -> !replacedSources.contains(new Source(issue.file(), issue.pack()));
            case BYPASSED_REFERENCE -> true;
            case USED_LOWER_RESOURCE, OBSERVED -> false;
        });
        var outcome = status;

        if (status.equals("COMPLETED")) {
            if (modified)
                outcome = "MODIFIED";
            else if (actions.get(ReportAction.USED_LOWER_RESOURCE) > 0)
                outcome = "FALLBACK";
            else if (actions.get(ReportAction.OBSERVED) > 0)
                outcome = "OBSERVED";
            else
                outcome = "CLEAN";
        }

        return new Summary(outcome, issues.size(), Map.copyOf(actions));
    }

    public String scope() {
        return scope;
    }

    public String fileName() {
        return scope + "-" + id + ".json";
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

        return new Snapshot(scope, startedAt, finishedAt, status, summary(), failure, List.copyOf(issues.values()),
                Map.copyOf(relevantSources), Map.copyOf(relevantReferences));
    }

    private void write() {
        try {
            Snapshot output;

            synchronized (this) {
                saveQueued = false;
                output = snapshot();
            }

            var json = GSON.toJson(output);

            Files.createDirectories(FaultFence.reportDirectory);

            if (!output.issues().isEmpty() || !output.failure().isEmpty())
                writeFile(FaultFence.reportDirectory.resolve(fileName()), json);

            if (Reports.latest() == this)
                writeFile(FaultFence.reportDirectory.resolve("latest-" + scope + ".json"), json);
        } catch (IOException | RuntimeException error) {
            FaultFence.LOGGER.error("Couldn't write Fault Fence report", error);
        }
    }

    private void writeFile(Path file, String json) throws IOException {
        var temporary = file.resolveSibling(file.getFileName() + ".tmp");

        Files.writeString(temporary, json, StandardCharsets.UTF_8);
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private record IssueKey(String node, String file, String pack, ReportAction action, String message, List<String> causedBy) {
    }

    public record Issue(RecoveryCategory category, String type, String id, String file, String pack, ReportAction action,
            String message, List<String> causedBy, String exception) {
        public String node() {
            return type + " " + id;
        }
    }

    public record Summary(String outcome, int issueCount, Map<ReportAction, Integer> actions) {
    }

    private record Source(String file, String pack) {
    }

    private record Snapshot(String scope, String startedAt, String finishedAt, String status, Summary summary, String failure, List<Issue> issues,
            Map<String, Set<Source>> sources, Map<String, Set<String>> references) {
    }
}
