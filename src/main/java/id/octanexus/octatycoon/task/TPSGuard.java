package id.octanexus.octatycoon.task;

import id.octanexus.octatycoon.OctaTycoonPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;

public final class TPSGuard {
    private final OctaTycoonPlugin plugin;
    private final double greenThreshold;
    private final double yellowThreshold;
    private final double orangeThreshold;
    private final long recoveryNanos;
    private GuardLevel level = GuardLevel.GREEN;
    private GuardLevel forcedLevel;
    private long goodSinceNanos = -1L;
    private double lastTps = 20.0;

    public TPSGuard(OctaTycoonPlugin plugin) {
        this.plugin = plugin;
        greenThreshold = plugin.getConfig().getDouble("settings.guard.green-tps", 19.5);
        yellowThreshold = plugin.getConfig().getDouble("settings.guard.yellow-tps", 18.5);
        orangeThreshold = plugin.getConfig().getDouble("settings.guard.orange-tps", 17.0);
        recoveryNanos = TimeUnit.SECONDS.toNanos(plugin.getConfig().getLong("settings.guard.recovery-seconds", 60));
    }

    public void sample() {
        double[] samples = Bukkit.getTPS();
        lastTps = samples.length == 0 ? 20.0 : Math.min(20.0, samples[0]);
        if (forcedLevel != null || !plugin.getConfig().getBoolean("settings.guard.enabled", true)) return;
        GuardLevel target = targetFor(lastTps);
        if (target.ordinal() > level.ordinal()) {
            transition(target);
            goodSinceNanos = -1L;
            return;
        }
        if (target.ordinal() < level.ordinal()) {
            long now = System.nanoTime();
            if (goodSinceNanos < 0) goodSinceNanos = now;
            if (now - goodSinceNanos >= recoveryNanos) {
                transition(GuardLevel.values()[level.ordinal() - 1]);
                goodSinceNanos = now;
            }
        } else {
            goodSinceNanos = -1L;
        }
    }

    private GuardLevel targetFor(double tps) {
        if (tps >= greenThreshold) return GuardLevel.GREEN;
        if (tps >= yellowThreshold) return GuardLevel.YELLOW;
        if (tps >= orangeThreshold) return GuardLevel.ORANGE;
        return GuardLevel.RED;
    }

    private void transition(GuardLevel next) {
        if (next == level) return;
        GuardLevel old = level;
        level = next;
        if (plugin.isReady()) plugin.audit().log("SYSTEM", "TPS_GUARD", old.id() + " -> " + next.id() + " @ " + String.format(java.util.Locale.US, "%.2f", lastTps));
        if (next == GuardLevel.ORANGE) {
            Bukkit.broadcastMessage("§6[OctaTycoon] TPS Guard ORANYE: batch job dibagi untuk menjaga performa.");
        } else if (old.ordinal() >= GuardLevel.ORANGE.ordinal() && next.ordinal() < old.ordinal()) {
            Bukkit.broadcastMessage("§a[OctaTycoon] TPS Guard pulih ke " + next.id().toUpperCase() + ".");
        } else if (next == GuardLevel.RED) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("octatycoon.admin.core")) player.sendMessage("§c[OctaTycoon] TPS Guard MERAH: job P2/P3 dihentikan.");
            }
            plugin.getLogger().warning("TPS Guard entered RED at " + lastTps + " TPS.");
        }
    }

    public GuardLevel level() { return forcedLevel == null ? level : forcedLevel; }
    public double lastTps() { return lastTps; }
    public int batchDivisor() { return level().ordinal() >= GuardLevel.ORANGE.ordinal() ? 2 : 1; }
    public boolean allows(Priority priority) {
        GuardLevel active = level();
        return active != GuardLevel.RED || priority == Priority.P1;
    }
    public void force(GuardLevel level) { forcedLevel = level; this.level = level; }
    public void auto() { forcedLevel = null; goodSinceNanos = -1L; }
    public boolean isForced() { return forcedLevel != null; }
}
