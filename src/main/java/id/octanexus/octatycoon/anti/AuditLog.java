package id.octanexus.octatycoon.anti;

import id.octanexus.octatycoon.OctaTycoonPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AuditLog {
    private final OctaTycoonPlugin plugin;
    private final int memoryLimit;
    private final Deque<String> ring = new ArrayDeque<>();
    private final Deque<String> runtimeRing = new ArrayDeque<>();
    private final ExecutorService fileWriter = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "OctaTycoon-Audit");
        thread.setDaemon(true);
        return thread;
    });
    private final File file;

    public AuditLog(OctaTycoonPlugin plugin) {
        this.plugin = plugin;
        memoryLimit = Math.max(50, plugin.getConfig().getInt("settings.audit.memory-lines", 200));
        file = new File(plugin.getDataFolder(), plugin.getConfig().getString("settings.audit.file-name", "kuy-audit.log"));
    }

    public void log(String actor, String action, String detail) {
        String line = "[" + Instant.now() + "] " + actor + " | " + action + " | " + detail;
        synchronized (ring) {
            ring.addLast(line);
            while (ring.size() > memoryLimit) ring.removeFirst();
        }
        synchronized (runtimeRing) {
            runtimeRing.addLast(line);
            while (runtimeRing.size() > 50) runtimeRing.removeFirst();
        }
        plugin.database().audit(actor, action, detail).exceptionally(error -> {
            plugin.getLogger().warning("Could not persist audit event: " + error.getMessage());
            return null;
        });
        fileWriter.execute(() -> {
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) return;
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
                    writer.write(line);
                    writer.newLine();
                }
            } catch (IOException exception) {
                plugin.getLogger().warning("Audit file write failed: " + exception.getMessage());
            }
        });
    }

    public List<String> latest(int amount) {
        synchronized (ring) {
            return ring.stream().skip(Math.max(0, ring.size() - amount)).toList();
        }
    }

    public List<String> recentLog(int amount) {
        synchronized (runtimeRing) {
            return runtimeRing.stream().skip(Math.max(0, runtimeRing.size() - amount)).toList();
        }
    }

    public void shutdown() { fileWriter.shutdown(); }
}
