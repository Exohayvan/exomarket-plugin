package com.exomarket;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseManager {

    private ExoMarketPlugin plugin;
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
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }

    private void createTable() {
        try (PreparedStatement statement = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS market_items (type TEXT, quantity INTEGER, price REAL, seller_uuid TEXT)")) {
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public MarketItem getMarketItem(Material type) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM market_items WHERE type = ?")) {
            statement.setString(1, type.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new MarketItem(
                            Material.valueOf(resultSet.getString("type")),
                            resultSet.getInt("quantity"),
                            resultSet.getDouble("price"),
                            resultSet.getString("seller_uuid")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void sellItemsDirectly(UUID playerUUID, Material itemType, int quantity) {
        MarketItem existingItem = getMarketItem(itemType);
        if (existingItem == null) {
            // Item doesn't exist in the market, create a new entry
            MarketItem newItem = new MarketItem(itemType, quantity, 0, playerUUID.toString());
            addMarketItem(newItem);
        } else {
            // Item exists, update the quantity
            existingItem.addQuantity(quantity);
            updateMarketItem(existingItem);
        }
    }
    
    public void addMarketItem(MarketItem marketItem) {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO market_items (type, quantity, price, seller_uuid) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, marketItem.getType().toString());
            statement.setInt(2, marketItem.getQuantity());
            statement.setDouble(3, marketItem.getPrice());
            statement.setString(4, marketItem.getSellerUUID());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateMarketItem(MarketItem marketItem) {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE market_items SET quantity = ?, price = ? WHERE type = ? AND seller_uuid = ?")) {
            statement.setInt(1, marketItem.getQuantity());
            statement.setDouble( 2, marketItem.getPrice());
            statement.setString(3, marketItem.getType().toString());
            statement.setString(4, marketItem.getSellerUUID());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removeMarketItem(MarketItem marketItem) {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM market_items WHERE type = ? AND seller_uuid = ?")) {
            statement.setString(1, marketItem.getType().toString());
            statement.setString(2, marketItem.getSellerUUID());
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
                MarketItem marketItem = new MarketItem(
                    Material.valueOf(resultSet.getString("type")),
                    resultSet.getInt("quantity"),
                    resultSet.getDouble("price"),
                    resultSet.getString("seller_uuid")
                );
                marketItems.add(marketItem);
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
                    MarketItem marketItem = new MarketItem(
                            Material.valueOf(resultSet.getString("type")),
                            resultSet.getInt("quantity"),
                            resultSet.getDouble("price"),
                            resultSet.getString("seller_uuid")
                    );
                    marketItems.add(marketItem);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return marketItems;
    }
}