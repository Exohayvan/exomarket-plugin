package com.exomarket;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseManager {

    private final ExoMarketPlugin plugin;
    private Connection connection;

    public DatabaseManager(ExoMarketPlugin plugin) {
        this.plugin = plugin;
        connect();
    }

    private void connect() {
        File pluginDir = new File("plugins/ExoMarketPlugin");
        if (!pluginDir.exists()) {
            pluginDir.mkdirs();
        }

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:plugins/ExoMarketPlugin/database.db");
            createTable();
            ensureItemDataColumn();
            populateMissingItemData();
            ensurePlayerTable();
            ensureStatsTable();
            seedGlobalStatsRow();
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }

    private void createTable() {
        try (PreparedStatement statement = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS market_items (type TEXT, quantity INTEGER, price REAL, seller_uuid TEXT, item_data TEXT)")) {
            statement.executeUpdate();
        } catch (SQLException e) {
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
                "CREATE TABLE IF NOT EXISTS market_stats (key TEXT PRIMARY KEY, items_bought INTEGER DEFAULT 0, items_sold INTEGER DEFAULT 0, money_spent REAL DEFAULT 0, money_earned REAL DEFAULT 0)")) {
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void seedGlobalStatsRow() {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO market_stats (key, items_bought, items_sold, money_spent, money_earned) VALUES ('global', 0, 0, 0, 0)")) {
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
                resultSet.getInt("quantity"),
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
        List<EnchantedBookSplitter.SplitEntry> entries = EnchantedBookSplitter.split(toSplit);
        for (EnchantedBookSplitter.SplitEntry entry : entries) {
            int amount = entry.getQuantity();
            if (amount <= 0) {
                continue;
            }
            ItemStack template = ItemSanitizer.sanitize(entry.getItemStack());
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

    public synchronized void addMarketItem(MarketItem marketItem) {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO market_items (type, quantity, price, seller_uuid, item_data) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, marketItem.getType().toString());
            statement.setInt(2, marketItem.getQuantity());
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
            statement.setInt(1, marketItem.getQuantity());
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
        if (quantity <= 0 || totalPrice < 0) {
            return;
        }

        // Global totals
        applyStatDelta("global", quantity, quantity, totalPrice, totalPrice);

        if (sellerUuid != null && !sellerUuid.isEmpty()) {
            applyStatDelta(sellerUuid, 0, quantity, 0, totalPrice);
        }

        if (buyerUuid != null && !buyerUuid.isEmpty()) {
            applyStatDelta(buyerUuid, quantity, 0, totalPrice, 0);
        }
    }

    private void applyStatDelta(String key, int bought, int sold, double spent, double earned) {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO market_stats (key, items_bought, items_sold, money_spent, money_earned) VALUES (?, ?, ?, ?, ?) " +
                        "ON CONFLICT(key) DO UPDATE SET " +
                        "items_bought = items_bought + excluded.items_bought, " +
                        "items_sold = items_sold + excluded.items_sold, " +
                        "money_spent = money_spent + excluded.money_spent, " +
                        "money_earned = money_earned + excluded.money_earned")) {
            statement.setString(1, key);
            statement.setInt(2, bought);
            statement.setInt(3, sold);
            statement.setDouble(4, spent);
            statement.setDouble(5, earned);
            statement.executeUpdate();
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
                    stats.itemsBought = rs.getInt("items_bought");
                    stats.itemsSold = rs.getInt("items_sold");
                    stats.moneySpent = rs.getDouble("money_spent");
                    stats.moneyEarned = rs.getDouble("money_earned");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return stats;
    }

    public synchronized long getTotalItemsInShop() {
        try (PreparedStatement statement = connection.prepareStatement("SELECT SUM(quantity) AS total FROM market_items");
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                return rs.getLong("total");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public synchronized long getTotalItemsInShopForSeller(String sellerUuid) {
        if (sellerUuid == null || sellerUuid.isEmpty()) {
            return 0;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT SUM(quantity) AS total FROM market_items WHERE seller_uuid = ?")) {
            statement.setString(1, sellerUuid);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("total");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static class Stats {
        public int itemsBought;
        public int itemsSold;
        public double moneySpent;
        public double moneyEarned;
    }
}
