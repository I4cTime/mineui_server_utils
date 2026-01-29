package com.mineui.serverutils;

import com.mineui.serverutils.config.AppConfig;
import com.mineui.serverutils.http.HttpServerManager;
import com.mineui.serverutils.runtime.ServerRuntime;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;

@Mod("mineui_server_utils")
public final class Main {
    private final HttpServerManager serverManager = new HttpServerManager();
    private final ServerRuntime runtime = new ServerRuntime();

    public Main() {
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
    }

    private void onServerStarting(ServerStartingEvent event) {
        runtime.attach(event.getServer());
        Path serverDir = event.getServer().getServerDirectory().toPath();
        AppConfig config = AppConfig.fromEnv(serverDir);
        serverManager.start(config, runtime);
    }

    private void onServerStopping(ServerStoppingEvent event) {
        serverManager.stop();
        runtime.detach();
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            runtime.recordTick();
        }
    }
}
