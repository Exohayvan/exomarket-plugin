package com.starhavensmpcore.notifier;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.market.db.DatabaseManager;
import com.starhavensmpcore.market.economy.CurrencyFormatter;
import com.starhavensmpcore.market.economy.QuantityFormatter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class NotifierManager implements Listener, CommandExecutor {

    private static final String ADMIN_PERMISSION = "Starhaven.Admin";
    private static final int MAX_NEWS = 8;
    private static final int MAX_LINES = 10;

    private final StarhavenSMPCore plugin;
    private final DatabaseManager databaseManager;
    private Connection connection;

    public NotifierManager(StarhavenSMPCore plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        setupDatabase();
    }

    private void setupDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:plugins/StarhavenSMPCore/whatsnew.db");
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS whatsnew (id INTEGER PRIMARY KEY AUTOINCREMENT, message TEXT, created_at INTEGER)");
                statement.execute("CREATE TABLE IF NOT EXISTS notify_players (uuid TEXT PRIMARY KEY, items_sold TEXT DEFAULT '0', money_earned REAL DEFAULT 0, last_seen_news_id INTEGER DEFAULT 0)");
            }
            pruneNews();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> sendNotifier(player), 20L * 10L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("new")) {
            return false;
        }

        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!player.hasPermission(ADMIN_PERMISSION) && !player.isOp()) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
        }

        String message = joinArgs(args, 0);
        if (message == null || message.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Usage: /new <message>");
            return true;
        }

        long id = addNewsItem(message);
        if (id <= 0L) {
            sender.sendMessage(ChatColor.RED + "Failed to add what's new entry.");
            return true;
        }
        pruneNews();

        String formatted = formatNewsMessage(message);
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(ChatColor.YELLOW + "- New: " + formatted);
                updateLastSeenNews(online.getUniqueId(), id);
            }
        }

        sender.sendMessage(ChatColor.GREEN + "Added what's new entry.");
        return true;
    }

    private void sendNotifier(Player player) {
        if (player == null || !player.isOnline() || connection == null || databaseManager == null) {
            return;
        }

        DatabaseManager.Stats stats = databaseManager.getStats(player.getUniqueId().toString());
        PlayerSnapshot snapshot = getSnapshot(player.getUniqueId());

        BigInteger deltaItems = subtractNonNegative(stats.itemsSold, snapshot.itemsSold);
        double deltaMoney = Math.max(0d, stats.moneyEarned - snapshot.moneyEarned);

        List<NewsItem> newest = getLatestNews();
        long lastSeen = snapshot.lastSeenNewsId;
        List<NewsItem> unseen = new ArrayList<>();
        long maxSeen = lastSeen;
        for (NewsItem item : newest) {
            if (item.id > lastSeen) {
                unseen.add(item);
                if (item.id > maxSeen) {
                    maxSeen = item.id;
                }
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "What you missed while you were gone:");

        String itemLabel = deltaItems.equals(BigInteger.ONE) ? "Market Item" : "Market Items";
        lines.add(ChatColor.GRAY + "You sold " + QuantityFormatter.format(deltaItems) + " " + itemLabel +
                " and earned " + CurrencyFormatter.format(deltaMoney) + ".");

        for (NewsItem item : unseen) {
            if (lines.size() >= MAX_LINES) {
                break;
            }
            lines.add(ChatColor.YELLOW + "- New: " + formatNewsMessage(item.message));
        }

        for (String line : lines) {
            player.sendMessage(line);
        }

        updateSnapshot(player.getUniqueId(), stats.itemsSold, stats.moneyEarned, maxSeen);
    }

    private long addNewsItem(String message) {
        if (connection == null) {
            return 0L;
        }
        long now = System.currentTimeMillis() / 1000L;
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO whatsnew (message, created_at) VALUES (?, ?)")) {
            insert.setString(1, message);
            insert.setLong(2, now);
            insert.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0L;
        }

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT last_insert_rowid()")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 0L;
    }

    private void pruneNews() {
        if (connection == null) {
            return;
        }
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM whatsnew WHERE id NOT IN (SELECT id FROM whatsnew ORDER BY id DESC LIMIT ?)")) {
            delete.setInt(1, MAX_NEWS);
            delete.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private List<NewsItem> getLatestNews() {
        if (connection == null) {
            return Collections.emptyList();
        }
        List<NewsItem> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, message FROM whatsnew ORDER BY id DESC LIMIT ?")) {
            statement.setInt(1, MAX_NEWS);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(new NewsItem(rs.getLong("id"), rs.getString("message")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        Collections.reverse(items);
        return items;
    }

    private PlayerSnapshot getSnapshot(UUID uuid) {
        if (connection == null || uuid == null) {
            return PlayerSnapshot.empty();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT items_sold, money_earned, last_seen_news_id FROM notify_players WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    BigInteger itemsSold = parseBigInteger(rs.getString("items_sold"));
                    double moneyEarned = rs.getDouble("money_earned");
                    long lastSeen = rs.getLong("last_seen_news_id");
                    return new PlayerSnapshot(itemsSold, moneyEarned, lastSeen);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return PlayerSnapshot.empty();
    }

    private void updateSnapshot(UUID uuid, BigInteger itemsSold, double moneyEarned, long lastSeenNewsId) {
        if (connection == null || uuid == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO notify_players (uuid, items_sold, money_earned, last_seen_news_id) " +
                        "VALUES (?, ?, ?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET items_sold=excluded.items_sold, money_earned=excluded.money_earned, last_seen_news_id=excluded.last_seen_news_id")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, toSafeString(itemsSold));
            statement.setDouble(3, moneyEarned);
            statement.setLong(4, lastSeenNewsId);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updateLastSeenNews(UUID uuid, long lastSeenNewsId) {
        if (connection == null || uuid == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO notify_players (uuid, items_sold, money_earned, last_seen_news_id) " +
                        "VALUES (?, '0', 0, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET last_seen_news_id=excluded.last_seen_news_id")) {
            statement.setString(1, uuid.toString());
            statement.setLong(2, lastSeenNewsId);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private String joinArgs(String[] args, int startIndex) {
        if (args == null || startIndex >= args.length) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (args[i] == null || args[i].isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        if (builder.length() == 0) {
            return null;
        }
        return builder.toString();
    }

    private String formatNewsMessage(String message) {
        if (message == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private BigInteger subtractNonNegative(BigInteger current, BigInteger previous) {
        BigInteger safeCurrent = current == null ? BigInteger.ZERO : current;
        BigInteger safePrevious = previous == null ? BigInteger.ZERO : previous;
        BigInteger delta = safeCurrent.subtract(safePrevious);
        return delta.signum() < 0 ? BigInteger.ZERO : delta;
    }

    private BigInteger parseBigInteger(String value) {
        if (value == null || value.isEmpty()) {
            return BigInteger.ZERO;
        }
        try {
            return new BigInteger(value);
        } catch (NumberFormatException ex) {
            return BigInteger.ZERO;
        }
    }

    private String toSafeString(BigInteger value) {
        if (value == null) {
            return "0";
        }
        return value.toString();
    }

    private static class NewsItem {
        private final long id;
        private final String message;

        private NewsItem(long id, String message) {
            this.id = id;
            this.message = message;
        }
    }

    private static class PlayerSnapshot {
        private final BigInteger itemsSold;
        private final double moneyEarned;
        private final long lastSeenNewsId;

        private PlayerSnapshot(BigInteger itemsSold, double moneyEarned, long lastSeenNewsId) {
            this.itemsSold = itemsSold;
            this.moneyEarned = moneyEarned;
            this.lastSeenNewsId = lastSeenNewsId;
        }

        private static PlayerSnapshot empty() {
            return new PlayerSnapshot(BigInteger.ZERO, 0d, 0L);
        }
    }
}
