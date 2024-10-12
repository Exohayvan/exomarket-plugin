package com.exomarket;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor;
import org.bukkit.inventory.InventoryView;

import java.util.*;

public class GUIManager implements Listener {

    private ExoMarketPlugin plugin;
    private Map<Player, Integer> currentPage = new HashMap<>();
    private Map<Player, String> inventoryTitle = new HashMap<>();

    public GUIManager(ExoMarketPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMarketGUI(Player player) {
        currentPage.put(player, 1);
        openMarketPage(player);
    }

    public void openQuantityMenu(Player player, MarketItem marketItem) {
        int availableQuantity = marketItem.getQuantity();
        int inventorySize = 9; // Start with the smallest size
        List<Integer> quantities = new ArrayList<>(Arrays.asList(1, 2, 4, 8, 16, 32, 64));

        // Filter out quantities that exceed the available stock
        quantities.removeIf(q -> q > availableQuantity);

        // Adjust inventory size based on how many options we have
        if (quantities.size() > 7) {
            inventorySize = 18;
        } else if (quantities.size() > 5) {
            inventorySize = 9;
        }

        Inventory inventory = Bukkit.createInventory(null, inventorySize, "Select Quantity");

        for (int i = 0; i < quantities.size(); i++) {
            int quantity = quantities.get(i);
            ItemStack quantityItem = createQuantityItem(Material.PAPER, quantity, "Buy " + quantity + "x");
            inventory.setItem(getSlot(i, inventorySize), quantityItem);
        }

        inventoryTitle.put(player, marketItem.getType().name());
        player.openInventory(inventory);
    }

    private ItemStack createQuantityItem(Material material, int quantity, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        meta.setLore(Collections.singletonList(ChatColor.GRAY + "Click to buy " + quantity));
        item.setItemMeta(meta);
        return item;
    }

    private int getSlot(int index, int inventorySize) {
        if (inventorySize == 9) {
            return index;
        } else {
            // For 18-slot inventory, center the items
            return 1 + index + (index / 7) * 2;
        }
    }

    public void openMarketPage(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, "Market Page " + currentPage.get(player));
        DatabaseManager databaseManager = plugin.getDatabaseManager();
        List<MarketItem> marketItems = databaseManager.getMarketItems();
        Collections.sort(marketItems, (a, b) -> a.getType().toString().compareTo(b.getType().toString()));

        int index = (currentPage.get(player) - 1) * 45;
        for (int i = 0; i < 45; i++) {
            if (index < marketItems.size()) {
                MarketItem marketItem = marketItems.get(index);
                ItemStack itemStack = new ItemStack(marketItem.getType());
                ItemMeta meta = itemStack.getItemMeta();
                meta.setDisplayName(ChatColor.GOLD + marketItem.getType().toString());
                
                String formattedPrice = String.format("%.2f", marketItem.getPrice());
                
                meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Price: $" + formattedPrice,
                    ChatColor.GRAY + "Quantity: " + marketItem.getQuantity()
                ));
                itemStack.setItemMeta(meta);
                inventory.setItem(i, itemStack);
                index++;
            } else {
                break;
            }
        }

        // Always add navigation arrows
        inventory.setItem(48, createNavigationItem(Material.ARROW, ChatColor.GREEN + "Previous Page"));
        inventory.setItem(50, createNavigationItem(Material.ARROW, ChatColor.GREEN + "Next Page"));

        // Disable arrows if necessary
        if (currentPage.get(player) <= 1) {
            inventory.setItem(48, createNavigationItem(Material.BARRIER, ChatColor.RED + "No Previous Page"));
        }
        if (index >= marketItems.size()) {
            inventory.setItem(50, createNavigationItem(Material.BARRIER, ChatColor.RED + "No Next Page"));
        }

        player.openInventory(inventory);
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        String title = view.getTitle();
        if (title.startsWith("Market Page")) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                if (event.getRawSlot() == 48 && clickedItem.getType() == Material.ARROW) {
                    currentPage.put(player, currentPage.get(player) - 1);
                    openMarketPage(player);
                } else if (event.getRawSlot() == 50 && clickedItem.getType() == Material.ARROW) {
                    currentPage.put(player, currentPage.get(player) + 1);
                    openMarketPage(player);
                } else {
                    String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
                    Material itemType = Material.getMaterial(itemName);
                    if (itemType != null) {
                        MarketItem marketItem = plugin.getDatabaseManager().getMarketItem(itemType);
                        if (marketItem != null) {
                            openQuantityMenu(player, marketItem);
                        }
                    }
                }
            }
        } else if (title.startsWith("Select Quantity")) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                String quantityString = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName()).split(" ")[1];
                int quantity = Integer.parseInt(quantityString.substring(0, quantityString.length() - 1)); // Remove the 'x' at the end
                String itemTypeName = inventoryTitle.get(player);
                if (itemTypeName != null) {
                    Material itemType = Material.getMaterial(itemTypeName);
                    if (itemType != null) {
                        MarketItem marketItem = plugin.getDatabaseManager().getMarketItem(itemType);
                        if (marketItem != null) {
                            plugin.getMarketManager().buyItem(player, marketItem, quantity);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryView view = event.getView();
        String title = view.getTitle();
        if (title.startsWith("Market Page") || title.equals("Select Quantity")) {
            event.setCancelled(true);
        }
    }
}