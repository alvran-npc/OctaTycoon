package id.octanexus.octatycoon.admin;

import id.octanexus.octatycoon.OctaTycoonPlugin;
import id.octanexus.octatycoon.config.Language;
import id.octanexus.octatycoon.task.GuardLevel;
import id.octanexus.octatycoon.util.Numbers;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class KuyCommand implements CommandExecutor {
    private final OctaTycoonPlugin plugin;
    public KuyCommand(OctaTycoonPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("octatycoon.admin.core")) {
            sender.sendMessage(plugin.lang().prefix() + plugin.lang().get("no-permission"));
            return true;
        }
        if (label.equalsIgnoreCase("kuyguard")) {
            if (!plugin.isReady()) {
                sender.sendMessage("§eOctaTycoon masih menyiapkan database. Coba lagi sebentar.");
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage("§bTPS Guard: §f" + plugin.tpsGuard().level().id().toUpperCase(Locale.ROOT) + " §7(" + (plugin.tpsGuard().isForced() ? "manual" : "otomatis") + ")");
            } else if (args[0].equalsIgnoreCase("bersihkan")) {
                plugin.tpsGuard().auto();
                sender.sendMessage("§aTPS Guard diatur ulang ke mode otomatis.");
            } else {
                guard(sender, new String[]{"guard", args[0]});
            }
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        if (!plugin.isReady()) {
            sender.sendMessage("§eOctaTycoon masih menyiapkan database. Coba lagi sebentar.");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender);
            case "guard" -> guard(sender, args);
            case "freeze" -> freeze(sender, args, true);
            case "unfreeze" -> freeze(sender, args, false);
            case "audit" -> audit(sender, args);
            case "player" -> player(sender, args);
            case "backup" -> backup(sender);
            case "eco" -> eco(sender, args);
            case "dryrun" -> dryrun(sender, args);
            default -> sender.sendMessage(plugin.lang().prefix() + plugin.lang().get("unknown-command"));
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§b§lOCTATYCOON §7— Core Admin");
        sender.sendMessage("§f/kuy status §7- status Guard, job dan database");
        sender.sendMessage("§f/kuy guard <auto|green|yellow|orange|red> §7- kontrol TPS Guard");
        sender.sendMessage("§f/kuy freeze <hoe|pickaxe|gens|all> §7- bekukan modul");
        sender.sendMessage("§f/kuy unfreeze <hoe|pickaxe|gens|all> §7- aktifkan modul");
        sender.sendMessage("§f/kuy audit [jumlah] §7- audit terakhir");
        sender.sendMessage("§f/kuy player <pemain> §7- lihat saldo Token pemain online");
        sender.sendMessage("§f/kuy eco [hari] §7- ringkasan faucet dan sink");
        sender.sendMessage("§f/kuy dryrun <event|reward> §7- simulasi tanpa perubahan data");
        sender.sendMessage("§f/kuy backup §7- flush cache dan checkpoint SQLite");
    }

    private void status(CommandSender sender) {
        sender.sendMessage(plugin.lang().get("admin.header"));
        sender.sendMessage(plugin.lang().get("admin.guard", Map.of("guard", plugin.tpsGuard().level().id().toUpperCase(Locale.ROOT), "tps", String.format(Locale.US, "%.2f", plugin.tpsGuard().lastTps()))));
        sender.sendMessage(plugin.lang().get("admin.players", Map.of("online", Bukkit.getOnlinePlayers().size())));
        sender.sendMessage(plugin.lang().get("admin.jobs", Map.of("jobs", plugin.tickBus().jobCount())));
        sender.sendMessage(plugin.lang().get("admin.frozen", Map.of("frozen", plugin.freezeManager().snapshot().isEmpty() ? "tidak ada" : String.join(", ", plugin.freezeManager().snapshot()))));
        sender.sendMessage(plugin.lang().get("admin.database", Map.of("database", "SQLite / " + plugin.playerCache().size() + " cache")));
        sender.sendMessage("§7Job termahal (max/last): " + plugin.profiler().slowestSummary(5));
        sender.sendMessage("§7Mode Guard: " + (plugin.tpsGuard().isForced() ? "manual" : "otomatis"));
        plugin.audit().recentLog(5).forEach(line -> sender.sendMessage("§8LOG » " + line));
    }

    private void guard(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("§eGunakan: /kuy guard <auto|green|yellow|orange|red>"); return; }
        if (args[1].equalsIgnoreCase("auto")) {
            plugin.tpsGuard().auto();
            sender.sendMessage(plugin.lang().get("admin.guard-auto"));
        } else {
            try {
                GuardLevel level = GuardLevel.from(args[1]);
                plugin.tpsGuard().force(level);
                sender.sendMessage("§aTPS Guard dipaksa ke " + level.id().toUpperCase(Locale.ROOT) + ".");
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(plugin.lang().get("admin.invalid-guard"));
            }
        }
        plugin.audit().log(sender.getName(), "SET_GUARD", args[1]);
    }

    private void freeze(CommandSender sender, String[] args, boolean freeze) {
        if (args.length < 2) { sender.sendMessage("§eSebutkan modul: hoe, pickaxe, gens, atau all."); return; }
        String module = args[1].toLowerCase(Locale.ROOT);
        boolean success = freeze ? plugin.freezeManager().freeze(module) : plugin.freezeManager().unfreeze(module);
        if (!success) { sender.sendMessage("§cModul tidak dikenal. Pilih hoe, pickaxe, gens, atau all."); return; }
        sender.sendMessage(plugin.lang().get(freeze ? "admin.frozen-success" : "admin.unfrozen-success", Map.of("module", module)));
        plugin.audit().log(sender.getName(), freeze ? "FREEZE" : "UNFREEZE", module);
    }

    private void audit(CommandSender sender, String[] args) {
        int amount = 10;
        String filter = null;
        if (args.length >= 2) {
            try { amount = Math.max(1, Math.min(30, Integer.parseInt(args[1]))); }
            catch (NumberFormatException ignored) { filter = args[1].toLowerCase(Locale.ROOT); amount = 30; }
        }
        sender.sendMessage(plugin.lang().get("admin.audit-header"));
        final String filterValue = filter;
        var lines = plugin.audit().latest(amount).stream()
                .filter(line -> filterValue == null || line.toLowerCase(Locale.ROOT).contains(filterValue)).toList();
        if (lines.isEmpty()) sender.sendMessage("§7Tidak ada aktivitas audit yang cocok.");
        else lines.forEach(line -> sender.sendMessage("§7" + line));
    }

    private void player(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("§eGunakan: /kuy player <pemain>"); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(plugin.lang().get("admin.player-not-found")); return; }
        plugin.database().getTokenBalance(target.getUniqueId()).thenAccept(balance -> Bukkit.getScheduler().runTask(plugin, () -> {
            sender.sendMessage("§b§lPLAYER §7» §f" + target.getName());
            sender.sendMessage("§7UUID: §f" + target.getUniqueId());
            sender.sendMessage("§7Token: §a" + balance);
            sender.sendMessage("§7Bedrock: §f" + plugin.isBedrock(target));
        }));
    }

    private void eco(CommandSender sender, String[] args) {
        int days = 1;
        if (args.length >= 2) {
            try { days = Math.max(1, Math.min(30, Integer.parseInt(args[1]))); }
            catch (NumberFormatException ignored) { sender.sendMessage("§cGunakan jumlah hari 1-30."); return; }
        }
        java.time.ZoneId zone;
        try { zone = java.time.ZoneId.of(plugin.getConfig().getString("settings.timezone", "Asia/Jakarta")); }
        catch (java.time.DateTimeException exception) { zone = java.time.ZoneId.of("Asia/Jakarta"); }
        final int queryDays = days;
        String startDay = java.time.LocalDate.now(zone).minusDays(queryDays - 1L).toString();
        plugin.database().economyTotalsSince(startDay).thenAccept(totals -> Bukkit.getScheduler().runTask(plugin, () -> {
            sender.sendMessage("§b§lECONOMY FLOW §7(periode " + queryDays + " hari sejak " + startDay + ")");
            if (totals.isEmpty()) {
                sender.sendMessage("§7Belum ada catatan faucet/sink.");
                return;
            }
            totals.forEach((currency, value) -> {
                double ratio = value.sinks() <= 0 ? (value.faucets() > 0 ? Double.POSITIVE_INFINITY : 0.0) : (double) value.faucets() / value.sinks();
                sender.sendMessage("§f" + currency + " §7| Faucet: §a" + Numbers.shortFormat(value.faucets())
                        + " §7| Sink: §c" + Numbers.shortFormat(value.sinks())
                        + " §7| Rasio: §f" + (Double.isFinite(ratio) ? String.format(Locale.US, "%.2f", ratio) : "∞"));
            });
        }));
    }

    private void dryrun(CommandSender sender, String[] args) {
        if (args.length < 2 || (!args[1].equalsIgnoreCase("event") && !args[1].equalsIgnoreCase("reward"))) {
            sender.sendMessage("§eGunakan: /kuy dryrun <event|reward>");
            return;
        }
        sender.sendMessage("§bDry-run " + args[1].toUpperCase(Locale.ROOT) + " selesai: mode simulasi saja, tidak ada saldo, event, atau data pemain yang diubah.");
        sender.sendMessage("§7Catatan: detail simulasi modul akan ditambahkan saat modul terkait masuk fase berikutnya.");
        plugin.audit().log(sender.getName(), "DRYRUN_" + args[1].toUpperCase(Locale.ROOT), "simulation only");
    }

    private void backup(CommandSender sender) {
        sender.sendMessage("§eMenjalankan flush cache dan checkpoint SQLite...");
        plugin.playerCache().flushAll().thenCompose(ignored -> plugin.database().checkpoint()).whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) sender.sendMessage("§cBackup gagal: " + error.getMessage());
            else {
                sender.sendMessage(plugin.lang().get("admin.backup-started"));
                plugin.audit().log(sender.getName(), "BACKUP", "flush cache + SQLite WAL checkpoint");
            }
        }));
    }
}
