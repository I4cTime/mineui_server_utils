package com.mineui.serverutils.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class ServerProperties {
    private final Path path;

    public ServerProperties(String path) {
        this.path = Path.of(path);
    }

    public Map<String, String> load() {
        if (!Files.exists(path)) {
            return Collections.emptyMap();
        }
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(path)) {
            properties.load(stream);
        } catch (IOException ex) {
            return Collections.emptyMap();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            values.put(name, properties.getProperty(name, ""));
        }
        return values;
    }
}
