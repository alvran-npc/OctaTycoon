package id.octanexus.octatycoon.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public final class DatabaseManager {
    private final File file;
    private final ExecutorService executor;
    private volatile Connection connection;
    private volatile boolean closed;

    public DatabaseManager(File file) {
        this.file = file;
        AtomicInteger index = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "OctaTycoon-DB-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newSingleThreadExecutor(factory);
    }

    public CompletableFuture<Void> openAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IllegalStateException("Could not create database directory: " + parent);
                }
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA journal_mode=WAL");
                    statement.execute("PRAGMA synchronous=NORMAL");
                    statement.execute("PRAGMA foreign_keys=ON");
                    statement.execute("PRAGMA busy_timeout=5000");
                }
                createSchema();
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to open OctaTycoon database", exception);
            }
        }, executor);
    }

    private void createSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS players (uuid TEXT PRIMARY KEY, token_balance INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL DEFAULT 0)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_stats (uuid TEXT NOT NULL, source TEXT NOT NULL, stat TEXT NOT NULL, value REAL NOT NULL, PRIMARY KEY(uuid,source,stat))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS pending_rewards (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, reward_type TEXT NOT NULL, amount TEXT NOT NULL, source TEXT NOT NULL, created_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pending_rewards_uuid ON pending_rewards(uuid, id)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS audit_events (id INTEGER PRIMARY KEY AUTOINCREMENT, actor TEXT NOT NULL, action TEXT NOT NULL, detail TEXT NOT NULL, created_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS eco_daily (day TEXT NOT NULL, currency TEXT NOT NULL, faucets INTEGER NOT NULL DEFAULT 0, sinks INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(day,currency))");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_events(created_at DESC)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_data (uuid TEXT NOT NULL, data_key TEXT NOT NULL, data_value TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(uuid,data_key))");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_player_data_uuid ON player_data(uuid)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            boolean empty;
            try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM schema_version")) {
                empty = result.next() && result.getInt(1) == 0;
            }
            if (empty) statement.executeUpdate("INSERT INTO schema_version(version) VALUES (2)");
            else statement.executeUpdate("UPDATE schema_version SET version=2 WHERE version<2");
        }
    }

    public <T> CompletableFuture<T> submit(Function<Connection, T> work) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Database is closed"));
        return CompletableFuture.supplyAsync(() -> {
            Connection current = connection;
            if (current == null) throw new IllegalStateException("Database is not ready");
            try {
                return work.apply(current);
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("Database operation failed", exception);
            }
        }, executor);
    }

    public CompletableFuture<java.util.Map<String, String>> loadPlayerData(UUID uuid) {
        return submit(connection -> {
            java.util.Map<String, String> values = new java.util.HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT data_key,data_value FROM player_data WHERE uuid=?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) values.put(result.getString("data_key"), result.getString("data_value"));
                }
            } catch (SQLException exception) { throw new IllegalStateException(exception); }
            return values;
        });
    }

    public CompletableFuture<Void> savePlayerData(UUID uuid, java.util.Map<String, String> values) {
        if (values.isEmpty()) return CompletableFuture.completedFuture(null);
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO player_data(uuid,data_key,data_value,updated_at) VALUES(?,?,?,?) ON CONFLICT(uuid,data_key) DO UPDATE SET data_value=excluded.data_value,updated_at=excluded.updated_at")) {
                for (java.util.Map.Entry<String, String> entry : values.entrySet()) {
                    if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) continue;
                    statement.setString(1, uuid.toString());
                    statement.setString(2, entry.getKey());
                    statement.setString(3, entry.getValue());
                    statement.setLong(4, System.currentTimeMillis());
                    statement.addBatch();
                }
                statement.executeBatch();
                return null;
            } catch (SQLException exception) { throw new IllegalStateException(exception); }
        });
    }

    public CompletableFuture<java.util.Map<String, Double>> loadStats(UUID uuid) {
        return submit(connection -> {
            java.util.Map<String, Double> values = new java.util.HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT source,stat,value FROM player_stats WHERE uuid=?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) values.put(result.getString("source") + "|" + result.getString("stat"), result.getDouble("value"));
                }
            } catch (SQLException exception) { throw new IllegalStateException(exception); }
            return values;
        });
    }

    public CompletableFuture<Void> saveStats(UUID uuid, java.util.Map<String, Double> values) {
        if (values.isEmpty()) return CompletableFuture.completedFuture(null);
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO player_stats(uuid,source,stat,value) VALUES(?,?,?,?) ON CONFLICT(uuid,source,stat) DO UPDATE SET value=excluded.value")) {
                for (java.util.Map.Entry<String, Double> entry : values.entrySet()) {
                    String[] parts = entry.getKey().split("\\|", 2);
                    if (parts.length != 2 || !Double.isFinite(entry.getValue())) continue;
                    statement.setString(1, uuid.toString());
                    statement.setString(2, parts[0]);
                    statement.setString(3, parts[1]);
                    statement.setDouble(4, entry.getValue());
                    statement.addBatch();
                }
                statement.executeBatch();
                return null;
            } catch (SQLException exception) { throw new IllegalStateException(exception); }
        });
    }

    public CompletableFuture<Long> getTokenBalance(UUID uuid) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT token_balance FROM players WHERE uuid=?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getLong(1) : 0L;
                }
            } catch (SQLException exception) { throw new IllegalStateException(exception); }
        });
    }

    public CompletableFuture<Long> creditToken(UUID uuid, long amount, long cap) {
        long safeCap = Math.max(0L, cap);
        long safeAmount = Math.max(0L, amount);
        return submit(connection -> {
            try {
                connection.setAutoCommit(false);
                long current = 0;
                try (PreparedStatement select = connection.prepareStatement("SELECT token_balance FROM players WHERE uuid=?")) {
                    select.setString(1, uuid.toString());
                    try (ResultSet result = select.executeQuery()) { if (result.next()) current = result.getLong(1); }
                }
                long next = Math.min(safeCap, safeAdd(current, safeAmount));
                try (PreparedStatement upsert = connection.prepareStatement("INSERT INTO players(uuid,token_balance,updated_at) VALUES(?,?,?) ON CONFLICT(uuid) DO UPDATE SET token_balance=excluded.token_balance, updated_at=excluded.updated_at")) {
                    upsert.setString(1, uuid.toString());
                    upsert.setLong(2, next);
                    upsert.setLong(3, System.currentTimeMillis());
                    upsert.executeUpdate();
                }
                connection.commit();
                return next - current;
            } catch (SQLException exception) {
                try { connection.rollback(); } catch (SQLException ignored) {}
                throw new IllegalStateException(exception);
            } finally {
                try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        });
    }

    public CompletableFuture<Boolean> queuePendingReward(UUID uuid, String type, String amount, String source, int limit) {
        return submit(connection -> {
            try {
                int count;
                try (PreparedStatement countStatement = connection.prepareStatement("SELECT COUNT(*) FROM pending_rewards WHERE uuid=?")) {
                    countStatement.setString(1, uuid.toString());
                    try (ResultSet result = countStatement.executeQuery()) { result.next(); count = result.getInt(1); }
                }
                if (count >= limit) return false;
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO pending_rewards(uuid,reward_type,amount,source,created_at) VALUES(?,?,?,?,?)")) {
                    insert.setString(1, uuid.toString());
                    insert.setString(2, type);
                    insert.setString(3, amount);
                    insert.setString(4, source);
                    insert.setLong(5, System.currentTimeMillis());
                    insert.executeUpdate();
                }
                return true;
            } catch (SQLException exception) { throw new IllegalStateException(exception); }
        });
    }

    public CompletableFuture<List<PendingReward>> getPendingRewards(UUID uuid, int limit) {
        return submit(connection -> {
            List<PendingReward> rewards = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT id,reward_type,amount,source FROM pending_rewards WHERE uuid=? ORDER BY id LIMIT ?")) {
                statement.setString(1, uuid.toString());
                statement.setInt(2, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) rewards.add(new PendingReward(result.getLong("id"), result.getString("reward_type"), result.getString("amount"), result.getString("source")));
                }
            } catch (SQLException exception) { throw new IllegalStateException(exception); }
            return rewards;
        });
    }

    public CompletableFuture<Void> deletePendingReward(long id) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM pending_rewards WHERE id=?")) {
                statement.setLong(1, id);
                statement.executeUpdate();
                return null;
            } catch (SQLException exception) { throw new IllegalStateException(exception); }
        });
    }

    public CompletableFuture<Long> deliverPendingToken(PendingReward reward, UUID uuid, long cap) {
        long safeCap = Math.max(0L, cap);
        return submit(connection -> {
            try {
                connection.setAutoCommit(false);
                long current = 0;
                try (PreparedStatement select = connection.prepareStatement("SELECT token_balance FROM players WHERE uuid=?")) {
                    select.setString(1, uuid.toString());
                    try (ResultSet result = select.executeQuery()) { if (result.next()) current = result.getLong(1); }
                }
                long amount = Math.max(0L, Long.parseLong(reward.amount()));
                long next = Math.min(safeCap, safeAdd(current, amount));
                try (PreparedStatement upsert = connection.prepareStatement("INSERT INTO players(uuid,token_balance,updated_at) VALUES(?,?,?) ON CONFLICT(uuid) DO UPDATE SET token_balance=excluded.token_balance, updated_at=excluded.updated_at")) {
                    upsert.setString(1, uuid.toString());
                    upsert.setLong(2, next);
                    upsert.setLong(3, System.currentTimeMillis());
                    upsert.executeUpdate();
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM pending_rewards WHERE id=?")) {
                    delete.setLong(1, reward.id());
                    delete.executeUpdate();
                }
                connection.commit();
                return next - current;
            } catch (SQLException | NumberFormatException exception) {
                try { connection.rollback(); } catch (SQLException ignored) {}
                throw new IllegalStateException(exception);
            } finally {
                try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        });
    }

    public CompletableFuture<Void> recordEconomyFlow(String day, String currency, long amount, boolean faucet) {
        if (amount <= 0) return CompletableFuture.completedFuture(null);
        return submit(connection -> {
            String sql = faucet
                    ? "INSERT INTO eco_daily(day,currency,faucets,sinks) VALUES(?,?,?,0) ON CONFLICT(day,currency) DO UPDATE SET faucets=faucets+excluded.faucets"
                    : "INSERT INTO eco_daily(day,currency,faucets,sinks) VALUES(?,?,0,?) ON CONFLICT(day,currency) DO UPDATE SET sinks=sinks+excluded.sinks";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, day);
                statement.setString(2, currency);
                statement.setLong(3, amount);
                statement.executeUpdate();
                return null;
            } catch (SQLException exception) { throw new IllegalStateException(exception); }
        });
    }

    public CompletableFuture<java.util.Map<String, EconomyTotals>> economyTotalsSince(String startDay) {
        return submit(connection -> {
            java.util.Map<String, EconomyTotals> totals = new java.util.LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT currency, COALESCE(SUM(faucets),0), COALESCE(SUM(sinks),0) FROM eco_daily WHERE day>=? GROUP BY currency ORDER BY currency")) {
                statement.setString(1, startDay);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) totals.put(result.getString(1), new EconomyTotals(result.getLong(2), result.getLong(3)));
                }
            } catch (SQLException exception) { throw new IllegalStateException(exception); }
            return totals;
        });
    }

    public CompletableFuture<Void> audit(String actor, String action, String detail) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO audit_events(actor,action,detail,created_at) VALUES(?,?,?,?)")) {
                statement.setString(1, actor);
                statement.setString(2, action);
                statement.setString(3, detail);
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
                return null;
            } catch (SQLException exception) { throw new IllegalStateException(exception); }
        });
    }

    public CompletableFuture<List<String>> recentAudit(int limit) {
        return submit(connection -> {
            List<String> lines = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT actor,action,detail,created_at FROM audit_events ORDER BY id DESC LIMIT ?")) {
                statement.setInt(1, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) lines.add("[" + result.getLong("created_at") + "] " + result.getString("actor") + " » " + result.getString("action") + " » " + result.getString("detail"));
                }
            } catch (SQLException exception) { throw new IllegalStateException(exception); }
            return lines;
        });
    }

    public CompletableFuture<Void> checkpoint() {
        return submit(connection -> {
            try (Statement statement = connection.createStatement()) { statement.execute("PRAGMA wal_checkpoint(FULL)"); }
            catch (SQLException exception) { throw new IllegalStateException(exception); }
            return null;
        });
    }

    public CompletableFuture<Void> closeAsync() {
        if (closed) return CompletableFuture.completedFuture(null);
        closed = true;
        return CompletableFuture.runAsync(() -> {
            Connection current = connection;
            if (current != null) {
                try { current.close(); } catch (SQLException ignored) {}
                connection = null;
            }
        }, executor).whenComplete((ignored, error) -> executor.shutdown());
    }

    private static long safeAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    public record PendingReward(long id, String type, String amount, String source) {}
    public record EconomyTotals(long faucets, long sinks) {}
}
