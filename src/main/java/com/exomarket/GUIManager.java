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
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.ChatColor;
import org.bukkit.inventory.InventoryView;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

public class GUIManager implements Listener {

    private ExoMarketPlugin plugin;
    private Map<Player, Integer> currentPage = new HashMap<>();
    private Map<Player, AggregatedListing> selectedMarketItem = new HashMap<>();
    private Map<Player, Map<Integer, AggregatedListing>> pageItems = new HashMap<>();
    private Map<Player, String> currentFilter = new HashMap<>();
    private Map<Player, Integer> selectedEnchantLevel = new HashMap<>();

    private static class AggregatedListing {
        private final String itemData;
        private final ItemStack template;
        private final Set<String> sellers = new HashSet<>();
        private BigInteger totalQuantity = BigInteger.ZERO;
        private double pricePerItem;

        AggregatedListing(String itemData, ItemStack template, double pricePerItem) {
            this.itemData = itemData;
            this.template = ItemSanitizer.sanitize(template);
            this.pricePerItem = pricePerItem;
        }

        void incorporate(MarketItem marketItem) {
            totalQuantity = totalQuantity.add(marketItem.getQuantity());
            sellers.add(marketItem.getSellerUUID());
            pricePerItem = marketItem.getPrice();
        }

        String getItemData() {
            return itemData;
        }

        ItemStack getTemplate() {
            return template.clone();
        }

        BigInteger getTotalQuantity() {
            return totalQuantity;
        }

        @SuppressWarnings("unused")
        double getPricePerItem() {
            return pricePerItem;
        }

        int getSellerCount() {
            return sellers.size();
        }

        String getTypeName() {
            return ItemDisplayNameFormatter.format(template);
        }

        String getSortKey() {
            return getTypeName();
        }

        ItemStack createDisplayItem() {
            ItemStack displayItem = getTemplate();
            ItemMeta meta = displayItem.getItemMeta();
            if (meta != null) {
                if (!meta.hasDisplayName()) {
                    meta.setDisplayName(ChatColor.GOLD + getTypeName());
                }
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                if (!lore.isEmpty()) {
                    lore.add(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "----------------");
                }
                lore.add(ChatColor.GRAY + "Price: " + CurrencyFormatter.format(pricePerItem));
                lore.add(ChatColor.GRAY + "Quantity: " + QuantityFormatter.format(totalQuantity));
                lore.add(ChatColor.GRAY + "Sellers: " + getSellerCount());
                if (!displayItem.getEnchantments().isEmpty()) {
                    displayItem.getEnchantments().forEach((enchantment, level) ->
                            lore.add(ChatColor.GRAY + "Enchant: " + enchantment.getKey().getKey() + " " + level));
                }
                if (meta instanceof EnchantmentStorageMeta) {
                    EnchantmentStorageMeta storageMeta = (EnchantmentStorageMeta) meta;
                    storageMeta.getStoredEnchants().forEach((enchantment, level) ->
                            lore.add(ChatColor.GRAY + "Stored: " + enchantment.getKey().getKey() + " " + level));
                    meta = storageMeta;
                }
                meta.setLore(lore);
                displayItem.setItemMeta(meta);
            }
            return displayItem;
        }
    }
    public GUIManager(ExoMarketPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMarketGUI(Player player) {
        openMarketGUI(player, null);
    }

    public void openMarketGUI(Player player, String filter) {
        currentPage.put(player, 1);
        String normalized = normalizeFilter(filter);
        if (normalized == null) {
            currentFilter.remove(player);
        } else {
            currentFilter.put(player, normalized);
        }
        selectedEnchantLevel.remove(player);
        openMarketPage(player);
    }

    public void openQuantityMenu(Player player, AggregatedListing listing) {
        if (isEnchantedBookListing(listing)) {
            openEnchantLevelMenu(player, listing);
            return;
        }

        BigInteger availableQuantity = listing.getTotalQuantity();
        int inventorySize = 9;
        List<Integer> quantities = new ArrayList<>(Arrays.asList(1, 2, 4, 8, 16, 32, 64, 128, 256));

        // Filter out quantities that exceed the available stock
        quantities.removeIf(q -> availableQuantity.compareTo(BigInteger.valueOf(q)) < 0);

        Inventory inventory = Bukkit.createInventory(null, inventorySize, "Select Quantity");

        for (int i = 0; i < quantities.size(); i++) {
            int quantity = quantities.get(i);
            double totalCost = listing.getPricePerItem() * quantity;
            ItemStack quantityItem = createQuantityItem(Material.PAPER, quantity, "Buy " + quantity + "x", totalCost);
            inventory.setItem(getSlot(i, inventorySize), quantityItem);
        }

        selectedMarketItem.put(player, listing);
        selectedEnchantLevel.remove(player);
        player.openInventory(inventory);
    }

    private ItemStack createQuantityItem(Material material, int quantity, String name, double totalCost) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Click to buy " + quantity);
        lore.add(ChatColor.GRAY + "Total: " + CurrencyFormatter.format(totalCost));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private int getSlot(int index, int inventorySize) {
        return index;
    }

    private void openEnchantLevelMenu(Player player, AggregatedListing listing) {
        BigInteger availableQuantity = listing.getTotalQuantity();
        List<Integer> levels = new ArrayList<>(Arrays.asList(1, 2, 4, 8, 16, 32, 64, 128, 255));
        List<Integer> validLevels = new ArrayList<>();
        for (int level : levels) {
            BigInteger required = countForLevel(level);
            if (required.signum() > 0 && required.compareTo(availableQuantity) <= 0) {
                validLevels.add(level);
            }
        }

        if (validLevels.isEmpty()) {
            player.sendMessage(ChatColor.RED + "There are not enough books in stock for that enchantment.");
            return;
        }

        Inventory inventory = Bukkit.createInventory(null, 9, "Select Enchant Level");
        ItemStack template = listing.getTemplate();
        for (int i = 0; i < validLevels.size() && i < 9; i++) {
            int level = validLevels.get(i);
            BigInteger required = countForLevel(level);
            double pricePerBook = listing.getPricePerItem() * toDoubleCapped(required);
            ItemStack levelItem = createEnchantLevelItem(template, level, required, pricePerBook);
            inventory.setItem(i, levelItem);
        }

        selectedMarketItem.put(player, listing);
        selectedEnchantLevel.remove(player);
        player.openInventory(inventory);
    }

    private void openEnchantQuantityMenu(Player player, AggregatedListing listing, int level) {
        BigInteger required = countForLevel(level);
        if (required.signum() <= 0) {
            player.sendMessage(ChatColor.RED + "That level is not available.");
            return;
        }

        BigInteger maxBooks = listing.getTotalQuantity().divide(required);
        int maxBooksInt = maxBooks.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
        if (maxBooksInt <= 0) {
            player.sendMessage(ChatColor.RED + "There are not enough books in stock for that level.");
            return;
        }

        List<Integer> quantities = new ArrayList<>(Arrays.asList(1, 2, 4, 8, 16, 32, 64, 128, 255));
        quantities.removeIf(q -> q > maxBooksInt);

        Inventory inventory = Bukkit.createInventory(null, 9, "Select Quantity");
        for (int i = 0; i < quantities.size() && i < 9; i++) {
            int quantity = quantities.get(i);
            double totalCost = listing.getPricePerItem() * toDoubleCapped(required) * quantity;
            ItemStack quantityItem = createQuantityItem(Material.PAPER, quantity, "Buy " + quantity + "x", totalCost);
            inventory.setItem(i, quantityItem);
        }

        selectedMarketItem.put(player, listing);
        selectedEnchantLevel.put(player, level);
        player.openInventory(inventory);
    }

    public void openMarketPage(Player player) {
        selectedMarketItem.remove(player);
        selectedEnchantLevel.remove(player);
        DatabaseManager databaseManager = plugin.getDatabaseManager();
        List<MarketItem> marketItems = databaseManager.getMarketItems();
        Map<String, AggregatedListing> aggregatedMap = new LinkedHashMap<>();
        for (MarketItem marketItem : marketItems) {
            AggregatedListing listing = aggregatedMap.computeIfAbsent(
                    marketItem.getItemData(),
                    key -> new AggregatedListing(key, marketItem.getItemStack(), marketItem.getPrice())
            );
            listing.incorporate(marketItem);
        }

        List<AggregatedListing> aggregatedListings = new ArrayList<>(aggregatedMap.values());
        String filter = currentFilter.get(player);
        if (filter != null) {
            aggregatedListings.removeIf(listing -> !matchesFilter(listing, filter));
        }
        aggregatedListings.sort(Comparator.comparing(AggregatedListing::getSortKey, String.CASE_INSENSITIVE_ORDER));
        int totalListings = aggregatedListings.size();
        int maxPage = Math.max(1, (int) Math.ceil(totalListings / 45.0));
        int pageNumber = currentPage.getOrDefault(player, 1);
        if (pageNumber > maxPage) {
            pageNumber = maxPage;
            currentPage.put(player, pageNumber);
        } else if (pageNumber < 1) {
            pageNumber = 1;
            currentPage.put(player, pageNumber);
        }

        Inventory inventory = Bukkit.createInventory(null, 54, "Market Page " + pageNumber);
        int startIndex = (pageNumber - 1) * 45;
        Map<Integer, AggregatedListing> slotsToItems = new HashMap<>();
        for (int slot = 0; slot < 45; slot++) {
            int listIndex = startIndex + slot;
            if (listIndex >= aggregatedListings.size()) {
                break;
            }

            AggregatedListing listing = aggregatedListings.get(listIndex);
            ItemStack displayItem = listing.createDisplayItem();
            inventory.setItem(slot, displayItem);
            slotsToItems.put(slot, listing);
        }
        pageItems.put(player, slotsToItems);

        // Always add navigation arrows
        inventory.setItem(48, createNavigationItem(Material.ARROW, ChatColor.GREEN + "Previous Page"));
        inventory.setItem(50, createNavigationItem(Material.ARROW, ChatColor.GREEN + "Next Page"));

        // Disable arrows if necessary
        if (pageNumber <= 1) {
            inventory.setItem(48, createNavigationItem(Material.BARRIER, ChatColor.RED + "No Previous Page"));
        }
        if (pageNumber >= maxPage) {
            inventory.setItem(50, createNavigationItem(Material.BARRIER, ChatColor.RED + "No Next Page"));
        }

        player.openInventory(inventory);
    }

    private String normalizeFilter(String filter) {
        if (filter == null) {
            return null;
        }
        String trimmed = filter.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private boolean matchesFilter(AggregatedListing listing, String filter) {
        String typeName = listing.getTypeName().toLowerCase(Locale.ROOT);
        if (typeName.contains(filter)) {
            return true;
        }

        ItemStack template = listing.getTemplate();
        ItemMeta meta = template.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String stripped = ChatColor.stripColor(meta.getDisplayName());
            if (stripped != null && stripped.toLowerCase(Locale.ROOT).contains(filter)) {
                return true;
            }
        }

        if (!template.getEnchantments().isEmpty()) {
            for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : template.getEnchantments().entrySet()) {
                String key = entry.getKey().getKey().getKey().toLowerCase(Locale.ROOT);
                if (key.contains(filter)) {
                    return true;
                }
            }
        }

        if (meta instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta storageMeta = (EnchantmentStorageMeta) meta;
            for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : storageMeta.getStoredEnchants().entrySet()) {
                String key = entry.getKey().getKey().getKey().toLowerCase(Locale.ROOT);
                if (key.contains(filter)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isEnchantedBookListing(AggregatedListing listing) {
        return listing != null && listing.getTemplate().getType() == Material.ENCHANTED_BOOK;
    }

    private BigInteger countForLevel(int level) {
        int safeLevel = Math.max(1, level);
        return BigInteger.ONE.shiftLeft(safeLevel - 1);
    }

    private double toDoubleCapped(BigInteger value) {
        if (value == null) {
            return 0d;
        }
        BigInteger limit = BigDecimal.valueOf(Double.MAX_VALUE).toBigInteger();
        if (value.compareTo(limit) > 0) {
            return Double.MAX_VALUE;
        }
        return value.doubleValue();
    }

    private ItemStack createEnchantLevelItem(ItemStack template, int level, BigInteger required, double pricePerBook) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta storageMeta = (EnchantmentStorageMeta) meta;
            ItemMeta templateMeta = template.getItemMeta();
            if (templateMeta instanceof EnchantmentStorageMeta) {
                ((EnchantmentStorageMeta) templateMeta).getStoredEnchants()
                        .forEach((enchant, ignored) -> storageMeta.addStoredEnchant(enchant, level, true));
            }
            book.setItemMeta(storageMeta);
        }

        ItemMeta displayMeta = book.getItemMeta();
        if (displayMeta != null) {
            displayMeta.setDisplayName(ChatColor.GOLD + ItemDisplayNameFormatter.format(book));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Level: " + level);
            lore.add(ChatColor.GRAY + "Requires: " + QuantityFormatter.format(required) + " level I book(s)");
            lore.add(ChatColor.GRAY + "Price: " + CurrencyFormatter.format(pricePerBook));
            displayMeta.setLore(lore);
            book.setItemMeta(displayMeta);
        }

        return book;
    }

    private Integer extractLevelFromItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return null;
        }
        for (String line : meta.getLore()) {
            String stripped = ChatColor.stripColor(line);
            if (stripped != null && stripped.toLowerCase(Locale.ROOT).startsWith("level:")) {
                String value = stripped.substring("level:".length()).trim();
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
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

            if (clickedItem != null && !clickedItem.getType().isAir()) {
                int rawSlot = event.getRawSlot();
                if (rawSlot == 48 && clickedItem.getType() == Material.ARROW) {
                    currentPage.put(player, currentPage.get(player) - 1);
                    openMarketPage(player);
                } else if (rawSlot == 50 && clickedItem.getType() == Material.ARROW) {
                    currentPage.put(player, currentPage.get(player) + 1);
                    openMarketPage(player);
                } else {
                    Map<Integer, AggregatedListing> slots = pageItems.get(player);
                    if (slots != null && rawSlot >= 0 && rawSlot < 45) {
                        AggregatedListing listing = slots.get(rawSlot);
                        if (listing != null) {
                            openQuantityMenu(player, listing);
                        }
                    }
                }
            }
        } else if (title.startsWith("Select Enchant Level")) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem != null && !clickedItem.getType().isAir()) {
                Integer level = extractLevelFromItem(clickedItem);
                AggregatedListing selected = selectedMarketItem.get(player);
                if (level != null && selected != null) {
                    openEnchantQuantityMenu(player, selected, level);
                }
            }
        } else if (title.startsWith("Select Quantity")) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem != null && !clickedItem.getType().isAir()) {
                String quantityString = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName()).split(" ")[1];
                int quantity = Integer.parseInt(quantityString.substring(0, quantityString.length() - 1)); // Remove the 'x' at the end
                AggregatedListing selected = selectedMarketItem.get(player);
                if (selected != null) {
                    Integer enchantLevel = selectedEnchantLevel.remove(player);
                    if (enchantLevel != null && isEnchantedBookListing(selected)) {
                        plugin.getMarketManager().buyEnchantedBookLevel(player, selected.getItemData(), selected.getTemplate(), enchantLevel, quantity);
                    } else {
                        plugin.getMarketManager().buyStackedItem(player, selected.getItemData(), selected.getTemplate(), quantity);
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> openMarketPage(player));
                    selectedMarketItem.remove(player);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryView view = event.getView();
        String title = view.getTitle();
        if (title.startsWith("Market Page") || title.equals("Select Quantity") || title.equals("Select Enchant Level")) {
            event.setCancelled(true);
        }
    }
}
