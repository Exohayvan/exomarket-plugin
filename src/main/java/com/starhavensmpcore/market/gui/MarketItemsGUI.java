package com.starhavensmpcore.market.gui;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.market.DemandMetric;
import com.starhavensmpcore.market.MarketItem;
import com.starhavensmpcore.market.MarketManager;
import com.starhavensmpcore.market.db.DatabaseManager;
import com.starhavensmpcore.market.economy.CurrencyFormatter;
import com.starhavensmpcore.market.economy.QuantityFormatter;
import com.starhavensmpcore.market.items.DurabilityQueue;
import com.starhavensmpcore.market.items.ItemDisplayNameFormatter;
import com.starhavensmpcore.market.items.ItemSanitizer;
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
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

public class MarketItemsGUI implements Listener {

    private static final String LIST_TITLE_PREFIX = "Your Market Listings";
    private static final String REMOVE_TITLE = "Remove Amount";
    private static final String REMOVE_LEVEL_TITLE = "Remove Enchant Level";

    private final StarhavenSMPCore plugin;
    private final MarketManager marketManager;
    private final DatabaseManager databaseManager;
    private final Map<UUID, MarketItem> selectedItem = new HashMap<>();
    private final Map<UUID, Integer> selectedEnchantLevel = new HashMap<>();
    private final Map<UUID, Integer> currentPage = new HashMap<>();
    private final Map<UUID, Map<Integer, MarketItem>> pageItems = new HashMap<>();
    private final Map<UUID, String> currentFilter = new HashMap<>();

    public MarketItemsGUI(StarhavenSMPCore plugin, MarketManager marketManager, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.marketManager = marketManager;
        this.databaseManager = databaseManager;
    }

    public void openListings(Player player) {
        openListings(player, 1);
    }

    public void openListings(Player player, String filter) {
        String normalized = normalizeFilter(filter);
        if (normalized == null) {
            currentFilter.remove(player.getUniqueId());
        } else {
            currentFilter.put(player.getUniqueId(), normalized);
        }
        openListings(player, 1);
    }

    private void openListings(Player player, int requestedPage) {
        selectedItem.remove(player.getUniqueId());
        selectedEnchantLevel.remove(player.getUniqueId());
        UUID playerId = player.getUniqueId();
        String filter = currentFilter.get(playerId);
        databaseManager.runOnDbThread(() -> {
            List<MarketItem> listings = databaseManager.getMarketItemsByOwner(playerId.toString());
            Map<String, DatabaseManager.DemandStats> demandMap = new HashMap<>();
            for (MarketItem listing : listings) {
                String itemData = listing.getItemData();
                if (itemData == null || itemData.isEmpty()) {
                    continue;
                }
                if (!demandMap.containsKey(itemData)) {
                    demandMap.put(itemData, databaseManager.getDemandForItem(itemData));
                }
            }
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    renderListings(playerId, requestedPage, filter, listings, demandMap));
        });
    }

    private void renderListings(UUID playerId,
                                int requestedPage,
                                String filter,
                                List<MarketItem> listings,
                                Map<String, DatabaseManager.DemandStats> demandMap) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        if (filter != null) {
            listings.removeIf(listing -> !matchesFilter(listing, filter));
        }
        if (listings.isEmpty()) {
            if (filter == null) {
                player.sendMessage(ChatColor.YELLOW + "You do not have any active listings.");
            } else {
                player.sendMessage(ChatColor.YELLOW + "No listings match \"" + filter + "\".");
            }
            pageItems.remove(playerId);
            currentPage.remove(playerId);
            player.closeInventory();
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(listings.size() / 45.0));
        int page = Math.min(Math.max(1, requestedPage), totalPages);
        currentPage.put(playerId, page);

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
            boolean isQueued = DurabilityQueue.isQueueItem(plugin, display);
            int queuedRemaining = 0;
            int queuedMax = 0;
            if (isQueued) {
                queuedMax = DurabilityQueue.getMaxDurability(display);
                if (queuedMax > 0) {
                    queuedRemaining = listing.getQuantity()
                            .min(BigInteger.valueOf(queuedMax))
                            .max(BigInteger.ZERO)
                            .intValue();
                }
                display = DurabilityQueue.applyRemainingDurability(
                        DurabilityQueue.stripQueueMarker(plugin, display),
                        queuedRemaining
                );
            }
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                if (!meta.hasDisplayName()) {
                    meta.setDisplayName(ChatColor.GOLD + ItemDisplayNameFormatter.format(display));
                }
                DatabaseManager.DemandStats demand = demandMap.getOrDefault(
                        listing.getItemData(),
                        new DatabaseManager.DemandStats()
                );
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                if (!lore.isEmpty()) {
                    lore.add(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "----------------");
                }
                if (isQueued) {
                    lore.add(ChatColor.GRAY + "Queued durability: " + queuedRemaining + "/" + queuedMax);
                    lore.add(ChatColor.DARK_GRAY + "Not listed for sale.");
                } else {
                    lore.add(ChatColor.GRAY + "Supply: " + QuantityFormatter.format(listing.getQuantity()));
                    lore.add(ChatColor.GRAY + "Demand: " + QuantityFormatter.format(DemandMetric.summarize(demand)));
                    lore.add(ChatColor.GRAY + "Price: " + ChatColor.GOLD + CurrencyFormatter.format(listing.getPrice()));
                }
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

        pageItems.put(playerId, slotMapping);
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

        amounts.removeIf(amount -> listing.getQuantity().compareTo(BigInteger.valueOf(amount)) < 0);

        for (int i = 0; i < amounts.size() && i < 9; i++) {
            int quantity = amounts.get(i);
            ItemStack button = new ItemStack(Material.PAPER);
            ItemMeta meta = button.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + "Remove " + quantity + "x");
            button.setItemMeta(meta);
            inventory.setItem(i, button);
        }

        selectedItem.put(player.getUniqueId(), listing);
        selectedEnchantLevel.remove(player.getUniqueId());
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
                if (DurabilityQueue.isQueueItem(plugin, listing.getItemStack())) {
                    removeQueuedDurability(player, listing);
                    return;
                }
                if (listing.getType() == Material.ENCHANTED_BOOK) {
                    openRemoveEnchantLevelMenu(player, listing);
                } else {
                    openRemoveAmountMenu(player, listing);
                }
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

            Integer enchantLevel = selectedEnchantLevel.remove(player.getUniqueId());
            if (enchantLevel != null && listing.getType() == Material.ENCHANTED_BOOK) {
                removeEnchantedBookQuantity(player, listing, enchantLevel, quantity);
            } else {
                removeListingQuantity(player, listing, quantity);
            }
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> openListings(player, currentPage.getOrDefault(player.getUniqueId(), 1)));
        } else if (title.equals(REMOVE_LEVEL_TITLE)) {
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

            Integer level = extractLevelFromItem(clicked);
            if (level == null) {
                return;
            }

            openRemoveEnchantQuantityMenu(player, listing, level);
        }
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

    private boolean matchesFilter(MarketItem listing, String filter) {
        ItemStack stack = listing.getItemStack();
        String typeName = listing.getType().toString().toLowerCase(Locale.ROOT);
        if (typeName.contains(filter)) {
            return true;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String stripped = ChatColor.stripColor(meta.getDisplayName());
            if (stripped != null && stripped.toLowerCase(Locale.ROOT).contains(filter)) {
                return true;
            }
        }

        if (!stack.getEnchantments().isEmpty()) {
            for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : stack.getEnchantments().entrySet()) {
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

    private void removeListingQuantity(Player player, MarketItem listing, int quantity) {
        if (DurabilityQueue.isQueueItem(plugin, listing.getItemStack())) {
            removeQueuedDurability(player, listing);
            return;
        }
        if (listing.getQuantity().compareTo(BigInteger.valueOf(quantity)) < 0) {
            player.sendMessage(ChatColor.RED + "You do not have that many items listed.");
            return;
        }

        listing.setQuantity(listing.getQuantity().subtract(BigInteger.valueOf(quantity)));
        databaseManager.runOnDbThread(() -> {
            if (listing.getQuantity().signum() <= 0) {
                databaseManager.removeMarketItem(listing);
            } else {
                databaseManager.updateMarketItem(listing);
            }
        });

        ItemStack item = listing.getItemStack();
        item.setAmount(quantity);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));

        player.sendMessage(ChatColor.GREEN + "Removed " + quantity + " " + listing.getType().toString() + " from your listings.");
        marketManager.recalculatePrices();
        selectedItem.remove(player.getUniqueId());
    }

    private void removeQueuedDurability(Player player, MarketItem listing) {
        int maxDurability = DurabilityQueue.getMaxDurability(listing.getItemStack());
        if (maxDurability <= 0) {
            player.sendMessage(ChatColor.RED + "That item cannot be removed from the queue.");
            return;
        }
        int remaining = listing.getQuantity()
                .min(BigInteger.valueOf(maxDurability))
                .max(BigInteger.ZERO)
                .intValue();
        databaseManager.runOnDbThread(() -> databaseManager.removeMarketItem(listing));

        ItemStack item = DurabilityQueue.applyRemainingDurability(
                DurabilityQueue.stripQueueMarker(plugin, listing.getItemStack()),
                remaining
        );
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));

        player.sendMessage(ChatColor.GREEN + "Removed queued item (" + remaining + "/" + maxDurability + " durability).");
        marketManager.recalculatePrices();
        selectedItem.remove(player.getUniqueId());
        refreshListings(player);
    }

    private void refreshListings(Player player) {
        if (player == null) {
            return;
        }
        int page = currentPage.getOrDefault(player.getUniqueId(), 1);
        openListings(player, page);
    }

    private void removeEnchantedBookQuantity(Player player, MarketItem listing, int level, int quantity) {
        BigInteger required = countForLevel(level);
        if (required.signum() <= 0) {
            player.sendMessage(ChatColor.RED + "That enchantment level is not available.");
            return;
        }
        BigInteger removeUnits = required.multiply(BigInteger.valueOf(quantity));
        if (listing.getQuantity().compareTo(removeUnits) < 0) {
            player.sendMessage(ChatColor.RED + "You do not have that many books listed.");
            return;
        }

        listing.setQuantity(listing.getQuantity().subtract(removeUnits));
        databaseManager.runOnDbThread(() -> {
            if (listing.getQuantity().signum() <= 0) {
                databaseManager.removeMarketItem(listing);
            } else {
                databaseManager.updateMarketItem(listing);
            }
        });

        ItemStack item = buildEnchantedBook(listing.getItemStack(), level, quantity);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));

        player.sendMessage(ChatColor.GREEN + "Removed " + quantity + " " +
                ItemDisplayNameFormatter.format(item) + " from your listings.");
        marketManager.recalculatePrices();
        selectedItem.remove(player.getUniqueId());
    }

    private void openRemoveEnchantLevelMenu(Player player, MarketItem listing) {
        BigInteger availableQuantity = listing.getQuantity();
        List<Integer> levels = new ArrayList<>();
        levels.add(1);
        levels.add(2);
        levels.add(4);
        levels.add(8);
        levels.add(16);
        levels.add(32);
        levels.add(64);
        levels.add(128);
        levels.add(255);

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

        Inventory inventory = Bukkit.createInventory(null, 9, REMOVE_LEVEL_TITLE);
        ItemStack template = listing.getItemStack();
        for (int i = 0; i < validLevels.size() && i < 9; i++) {
            int level = validLevels.get(i);
            BigInteger required = countForLevel(level);
            ItemStack levelItem = createEnchantLevelItem(template, level, required);
            inventory.setItem(i, levelItem);
        }

        selectedItem.put(player.getUniqueId(), listing);
        selectedEnchantLevel.remove(player.getUniqueId());
        player.openInventory(inventory);
    }

    private void openRemoveEnchantQuantityMenu(Player player, MarketItem listing, int level) {
        BigInteger required = countForLevel(level);
        if (required.signum() <= 0) {
            player.sendMessage(ChatColor.RED + "That level is not available.");
            return;
        }

        BigInteger maxBooks = listing.getQuantity().divide(required);
        int maxBooksInt = maxBooks.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
        if (maxBooksInt <= 0) {
            player.sendMessage(ChatColor.RED + "There are not enough books in stock for that level.");
            return;
        }

        List<Integer> quantities = new ArrayList<>();
        quantities.add(1);
        quantities.add(2);
        quantities.add(4);
        quantities.add(8);
        quantities.add(16);
        quantities.add(32);
        quantities.add(64);
        quantities.add(128);
        quantities.add(255);
        quantities.removeIf(q -> q > maxBooksInt);

        Inventory inventory = Bukkit.createInventory(null, 9, REMOVE_TITLE);
        for (int i = 0; i < quantities.size() && i < 9; i++) {
            int quantity = quantities.get(i);
            ItemStack button = new ItemStack(Material.PAPER);
            ItemMeta meta = button.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + "Remove " + quantity + "x");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Requires: " + QuantityFormatter.format(required) + " level I book(s)");
            meta.setLore(lore);
            button.setItemMeta(meta);
            inventory.setItem(i, button);
        }

        selectedItem.put(player.getUniqueId(), listing);
        selectedEnchantLevel.put(player.getUniqueId(), level);
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

    private BigInteger countForLevel(int level) {
        int safeLevel = Math.max(1, level);
        return BigInteger.ONE.shiftLeft(safeLevel - 1);
    }

    private ItemStack createEnchantLevelItem(ItemStack template, int level, BigInteger required) {
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

    private ItemStack buildEnchantedBook(ItemStack template, int level, int amount) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        ItemMeta templateMeta = template.getItemMeta();
        if (meta != null && templateMeta instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta templateStorage = (EnchantmentStorageMeta) templateMeta;
            templateStorage.getStoredEnchants().forEach((enchant, ignored) ->
                    meta.addStoredEnchant(enchant, level, true));
            book.setItemMeta(meta);
        }
        ItemStack sanitized = ItemSanitizer.sanitize(book);
        sanitized.setAmount(amount);
        return sanitized;
    }
}
