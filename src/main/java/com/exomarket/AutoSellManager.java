package com.exomarket;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class AutoSellManager implements Listener, CommandExecutor {

    private final ExoMarketPlugin plugin;
    private final Map<UUID, Inventory> autoSellInventories;
    private Connection connection;

    public AutoSellManager(ExoMarketPlugin plugin) {
        this.plugin = plugin;
        this.autoSellInventories = new HashMap<>();
        setupDatabase();
        startAutoSellTask();
    }

    private void setupDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:plugins/ExoMarketPlugin/autosell.db");
            Statement stmt = connection.createStatement();
            stmt.execute("CREATE TABLE IF NOT EXISTS autosell_items (uuid TEXT, item TEXT)");
            stmt.close();
            normalizeStoredItems();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void normalizeStoredItems() {
        try (PreparedStatement select = connection.prepareStatement("SELECT rowid, item FROM autosell_items");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                long rowId = rs.getLong("rowid");
                String storedData = rs.getString("item");
                ItemStack sanitized = ItemSanitizer.deserializeFromString(storedData);
                String normalized = ItemSanitizer.serializeToString(sanitized);
                if (!normalized.equals(storedData)) {
                    try (PreparedStatement update = connection.prepareStatement("UPDATE autosell_items SET item = ? WHERE rowid = ?")) {
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
            Set<String> uniqueItems = new HashSet<>();

            inventory.clear();
            while (rs.next()) {
                String itemString = rs.getString("item");
                if (uniqueItems.add(itemString)) {
                    ItemStack item = ItemSanitizer.deserializeFromString(itemString);
                    item.setAmount(1);
                    inventory.addItem(item);
                }
            }

            rs.close();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private String serializeItemTemplate(ItemStack item) {
        return ItemSanitizer.serializeToString(item);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals("AutoSell Inventory")) {
            event.setCancelled(true); // Cancel the event to prevent item moving
    
            Player player = (Player) event.getWhoClicked();
            UUID playerUUID = player.getUniqueId();
            Inventory inventory = autoSellInventories.get(playerUUID);
            ItemStack clickedItem = event.getCurrentItem();
    
            if (clickedItem != null && !clickedItem.getType().isAir()) {
                // Check if the item is already in the auto-sell list
                if (isItemInAutoSellList(playerUUID, clickedItem)) {
                    // Remove the clicked item from the auto-sell list
                    removeAutoSellItem(playerUUID, clickedItem);
                    player.sendMessage(ChatColor.RED + "Removed " + clickedItem.getType().toString() + " from auto-sell list.");
                } else {
                    if (ItemSanitizer.isDamaged(clickedItem)) {
                        player.sendMessage(ChatColor.RED + "Damaged items cannot be added to auto-sell.");
                    } else {
                        // Add the clicked item to the auto-sell list
                        addAutoSellItem(playerUUID, clickedItem);
                        player.sendMessage(ChatColor.GREEN + "Added " + clickedItem.getType().toString() + " to auto-sell list.");
                    }
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
            stmt.setString(2, serializeItemTemplate(item)); // Use normalized serialization for checking
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
            stmt.setString(2, serializeItemTemplate(item));
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
            stmt.setString(2, serializeItemTemplate(item));
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
                    List<ItemStack> autoSellItems = getAutoSellItemsFromDatabase(playerUUID);
                    
                    if (autoSellItems.isEmpty()) {
                        continue; // Skip if player has no auto-sell items
                    }

                    for (ItemStack template : autoSellItems) {
                        if (ItemSanitizer.isDamaged(template)) {
                            removeAutoSellItem(playerUUID, template);
                            String display = template.hasItemMeta() && template.getItemMeta().hasDisplayName()
                                    ? template.getItemMeta().getDisplayName()
                                    : template.getType().toString();
                            player.sendMessage(ChatColor.RED + "Removed damaged item from auto-sell list: " + display);
                            continue;
                        }

                        int amountInInventory = getAmountInInventory(player, template);
                        
                        if (amountInInventory > 0) {
                            plugin.getDatabaseManager().sellItemsDirectly(playerUUID, template, amountInInventory);
                            removeItemsFromInventory(player, template, amountInInventory);
                            String display = template.hasItemMeta() && template.getItemMeta().hasDisplayName()
                                    ? template.getItemMeta().getDisplayName()
                                    : template.getType().toString();
                            player.sendMessage(ChatColor.GREEN + "AutoSold " + amountInInventory + " " + display);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20, 40); // Run every 2 seconds
    }
    
    private List<ItemStack> getAutoSellItemsFromDatabase(UUID playerUUID) {
        List<ItemStack> autoSellItems = new ArrayList<>();
        Set<String> uniqueItems = new HashSet<>();
        try {
            PreparedStatement stmt = connection.prepareStatement("SELECT item FROM autosell_items WHERE uuid = ?");
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String itemString = rs.getString("item");
                if (uniqueItems.add(itemString)) {
                    ItemStack item = ItemSanitizer.deserializeFromString(itemString);
                    item.setAmount(1);
                    autoSellItems.add(item);
                }
            }

            rs.close();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return autoSellItems;
    }

    private void removeItemsFromInventory(Player player, ItemStack template, int amount) {
        Inventory inventory = player.getInventory();
        int remainingAmount = amount;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length && remainingAmount > 0; slot++) {
            ItemStack item = contents[slot];
            if (item != null && ItemSanitizer.matches(item, template)) {
                if (item.getAmount() <= remainingAmount) {
                    remainingAmount -= item.getAmount();
                    inventory.clear(slot);
                } else {
                    item.setAmount(item.getAmount() - remainingAmount);
                    remainingAmount = 0;
                }
            }
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

    private int getAmountInInventory(Player player, ItemStack template) {
        int amount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && ItemSanitizer.matches(item, template)) {
                amount += item.getAmount();
            }
        }
        return amount;
    }
}
