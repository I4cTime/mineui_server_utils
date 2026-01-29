package com.mineui.serverutils.service;

import com.mineui.serverutils.config.AppConfig;
import com.mineui.serverutils.rcon.RconClient;
import com.mineui.serverutils.rcon.RconResponse;
import com.mineui.serverutils.runtime.ServerRuntime;
import com.mineui.serverutils.server.ServerProperties;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class StatusService {
    private final AppConfig config;
    private final ServerProperties serverProperties;
    private final ServerRuntime runtime;

    public StatusService(AppConfig config, ServerRuntime runtime) {
        this.config = config;
        this.serverProperties = new ServerProperties(config.serverPropertiesPath());
        this.runtime = runtime;
    }

    public Map<String, String> loadProperties() {
        return serverProperties.load();
    }

    public PlayersResult fetchPlayers() {
        if (config.rconPassword().isBlank()) {
            return PlayersResult.error("RCON_PASSWORD is not set");
        }
        try (RconClient client = RconClient.connect(config)) {
            RconResponse response = client.command("list");
            if (!response.ok()) {
                return PlayersResult.error(response.message());
            }
            return PlayersResult.ok(parsePlayers(response.message()));
        } catch (Exception ex) {
            return PlayersResult.error(ex.getMessage());
        }
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

    public CommandResult fetchTps() {
        if (config.rconPassword().isBlank()) {
            return CommandResult.error("RCON_PASSWORD is not set");
        }
        try (RconClient client = RconClient.connect(config)) {
            RconResponse response = client.command("tps");
            if (!response.ok()) {
                return CommandResult.error(response.message());
            }
            return CommandResult.ok(response.message());
        } catch (Exception ex) {
            return CommandResult.error(ex.getMessage());
        }
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

        PlayersResult playersResult = fetchPlayersDirect();
        if (playersResult.ok()) {
            players = playersResult.players();
        } else {
            ok = false;
            errors.add("players: " + playersResult.errorMessage());
        }

        CommandResult tpsResult = fetchTps();
        if (tpsResult.ok()) {
            tpsRaw = tpsResult.output();
        } else {
            ok = false;
            errors.add("tps: " + tpsResult.errorMessage());
        }

        MetricsSnapshot metrics = metricsSnapshot();
        if (metrics.ok()) {
            chunks = metrics.chunks();
            entities = metrics.entities();
            msptOne = metrics.msptOne();
            msptFive = metrics.msptFive();
            msptFifteen = metrics.msptFifteen();
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
                metrics.dimensions(),
                metrics.systemMetrics()
        );
    }

    private static List<String> parsePlayers(String response) {
        if (response == null || response.isBlank()) {
            return Collections.emptyList();
        }
        int colon = response.indexOf(':');
        if (colon < 0 || colon == response.length() - 1) {
            return Collections.emptyList();
        }
        String tail = response.substring(colon + 1).trim();
        if (tail.isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = tail.split(",");
        List<String> players = new ArrayList<>();
        for (String part : parts) {
            String name = part.trim();
            if (!name.isEmpty()) {
                players.add(name);
            }
        }
        return players;
    }

    public MetricsSnapshot metricsSnapshot() {
        if (runtime == null || !runtime.isActive()) {
            return MetricsSnapshot.error("Server runtime unavailable");
        }
        ServerRuntime.MetricsSnapshot snapshot = runtime.snapshot();
        SystemMetrics systemMetrics = SystemMetrics.capture();
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
                systemMetrics
        );
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

    public record CommandResult(boolean ok, String output, String errorMessage) {
        public static CommandResult ok(String output) {
            return new CommandResult(true, output, "");
        }

        public static CommandResult error(String message) {
            return new CommandResult(false, "", message == null ? "Unknown error" : message);
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
                String containerName = System.getenv("PODMAN_CONTAINER_NAME");
                com.mineui.serverutils.system.PodmanContainerStats.ContainerStats stats =
                        com.mineui.serverutils.system.PodmanContainerStats.read(containerName);
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
