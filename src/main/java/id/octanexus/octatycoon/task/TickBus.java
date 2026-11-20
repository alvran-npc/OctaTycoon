package id.octanexus.octatycoon.task;

import id.octanexus.octatycoon.OctaTycoonPlugin;
import id.octanexus.octatycoon.profile.JobProfiler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class TickBus {
    private final OctaTycoonPlugin plugin;
    private final TPSGuard guard;
    private final JobProfiler profiler;
    private final List<Job> jobs = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> cursors = new HashMap<>();
    private BukkitTask task;

    public TickBus(OctaTycoonPlugin plugin, TPSGuard guard, JobProfiler profiler) {
        this.plugin = plugin;
        this.guard = guard;
        this.profiler = profiler;
    }

    public void register(String name, Duration interval, Priority priority, Consumer<JobContext> action) {
        if (interval.isNegative() || interval.isZero()) throw new IllegalArgumentException("Job interval must be positive");
        jobs.removeIf(job -> job.name.equalsIgnoreCase(name));
        jobs.add(new Job(name, interval.toMillis(), priority, action, 0L));
    }

    public void start() {
        if (task != null) return;
        long period = Math.max(20L, plugin.getConfig().getLong("settings.scheduler.tick-bus-period-ticks", 100L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::pulse, period, period);
    }

    private void pulse() {
        guard.sample();
        long now = System.currentTimeMillis();
        JobContext context = new JobContext(now, guard.level(), guard.batchDivisor());
        for (Job job : jobs) {
            if (!guard.allows(job.priority)) continue;
            if (now - job.lastRunMillis < job.intervalMillis) continue;
            job.lastRunMillis = now;
            profiler.measure(job.name, () -> {
                try { job.action.accept(context); }
                catch (Throwable throwable) { plugin.getLogger().severe("Tick Bus job '" + job.name + "' failed: " + throwable.getMessage()); throwable.printStackTrace(); }
            });
        }
    }

    public List<Player> slicePlayers(String key, Collection<? extends Player> online, int baseBatch, int divisor) {
        if (online.isEmpty()) return List.of();
        List<Player> players = new ArrayList<>(online);
        int size = players.size();
        int batch = Math.max(1, (int) Math.ceil((double) Math.max(1, baseBatch) / Math.max(1, divisor)));
        synchronized (cursors) {
            int start = Math.floorMod(cursors.getOrDefault(key, 0), size);
            int count = Math.min(size, batch);
            List<Player> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) result.add(players.get((start + i) % size));
            cursors.put(key, (start + count) % size);
            return result;
        }
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    public int jobCount() { return jobs.size(); }
    public List<String> jobNames() { return jobs.stream().map(job -> job.name).toList(); }

    private static final class Job {
        private final String name;
        private final long intervalMillis;
        private final Priority priority;
        private final Consumer<JobContext> action;
        private long lastRunMillis;
        private Job(String name, long intervalMillis, Priority priority, Consumer<JobContext> action, long lastRunMillis) {
            this.name = name; this.intervalMillis = intervalMillis; this.priority = priority; this.action = action; this.lastRunMillis = lastRunMillis;
        }
    }
}
