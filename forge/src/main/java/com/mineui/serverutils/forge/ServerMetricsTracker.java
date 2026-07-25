package com.mineui.serverutils.forge;

public final class ServerMetricsTracker {
    private final RollingAverage oneMinute = new RollingAverage(1200);
    private final RollingAverage fiveMinutes = new RollingAverage(6000);
    private final RollingAverage fifteenMinutes = new RollingAverage(18000);
    private long lastTickNs = -1L;

    public void recordTick() {
        long now = System.nanoTime();
        if (lastTickNs > 0) {
            long deltaNs = now - lastTickNs;
            oneMinute.add(deltaNs);
            fiveMinutes.add(deltaNs);
            fifteenMinutes.add(deltaNs);
        }
        lastTickNs = now;
    }

    public double getTpsOne() {
        return tpsFromAvg(oneMinute.averageMs());
    }

    public double getTpsFive() {
        return tpsFromAvg(fiveMinutes.averageMs());
    }

    public double getTpsFifteen() {
        return tpsFromAvg(fifteenMinutes.averageMs());
    }

    public double getMsptOne() {
        return oneMinute.averageMs();
    }

    public double getMsptFive() {
        return fiveMinutes.averageMs();
    }

    public double getMsptFifteen() {
        return fifteenMinutes.averageMs();
    }

    private double tpsFromAvg(double avgMs) {
        if (avgMs <= 0) {
            return 20.0;
        }
        return Math.min(20.0, 1000.0 / avgMs);
    }

    private static final class RollingAverage {
        private final long[] samples;
        private int index = 0;
        private int count = 0;
        private long sum = 0;

        private RollingAverage(int size) {
            this.samples = new long[size];
        }

        private void add(long value) {
            if (count < samples.length) {
                count++;
            } else {
                sum -= samples[index];
            }
            samples[index] = value;
            sum += value;
            index = (index + 1) % samples.length;
        }

        private double averageMs() {
            if (count == 0) {
                return 0.0;
            }
            return (sum / (double) count) / 1_000_000.0;
        }
    }
}
