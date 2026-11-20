package id.octanexus.octatycoon.migration;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Safe first-stage importer for a normalized CSV export (variable,value). It deliberately does not
 * guess Skript's raw variable-file serialization. Export/normalize the old variables first.
 */
public final class SkriptVariableImporter {
    public record ImportedValue(UUID uuid, String key, String value) {}
    public record ImportReport(List<ImportedValue> values, int skipped, List<String> warnings) {}

    public ImportReport readNormalizedCsv(Path path) throws IOException {
        List<ImportedValue> values = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int skipped = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int row = 0;
            while ((line = reader.readLine()) != null) {
                row++;
                if (line.isBlank() || row == 1 && line.toLowerCase(Locale.ROOT).startsWith("variable,")) continue;
                int comma = line.indexOf(',');
                if (comma < 1) { skipped++; warnings.add("Row " + row + ": expected variable,value"); continue; }
                String variable = line.substring(0, comma).trim();
                String value = line.substring(comma + 1).trim();
                UUID uuid = extractUuid(variable);
                String key = mapKnownKey(variable);
                if (uuid == null || key == null || value.isBlank()) { skipped++; continue; }
                values.add(new ImportedValue(uuid, key, value));
            }
        }
        return new ImportReport(List.copyOf(values), skipped, List.copyOf(warnings));
    }

    private UUID extractUuid(String variable) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12})").matcher(variable);
        if (!matcher.find()) return null;
        try { return UUID.fromString(matcher.group(1)); } catch (IllegalArgumentException ignored) { return null; }
    }

    private String mapKnownKey(String variable) {
        String lower = variable.toLowerCase(Locale.ROOT);
        if (lower.startsWith("{token_all::")) return "economy.token";
        if (lower.startsWith("{kuy::lvl::")) return "progress.level";
        if (lower.startsWith("{kuy::xp::")) return "progress.xp";
        if (lower.startsWith("{kg::g::")) return "gens.legacy";
        if (lower.startsWith("{kp::")) return "mining.legacy";
        return null;
    }
}
