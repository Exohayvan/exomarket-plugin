package com.starhavensmpcore.market.db;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.market.MarketItem;
import com.starhavensmpcore.market.items.EnchantedBookSplitter;
import com.starhavensmpcore.market.items.ItemSanitizer;
import com.starhavensmpcore.market.items.OreBreakdown;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class DatabaseManager {

    private final StarhavenSMPCore plugin;
    private Connection connection;
    private long lastDemandCleanup = 0L;
    private static final long DEMAND_WINDOW_HOUR = 60L * 60L;
    private static final long DEMAND_WINDOW_DAY = 60L * 60L * 24L;
    private static final long DEMAND_WINDOW_MONTH = 60L * 60L * 24L * 30L;
    private static final long DEMAND_WINDOW_YEAR = 60L * 60L * 24L * 365L;

    public DatabaseManager(StarhavenSMPCore plugin) {
        this.plugin = plugin;
        connect();
    }

    private void connect() {
        File pluginDir = new File("plugins/StarhavenSMPCore");
        if (!pluginDir.exists()) {
            pluginDir.mkdirs();
        }

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:plugins/StarhavenSMPCore/database.db");
            createTable();
            ensureQuantityColumnType();
            ensureItemDataColumn();
            populateMissingItemData();
            ensureDemandTable();
            ensurePlayerTable();
            ensureStatsTable();
            ensureStatsQuantityColumns();
            seedGlobalStatsRow();
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }

    private void createTable() {
        try (PreparedStatement statement = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS market_items (type TEXT, quantity TEXT, price REAL, seller_uuid TEXT, item_data TEXT)")) {
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void ensureQuantityColumnType() {
        boolean needsMigration = false;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(market_items)")) {
            while (resultSet.next()) {
                if ("quantity".equalsIgnoreCase(resultSet.getString("name"))) {
                    String type = resultSet.getString("type");
                    if (type == null || !type.toUpperCase(Locale.ROOT).contains("TEXT")) {
                        needsMigration = true;
                    }
                    break;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        if (!needsMigration) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("BEGIN TRANSACTION");
            statement.execute("ALTER TABLE market_items RENAME TO market_items_old");
            statement.execute("CREATE TABLE market_items (type TEXT, quantity TEXT, price REAL, seller_uuid TEXT, item_data TEXT)");
            statement.execute("INSERT INTO market_items (type, quantity, price, seller_uuid, item_data) " +
                    "SELECT type, CAST(quantity AS TEXT), price, seller_uuid, item_data FROM market_items_old");
            statement.execute("DROP TABLE market_items_old");
            statement.execute("COMMIT");
        } catch (SQLException e) {
            try (Statement rollback = connection.createStatement()) {
                rollback.execute("ROLLBACK");
            } catch (SQLException ignored) {
                // Ignore rollback failures
            }
            e.printStackTrace();
        }
    }

    private void ensureItemDataColumn() {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(market_items)")) {
            boolean hasColumn = false;
            while (resultSet.next()) {
                if ("item_data".equalsIgnoreCase(resultSet.getString("name"))) {
                    hasColumn = true;
                    break;
                }
            }
            if (!hasColumn) {
                try (Statement alterStatement = connection.createStatement()) {
                    alterStatement.execute("ALTER TABLE market_items ADD COLUMN item_data TEXT");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void populateMissingItemData() {
        try (PreparedStatement select = connection.prepareStatement("SELECT rowid, type, item_data FROM market_items");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                long rowId = rs.getLong("rowid");
                Material type = Material.valueOf(rs.getString("type"));
                String storedData = rs.getString("item_data");

                ItemStack baseItem;
                if (storedData == null || storedData.isEmpty()) {
                    baseItem = new ItemStack(type);
                } else {
                    baseItem = ItemSanitizer.deserializeFromString(storedData);
                }

                String normalized = serializeItem(baseItem);
                if (storedData == null || !storedData.equals(normalized)) {
                    try (PreparedStatement update = connection.prepareStatement("UPDATE market_items SET item_data = ? WHERE rowid = ?")) {
                        update.setString(1, normalized);
                        update.setLong(2, rowId);
                        update.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void ensurePlayerTable() {
        try (PreparedStatement statement = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS market_players (uuid TEXT PRIMARY KEY, last_name TEXT, last_seen INTEGER)")) {
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void ensureStatsTable() {
        try (PreparedStatement statement = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS market_stats (key TEXT PRIMARY KEY, items_bought TEXT DEFAULT '0', items_sold TEXT DEFAULT '0', money_spent REAL DEFAULT 0, money_earned REAL DEFAULT 0)")) {
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void ensureDemandTable() {
        try (PreparedStatement statement = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS market_demand (item_data TEXT, quantity TEXT, ts INTEGER)")) {
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "CREATE INDEX IF NOT EXISTS idx_market_demand_item_ts ON market_demand(item_data, ts)")) {
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void ensureStatsQuantityColumns() {
        boolean needsMigration = false;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(market_stats)")) {
            while (resultSet.next()) {
                String name = resultSet.getString("name");
                if ("items_bought".equalsIgnoreCase(name) || "items_sold".equalsIgnoreCase(name)) {
                    String type = resultSet.getString("type");
                    if (type == null || !type.toUpperCase(Locale.ROOT).contains("TEXT")) {
                        needsMigration = true;
                        break;
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        if (!needsMigration) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("BEGIN TRANSACTION");
            statement.execute("ALTER TABLE market_stats RENAME TO market_stats_old");
            statement.execute("CREATE TABLE market_stats (key TEXT PRIMARY KEY, items_bought TEXT DEFAULT '0', items_sold TEXT DEFAULT '0', money_spent REAL DEFAULT 0, money_earned REAL DEFAULT 0)");
            statement.execute("INSERT INTO market_stats (key, items_bought, items_sold, money_spent, money_earned) " +
                    "SELECT key, CAST(items_bought AS TEXT), CAST(items_sold AS TEXT), money_spent, money_earned FROM market_stats_old");
            statement.execute("DROP TABLE market_stats_old");
            statement.execute("COMMIT");
        } catch (SQLException e) {
            try (Statement rollback = connection.createStatement()) {
                rollback.execute("ROLLBACK");
            } catch (SQLException ignored) {
                // Ignore rollback failures
            }
            e.printStackTrace();
        }
    }

    private void seedGlobalStatsRow() {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO market_stats (key, items_bought, items_sold, money_spent, money_earned) VALUES ('global', '0', '0', 0, 0)")) {
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getItemDataString(ItemStack itemStack) {
        return serializeItem(itemStack);
    }

    private String serializeItem(ItemStack itemStack) {
        return ItemSanitizer.serializeToString(itemStack);
    }

    private ItemStack deserializeItem(String data, Material fallbackType) {
        if (data == null || data.isEmpty()) {
            return ItemSanitizer.sanitize(new ItemStack(fallbackType));
        }
        return ItemSanitizer.deserializeFromString(data);
    }

    private MarketItem mapRowToMarketItem(ResultSet resultSet) throws SQLException {
        Material type = Material.valueOf(resultSet.getString("type"));
        String itemData = resultSet.getString("item_data");
        ItemStack itemStack = deserializeItem(itemData, type);
        return new MarketItem(
                itemStack,
                itemData,
                parseBigInteger(resultSet.getString("quantity")),
                resultSet.getDouble("price"),
                resultSet.getString("seller_uuid")
        );
    }

    public synchronized MarketItem getMarketItem(Material type) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM market_items WHERE type = ?")) {
            statement.setString(1, type.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapRowToMarketItem(resultSet);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public synchronized MarketItem getMarketItem(Material type, String sellerUUID) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM market_items WHERE type = ? AND seller_uuid = ?")) {
            statement.setString(1, type.toString());
            statement.setString(2, sellerUUID);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapRowToMarketItem(resultSet);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public synchronized MarketItem getMarketItem(ItemStack itemStack, String sellerUUID) {
        String serialized = serializeItem(itemStack);
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM market_items WHERE seller_uuid = ? AND item_data = ?")) {
            statement.setString(1, sellerUUID);
            statement.setString(2, serialized);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapRowToMarketItem(resultSet);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public synchronized List<MarketItem> getMarketItemsByItemData(String itemData) {
        List<MarketItem> marketItems = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM market_items WHERE item_data = ? ORDER BY rowid")) {
            statement.setString(1, itemData);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    marketItems.add(mapRowToMarketItem(resultSet));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return marketItems;
    }

    public synchronized void sellItemsDirectly(UUID playerUUID, ItemStack itemStack, int quantity) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }

        if (ItemSanitizer.isDamaged(itemStack)) {
            return;
        }

        if (playerUUID == null) {
            return;
        }

        try {
            String name = plugin.getServer().getOfflinePlayer(playerUUID).getName();
            if (name != null) {
                recordPlayerName(playerUUID, name);
            }
        } catch (Exception ignored) {
            // Ignore name lookup issues; still proceed with listing
        }

        String sellerId = playerUUID.toString();
        ItemStack toSplit = itemStack.clone();
        toSplit.setAmount(quantity);
        List<EnchantedBookSplitter.SplitEntry> entries = EnchantedBookSplitter.splitWithEnchantmentBooks(toSplit, BigInteger.valueOf(quantity));
        for (EnchantedBookSplitter.SplitEntry entry : entries) {
            List<OreBreakdown.SplitEntry> oreEntries = OreBreakdown.split(entry.getItemStack(), entry.getQuantity());
            for (OreBreakdown.SplitEntry oreEntry : oreEntries) {
                BigInteger amount = oreEntry.getQuantity();
                if (amount.signum() <= 0) {
                    continue;
                }
                ItemStack template = ItemSanitizer.sanitize(oreEntry.getItemStack());
                MarketItem existingItem = getMarketItem(template, sellerId);
                if (existingItem == null) {
                    MarketItem newItem = new MarketItem(template, amount, 0, sellerId);
                    addMarketItem(newItem);
                } else {
                    existingItem.addQuantity(amount);
                    updateMarketItem(existingItem);
                }
            }
        }
    }

    public synchronized void addMarketItem(MarketItem marketItem) {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO market_items (type, quantity, price, seller_uuid, item_data) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, marketItem.getType().toString());
            statement.setString(2, marketItem.getQuantity().toString());
            statement.setDouble(3, marketItem.getPrice());
            statement.setString(4, marketItem.getSellerUUID());
            statement.setString(5, marketItem.getItemData());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void updateMarketItem(MarketItem marketItem) {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE market_items SET quantity = ?, price = ?, item_data = ? WHERE type = ? AND seller_uuid = ? AND item_data = ?")) {
            statement.setString(1, marketItem.getQuantity().toString());
            statement.setDouble(2, marketItem.getPrice());
            statement.setString(3, marketItem.getItemData());
            statement.setString(4, marketItem.getType().toString());
            statement.setString(5, marketItem.getSellerUUID());
            statement.setString(6, marketItem.getItemData());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void removeMarketItem(MarketItem marketItem) {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM market_items WHERE type = ? AND seller_uuid = ? AND item_data = ?")) {
            statement.setString(1, marketItem.getType().toString());
            statement.setString(2, marketItem.getSellerUUID());
            statement.setString(3, marketItem.getItemData());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized List<MarketItem> getMarketItems() {
        List<MarketItem> marketItems = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM market_items");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                marketItems.add(mapRowToMarketItem(resultSet));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return marketItems;
    }

    public synchronized List<MarketItem> getMarketItemsBySeller(Material type) {
        List<MarketItem> marketItems = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM market_items WHERE type = ?")) {
            statement.setString(1, type.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    marketItems.add(mapRowToMarketItem(resultSet));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return marketItems;
    }

    public synchronized List<MarketItem> getMarketItemsByOwner(String ownerUUID) {
        List<MarketItem> marketItems = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM market_items WHERE seller_uuid = ?")) {
            statement.setString(1, ownerUUID);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    marketItems.add(mapRowToMarketItem(resultSet));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return marketItems;
    }

    public synchronized void recordPlayerName(UUID playerUUID, String name) {
        if (playerUUID == null || name == null || name.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO market_players (uuid, last_name, last_seen) VALUES (?, ?, strftime('%s','now')) " +
                        "ON CONFLICT(uuid) DO UPDATE SET last_name=excluded.last_name, last_seen=excluded.last_seen")) {
            statement.setString(1, playerUUID.toString());
            statement.setString(2, name);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized String getLastKnownName(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return null;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT last_name FROM market_players WHERE uuid = ?")) {
            statement.setString(1, uuid);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("last_name");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public synchronized void recordSale(String sellerUuid, String buyerUuid, int quantity, double totalPrice) {
        recordSale(sellerUuid, buyerUuid, BigInteger.valueOf(quantity), totalPrice);
    }

    public synchronized void recordSale(String sellerUuid, String buyerUuid, BigInteger quantity, double totalPrice) {
        if (quantity == null || quantity.signum() <= 0 || totalPrice < 0) {
            return;
        }

        // Global totals
        applyStatDelta("global", quantity, quantity, totalPrice, totalPrice);

        if (sellerUuid != null && !sellerUuid.isEmpty()) {
            applyStatDelta(sellerUuid, BigInteger.ZERO, quantity, 0, totalPrice);
        }

        if (buyerUuid != null && !buyerUuid.isEmpty()) {
            applyStatDelta(buyerUuid, quantity, BigInteger.ZERO, totalPrice, 0);
        }
    }

    private void applyStatDelta(String key, BigInteger bought, BigInteger sold, double spent, double earned) {
        if (key == null || key.isEmpty()) {
            return;
        }
        BigInteger safeBought = bought == null ? BigInteger.ZERO : bought;
        BigInteger safeSold = sold == null ? BigInteger.ZERO : sold;
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT OR IGNORE INTO market_stats (key, items_bought, items_sold, money_spent, money_earned) VALUES (?, '0', '0', 0, 0)")) {
            insert.setString(1, key);
            insert.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        BigInteger currentBought = BigInteger.ZERO;
        BigInteger currentSold = BigInteger.ZERO;
        double currentSpent = 0d;
        double currentEarned = 0d;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT items_bought, items_sold, money_spent, money_earned FROM market_stats WHERE key = ?")) {
            select.setString(1, key);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    currentBought = parseBigInteger(rs.getString("items_bought"));
                    currentSold = parseBigInteger(rs.getString("items_sold"));
                    currentSpent = rs.getDouble("money_spent");
                    currentEarned = rs.getDouble("money_earned");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        BigInteger newBought = currentBought.add(safeBought);
        BigInteger newSold = currentSold.add(safeSold);
        double newSpent = currentSpent + spent;
        double newEarned = currentEarned + earned;

        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE market_stats SET items_bought = ?, items_sold = ?, money_spent = ?, money_earned = ? WHERE key = ?")) {
            update.setString(1, newBought.toString());
            update.setString(2, newSold.toString());
            update.setDouble(3, newSpent);
            update.setDouble(4, newEarned);
            update.setString(5, key);
            update.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized Stats getStats(String key) {
        Stats stats = new Stats();
        if (key == null || key.isEmpty()) {
            return stats;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT items_bought, items_sold, money_spent, money_earned FROM market_stats WHERE key = ?")) {
            statement.setString(1, key);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    stats.itemsBought = parseBigInteger(rs.getString("items_bought"));
                    stats.itemsSold = parseBigInteger(rs.getString("items_sold"));
                    stats.moneySpent = rs.getDouble("money_spent");
                    stats.moneyEarned = rs.getDouble("money_earned");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return stats;
    }

    public synchronized void recordDemand(String itemData, BigInteger quantity) {
        if (itemData == null || itemData.isEmpty()) {
            return;
        }
        if (quantity == null || quantity.signum() <= 0) {
            return;
        }
        long nowSeconds = System.currentTimeMillis() / 1000L;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO market_demand (item_data, quantity, ts) VALUES (?, ?, ?)")) {
            statement.setString(1, itemData);
            statement.setString(2, quantity.toString());
            statement.setLong(3, nowSeconds);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        cleanupOldDemand(nowSeconds);
    }

    public synchronized DemandStats getDemandForItem(String itemData) {
        DemandStats stats = new DemandStats();
        if (itemData == null || itemData.isEmpty()) {
            return stats;
        }
        long nowSeconds = System.currentTimeMillis() / 1000L;
        long earliest = nowSeconds - DEMAND_WINDOW_YEAR;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT quantity, ts FROM market_demand WHERE item_data = ? AND ts >= ?")) {
            statement.setString(1, itemData);
            statement.setLong(2, earliest);
            try (ResultSet rs = statement.executeQuery()) {
                accumulateDemand(stats, rs, nowSeconds);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return stats;
    }

    public synchronized DemandStats getDemandTotals() {
        DemandStats stats = new DemandStats();
        long nowSeconds = System.currentTimeMillis() / 1000L;
        long earliest = nowSeconds - DEMAND_WINDOW_YEAR;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT quantity, ts FROM market_demand WHERE ts >= ?")) {
            statement.setLong(1, earliest);
            try (ResultSet rs = statement.executeQuery()) {
                accumulateDemand(stats, rs, nowSeconds);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return stats;
    }

    private void accumulateDemand(DemandStats stats, ResultSet rs, long nowSeconds) throws SQLException {
        while (rs.next()) {
            BigInteger amount = parseBigInteger(rs.getString("quantity"));
            if (amount.signum() <= 0) {
                continue;
            }
            long ts = rs.getLong("ts");
            if (ts >= nowSeconds - DEMAND_WINDOW_HOUR) {
                stats.hour = stats.hour.add(amount);
            }
            if (ts >= nowSeconds - DEMAND_WINDOW_DAY) {
                stats.day = stats.day.add(amount);
            }
            if (ts >= nowSeconds - DEMAND_WINDOW_MONTH) {
                stats.month = stats.month.add(amount);
            }
            if (ts >= nowSeconds - DEMAND_WINDOW_YEAR) {
                stats.year = stats.year.add(amount);
            }
        }
    }

    private void cleanupOldDemand(long nowSeconds) {
        if (nowSeconds - lastDemandCleanup < 3600L) {
            return;
        }
        lastDemandCleanup = nowSeconds;
        long cutoff = nowSeconds - DEMAND_WINDOW_YEAR;
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM market_demand WHERE ts < ?")) {
            statement.setLong(1, cutoff);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized BigInteger getTotalItemsInShop() {
        BigInteger total = BigInteger.ZERO;
        try (PreparedStatement statement = connection.prepareStatement("SELECT quantity FROM market_items");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                total = total.add(parseBigInteger(rs.getString("quantity")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return total;
    }

    public synchronized BigInteger getTotalItemsInShopForSeller(String sellerUuid) {
        if (sellerUuid == null || sellerUuid.isEmpty()) {
            return BigInteger.ZERO;
        }
        BigInteger total = BigInteger.ZERO;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT quantity FROM market_items WHERE seller_uuid = ?")) {
            statement.setString(1, sellerUuid);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    total = total.add(parseBigInteger(rs.getString("quantity")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return total;
    }

    private BigInteger parseBigInteger(String value) {
        if (value == null || value.isEmpty()) {
            return BigInteger.ZERO;
        }
        try {
            return new BigInteger(value);
        } catch (NumberFormatException ex) {
            try {
                return new BigDecimal(value).toBigInteger();
            } catch (NumberFormatException ignored) {
                return BigInteger.ZERO;
            }
        }
    }

    public static class Stats {
        public BigInteger itemsBought = BigInteger.ZERO;
        public BigInteger itemsSold = BigInteger.ZERO;
        public double moneySpent;
        public double moneyEarned;
    }

    public static class DemandStats {
        public BigInteger hour = BigInteger.ZERO;
        public BigInteger day = BigInteger.ZERO;
        public BigInteger month = BigInteger.ZERO;
        public BigInteger year = BigInteger.ZERO;
    }
}
