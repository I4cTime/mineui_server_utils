package com.mineui.serverutils.config;

import java.nio.file.Path;
import java.util.Set;

public record AppConfig(
        String httpHost,
        int httpPort,
        String authToken,
        String serverPropertiesPath
) {
    private static final Set<String> LOOPBACK_HOSTS = Set.of("127.0.0.1", "localhost", "::1");

    public static AppConfig fromEnv(Path serverDir) {
        String httpHost = getEnv("HTTP_HOST", "127.0.0.1");
        int httpPort = getEnvInt("HTTP_PORT", 8787);
        String authToken = getEnv("MINEUI_SERVER_UTILS_TOKEN", "");
        String defaultProperties = serverDir.resolve("server.properties").toString();
        String serverPropertiesPath = getEnv("SERVER_PROPERTIES_PATH", defaultProperties);

        return new AppConfig(httpHost, httpPort, authToken, serverPropertiesPath);
    }

    /** True when the configured bind address is a loopback-only interface. */
    public boolean isLoopbackBind() {
        return LOOPBACK_HOSTS.contains(httpHost);
    }

    public boolean hasAuthToken() {
        return authToken != null && !authToken.isBlank();
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
