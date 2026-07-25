package com.mineui.serverutils.http;

import com.mineui.serverutils.service.StatusService;
import com.mineui.serverutils.service.StatusService.MetricsSnapshot;
import com.mineui.serverutils.service.StatusService.StatusSnapshot;
import com.mineui.serverutils.runtime.ServerRuntime;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

public final class ApiHandlers {
    private ApiHandlers() {}

    public static void register(HttpServer server, StatusService statusService, String authToken) {
        Filter authFilter = (authToken != null && !authToken.isBlank()) ? new AuthFilter(authToken) : null;

        // /health is intentionally unauthenticated so liveness probes work
        // without the token; it returns no server data.
        server.createContext("/health", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, "Method Not Allowed");
                return;
            }
            send(exchange, 200, "ok");
        });

        protectedContext(server, authFilter, "/properties", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, "Method Not Allowed");
                return;
            }
            Map<String, String> properties = statusService.loadProperties();
            sendJson(exchange, 200, JsonUtil.object(properties));
        });

        protectedContext(server, authFilter, "/players", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, "Method Not Allowed");
                return;
            }
            StatusService.PlayersResult result = statusService.fetchPlayersDirect();
            if (!result.ok()) {
                sendJson(exchange, 503, "{\"ok\":false,\"error\":" + JsonUtil.quote(result.errorMessage()) + "}");
                return;
            }
            String response = "{\"ok\":true,\"players\":" + JsonUtil.stringArray(result.players()) + "}";
            sendJson(exchange, 200, response);
        });

        protectedContext(server, authFilter, "/tps", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, "Method Not Allowed");
                return;
            }
            StatusService.TpsResult result = statusService.fetchTpsDirect();
            if (!result.ok()) {
                sendJson(exchange, 503, "{\"ok\":false,\"error\":" + JsonUtil.quote(result.errorMessage()) + "}");
                return;
            }
            StatusService.TpsSnapshot tps = result.tps();
            String response = "{\"ok\":true"
                    + ",\"one\":" + numberOrNull(tps.one())
                    + ",\"five\":" + numberOrNull(tps.five())
                    + ",\"fifteen\":" + numberOrNull(tps.fifteen())
                    + ",\"raw\":" + JsonUtil.quote(tps.raw()) + "}";
            sendJson(exchange, 200, response);
        });

        protectedContext(server, authFilter, "/metrics", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, "Method Not Allowed");
                return;
            }
            MetricsSnapshot snapshot = statusService.metricsSnapshot();
            String json = buildMetricsJson(snapshot);
            sendJson(exchange, snapshot.ok() ? 200 : 503, json);
        });

        protectedContext(server, authFilter, "/mods", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, "Method Not Allowed");
                return;
            }
            StatusService.ModListSnapshot snapshot = statusService.fetchMods();
            String json = buildModsJson(snapshot);
            sendJson(exchange, snapshot.ok() ? 200 : 503, json);
        });

        protectedContext(server, authFilter, "/status", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, "Method Not Allowed");
                return;
            }
            StatusSnapshot snapshot = statusService.snapshot();
            StringBuilder json = new StringBuilder();
            json.append('{');
            json.append("\"ok\":").append(snapshot.ok());
            json.append(",\"timestamp\":").append(JsonUtil.quote(snapshot.timestamp()));
            json.append(",\"properties\":").append(JsonUtil.object(snapshot.properties()));
            json.append(",\"players\":").append(JsonUtil.stringArray(snapshot.players()));
            json.append(",\"tpsRaw\":").append(JsonUtil.quote(snapshot.tpsRaw()));
            if (!snapshot.errors().isEmpty()) {
                json.append(",\"errors\":").append(JsonUtil.stringArray(snapshot.errors()));
            } else {
                json.append(",\"errors\":[]");
            }
            json.append(",\"chunks\":").append(intOrNull(snapshot.chunks()));
            json.append(",\"entities\":").append(intOrNull(snapshot.entities()));
            json.append(",\"mspt\":").append(buildMsptJson(snapshot.msptOne(), snapshot.msptFive(), snapshot.msptFifteen()));
            json.append(",\"dimensions\":").append(buildDimensionsJson(snapshot.dimensions()));
            json.append(",\"system\":").append(buildSystemJson(snapshot.systemMetrics()));
            json.append('}');
            sendJson(exchange, snapshot.ok() ? 200 : 207, json.toString());
        });
    }

    private static void protectedContext(HttpServer server, Filter authFilter, String path, HttpHandler handler) {
        HttpContext context = server.createContext(path, handler);
        if (authFilter != null) {
            context.getFilters().add(authFilter);
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }

    private static String buildMetricsJson(MetricsSnapshot snapshot) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"ok\":").append(snapshot.ok());
        if (!snapshot.ok()) {
            json.append(",\"error\":").append(JsonUtil.quote(snapshot.errorMessage()));
        }
        json.append(",\"chunks\":").append(intOrNull(snapshot.chunks()));
        json.append(",\"entities\":").append(intOrNull(snapshot.entities()));
        json.append(",\"players\":{");
        json.append("\"online\":").append(intOrNull(snapshot.playersOnline()));
        json.append(",\"max\":").append(intOrNull(snapshot.playersMax()));
        json.append('}');
        if (snapshot.tps() != null) {
            json.append(",\"tps\":{");
            json.append("\"one\":").append(numberOrNull(snapshot.tps().one()));
            json.append(",\"five\":").append(numberOrNull(snapshot.tps().five()));
            json.append(",\"fifteen\":").append(numberOrNull(snapshot.tps().fifteen()));
            json.append(",\"raw\":").append(JsonUtil.quote(snapshot.tps().raw()));
            json.append('}');
        } else {
            json.append(",\"tps\":null");
        }
        json.append(",\"mspt\":").append(buildMsptJson(snapshot.msptOne(), snapshot.msptFive(), snapshot.msptFifteen()));
        json.append(",\"dimensions\":").append(buildDimensionsJson(snapshot.dimensions()));
        json.append(",\"system\":").append(buildSystemJson(snapshot.systemMetrics()));
        json.append('}');
        return json.toString();
    }

    private static String numberOrNull(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return "null";
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private static String intOrNull(Integer value) {
        return value == null ? "null" : value.toString();
    }

    private static String buildMsptJson(Double one, Double five, Double fifteen) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"one\":").append(numberOrNull(one));
        json.append(",\"five\":").append(numberOrNull(five));
        json.append(",\"fifteen\":").append(numberOrNull(fifteen));
        json.append('}');
        return json.toString();
    }

    private static String buildDimensionsJson(Map<String, ServerRuntime.DimensionSnapshot> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return "{}";
        }
        StringBuilder json = new StringBuilder();
        json.append('{');
        boolean first = true;
        for (Map.Entry<String, ServerRuntime.DimensionSnapshot> entry : dimensions.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(JsonUtil.quote(entry.getKey()));
            json.append(":{");
            ServerRuntime.DimensionSnapshot value = entry.getValue();
            json.append("\"chunks\":").append(intOrNull(value == null ? null : value.chunks()));
            json.append(",\"entities\":").append(intOrNull(value == null ? null : value.entities()));
            json.append('}');
        }
        json.append('}');
        return json.toString();
    }

    private static String buildModsJson(StatusService.ModListSnapshot snapshot) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"ok\":").append(snapshot.ok());
        if (!snapshot.ok()) {
            json.append(",\"error\":").append(JsonUtil.quote(snapshot.errorMessage()));
        }
        json.append(",\"mods\":[");
        boolean first = true;
        for (StatusService.ModEntry mod : snapshot.mods()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{');
            json.append("\"id\":").append(JsonUtil.quote(mod.id()));
            json.append(",\"name\":").append(JsonUtil.quote(mod.name()));
            json.append(",\"version\":").append(JsonUtil.quote(mod.version()));
            json.append('}');
        }
        json.append(']');
        json.append('}');
        return json.toString();
    }

    private static String buildSystemJson(StatusService.SystemMetrics metrics) {
        if (metrics == null) {
            return "{}";
        }
        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"cpuPercent\":").append(numberOrNull(metrics.cpuPercent()));
        json.append(",\"mem\":{");
        json.append("\"totalBytes\":").append(longOrNull(metrics.memTotalBytes()));
        json.append(",\"usedBytes\":").append(longOrNull(metrics.memUsedBytes()));
        json.append('}');
        json.append(",\"net\":{");
        json.append("\"inputBytes\":").append(longOrNull(metrics.netInputBytes()));
        json.append(",\"outputBytes\":").append(longOrNull(metrics.netOutputBytes()));
        json.append('}');
        json.append(",\"disk\":{");
        json.append("\"readBytes\":").append(longOrNull(metrics.diskReadBytes()));
        json.append(",\"writeBytes\":").append(longOrNull(metrics.diskWriteBytes()));
        json.append('}');
        if (metrics.containerStats() != null) {
            json.append(",\"container\":").append(buildContainerJson(metrics.containerStats()));
        }
        json.append('}');
        return json.toString();
    }

    private static String buildContainerJson(StatusService.ContainerStats container) {
        if (container == null) {
            return "null";
        }
        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"name\":").append(JsonUtil.quote(container.name()));
        json.append(",\"cpuPercent\":").append(numberOrNull(container.cpuPercent()));
        json.append(",\"mem\":{");
        json.append("\"usedBytes\":").append(container.memUsedBytes());
        json.append(",\"limitBytes\":").append(container.memLimitBytes());
        json.append(",\"percent\":").append(numberOrNull(container.memPercent()));
        json.append('}');
        json.append(",\"net\":{");
        json.append("\"inputBytes\":").append(container.netInputBytes());
        json.append(",\"outputBytes\":").append(container.netOutputBytes());
        json.append('}');
        json.append(",\"block\":{");
        json.append("\"readBytes\":").append(container.blockReadBytes());
        json.append(",\"writeBytes\":").append(container.blockWriteBytes());
        json.append('}');
        json.append(",\"pids\":").append(container.pids());
        json.append('}');
        return json.toString();
    }

    private static String longOrNull(Long value) {
        return value == null ? "null" : value.toString();
    }
}
