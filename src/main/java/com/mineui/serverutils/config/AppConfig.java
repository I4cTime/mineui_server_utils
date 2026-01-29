package com.mineui.serverutils.config;

import java.nio.file.Path;

public record AppConfig(
        String httpHost,
        int httpPort,
        String rconHost,
        int rconPort,
        String rconPassword,
        int rconTimeoutMs,
        String serverPropertiesPath
) {
    public static AppConfig fromEnv(Path serverDir) {
        String httpHost = getEnv("HTTP_HOST", "0.0.0.0");
        int httpPort = getEnvInt("HTTP_PORT", 8787);
        String rconHost = getEnv("RCON_HOST", "127.0.0.1");
        int rconPort = getEnvInt("RCON_PORT", 25575);
        String rconPassword = getEnv("RCON_PASSWORD", "");
        int rconTimeoutMs = getEnvInt("RCON_TIMEOUT_MS", 3000);
        String defaultProperties = serverDir.resolve("server.properties").toString();
        String serverPropertiesPath = getEnv("SERVER_PROPERTIES_PATH", defaultProperties);

        return new AppConfig(httpHost, httpPort, rconHost, rconPort, rconPassword, rconTimeoutMs, serverPropertiesPath);
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int getEnvInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
