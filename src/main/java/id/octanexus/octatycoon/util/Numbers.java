package id.octanexus.octatycoon.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Numbers {
    private static final Pattern SUFFIX = Pattern.compile("^([+-]?[0-9]+(?:\\.[0-9]+)?)\\s*([kmbt]|qa)?$", Pattern.CASE_INSENSITIVE);
    private Numbers() {}

    public static BigDecimal parse(String input) {
        if (input == null) throw new IllegalArgumentException("Input cannot be null");
        String normalized = input.trim().replace("_", "").replace(",", "");
        Matcher matcher = SUFFIX.matcher(normalized);
        if (!matcher.matches()) throw new IllegalArgumentException("Invalid number: " + input);
        BigDecimal value = new BigDecimal(matcher.group(1));
        String suffix = matcher.group(2);
        if (suffix == null) return value;
        return switch (suffix.toLowerCase(Locale.ROOT)) {
            case "k" -> value.multiply(BigDecimal.valueOf(1_000L));
            case "m" -> value.multiply(BigDecimal.valueOf(1_000_000L));
            case "b" -> value.multiply(BigDecimal.valueOf(1_000_000_000L));
            case "t" -> value.multiply(BigDecimal.valueOf(1_000_000_000_000L));
            case "qa" -> value.multiply(BigDecimal.valueOf(1_000_000_000_000_000L));
            default -> throw new IllegalArgumentException("Unknown suffix: " + suffix);
        };
    }

    public static String shortFormat(long value) {
        BigDecimal n = BigDecimal.valueOf(value);
        BigDecimal[] limits = {
                BigDecimal.valueOf(1_000_000_000_000_000L),
                BigDecimal.valueOf(1_000_000_000_000L),
                BigDecimal.valueOf(1_000_000_000L),
                BigDecimal.valueOf(1_000_000L),
                BigDecimal.valueOf(1_000L)
        };
        String[] suffix = {"Qa", "T", "B", "M", "K"};
        for (int i = 0; i < limits.length; i++) {
            if (n.abs().compareTo(limits[i]) >= 0) {
                return n.divide(limits[i], 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + suffix[i];
            }
        }
        return n.toPlainString();
    }
}
