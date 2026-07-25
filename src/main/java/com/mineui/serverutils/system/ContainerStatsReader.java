package com.mineui.serverutils.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads the current process's own container resource usage directly from the
 * kernel cgroup interface. This is what {@code podman stats} / {@code docker
 * stats} surface, but read from inside the container where this mod actually
 * runs — so it needs no {@code podman} binary, no daemon socket, and works when
 * the Minecraft server is launched inside a MineUI-managed container.
 *
 * <p>Stats are only reported when the process is actually containerized. On a
 * bare-metal server the container block is omitted (host-level metrics already
 * cover that machine), avoiding a misleading whole-host "container" reading.
 */
public final class ContainerStatsReader {
    private ContainerStatsReader() {}

    private static final Path CGROUP_ROOT = Path.of("/sys/fs/cgroup");
    private static final Path V2_MARKER = CGROUP_ROOT.resolve("cgroup.controllers");

    // Previous CPU sample for delta-based percentage. usage is stored in nanos.
    private static long prevCpuUsageNanos = -1;
    private static long prevSampleNanos = -1;

    public static ContainerStats read() {
        if (!isContainerized()) {
            return null;
        }
        boolean v2 = Files.exists(V2_MARKER);
        try {
            long memUsed = v2 ? readLong(CGROUP_ROOT.resolve("memory.current"))
                    : readLong(CGROUP_ROOT.resolve("memory/memory.usage_in_bytes"));
            long memLimit = v2 ? readMemoryMax(CGROUP_ROOT.resolve("memory.max"))
                    : normalizeLimit(readLong(CGROUP_ROOT.resolve("memory/memory.limit_in_bytes")));
            double memPercent = (memLimit > 0 && memUsed >= 0)
                    ? (memUsed / (double) memLimit) * 100.0 : 0.0;

            double cpuPercent = readCpuPercent(v2);
            long pids = v2 ? readLong(CGROUP_ROOT.resolve("pids.current"))
                    : readLong(CGROUP_ROOT.resolve("pids/pids.current"));
            long[] block = v2 ? readBlockIoV2() : readBlockIoV1();

            return new ContainerStats(
                    containerName(),
                    cpuPercent,
                    Math.max(0, memUsed),
                    Math.max(0, memLimit),
                    memPercent,
                    0L, // net I/O is namespace-level; covered by host NetStats
                    0L,
                    block[0],
                    block[1],
                    (int) Math.max(0, pids)
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isContainerized() {
        return Files.exists(Path.of("/run/.containerenv")) // podman
                || Files.exists(Path.of("/.dockerenv"));    // docker
    }

    private static double readCpuPercent(boolean v2) {
        try {
            long usageNanos = v2 ? readCpuUsageV2() : readLong(CGROUP_ROOT.resolve("cpuacct/cpuacct.usage"));
            long nowNanos = System.nanoTime();
            double percent = 0.0;
            if (prevCpuUsageNanos >= 0 && usageNanos >= prevCpuUsageNanos && nowNanos > prevSampleNanos) {
                long deltaUsage = usageNanos - prevCpuUsageNanos;
                long deltaWall = nowNanos - prevSampleNanos;
                percent = (deltaUsage / (double) deltaWall) * 100.0;
            }
            prevCpuUsageNanos = usageNanos;
            prevSampleNanos = nowNanos;
            return percent;
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    /** cgroup v2 cpu.stat reports {@code usage_usec} (microseconds). */
    private static long readCpuUsageV2() throws IOException {
        for (String line : Files.readAllLines(CGROUP_ROOT.resolve("cpu.stat"))) {
            if (line.startsWith("usage_usec")) {
                return Long.parseLong(line.trim().split("\\s+")[1]) * 1_000L;
            }
        }
        return 0L;
    }

    /** Sum rbytes/wbytes across all devices in cgroup v2 io.stat. */
    private static long[] readBlockIoV2() {
        long read = 0;
        long write = 0;
        try {
            for (String line : Files.readAllLines(CGROUP_ROOT.resolve("io.stat"))) {
                for (String field : line.trim().split("\\s+")) {
                    if (field.startsWith("rbytes=")) {
                        read += parseLongSafe(field.substring("rbytes=".length()));
                    } else if (field.startsWith("wbytes=")) {
                        write += parseLongSafe(field.substring("wbytes=".length()));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return new long[] {read, write};
    }

    /** cgroup v1 blkio.throttle.io_service_bytes: lines like "MAJ:MIN Read N". */
    private static long[] readBlockIoV1() {
        long read = 0;
        long write = 0;
        try {
            List<String> lines = Files.readAllLines(
                    CGROUP_ROOT.resolve("blkio/blkio.throttle.io_service_bytes"));
            for (String line : lines) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 3) {
                    continue;
                }
                if ("Read".equalsIgnoreCase(parts[1])) {
                    read += parseLongSafe(parts[2]);
                } else if ("Write".equalsIgnoreCase(parts[1])) {
                    write += parseLongSafe(parts[2]);
                }
            }
        } catch (Exception ignored) {
        }
        return new long[] {read, write};
    }

    private static long readMemoryMax(Path path) throws IOException {
        String raw = Files.readString(path).trim();
        if ("max".equals(raw)) {
            return 0L; // unlimited
        }
        return parseLongSafe(raw);
    }

    /** cgroup v1 reports "unlimited" as a near-Long.MAX sentinel. */
    private static long normalizeLimit(long value) {
        // 0x7FFFFFFFFFFFF000 and similar page-aligned sentinels mean "no limit".
        return value >= (Long.MAX_VALUE - 4096) ? 0L : value;
    }

    private static long readLong(Path path) throws IOException {
        return parseLongSafe(Files.readString(path).trim());
    }

    private static long parseLongSafe(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static String containerName() {
        String host = System.getenv("HOSTNAME");
        if (host != null && !host.isBlank()) {
            return host;
        }
        try {
            return Files.readString(Path.of("/etc/hostname")).trim();
        } catch (Exception ignored) {
            return "self";
        }
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
