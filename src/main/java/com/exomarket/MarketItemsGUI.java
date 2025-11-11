package com.exomarket;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MarketItemsGUI implements Listener {

    private static final String LIST_TITLE_PREFIX = "Your Market Listings";
    private static final String REMOVE_TITLE = "Remove Amount";

    private final ExoMarketPlugin plugin;
    private final MarketManager marketManager;
    private final DatabaseManager databaseManager;
    private final Map<UUID, MarketItem> selectedItem = new HashMap<>();
    private final Map<UUID, Integer> currentPage = new HashMap<>();
    private final Map<UUID, Map<Integer, MarketItem>> pageItems = new HashMap<>();

    public MarketItemsGUI(ExoMarketPlugin plugin, MarketManager marketManager, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.marketManager = marketManager;
        this.databaseManager = databaseManager;
    }

    public void openListings(Player player) {
        openListings(player, 1);
    }

    private void openListings(Player player, int requestedPage) {
        List<MarketItem> listings = databaseManager.getMarketItemsByOwner(player.getUniqueId().toString());
        if (listings.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You do not have any active listings.");
            pageItems.remove(player.getUniqueId());
            currentPage.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(listings.size() / 45.0));
        int page = Math.min(Math.max(1, requestedPage), totalPages);
        currentPage.put(player.getUniqueId(), page);

        Inventory inventory = Bukkit.createInventory(null, 54, LIST_TITLE_PREFIX + " - Page " + page + "/" + totalPages);
        Map<Integer, MarketItem> slotMapping = new HashMap<>();

        int startIndex = (page - 1) * 45;
        for (int slot = 0; slot < 45; slot++) {
            int index = startIndex + slot;
            if (index >= listings.size()) {
                break;
            }

            MarketItem listing = listings.get(index);
            ItemStack display = listing.getItemStack();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + listing.getType().toString());
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Quantity: " + listing.getQuantity());
                lore.add(ChatColor.GRAY + "Price: $" + String.format("%.2f", listing.getPrice()));
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inventory.setItem(slot, display);
            slotMapping.put(slot, listing);
        }

        inventory.setItem(45, createInfoItem());

        if (page > 1) {
            inventory.setItem(48, createNavigationItem(Material.ARROW, ChatColor.GREEN + "Previous Page"));
        } else {
            inventory.setItem(48, createNavigationItem(Material.BARRIER, ChatColor.RED + "No Previous Page"));
        }

        if (page < totalPages) {
            inventory.setItem(50, createNavigationItem(Material.ARROW, ChatColor.GREEN + "Next Page"));
        } else {
            inventory.setItem(50, createNavigationItem(Material.BARRIER, ChatColor.RED + "No Next Page"));
        }

        pageItems.put(player.getUniqueId(), slotMapping);
        player.openInventory(inventory);
    }

    private void openRemoveAmountMenu(Player player, MarketItem listing) {
        Inventory inventory = Bukkit.createInventory(null, 9, REMOVE_TITLE);
        List<Integer> amounts = new ArrayList<>();
        amounts.add(1);
        amounts.add(2);
        amounts.add(4);
        amounts.add(8);
        amounts.add(16);
        amounts.add(32);
        amounts.add(64);

        amounts.removeIf(amount -> amount > listing.getQuantity());

        for (int i = 0; i < amounts.size() && i < 9; i++) {
            int quantity = amounts.get(i);
            ItemStack button = new ItemStack(Material.PAPER);
            ItemMeta meta = button.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + "Remove " + quantity + "x");
            button.setItemMeta(meta);
            inventory.setItem(i, button);
        }

        selectedItem.put(player.getUniqueId(), listing);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        String title = event.getView().getTitle();
        Player player = (Player) event.getWhoClicked();

        if (title.startsWith(LIST_TITLE_PREFIX)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) {
                return;
            }

            if (event.getRawSlot() == 48 && clicked.getType() == Material.ARROW) {
                int page = currentPage.getOrDefault(player.getUniqueId(), 1);
                openListings(player, page - 1);
                return;
            }

            if (event.getRawSlot() == 50 && clicked.getType() == Material.ARROW) {
                int page = currentPage.getOrDefault(player.getUniqueId(), 1);
                openListings(player, page + 1);
                return;
            }

            if (event.getRawSlot() == 48 || event.getRawSlot() == 50 || event.getRawSlot() == 45) {
                return;
            }

            Map<Integer, MarketItem> slots = pageItems.get(player.getUniqueId());
            if (slots == null) {
                return;
            }

            MarketItem listing = slots.get(event.getRawSlot());
            if (listing != null) {
                openRemoveAmountMenu(player, listing);
            }
        } else if (title.equals(REMOVE_TITLE)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) {
                return;
            }

            MarketItem listing = selectedItem.get(player.getUniqueId());
            if (listing == null) {
                player.closeInventory();
                return;
            }

            String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            if (displayName == null || !displayName.startsWith("Remove")) {
                return;
            }

            String[] parts = displayName.split(" ");
            if (parts.length < 2) {
                return;
            }

            int quantity;
            try {
                quantity = Integer.parseInt(parts[1].replace("x", ""));
            } catch (NumberFormatException e) {
                return;
            }

            if (quantity <= 0) {
                return;
            }

            removeListingQuantity(player, listing, quantity);
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> openListings(player, currentPage.getOrDefault(player.getUniqueId(), 1)));
        }
    }

    private void removeListingQuantity(Player player, MarketItem listing, int quantity) {
        if (listing.getQuantity() < quantity) {
            player.sendMessage(ChatColor.RED + "You do not have that many items listed.");
            return;
        }

        listing.setQuantity(listing.getQuantity() - quantity);
        if (listing.getQuantity() <= 0) {
            databaseManager.removeMarketItem(listing);
        } else {
            databaseManager.updateMarketItem(listing);
        }

        ItemStack item = listing.getItemStack();
        item.setAmount(quantity);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));

        player.sendMessage(ChatColor.GREEN + "Removed " + quantity + " " + listing.getType().toString() + " from your listings.");
        marketManager.recalculatePrices();
        selectedItem.remove(player.getUniqueId());
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

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Click an item to remove listings");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Use the arrows to navigate pages");
            lore.add(ChatColor.GRAY + "Removing does not cost money");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
