package com.mineui.serverutils.paper;

import com.mineui.serverutils.runtime.ServerRuntime;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Paper/Bukkit implementation of {@link ServerRuntime}. One jar built against
 * the Bukkit API covers Paper, Spigot, Purpur, Folia, and the Forge/Fabric
 * hybrid servers (Mohist, Arclight, …) that also load Bukkit plugins.
 */
public final class PaperServerRuntime implements ServerRuntime {
    private volatile boolean active;

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public List<String> getPlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    @Override
    public List<ModInfo> listMods() {
        List<ModInfo> mods = new ArrayList<>();
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            mods.add(new ModInfo(
                    plugin.getName().toLowerCase(Locale.ROOT),
                    plugin.getName(),
                    plugin.getPluginMeta().getVersion()
            ));
        }
        return mods;
    }

    @Override
    public MetricsSnapshot snapshot() {
        double[] tps = Bukkit.getTPS(); // [1m, 5m, 15m]
        double one = tps.length > 0 ? tps[0] : 20.0;
        double five = tps.length > 1 ? tps[1] : one;
        double fifteen = tps.length > 2 ? tps[2] : five;
        String raw = String.format(Locale.US, "TPS avg 1m/5m/15m: %.2f, %.2f, %.2f", one, five, fifteen);

        // Paper exposes a single rolling average tick time (ms); reuse it for
        // all three windows since Bukkit has no 5m/15m MSPT breakdown.
        double mspt = Bukkit.getAverageTickTime();

        Integer chunks = null;
        Integer entities = null;
        Map<String, DimensionSnapshot> dimensions = new LinkedHashMap<>();
        for (World world : Bukkit.getWorlds()) {
            int worldChunks = world.getLoadedChunks().length;
            int worldEntities = world.getEntities().size();
            chunks = chunks == null ? worldChunks : chunks + worldChunks;
            entities = entities == null ? worldEntities : entities + worldEntities;
            dimensions.put(world.getName(), new DimensionSnapshot(worldChunks, worldEntities));
        }

        return new MetricsSnapshot(
                one,
                five,
                fifteen,
                raw,
                mspt,
                mspt,
                mspt,
                chunks,
                entities,
                Bukkit.getOnlinePlayers().size(),
                Bukkit.getMaxPlayers(),
                dimensions
        );
    }
}
