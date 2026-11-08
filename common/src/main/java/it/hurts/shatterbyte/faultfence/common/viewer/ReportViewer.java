package it.hurts.shatterbyte.faultfence.common.viewer;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import it.hurts.shatterbyte.faultfence.common.FaultFence;
import it.hurts.shatterbyte.faultfence.common.report.Reports;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReportViewer {
    private static final Gson GSON = new Gson();
    private static HttpServer server;
    private static ExecutorService executor;
    private static URI address;

    public static synchronized URI start() throws IOException {
        if (server != null)
            return address;

        try (var resource = ReportViewer.class.getResourceAsStream("/assets/faultfence/viewer/report-viewer.html")) {
            var page = new String(resource.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("data-report-api=\"\"", "data-report-api=\"reports\"")
                    .getBytes(StandardCharsets.UTF_8);

            var directory = FaultFence.reportDirectory.toAbsolutePath().normalize();
            var basePath = "/" + UUID.randomUUID() + "/";
            var httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var requestExecutor = Executors.newVirtualThreadPerTaskExecutor();

            httpServer.createContext(basePath, exchange -> handle(exchange, directory, basePath, page));
            httpServer.setExecutor(requestExecutor);
            httpServer.start();

            server = httpServer;
            executor = requestExecutor;
            address = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + basePath);

            return address;
        }
    }

    public static synchronized void stop() {
        if (server == null)
            return;

        server.stop(0);
        executor.shutdownNow();
        server = null;
        executor = null;
        address = null;
    }

    private static void handle(HttpExchange exchange, Path directory, String basePath, byte[] page) throws IOException {
        try (exchange) {
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.getResponseHeaders().set("Allow", "GET");
                send(exchange, 405, "text/plain", "Method not allowed".getBytes(StandardCharsets.UTF_8));

                return;
            }

            var path = exchange.getRequestURI().getPath();

            if (path.equals(basePath)) {
                send(exchange, 200, "text/html", page);

                return;
            }

            if (path.equals(basePath + "reports")) {
                send(exchange, 200, "application/json", GSON.toJson(listReports(directory)).getBytes(StandardCharsets.UTF_8));

                return;
            }

            if (path.startsWith(basePath + "reports/")) {
                var file = directory.resolve(path.substring((basePath + "reports/").length())).normalize();

                if (directory.equals(file.getParent()) && file.toString().endsWith(".json")) {
                    var report = Reports.latest();

                    if (report != null && report.fileName().equals(file.getFileName().toString())) {
                        send(exchange, 200, "application/json", report.json().getBytes(StandardCharsets.UTF_8));

                        return;
                    }

                    if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                        send(exchange, 200, "application/json", Files.readAllBytes(file));

                        return;
                    }
                }
            }

            send(exchange, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static List<SavedReport> listReports(Path directory) throws IOException {
        var reports = new ArrayList<SavedReport>();

        if (!Files.isDirectory(directory))
            return reports;

        try (var files = Files.list(directory)) {
            for (var file : files.toList()) {
                var name = file.getFileName().toString();

                if (!name.endsWith(".json") || name.startsWith("latest-"))
                    continue;

                var attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

                if (attributes.isRegularFile())
                    reports.add(new SavedReport(name, attributes.lastModifiedTime().toMillis()));
            }
        }

        reports.sort(Comparator.comparingLong(SavedReport::modifiedAt).reversed().thenComparing(SavedReport::name));

        return reports;
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private record SavedReport(String name, long modifiedAt) {
    }
}
