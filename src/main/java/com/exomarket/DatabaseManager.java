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

    public MarketItem getMarketItem(Material type) {
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

    public MarketItem getMarketItem(Material type, String sellerUUID) {
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

    public MarketItem getMarketItem(ItemStack itemStack, String sellerUUID) {
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

    public List<MarketItem> getMarketItemsByItemData(String itemData) {
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

    public void sellItemsDirectly(UUID playerUUID, ItemStack itemStack, int quantity) {
        MarketItem existingItem = getMarketItem(itemStack, playerUUID.toString());
        if (existingItem == null) {
            MarketItem newItem = new MarketItem(itemStack, quantity, 0, playerUUID.toString());
            addMarketItem(newItem);
        } else {
            existingItem.addQuantity(quantity);
            updateMarketItem(existingItem);
        }
    }

    public void addMarketItem(MarketItem marketItem) {
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

    public void updateMarketItem(MarketItem marketItem) {
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

    public void removeMarketItem(MarketItem marketItem) {
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

    public List<MarketItem> getMarketItems() {
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

    public List<MarketItem> getMarketItemsBySeller(Material type) {
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
}
