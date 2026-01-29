package com.mineui.serverutils.system;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public final class DiskStats {
    private DiskStats() {}

    public static long[] read() {
        File proc = new File("/proc/diskstats");
        if (!proc.exists()) {
            return null;
        }
        long readBytes = 0;
        long writeBytes = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(proc))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.trim().split("\\s+");
                if (values.length < 14) {
                    continue;
                }
                String device = values[2];
                if (isIgnoredDevice(device)) {
                    continue;
                }
                long sectorsRead = parseLong(values[5]);
                long sectorsWritten = parseLong(values[9]);
                readBytes += sectorsRead * 512L;
                writeBytes += sectorsWritten * 512L;
            }
        } catch (Exception ignored) {
            return null;
        }
        return new long[] { readBytes, writeBytes };
    }

    private static boolean isIgnoredDevice(String device) {
        return device.startsWith("loop")
                || device.startsWith("ram")
                || device.startsWith("dm-")
                || device.startsWith("sr")
                || device.startsWith("md");
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
