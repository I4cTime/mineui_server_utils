package com.mineui.serverutils.paper;

import com.mineui.serverutils.config.AppConfig;
import com.mineui.serverutils.http.HttpServerManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

public final class PaperPlugin extends JavaPlugin {
    private final HttpServerManager serverManager = new HttpServerManager();
    private final PaperServerRuntime runtime = new PaperServerRuntime();

    @Override
    public void onEnable() {
        runtime.activate();
        Path serverDir = getServer().getWorldContainer().toPath();
        AppConfig config = AppConfig.fromEnv(serverDir);
        serverManager.start(config, runtime);
    }

    @Override
    public void onDisable() {
        serverManager.stop();
        runtime.deactivate();
    }
}
