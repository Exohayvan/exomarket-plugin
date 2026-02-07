package com.starhavensmpcore.market;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.market.items.ItemSanitizer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

public class AutoSellManager implements Listener, CommandExecutor {

    private static final String INVENTORY_TITLE = "AutoSell Inventory";
    private static final int INVENTORY_SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 48;
    private static final int NEXT_SLOT = 50;
    private static final int INFO_SLOT = 49;

    private final StarhavenSMPCore plugin;
    private final Map<UUID, Inventory> autoSellInventories;
    private final Map<UUID, Integer> currentPage;
    private Connection connection;

    public AutoSellManager(StarhavenSMPCore plugin) {
        this.plugin = plugin;
        this.autoSellInventories = new HashMap<>();
        this.currentPage = new HashMap<>();
        setupDatabase();
        startAutoSellTask();
    }

    private void setupDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:plugins/StarhavenSMPCore/autosell.db");
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
        UUID playerUUID = player.getUniqueId();
        currentPage.put(playerUUID, 1);
        Inventory inventory = autoSellInventories.computeIfAbsent(playerUUID,
                k -> Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE));
        renderAutoSellInventory(playerUUID, inventory);
        player.openInventory(inventory);
    }

    private void renderAutoSellInventory(UUID playerUUID, Inventory inventory) {
        List<ItemStack> items = getAutoSellItemsFromDatabase(playerUUID);
        int totalPages = Math.max(1, (int) Math.ceil(items.size() / (double) PAGE_SIZE));
        int page = currentPage.getOrDefault(playerUUID, 1);
        page = Math.min(Math.max(1, page), totalPages);
        currentPage.put(playerUUID, page);

        inventory.clear();
        int startIndex = (page - 1) * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = startIndex + slot;
            if (index >= items.size()) {
                break;
            }
            inventory.setItem(slot, items.get(index));
        }

        inventory.setItem(PREVIOUS_SLOT, createNavigationItem(
                page > 1 ? Material.ARROW : Material.BARRIER,
                page > 1 ? ChatColor.GREEN + "Previous Page" : ChatColor.RED + "No Previous Page"));
        inventory.setItem(NEXT_SLOT, createNavigationItem(
                page < totalPages ? Material.ARROW : Material.BARRIER,
                page < totalPages ? ChatColor.GREEN + "Next Page" : ChatColor.RED + "No Next Page"));
        inventory.setItem(INFO_SLOT, createInfoItem(page, totalPages));
    }

    private String serializeItemTemplate(ItemStack item) {
        return ItemSanitizer.serializeToString(item);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(INVENTORY_TITLE)) {
            event.setCancelled(true); // Cancel the event to prevent item moving
    
            Player player = (Player) event.getWhoClicked();
            UUID playerUUID = player.getUniqueId();
            Inventory inventory = autoSellInventories.get(playerUUID);
            ItemStack clickedItem = event.getCurrentItem();

            if (inventory == null) {
                return;
            }

            int rawSlot = event.getRawSlot();
            if (rawSlot < inventory.getSize()) {
                if (rawSlot == PREVIOUS_SLOT && clickedItem != null && clickedItem.getType() == Material.ARROW) {
                    currentPage.put(playerUUID, currentPage.getOrDefault(playerUUID, 1) - 1);
                    renderAutoSellInventory(playerUUID, inventory);
                    player.updateInventory();
                    return;
                }
                if (rawSlot == NEXT_SLOT && clickedItem != null && clickedItem.getType() == Material.ARROW) {
                    currentPage.put(playerUUID, currentPage.getOrDefault(playerUUID, 1) + 1);
                    renderAutoSellInventory(playerUUID, inventory);
                    player.updateInventory();
                    return;
                }
                if (rawSlot == INFO_SLOT) {
                    return;
                }
            }
    
            if (clickedItem != null && !clickedItem.getType().isAir()) {
                // Check if the item is already in the auto-sell list
                if (isItemInAutoSellList(playerUUID, clickedItem)) {
                    // Remove the clicked item from the auto-sell list
                    removeAutoSellItem(playerUUID, clickedItem);
                    player.sendMessage(ChatColor.RED + "Removed " + clickedItem.getType().toString() + " from auto-sell list.");
                } else {
                    if (isDamaged(clickedItem)) {
                        player.sendMessage(ChatColor.RED + "Damaged items cannot be added to auto-sell.");
                    } else {
                        // Add the clicked item to the auto-sell list
                        addAutoSellItem(playerUUID, clickedItem);
                        player.sendMessage(ChatColor.GREEN + "Added " + clickedItem.getType().toString() + " to auto-sell list.");
                    }
                }

                // Reload the inventory to reflect the changes
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
        int removed = 0;
        try {
            PreparedStatement stmt = connection.prepareStatement("DELETE FROM autosell_items WHERE uuid = ? AND item = ?");
            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, serializeItemTemplate(item));
            removed = stmt.executeUpdate();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (removed == 0) {
            removeAutoSellItemByMatch(playerUUID, item);
        }

        // Schedule an update to the AutoSell GUI
        updateAutoSellInventory(playerUUID);
    }

    private void removeAutoSellItemByMatch(UUID playerUUID, ItemStack item) {
        if (item == null) {
            return;
        }
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT rowid, item FROM autosell_items WHERE uuid = ?")) {
            select.setString(1, playerUUID.toString());
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    long rowId = rs.getLong("rowid");
                    String storedItem = rs.getString("item");
                    ItemStack stored = ItemSanitizer.deserializeFromString(storedItem);
                    if (ItemSanitizer.matches(stored, item)) {
                        try (PreparedStatement delete = connection.prepareStatement(
                                "DELETE FROM autosell_items WHERE rowid = ?")) {
                            delete.setLong(1, rowId);
                            delete.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
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
                    renderAutoSellInventory(playerUUID, autoSellInventory);
                    player.updateInventory();
                });
            }
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(INVENTORY_TITLE)) {
            Player player = (Player) event.getPlayer();
            UUID playerUUID = player.getUniqueId();
            autoSellInventories.remove(playerUUID);
            currentPage.remove(playerUUID);
        }
    }

    private void startAutoSellTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                runAutoSellTick();
            }
        }.runTaskTimer(plugin, 20, 40); // Run every 2 seconds
    }

    void runAutoSellTick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerUUID = player.getUniqueId();
            List<ItemStack> autoSellItems = getAutoSellItemsFromDatabase(playerUUID);

            if (autoSellItems.isEmpty()) {
                continue; // Skip if player has no auto-sell items
            }

            for (ItemStack template : autoSellItems) {
                if (isDamaged(template)) {
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
        autoSellItems.sort(Comparator
                .comparing((ItemStack item) -> item.getType().toString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ItemSanitizer::serializeToString));
        return autoSellItems;
    }

    static List<ItemStack> access$0(AutoSellManager manager, UUID playerUUID) {
        return manager.getAutoSellItemsFromDatabase(playerUUID);
    }

    private void removeItemsFromInventory(Player player, ItemStack template, int amount) {
        Inventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        removeMatchingItems(contents, template, amount);
        inventory.setContents(contents);
        player.updateInventory();
    
        // Refresh the AutoSell GUI
        UUID playerUUID = player.getUniqueId();
        Inventory autoSellInventory = autoSellInventories.get(playerUUID);
        if (autoSellInventory != null) {
            renderAutoSellInventory(playerUUID, autoSellInventory);
            player.updateInventory();
        }
    }

    protected boolean isDamaged(ItemStack item) {
        return ItemSanitizer.isDamaged(item);
    }

    private int getAmountInInventory(Player player, ItemStack template) {
        return countMatchingItems(player.getInventory().getContents(), template);
    }

    static int countMatchingItems(ItemStack[] contents, ItemStack template) {
        return countMatchingItems(contents, template, ItemSanitizer::matches);
    }

    static int countMatchingItems(ItemStack[] contents, ItemStack template, BiPredicate<ItemStack, ItemStack> matcher) {
        if (contents == null || template == null || matcher == null) {
            return 0;
        }
        int amount = 0;
        for (ItemStack item : contents) {
            if (item != null && matcher.test(item, template)) {
                amount += item.getAmount();
            }
        }
        return amount;
    }

    static void removeMatchingItems(ItemStack[] contents, ItemStack template, int amount) {
        removeMatchingItems(contents, template, amount, ItemSanitizer::matches);
    }

    static void removeMatchingItems(ItemStack[] contents, ItemStack template, int amount, BiPredicate<ItemStack, ItemStack> matcher) {
        if (contents == null || template == null || amount <= 0 || matcher == null) {
            return;
        }
        int remainingAmount = amount;
        for (int slot = 0; slot < contents.length && remainingAmount > 0; slot++) {
            ItemStack item = contents[slot];
            if (item != null && matcher.test(item, template)) {
                if (item.getAmount() <= remainingAmount) {
                    remainingAmount -= item.getAmount();
                    contents[slot] = null;
                } else {
                    item.setAmount(item.getAmount() - remainingAmount);
                    remainingAmount = 0;
                }
            }
        }
    }

    private ItemStack createNavigationItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createInfoItem(int page, int totalPages) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "AutoSell Info");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click items to toggle auto-sell");
            lore.add(ChatColor.GRAY + "Page " + page + " of " + totalPages);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
