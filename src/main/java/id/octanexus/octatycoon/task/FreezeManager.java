package id.octanexus.octatycoon.task;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FreezeManager {
    private static final Set<String> VALID = Set.of("hoe", "pickaxe", "gens");
    private final Set<String> frozen = ConcurrentHashMap.newKeySet();

    public FreezeManager(Set<String> initial) {
        for (String module : initial) freeze(module);
    }

    public boolean freeze(String module) {
        String normalized = module.toLowerCase();
        if (normalized.equals("all")) {
            frozen.addAll(VALID);
            return true;
        }
        if (!VALID.contains(normalized)) return false;
        frozen.add(normalized);
        return true;
    }

    public boolean unfreeze(String module) {
        String normalized = module.toLowerCase();
        if (normalized.equals("all")) {
            frozen.clear();
            return true;
        }
        if (!VALID.contains(normalized)) return false;
        frozen.remove(normalized);
        return true;
    }

    public boolean isFrozen(String module) {
        return frozen.contains("all") || frozen.contains(module.toLowerCase());
    }

    public Set<String> snapshot() { return Set.copyOf(frozen); }
}
