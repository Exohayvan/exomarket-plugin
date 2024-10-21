package com.exomarket;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AutoSellManager implements Listener, CommandExecutor {

    private final ExoMarketPlugin plugin;
    private final MarketManager marketManager;
    private final Map<UUID, Inventory> autoSellInventories;
    private Connection connection;
    private final Gson gson;

    public AutoSellManager(ExoMarketPlugin plugin, MarketManager marketManager) {
        this.plugin = plugin;
        this.marketManager = marketManager;
        this.autoSellInventories = new HashMap<>();
        this.gson = new Gson();
        setupDatabase();
        startAutoSellTask();
    }

    private void setupDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:plugins/ExoMarketPlugin/autosell.db");
            Statement stmt = connection.createStatement();
            stmt.execute("CREATE TABLE IF NOT EXISTS autosell_items (uuid TEXT, item TEXT)");
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        openAutoSellInventory(player);
        return true;
    }

    private void openAutoSellInventory(Player player) {
        Inventory inventory = autoSellInventories.computeIfAbsent(player.getUniqueId(), k -> Bukkit.createInventory(null, 54, "AutoSell Inventory"));
        loadItemsFromDatabase(player.getUniqueId(), inventory);
        player.openInventory(inventory);
    }

    private void loadItemsFromDatabase(UUID playerUUID, Inventory inventory) {
        try {
            PreparedStatement stmt = connection.prepareStatement("SELECT item FROM autosell_items WHERE uuid = ?");
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            Set<Material> uniqueItemTypes = new HashSet<>(); // Store unique item types

            while (rs.next()) {
                String itemString = rs.getString("item");
                ItemStack item = ItemStack.deserialize(convertStringToMap(itemString));
                uniqueItemTypes.add(item.getType()); // Store only the type of each item
            }
            
            // Clear inventory and add a single item for each unique type
            inventory.clear();
            for (Material material : uniqueItemTypes) {
                ItemStack singleItem = new ItemStack(material, 1); // Create a single item
                inventory.addItem(singleItem);
            }

            rs.close();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Map<String, Object> convertStringToMap(String itemString) {
        return gson.fromJson(itemString, new TypeToken<Map<String, Object>>() {}.getType());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals("AutoSell Inventory")) {
            event.setCancelled(true); // Cancel the event to prevent item moving
    
            Player player = (Player) event.getWhoClicked();
            UUID playerUUID = player.getUniqueId();
            Inventory inventory = autoSellInventories.get(playerUUID);
            ItemStack clickedItem = event.getCurrentItem();
    
            if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                // Check if the item is already in the auto-sell list
                if (isItemInAutoSellList(playerUUID, clickedItem)) {
                    // Remove the clicked item from the auto-sell list
                    removeAutoSellItem(playerUUID, clickedItem);
                    player.sendMessage(ChatColor.RED + "Removed " + clickedItem.getType().toString() + " from auto-sell list.");
                } else {
                    // Add the clicked item to the auto-sell list
                    addAutoSellItem(playerUUID, clickedItem);
                    player.sendMessage(ChatColor.GREEN + "Added " + clickedItem.getType().toString() + " to auto-sell list.");
                }
    
                // Reload the inventory to reflect the changes
                loadItemsFromDatabase(playerUUID, inventory);
                player.openInventory(inventory);
            }
        }
    }
    
    private boolean isItemInAutoSellList(UUID playerUUID, ItemStack item) {
        try {
            PreparedStatement stmt = connection.prepareStatement("SELECT COUNT(*) FROM autosell_items WHERE uuid = ? AND item = ?");
            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, gson.toJson(item.serialize())); // Use the serialized item for checking
            ResultSet rs = stmt.executeQuery();
            rs.next();
            boolean exists = rs.getInt(1) > 0; // Check if count is greater than 0
            rs.close();
            stmt.close();
            return exists;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    private void removeAutoSellItem(UUID playerUUID, ItemStack item) {
        try {
            PreparedStatement stmt = connection.prepareStatement("DELETE FROM autosell_items WHERE uuid = ? AND item = ?");
            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, gson.toJson(item.serialize()));
            stmt.executeUpdate();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Schedule an update to the AutoSell GUI
        updateAutoSellInventory(playerUUID);
    }

    private void addAutoSellItem(UUID playerUUID, ItemStack item) {
        try {
            PreparedStatement stmt = connection.prepareStatement("INSERT INTO autosell_items (uuid, item) VALUES (?, ?)");
            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, gson.toJson(item.serialize()));
            stmt.executeUpdate();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Schedule an update to the AutoSell GUI
        updateAutoSellInventory(playerUUID);
    }
    
    private void updateAutoSellInventory(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null) {
            Inventory autoSellInventory = autoSellInventories.get(playerUUID);
            if (autoSellInventory != null) {
                // Load items from the database in a separate task to avoid blocking the main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    loadItemsFromDatabase(playerUUID, autoSellInventory);
                    // Close the player's current inventory
                    player.closeInventory();
                    // Open the updated inventory
                    player.openInventory(autoSellInventory);
                });
            }
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals("AutoSell Inventory")) {
            Player player = (Player) event.getPlayer();
            UUID playerUUID = player.getUniqueId();
            autoSellInventories.remove(playerUUID);
        }
    }

    private void startAutoSellTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID playerUUID = player.getUniqueId();
                    Set<Material> autoSellItems = getAutoSellItemsFromDatabase(playerUUID);
                    
                    if (autoSellItems.isEmpty()) {
                        continue; // Skip if player has no auto-sell items
                    }

                    for (Material itemType : autoSellItems) {
                        int amountInInventory = getAmountInInventory(player, itemType);
                        
                        if (amountInInventory > 0) {
                            plugin.getDatabaseManager().sellItemsDirectly(playerUUID, itemType, amountInInventory);
                            removeItemsFromInventory(player, itemType, amountInInventory);
                            player.sendMessage(ChatColor.GREEN + "AutoSold " + amountInInventory + " " + itemType.toString());
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20, 40); // Run every 2 seconds
    }
    
    private Set<Material> getAutoSellItemsFromDatabase(UUID playerUUID) {
        Set<Material> autoSellItems = new HashSet<>();
        try {
            PreparedStatement stmt = connection.prepareStatement("SELECT item FROM autosell_items WHERE uuid = ?");
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String itemString = rs.getString("item");
                ItemStack item = ItemStack.deserialize(convertStringToMap(itemString));
                autoSellItems.add(item.getType());
            }

            rs.close();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return autoSellItems;
    }

    private void removeItemsFromInventory(Player player, Material material, int amount) {
        Inventory inventory = player.getInventory();
        int remainingAmount = amount;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() == material) {
                if (item.getAmount() <= remainingAmount) {
                    remainingAmount -= item.getAmount();
                    inventory.remove(item);
                } else {
                    item.setAmount(item.getAmount() - remainingAmount);
                    break;
                }
            }
            if (remainingAmount == 0) break;
        }
        player.updateInventory();
    
        // Refresh the AutoSell GUI
        UUID playerUUID = player.getUniqueId();
        Inventory autoSellInventory = autoSellInventories.get(playerUUID);
        if (autoSellInventory != null) {
            loadItemsFromDatabase(playerUUID, autoSellInventory);
            player.openInventory(autoSellInventory);
        }
    }

    private int getAmountInInventory(Player player, Material material) {
        int amount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                amount += item.getAmount();
            }
        }
        return amount;
    }
}