package com.mineui.serverutils.service;

import com.mineui.serverutils.config.AppConfig;
import com.mineui.serverutils.runtime.ServerRuntime;
import com.mineui.serverutils.server.ServerProperties;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StatusService {
    /**
     * server.properties keys never returned over the API. rcon.password is the
     * critical one — leaking it hands out full RCON command execution; the ports
     * are withheld to avoid disclosing internal topology.
     */
    private static final Set<String> REDACTED_PROPERTY_KEYS =
            Set.of("rcon.password", "rcon.port", "query.port");

    private static final long SYSTEM_CACHE_TTL_NANOS = 1_000_000_000L; // 1s

    private final ServerProperties serverProperties;
    private final ServerRuntime runtime;

    private SystemMetrics cachedSystemMetrics;
    private long cachedSystemMetricsAtNanos;

    public StatusService(AppConfig config, ServerRuntime runtime) {
        this.serverProperties = new ServerProperties(config.serverPropertiesPath());
        this.runtime = runtime;
    }

    /** Loads server.properties with sensitive keys stripped out. */
    public Map<String, String> loadProperties() {
        Map<String, String> raw = serverProperties.load();
        Map<String, String> safe = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            if (!REDACTED_PROPERTY_KEYS.contains(entry.getKey())) {
                safe.put(entry.getKey(), entry.getValue());
            }
        }
        return safe;
    }

    public PlayersResult fetchPlayersDirect() {
        if (runtime == null || !runtime.isActive()) {
            return PlayersResult.error("Server runtime unavailable");
        }
        return PlayersResult.ok(runtime.getPlayerNames());
    }

    public ModListSnapshot fetchMods() {
        try {
            List<ModEntry> mods = new ArrayList<>();
            for (IModInfo mod : ModList.get().getMods()) {
                mods.add(new ModEntry(
                        mod.getModId(),
                        mod.getDisplayName(),
                        mod.getVersion().toString()
                ));
            }
            return ModListSnapshot.ok(mods);
        } catch (Exception ex) {
            return ModListSnapshot.error(ex.getMessage());
        }
    }

    /**
     * TPS read from the in-JVM tick tracker. The mod runs inside the server, so
     * this is real per-tick timing — no RCON round-trip and no dependency on a
     * {@code tps} command that vanilla Forge does not have.
     */
    public TpsResult fetchTpsDirect() {
        if (runtime == null || !runtime.isActive()) {
            return TpsResult.error("Server runtime unavailable");
        }
        ServerRuntime.MetricsSnapshot s = runtime.snapshot();
        return TpsResult.ok(new TpsSnapshot(s.tpsOne(), s.tpsFive(), s.tpsFifteen(), s.tpsRaw()));
    }

    public StatusSnapshot snapshot() {
        Map<String, String> properties = loadProperties();
        List<String> errors = new ArrayList<>();

        List<String> players = Collections.emptyList();
        String tpsRaw = "";
        boolean ok = true;
        Integer chunks = null;
        Integer entities = null;
        Double msptOne = null;
        Double msptFive = null;
        Double msptFifteen = null;
        Map<String, ServerRuntime.DimensionSnapshot> dimensions = Map.of();
        SystemMetrics systemMetrics = SystemMetrics.empty();

        PlayersResult playersResult = fetchPlayersDirect();
        if (playersResult.ok()) {
            players = playersResult.players();
        } else {
            ok = false;
            errors.add("players: " + playersResult.errorMessage());
        }

        MetricsSnapshot metrics = metricsSnapshot();
        if (metrics.ok()) {
            chunks = metrics.chunks();
            entities = metrics.entities();
            msptOne = metrics.msptOne();
            msptFive = metrics.msptFive();
            msptFifteen = metrics.msptFifteen();
            dimensions = metrics.dimensions();
            systemMetrics = metrics.systemMetrics();
            if (metrics.tps() != null) {
                tpsRaw = metrics.tps().raw();
            }
        } else {
            ok = false;
            errors.add("metrics: " + metrics.errorMessage());
        }

        return new StatusSnapshot(
                ok,
                Instant.now().toString(),
                properties,
                players,
                tpsRaw,
                errors,
                chunks,
                entities,
                msptOne,
                msptFive,
                msptFifteen,
                dimensions,
                systemMetrics
        );
    }

    public MetricsSnapshot metricsSnapshot() {
        if (runtime == null || !runtime.isActive()) {
            return MetricsSnapshot.error("Server runtime unavailable");
        }
        ServerRuntime.MetricsSnapshot snapshot = runtime.snapshot();
        return MetricsSnapshot.ok(
                new TpsSnapshot(
                        snapshot.tpsOne(),
                        snapshot.tpsFive(),
                        snapshot.tpsFifteen(),
                        snapshot.tpsRaw()
                ),
                snapshot.chunks(),
                snapshot.entities(),
                snapshot.playersOnline(),
                snapshot.playersMax(),
                snapshot.msptOne(),
                snapshot.msptFive(),
                snapshot.msptFifteen(),
                snapshot.dimensions(),
                systemMetricsCached()
        );
    }

    /**
     * System metrics are OS/cgroup probes; cache them briefly so a client
     * hammering {@code /metrics} or {@code /status} cannot drive repeated file
     * reads faster than they meaningfully change.
     */
    private synchronized SystemMetrics systemMetricsCached() {
        long now = System.nanoTime();
        if (cachedSystemMetrics != null && (now - cachedSystemMetricsAtNanos) < SYSTEM_CACHE_TTL_NANOS) {
            return cachedSystemMetrics;
        }
        cachedSystemMetrics = SystemMetrics.capture();
        cachedSystemMetricsAtNanos = now;
        return cachedSystemMetrics;
    }

    public record PlayersResult(boolean ok, List<String> players, String errorMessage) {
        public static PlayersResult ok(List<String> players) {
            return new PlayersResult(true, players, "");
        }

        public static PlayersResult error(String message) {
            return new PlayersResult(false, Collections.emptyList(), message == null ? "Unknown error" : message);
        }
    }

    public record ModEntry(String id, String name, String version) {}

    public record ModListSnapshot(boolean ok, String errorMessage, List<ModEntry> mods) {
        public static ModListSnapshot ok(List<ModEntry> mods) {
            return new ModListSnapshot(true, "", mods);
        }

        public static ModListSnapshot error(String message) {
            return new ModListSnapshot(false, message == null ? "Unknown error" : message, Collections.emptyList());
        }
    }

    public record TpsResult(boolean ok, TpsSnapshot tps, String errorMessage) {
        public static TpsResult ok(TpsSnapshot tps) {
            return new TpsResult(true, tps, "");
        }

        public static TpsResult error(String message) {
            return new TpsResult(false, null, message == null ? "Unknown error" : message);
        }
    }

    public record StatusSnapshot(
            boolean ok,
            String timestamp,
            Map<String, String> properties,
            List<String> players,
            String tpsRaw,
            List<String> errors,
            Integer chunks,
            Integer entities,
            Double msptOne,
            Double msptFive,
            Double msptFifteen,
            Map<String, ServerRuntime.DimensionSnapshot> dimensions,
            SystemMetrics systemMetrics
    ) {}

    public record TpsSnapshot(double one, double five, double fifteen, String raw) {}

    public record MetricsSnapshot(
            boolean ok,
            String errorMessage,
            TpsSnapshot tps,
            Integer chunks,
            Integer entities,
            Integer playersOnline,
            Integer playersMax,
            Double msptOne,
            Double msptFive,
            Double msptFifteen,
            Map<String, ServerRuntime.DimensionSnapshot> dimensions,
            SystemMetrics systemMetrics
    ) {
        public static MetricsSnapshot ok(
                TpsSnapshot tps,
                Integer chunks,
                Integer entities,
                Integer playersOnline,
                Integer playersMax,
                Double msptOne,
                Double msptFive,
                Double msptFifteen,
                Map<String, ServerRuntime.DimensionSnapshot> dimensions,
                SystemMetrics systemMetrics
        ) {
            return new MetricsSnapshot(
                    true,
                    "",
                    tps,
                    chunks,
                    entities,
                    playersOnline,
                    playersMax,
                    msptOne,
                    msptFive,
                    msptFifteen,
                    dimensions,
                    systemMetrics
            );
        }

        public static MetricsSnapshot error(String message) {
            return new MetricsSnapshot(
                    false,
                    message == null ? "Unknown error" : message,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of(),
                    SystemMetrics.empty()
            );
        }
    }

    public record ContainerStats(
            String name,
            double cpuPercent,
            long memUsedBytes,
            long memLimitBytes,
            double memPercent,
            long netInputBytes,
            long netOutputBytes,
            long blockReadBytes,
            long blockWriteBytes,
            int pids
    ) {}

    public record SystemMetrics(
            Double cpuPercent,
            Long memTotalBytes,
            Long memUsedBytes,
            Long netInputBytes,
            Long netOutputBytes,
            Long diskReadBytes,
            Long diskWriteBytes,
            ContainerStats containerStats
    ) {
        public static SystemMetrics empty() {
            return new SystemMetrics(null, null, null, null, null, null, null, null);
        }

        public static SystemMetrics capture() {
            Double cpuPercent = null;
            Long memTotal = null;
            Long memUsed = null;
            Long netIn = null;
            Long netOut = null;
            Long diskRead = null;
            Long diskWrite = null;
            ContainerStats containerStats = null;

            try {
                java.lang.management.OperatingSystemMXBean baseBean =
                        ManagementFactory.getOperatingSystemMXBean();
                if (baseBean instanceof com.sun.management.OperatingSystemMXBean osBean) {
                    double load = osBean.getSystemCpuLoad();
                    if (load >= 0) {
                        cpuPercent = load * 100.0;
                    }
                    long total = osBean.getTotalPhysicalMemorySize();
                    long free = osBean.getFreePhysicalMemorySize();
                    if (total > 0) {
                        memTotal = total;
                        memUsed = Math.max(0, total - free);
                    }
                }
            } catch (Exception ignored) {
            }

            try {
                long[] net = com.mineui.serverutils.system.NetStats.read();
                if (net != null && net.length == 2) {
                    netIn = net[0];
                    netOut = net[1];
                }
            } catch (Exception ignored) {
            }

            try {
                long[] disk = com.mineui.serverutils.system.DiskStats.read();
                if (disk != null && disk.length == 2) {
                    diskRead = disk[0];
                    diskWrite = disk[1];
                }
            } catch (Exception ignored) {
            }

            try {
                com.mineui.serverutils.system.ContainerStatsReader.ContainerStats stats =
                        com.mineui.serverutils.system.ContainerStatsReader.read();
                if (stats != null) {
                    containerStats = new ContainerStats(
                            stats.name(),
                            stats.cpuPercent(),
                            stats.memUsedBytes(),
                            stats.memLimitBytes(),
                            stats.memPercent(),
                            stats.netInputBytes(),
                            stats.netOutputBytes(),
                            stats.blockReadBytes(),
                            stats.blockWriteBytes(),
                            stats.pids()
                    );
                }
            } catch (Exception ignored) {
            }

            return new SystemMetrics(cpuPercent, memTotal, memUsed, netIn, netOut, diskRead, diskWrite, containerStats);
        }
    }
}
