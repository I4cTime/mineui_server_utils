package com.mineui.serverutils.http;

import com.mineui.serverutils.config.AppConfig;
import com.mineui.serverutils.runtime.ServerRuntime;
import com.mineui.serverutils.service.StatusService;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class HttpServerManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerManager.class);
    private static final int WORKER_THREADS = 4;

    private HttpServer server;
    private ExecutorService executor;

    public void start(AppConfig config, ServerRuntime runtime) {
        if (server != null) {
            return;
        }

        // Refuse to expose the API on a non-loopback interface without a token.
        // This is the guardrail that makes the remote-monitoring case safe: you
        // cannot bind 0.0.0.0 unless MINEUI_SERVER_UTILS_TOKEN is set.
        if (!config.isLoopbackBind() && !config.hasAuthToken()) {
            LOGGER.error(
                    "MineUI server utils refusing to bind non-loopback host {} without MINEUI_SERVER_UTILS_TOKEN set. "
                            + "Set a token to enable remote access, or bind 127.0.0.1.",
                    config.httpHost());
            return;
        }

        try {
            StatusService statusService = new StatusService(config, runtime);
            InetSocketAddress address = new InetSocketAddress(config.httpHost(), config.httpPort());
            server = HttpServer.create(address, 0);
            executor = Executors.newFixedThreadPool(WORKER_THREADS);
            server.setExecutor(executor);
            ApiHandlers.register(server, statusService, config.authToken());
            server.start();
            LOGGER.info(
                    "MineUI server utils listening on http://{}:{} (auth {})",
                    config.httpHost(),
                    config.httpPort(),
                    config.hasAuthToken() ? "enabled" : "disabled (loopback only)");
        } catch (IOException ex) {
            LOGGER.error("MineUI server utils failed to start", ex);
            shutdownExecutor();
            server = null;
        }
    }

    public void stop() {
        if (server == null) {
            return;
        }
        server.stop(0);
        server = null;
        shutdownExecutor();
    }

    private void shutdownExecutor() {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        executor = null;
    }
}
