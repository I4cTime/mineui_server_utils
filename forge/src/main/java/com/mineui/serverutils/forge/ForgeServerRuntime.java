package com.mineui.serverutils.forge;

import com.mineui.serverutils.runtime.ServerRuntime;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Forge implementation of {@link ServerRuntime}, reading server internals in-JVM. */
public final class ForgeServerRuntime implements ServerRuntime {
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

    public void recordTick() {
        if (tracker != null) {
            tracker.recordTick();
        }
    }

    @Override
    public boolean isActive() {
        return server != null;
    }

    @Override
    public List<String> getPlayerNames() {
        if (server == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        server.getPlayerList().getPlayers().forEach(player -> names.add(player.getGameProfile().getName()));
        return names;
    }

    @Override
    public List<ModInfo> listMods() {
        List<ModInfo> mods = new ArrayList<>();
        ModList.get().getMods().forEach(mod -> mods.add(new ModInfo(
                mod.getModId(),
                mod.getDisplayName(),
                mod.getVersion().toString()
        )));
        return mods;
    }

    @Override
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

        return new MetricsSnapshot(
                one,
                five,
                fifteen,
                raw,
                tracker.getMsptOne(),
                tracker.getMsptFive(),
                tracker.getMsptFifteen(),
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
}
