package com.mineui.serverutils.runtime;

import java.util.List;
import java.util.Map;

/**
 * Loader-agnostic view of a running Minecraft server. Each server platform
 * (Forge, Paper, Fabric, …) provides its own implementation; the core HTTP/
 * service layer depends only on this interface, never on a specific loader's
 * API. This is the single seam that touches server internals.
 */
public interface ServerRuntime {

    /** True when a server is attached and metrics can be read. */
    boolean isActive();

    /** Names of currently-online players. */
    List<String> getPlayerNames();

    /** Loaded mods (Forge/Fabric) or plugins (Paper/Bukkit). */
    List<ModInfo> listMods();

    /** A point-in-time snapshot of tick performance and world counts. */
    MetricsSnapshot snapshot();

    record MetricsSnapshot(
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

    record DimensionSnapshot(Integer chunks, Integer entities) {}

    record ModInfo(String id, String name, String version) {}
}
