package it.hurts.shatterbyte.faultfence.common.report;

import it.hurts.shatterbyte.faultfence.common.config.RecoveryCategory;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public class Reports {
    private static final Map<ResourceManager, LoadReport> REPORTS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Exception, ResourceOrigin> FAILURES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final LoadReport UNSCOPED = new LoadReport("unscoped");
    private static final ThreadLocal<ResourceOrigin> CURRENT = new ThreadLocal<>();
    private static volatile LoadReport server;

    public static void begin(ResourceManager manager, PackType type) {
        if (type != PackType.SERVER_DATA)
            return;

        REPORTS.put(manager, new LoadReport("server"));
    }

    public static LoadReport get(ResourceManager manager) {
        return REPORTS.computeIfAbsent(manager, key -> new LoadReport("unscoped"));
    }

    public static void activate(ResourceManager manager) {
        var report = REPORTS.get(manager);

        if (report != null && report.scope().equals("server"))
            server = report;
    }

    public static LoadReport latest() {
        return server;
    }

    public static void attach(ResourceManager manager, String file, Resource resource) {
        var report = REPORTS.get(manager);

        if (report != null) {
            var tracked = (ReportedResource) resource;

            tracked.faultfence$origin(new ResourceOrigin(report, RecoveryCategory.OTHER, "resource", file, file, resource.sourcePackId()));
            tracked.faultfence$manager(manager);
        }
    }

    public static ResourceOrigin origin(Resource resource, RecoveryCategory category, String type, String id, String file) {
        var existing = ((ReportedResource) resource).faultfence$origin();
        var report = existing != null ? existing.report() : current(category, type, id).report();

        return new ResourceOrigin(report, category, type, id.isEmpty() && existing != null ? existing.id() : id,
                file.isEmpty() && existing != null ? existing.file() : file, resource.sourcePackId());
    }

    public static ResourceOrigin current(RecoveryCategory category, String type, String id) {
        var current = CURRENT.get();

        if (current != null)
            return new ResourceOrigin(current.report(), category, type, id.isEmpty() ? current.id() : id, current.file(), current.pack());

        var report = latest();

        return new ResourceOrigin(report != null ? report : UNSCOPED, category, type, id, "", "");
    }

    public static void rememberFailure(ResourceOrigin origin, Exception error) {
        FAILURES.put(error, origin);
    }

    public static ResourceOrigin origin(Exception error, RecoveryCategory category, String type, String id) {
        var origin = FAILURES.get(error);

        return origin == null ? current(category, type, id)
                : new ResourceOrigin(origin.report(), category, type, id, origin.file(), origin.pack());
    }

    public static <T> T within(ResourceOrigin origin, Supplier<T> operation) {
        var previous = CURRENT.get();

        CURRENT.set(origin);

        try {
            return operation.get();
        } finally {
            if (previous == null)
                CURRENT.remove();
            else
                CURRENT.set(previous);
        }
    }

    public static void record(ResourceOrigin origin, ReportAction action, String message, Throwable error, List<String> causedBy) {
        origin.report().add(origin, action, message, error, causedBy);
    }

    public static void record(ResourceOrigin origin, ReportAction action, Throwable error) {
        record(origin, action, error.toString(), error, List.of());
    }
}
