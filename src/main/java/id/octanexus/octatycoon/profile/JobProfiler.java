package id.octanexus.octatycoon.profile;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class JobProfiler {
    private static final long WINDOW_NANOS = 60_000_000_000L;
    private final Map<String, Stats> stats = new ConcurrentHashMap<>();

    public void measure(String name, Runnable action) {
        long start = System.nanoTime();
        try {
            action.run();
        } finally {
            record(name, System.nanoTime() - start);
        }
    }

    public void record(String name, long durationNanos) {
        Stats bucket = stats.computeIfAbsent(name, ignored -> new Stats());
        bucket.record(durationNanos, System.nanoTime());
    }

    public Map<String, Snapshot> snapshot() {
        Map<String, Snapshot> copy = new ConcurrentHashMap<>();
        stats.forEach((key, value) -> copy.put(key, value.snapshot(System.nanoTime())));
        return copy;
    }

    public String slowestSummary(int limit) {
        return snapshot().entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, Snapshot> e) -> e.getValue().maxMicros()).reversed())
                .limit(limit)
                .map(e -> e.getKey() + "=" + e.getValue().lastMicros() + "/" + e.getValue().maxMicros() + "us")
                .reduce((a, b) -> a + ", " + b).orElse("belum ada sampel");
    }

    private static final class Stats {
        private final Deque<Sample> recent = new ArrayDeque<>();
        private long last;
        private long max;
        private long spikes5;
        private long spikes15;
        private long total;

        synchronized void record(long nanos, long now) {
            last = nanos;
            max = Math.max(max, nanos);
            total++;
            if (nanos > 5_000_000L) spikes5++;
            if (nanos > 15_000_000L) spikes15++;
            recent.addLast(new Sample(now, nanos));
            trim(now);
        }

        synchronized Snapshot snapshot(long now) {
            trim(now);
            long windowMax = recent.stream().mapToLong(Sample::duration).max().orElse(0);
            long window5 = recent.stream().filter(s -> s.duration > 5_000_000L).count();
            long window15 = recent.stream().filter(s -> s.duration > 15_000_000L).count();
            return new Snapshot(last / 1_000, windowMax / 1_000, window5, window15, total);
        }

        private void trim(long now) {
            while (!recent.isEmpty() && now - recent.peekFirst().time > WINDOW_NANOS) recent.removeFirst();
        }
    }

    private record Sample(long time, long duration) {}
    public record Snapshot(long lastMicros, long maxMicros, long spikesOver5ms, long spikesOver15ms, long totalRuns) {}
}
