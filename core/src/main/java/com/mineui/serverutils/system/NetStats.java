package com.mineui.serverutils.system;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public final class NetStats {
    private NetStats() {}

    public static long[] read() {
        File proc = new File("/proc/net/dev");
        if (!proc.exists()) {
            return null;
        }
        long in = 0;
        long out = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(proc))) {
            String line;
            int lineIndex = 0;
            while ((line = reader.readLine()) != null) {
                lineIndex++;
                if (lineIndex <= 2) {
                    continue;
                }
                String[] parts = line.trim().split(":");
                if (parts.length != 2) {
                    continue;
                }
                String iface = parts[0].trim();
                if (iface.startsWith("lo")) {
                    continue;
                }
                String[] values = parts[1].trim().split("\\s+");
                if (values.length < 9) {
                    continue;
                }
                in += parseLong(values[0]);
                out += parseLong(values[8]);
            }
        } catch (Exception ignored) {
            return null;
        }
        return new long[] { in, out };
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
