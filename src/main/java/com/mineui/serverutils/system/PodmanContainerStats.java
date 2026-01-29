package com.mineui.serverutils.system;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PodmanContainerStats {
    private PodmanContainerStats() {}

    private static final String DEFAULT_CONTAINER_NAME = "minecraft-server";
    private static final String PODMAN_BINARY = System.getenv().getOrDefault("PODMAN_BINARY", "podman");

    public static ContainerStats read(String containerName) {
        if (containerName == null || containerName.isBlank()) {
            containerName = DEFAULT_CONTAINER_NAME;
        }
        
        try {
            // Use JSON format for more reliable parsing
            ProcessBuilder pb = new ProcessBuilder(
                PODMAN_BINARY,
                "stats",
                "--no-stream",
                "--format", "json",
                containerName
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0 || output.length() == 0) {
                return null;
            }
            
            // Parse JSON-like output (podman stats --format json outputs one JSON object per line)
            String json = output.toString().trim();
            if (json.isEmpty() || !json.startsWith("{")) {
                return null;
            }
            
            return parseJsonStats(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ContainerStats parseJsonStats(String json) {
        try {
            // Extract values using simple regex (avoiding full JSON parser dependency)
            String name = extractJsonString(json, "Name");
            double cpuPercent = extractJsonDouble(json, "CPUPerc");
            String memUsage = extractJsonString(json, "MemUsage");
            double memPercent = extractJsonDouble(json, "MemPerc");
            String netIO = extractJsonString(json, "NetIO");
            String blockIO = extractJsonString(json, "BlockIO");
            int pids = extractJsonInt(json, "PIDs");
            
            // Parse memory usage: "2.1GiB / 4GiB"
            long memUsed = 0;
            long memLimit = 0;
            if (memUsage != null && memUsage.contains(" / ")) {
                String[] memParts = memUsage.split("\\s+/\\s+");
                if (memParts.length >= 1) {
                    memUsed = parseBytes(memParts[0]);
                }
                if (memParts.length >= 2) {
                    memLimit = parseBytes(memParts[1]);
                }
            }
            
            // Parse network I/O: "1.2GB / 500MB"
            long netIn = 0;
            long netOut = 0;
            if (netIO != null && netIO.contains(" / ")) {
                String[] netParts = netIO.split("\\s+/\\s+");
                if (netParts.length >= 1) {
                    netIn = parseBytes(netParts[0]);
                }
                if (netParts.length >= 2) {
                    netOut = parseBytes(netParts[1]);
                }
            }
            
            // Parse block I/O: "100MB / 50MB"
            long blockRead = 0;
            long blockWrite = 0;
            if (blockIO != null && blockIO.contains(" / ")) {
                String[] blockParts = blockIO.split("\\s+/\\s+");
                if (blockParts.length >= 1) {
                    blockRead = parseBytes(blockParts[0]);
                }
                if (blockParts.length >= 2) {
                    blockWrite = parseBytes(blockParts[1]);
                }
            }
            
            return new ContainerStats(
                name != null ? name : "",
                cpuPercent,
                memUsed,
                memLimit,
                memPercent,
                netIn,
                netOut,
                blockRead,
                blockWrite,
                pids
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String extractJsonString(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static double extractJsonDouble(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([\\d.]+)\"?");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                String value = matcher.group(1).replace("%", "");
                return Double.parseDouble(value);
            } catch (NumberFormatException ex) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private static int extractJsonInt(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ex) {
                return 0;
            }
        }
        return 0;
    }

    private static long parseBytes(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        value = value.trim();
        
        // Handle formats like "2.1GiB", "500MB", "1.2GB", "2.1 GiB"
        Pattern pattern = Pattern.compile("^([\\d.]+)\\s*([KMGT]?i?B)$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(value);
        
        if (!matcher.matches()) {
            return 0;
        }
        
        double num = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2).toUpperCase();
        
        // Convert to bytes
        long multiplier = 1;
        if (unit.startsWith("K")) {
            multiplier = 1024;
        } else if (unit.startsWith("M")) {
            multiplier = 1024 * 1024;
        } else if (unit.startsWith("G")) {
            multiplier = 1024L * 1024 * 1024;
        } else if (unit.startsWith("T")) {
            multiplier = 1024L * 1024 * 1024 * 1024;
        }
        
        return (long) (num * multiplier);
    }

    public record ContainerStats(
        String name,
        double cpuPercent,
        long memUsedBytes,
        long memLimitBytes,
        double memPercent,
        long netInputBytes,
        long netOutputBytes,
        long blockReadBytes,
        long blockWriteBytes,
        int pids
    ) {}
}
