package com.mineui.serverutils.runtime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ServerRuntime {
    private MinecraftServer server;
    private ServerMetricsTracker tracker;

    public void attach(MinecraftServer server) {
        this.server = server;
        this.tracker = new ServerMetricsTracker();
    }

    public void detach() {
        this.server = null;
        this.tracker = null;
    }

    public boolean isActive() {
        return server != null;
    }

    public void recordTick() {
        if (tracker != null) {
            tracker.recordTick();
        }
    }

    public MetricsSnapshot snapshot() {
        if (server == null || tracker == null) {
            return MetricsSnapshot.empty();
        }
        Integer chunks = null;
        Integer entities = null;
        int online = server.getPlayerCount();
        int max = server.getMaxPlayers();
        Map<String, DimensionSnapshot> dimensions = new LinkedHashMap<>();

        for (ServerLevel level : server.getAllLevels()) {
            Integer levelChunks = getLoadedChunksCount(level);
            Integer levelEntities = getEntityCount(level);
            chunks = addNullable(chunks, levelChunks);
            entities = addNullable(entities, levelEntities);
            dimensions.put(
                    level.dimension().location().toString(),
                    new DimensionSnapshot(levelChunks, levelEntities)
            );
        }

        double one = tracker.getTpsOne();
        double five = tracker.getTpsFive();
        double fifteen = tracker.getTpsFifteen();
        String raw = String.format("TPS avg 1m/5m/15m: %.2f, %.2f, %.2f", one, five, fifteen);
        double msptOne = tracker.getMsptOne();
        double msptFive = tracker.getMsptFive();
        double msptFifteen = tracker.getMsptFifteen();

        return new MetricsSnapshot(
                one,
                five,
                fifteen,
                raw,
                msptOne,
                msptFive,
                msptFifteen,
                chunks,
                entities,
                online,
                max,
                dimensions
        );
    }

    private Integer addNullable(Integer current, Integer value) {
        if (value == null) {
            return current;
        }
        return current == null ? value : current + value;
    }

    private Integer getLoadedChunksCount(ServerLevel level) {
        try {
            ServerChunkCache chunkSource = level.getChunkSource();
            return chunkSource.getLoadedChunksCount();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer getEntityCount(ServerLevel level) {
        try {
            int count = 0;
            for (Object ignored : level.getEntities().getAll()) {
                count++;
            }
            return count;
        } catch (Exception ignored) {
            return null;
        }
    }

    public List<String> getPlayerNames() {
        if (server == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        server.getPlayerList().getPlayers().forEach(player -> names.add(player.getGameProfile().getName()));
        return names;
    }

    public record MetricsSnapshot(
            double tpsOne,
            double tpsFive,
            double tpsFifteen,
            String tpsRaw,
            double msptOne,
            double msptFive,
            double msptFifteen,
            Integer chunks,
            Integer entities,
            int playersOnline,
            int playersMax,
            Map<String, DimensionSnapshot> dimensions
    ) {
        public static MetricsSnapshot empty() {
            return new MetricsSnapshot(20.0, 20.0, 20.0, "", 0.0, 0.0, 0.0, null, null, 0, 0, Map.of());
        }
    }

    public record DimensionSnapshot(Integer chunks, Integer entities) {}
}
