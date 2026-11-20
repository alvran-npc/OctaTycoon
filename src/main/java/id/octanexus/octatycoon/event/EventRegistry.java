package id.octanexus.octatycoon.event;

import id.octanexus.octatycoon.task.GuardLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Weighted event selection with minimum-online and maximum-guard gates. */
public final class EventRegistry {
    public record Definition(String id, String category, int weight, int durationSeconds,
                             int cooldownMinutes, int minimumOnline, GuardLevel maximumGuard, double multiplier) {
        public Definition {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Event id is required");
            weight = Math.max(0, weight);
            durationSeconds = Math.max(1, durationSeconds);
            cooldownMinutes = Math.max(0, cooldownMinutes);
            minimumOnline = Math.max(0, minimumOnline);
            maximumGuard = maximumGuard == null ? GuardLevel.YELLOW : maximumGuard;
            multiplier = Double.isFinite(multiplier) ? Math.max(0, Math.min(10, multiplier)) : 1;
        }
    }
    private final List<Definition> definitions = new ArrayList<>();
    public synchronized void register(Definition definition) { definitions.removeIf(e -> e.id().equalsIgnoreCase(definition.id())); definitions.add(definition); }
    public synchronized List<Definition> definitions() { return List.copyOf(definitions); }
    public synchronized Definition choose(String category, int online, GuardLevel guard, Random random) {
        List<Definition> eligible = definitions.stream()
                .filter(e -> category == null || e.category().equalsIgnoreCase(category))
                .filter(e -> online >= e.minimumOnline())
                .filter(e -> guard != null && guard.ordinal() <= e.maximumGuard().ordinal())
                .filter(e -> e.weight() > 0).toList();
        int total = eligible.stream().mapToInt(Definition::weight).sum();
        if (total <= 0) return null;
        int roll = random.nextInt(total);
        for (Definition event : eligible) {
            roll -= event.weight();
            if (roll < 0) return event;
        }
        return eligible.get(eligible.size() - 1);
    }
}
