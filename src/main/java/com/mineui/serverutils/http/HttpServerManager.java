package com.mineui.serverutils.http;

import com.mineui.serverutils.config.AppConfig;
import com.mineui.serverutils.runtime.ServerRuntime;
import com.mineui.serverutils.service.StatusService;
import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;

public final class HttpServerManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private HttpServer server;

    public void start(AppConfig config, ServerRuntime runtime) {
        if (server != null) {
            return;
        }
        try {
            StatusService statusService = new StatusService(config, runtime);
            InetSocketAddress address = new InetSocketAddress(config.httpHost(), config.httpPort());
            server = HttpServer.create(address, 0);
            ApiHandlers.register(server, statusService);
            server.start();
            LOGGER.info("MineUI server utils listening on http://{}:{}", config.httpHost(), config.httpPort());
        } catch (IOException ex) {
            LOGGER.error("MineUI server utils failed to start", ex);
            server = null;
        }
    }

    public void stop() {
        if (server == null) {
            return;
        }
        server.stop(0);
        server = null;
    }
}
