package id.octanexus.octatycoon.config;

import id.octanexus.octatycoon.OctaTycoonPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Language {
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private final OctaTycoonPlugin plugin;
    private YamlConfiguration messages;

    public Language(OctaTycoonPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File folder = new File(plugin.getDataFolder(), "lang");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create lang directory.");
        }
        File file = new File(folder, "id.yml");
        if (!file.exists()) plugin.saveResource("lang/id.yml", false);
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public String raw(String path) {
        return messages == null ? path : messages.getString(path, path);
    }

    public String get(String path) {
        return color(raw(path));
    }

    public String get(String path, Map<String, ?> placeholders) {
        String value = raw(path);
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return color(value);
    }

    public String prefix() {
        return get("prefix");
    }

    public static String color(String input) {
        if (input == null) return "";
        Matcher matcher = HEX.matcher(input);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) replacement.append('§').append(c);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(out);
        return ChatColor.translateAlternateColorCodes('&', out.toString());
    }
}
