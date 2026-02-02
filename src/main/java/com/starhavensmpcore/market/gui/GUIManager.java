package com.starhavensmpcore.market.gui;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.market.DemandMetric;
import com.starhavensmpcore.market.MarketItem;
import com.starhavensmpcore.market.db.DatabaseManager;
import com.starhavensmpcore.market.economy.CurrencyFormatter;
import com.starhavensmpcore.market.economy.QuantityFormatter;
import com.starhavensmpcore.market.items.ItemDisplayNameFormatter;
import com.starhavensmpcore.market.items.ItemSanitizer;
import com.starhavensmpcore.market.items.OreBreakdown;
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

    private StarhavenSMPCore plugin;
    private Map<Player, Integer> currentPage = new HashMap<>();
    private Map<Player, AggregatedListing> selectedMarketItem = new HashMap<>();
    private Map<Player, Map<Integer, AggregatedListing>> pageItems = new HashMap<>();
    private Map<Player, String> currentFilter = new HashMap<>();
    private Map<Player, Integer> selectedEnchantLevel = new HashMap<>();
    private Map<Player, Integer> selectedOreUnitSize = new HashMap<>();
    private Map<Player, Integer> selectedOreOutputMultiplier = new HashMap<>();
    private Map<Player, ItemStack> selectedOreTemplate = new HashMap<>();

    private static class AggregatedListing {
        private final String itemData;
        private final ItemStack template;
        private final Set<String> sellers = new HashSet<>();
        private BigInteger totalQuantity = BigInteger.ZERO;
        private double pricePerItem;
        private DatabaseManager.DemandStats demand = new DatabaseManager.DemandStats();

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

        void setDemand(DatabaseManager.DemandStats demand) {
            this.demand = demand == null ? new DatabaseManager.DemandStats() : demand;
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
                lore.add(ChatColor.GRAY + "Price: " + ChatColor.GOLD + CurrencyFormatter.format(pricePerItem));
                lore.add(ChatColor.GRAY + "Supply: " + QuantityFormatter.format(totalQuantity));
                lore.add(ChatColor.GRAY + "Demand: " + QuantityFormatter.format(DemandMetric.summarize(demand)));
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
    public GUIManager(StarhavenSMPCore plugin) {
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
        selectedOreUnitSize.remove(player);
        selectedOreOutputMultiplier.remove(player);
        selectedOreTemplate.remove(player);
        openMarketPage(player);
    }

    public void openQuantityMenu(Player player, AggregatedListing listing) {
        if (isEnchantedBookListing(listing)) {
            openEnchantLevelMenu(player, listing);
            return;
        }
        if (isDiamondListing(listing) || isIronIngotListing(listing)) {
            openOreUnitMenu(player, listing);
            return;
        }
        if (isCopperIngotListing(listing) || isGoldIngotListing(listing)) {
            openOreUnitMenu(player, listing);
            return;
        }
        if (isRawIronListing(listing) || isRawGoldListing(listing) || isRawCopperListing(listing)) {
            openOreUnitMenu(player, listing);
            return;
        }

        openQuantityMenu(player, listing, listing.getTemplate(), BigInteger.ONE, 1);
    }

    private void openQuantityMenu(Player player, AggregatedListing listing, ItemStack unitTemplate, BigInteger unitSize, int outputMultiplier) {
        BigInteger availableQuantity = listing.getTotalQuantity();
        BigInteger availableUnits = availableQuantity.divide(unitSize);
        if (availableUnits.signum() <= 0) {
            player.sendMessage(ChatColor.RED + "There are not enough items in stock to fulfill your request.");
            return;
        }

        int inventorySize = 9;
        List<Integer> quantities = new ArrayList<>(Arrays.asList(1, 2, 4, 8, 16, 32, 64, 128, 256));

        quantities.removeIf(q -> availableUnits.compareTo(BigInteger.valueOf(q)) < 0);

        Inventory inventory = Bukkit.createInventory(null, inventorySize, "Select Quantity");
        double unitPrice = listing.getPricePerItem() * unitSize.doubleValue();
        for (int i = 0; i < quantities.size(); i++) {
            int quantity = quantities.get(i);
            double totalCost = unitPrice * quantity;
            int displayQuantity = quantity * outputMultiplier;
            ItemStack quantityItem = createQuantityItem(Material.PAPER, displayQuantity, "Buy " + displayQuantity + "x", totalCost);
            inventory.setItem(getSlot(i, inventorySize), quantityItem);
        }

        selectedMarketItem.put(player, listing);
        selectedEnchantLevel.remove(player);
        selectedOreUnitSize.remove(player);
        selectedOreOutputMultiplier.remove(player);
        selectedOreTemplate.remove(player);
        if (unitSize.compareTo(BigInteger.ONE) > 0 || outputMultiplier != 1 || unitTemplate.getType() != listing.getTemplate().getType()) {
            selectedOreUnitSize.put(player, unitSize.intValue());
            selectedOreOutputMultiplier.put(player, outputMultiplier);
            ItemStack template = unitTemplate.clone();
            template.setAmount(1);
            selectedOreTemplate.put(player, template);
        }
        player.openInventory(inventory);
    }

    private void openOreUnitMenu(Player player, AggregatedListing listing) {
        Inventory inventory = Bukkit.createInventory(null, 9, "Select Unit");

        if (isDiamondListing(listing)) {
            BigInteger availableDiamonds = listing.getTotalQuantity();
            ItemStack diamondOption = createOreUnitItem(
                    Material.DIAMOND,
                    "Buy Diamonds",
                    availableDiamonds,
                    listing.getPricePerItem());
            inventory.setItem(3, diamondOption);

            if (availableDiamonds.compareTo(OreBreakdown.DIAMOND_BLOCK_RATIO) >= 0) {
                BigInteger availableBlocks = availableDiamonds.divide(OreBreakdown.DIAMOND_BLOCK_RATIO);
                ItemStack blockOption = createOreUnitItem(
                        Material.DIAMOND_BLOCK,
                        "Buy Diamond Blocks",
                        availableBlocks,
                        listing.getPricePerItem() * OreBreakdown.DIAMOND_BLOCK_RATIO.doubleValue());
                inventory.setItem(5, blockOption);
            }
        } else if (isIronIngotListing(listing)) {
            populateIngotUnitMenu(
                    inventory,
                    player,
                    listing,
                    Material.IRON_NUGGET,
                    Material.IRON_INGOT,
                    Material.IRON_BLOCK,
                    OreBreakdown.IRON_NUGGET_RATIO,
                    OreBreakdown.IRON_BLOCK_RATIO,
                    "Iron");
        } else if (isCopperIngotListing(listing)) {
            populateIngotUnitMenu(
                    inventory,
                    player,
                    listing,
                    OreBreakdown.getCopperNuggetMaterial(),
                    Material.COPPER_INGOT,
                    Material.COPPER_BLOCK,
                    OreBreakdown.COPPER_NUGGET_RATIO,
                    OreBreakdown.COPPER_BLOCK_RATIO,
                    "Copper");
        } else if (isGoldIngotListing(listing)) {
            populateIngotUnitMenu(
                    inventory,
                    player,
                    listing,
                    Material.GOLD_NUGGET,
                    Material.GOLD_INGOT,
                    Material.GOLD_BLOCK,
                    OreBreakdown.GOLD_NUGGET_RATIO,
                    OreBreakdown.GOLD_BLOCK_RATIO,
                    "Gold");
        } else if (isRawIronListing(listing)) {
            populateRawUnitMenu(
                    inventory,
                    listing,
                    Material.RAW_IRON,
                    Material.RAW_IRON_BLOCK,
                    OreBreakdown.RAW_IRON_BLOCK_RATIO,
                    "Raw Iron");
        } else if (isRawGoldListing(listing)) {
            populateRawUnitMenu(
                    inventory,
                    listing,
                    Material.RAW_GOLD,
                    Material.RAW_GOLD_BLOCK,
                    OreBreakdown.RAW_GOLD_BLOCK_RATIO,
                    "Raw Gold");
        } else if (isRawCopperListing(listing)) {
            populateRawUnitMenu(
                    inventory,
                    listing,
                    Material.RAW_COPPER,
                    Material.RAW_COPPER_BLOCK,
                    OreBreakdown.RAW_COPPER_BLOCK_RATIO,
                    "Raw Copper");
        }

        selectedMarketItem.put(player, listing);
        selectedEnchantLevel.remove(player);
        selectedOreUnitSize.remove(player);
        selectedOreOutputMultiplier.remove(player);
        selectedOreTemplate.remove(player);
        player.openInventory(inventory);
    }

    private void populateIngotUnitMenu(Inventory inventory,
                                       Player player,
                                       AggregatedListing listing,
                                       Material nuggetType,
                                       Material ingotType,
                                       Material blockType,
                                       BigInteger nuggetRatio,
                                       BigInteger blockRatio,
                                       String label) {
        BigInteger availableIngots = listing.getTotalQuantity();
        ItemStack nuggetInfo = createNuggetInfoItem(player, nuggetType, nuggetRatio, label);
        inventory.setItem(2, nuggetInfo);

        ItemStack ingotOption = createOreUnitItem(
                ingotType,
                "Buy " + label + " Ingots",
                availableIngots,
                listing.getPricePerItem());
        inventory.setItem(4, ingotOption);

        if (availableIngots.compareTo(blockRatio) >= 0) {
            BigInteger availableBlocks = availableIngots.divide(blockRatio);
            ItemStack blockOption = createOreUnitItem(
                    blockType,
                    "Buy " + label + " Blocks",
                    availableBlocks,
                    listing.getPricePerItem() * blockRatio.doubleValue());
            inventory.setItem(6, blockOption);
        }
    }

    private void populateRawUnitMenu(Inventory inventory,
                                     AggregatedListing listing,
                                     Material rawType,
                                     Material rawBlockType,
                                     BigInteger blockRatio,
                                     String label) {
        BigInteger availableRaw = listing.getTotalQuantity();
        ItemStack rawOption = createOreUnitItem(
                rawType,
                "Buy " + label,
                availableRaw,
                listing.getPricePerItem());
        inventory.setItem(3, rawOption);

        if (availableRaw.compareTo(blockRatio) >= 0) {
            BigInteger availableBlocks = availableRaw.divide(blockRatio);
            ItemStack blockOption = createOreUnitItem(
                    rawBlockType,
                    "Buy " + label + " Blocks",
                    availableBlocks,
                    listing.getPricePerItem() * blockRatio.doubleValue());
            inventory.setItem(5, blockOption);
        }
    }

    private ItemStack createNuggetInfoItem(Player player, Material nuggetType, BigInteger ratio, String label) {
        if (nuggetType == null) {
            ItemStack fallback = new ItemStack(Material.PAPER);
            ItemMeta meta = fallback.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + "Queued " + label + " Nuggets");
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "This server API does not");
                lore.add(ChatColor.GRAY + "expose " + label.toLowerCase() + " nuggets.");
                lore.add(ChatColor.DARK_GRAY + "Not for sale");
                meta.setLore(lore);
                fallback.setItemMeta(meta);
            }
            return fallback;
        }

        ItemStack item = new ItemStack(nuggetType);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            BigInteger queued = getQueuedNuggets(player, nuggetType);
            BigInteger remainder = queued.remainder(ratio);
            BigInteger needed = remainder.signum() == 0 ? BigInteger.ZERO : ratio.subtract(remainder);
            meta.setDisplayName(ChatColor.YELLOW + "Queued " + label + " Nuggets");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Queued: " + QuantityFormatter.format(queued));
            if (queued.signum() == 0) {
                lore.add(ChatColor.GRAY + "Need: " + ratio + " to convert 1 ingot");
            } else if (needed.signum() == 0) {
                lore.add(ChatColor.GREEN + "Ready to convert on next recalculation");
            } else {
                lore.add(ChatColor.GRAY + "Need: " + QuantityFormatter.format(needed) + " more to convert 1 ingot");
            }
            lore.add(ChatColor.DARK_GRAY + "Not for sale");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private BigInteger getQueuedNuggets(Player player, Material nuggetType) {
        if (player == null) {
            return BigInteger.ZERO;
        }
        List<MarketItem> listings = plugin.getDatabaseManager()
                .getMarketItemsByOwner(player.getUniqueId().toString());
        BigInteger total = BigInteger.ZERO;
        for (MarketItem listing : listings) {
            if (listing.getType() == nuggetType) {
                total = total.add(listing.getQuantity().max(BigInteger.ZERO));
            }
        }
        return total;
    }

    private ItemStack createOreUnitItem(Material material, String name, BigInteger available, double unitPrice) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Available: " + QuantityFormatter.format(available));
            lore.add(ChatColor.GRAY + "Price: " + ChatColor.GOLD + CurrencyFormatter.format(unitPrice));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
    private ItemStack createQuantityItem(Material material, int quantity, String name, double totalCost) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Click to buy " + quantity);
        lore.add(ChatColor.GRAY + "Total: " + ChatColor.GOLD + CurrencyFormatter.format(totalCost));
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
        selectedOreUnitSize.remove(player);
        selectedOreOutputMultiplier.remove(player);
        selectedOreTemplate.remove(player);
        DatabaseManager databaseManager = plugin.getDatabaseManager();
        List<MarketItem> marketItems = databaseManager.getMarketItems();
        marketItems.removeIf(this::shouldHideFromMarket);
        marketItems = normalizeRawBlockListingsForDisplay(marketItems);
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
            listing.setDemand(databaseManager.getDemandForItem(listing.getItemData()));
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

    private List<MarketItem> normalizeRawBlockListingsForDisplay(List<MarketItem> marketItems) {
        if (marketItems == null || marketItems.isEmpty()) {
            return marketItems;
        }
        List<MarketItem> normalized = new ArrayList<>(marketItems.size());
        for (MarketItem listing : marketItems) {
            if (listing == null) {
                continue;
            }
            Material type = listing.getType();
            if (type == Material.RAW_IRON_BLOCK) {
                normalized.add(convertRawBlockListing(listing, Material.RAW_IRON, OreBreakdown.RAW_IRON_BLOCK_RATIO));
                continue;
            }
            if (type == Material.RAW_GOLD_BLOCK) {
                normalized.add(convertRawBlockListing(listing, Material.RAW_GOLD, OreBreakdown.RAW_GOLD_BLOCK_RATIO));
                continue;
            }
            if (type == Material.RAW_COPPER_BLOCK) {
                normalized.add(convertRawBlockListing(listing, Material.RAW_COPPER, OreBreakdown.RAW_COPPER_BLOCK_RATIO));
                continue;
            }
            normalized.add(listing);
        }
        return normalized;
    }

    private MarketItem convertRawBlockListing(MarketItem listing, Material rawType, BigInteger ratio) {
        BigInteger quantity = listing.getQuantity().max(BigInteger.ZERO);
        if (quantity.signum() <= 0) {
            return listing;
        }
        BigInteger rawAmount = quantity.multiply(ratio);
        double pricePerRaw = listing.getPrice() / ratio.doubleValue();
        return new MarketItem(new ItemStack(rawType), rawAmount, pricePerRaw, listing.getSellerUUID());
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

    private boolean shouldHideFromMarket(MarketItem listing) {
        return listing != null && (listing.getType() == Material.IRON_NUGGET
                || OreBreakdown.isCopperNugget(listing.getType())
                || listing.getType() == Material.GOLD_NUGGET);
    }

    private boolean isEnchantedBookListing(AggregatedListing listing) {
        return listing != null && listing.getTemplate().getType() == Material.ENCHANTED_BOOK;
    }

    private boolean isDiamondListing(AggregatedListing listing) {
        return listing != null && OreBreakdown.isDiamondListing(listing.getTemplate());
    }

    private boolean isIronIngotListing(AggregatedListing listing) {
        return listing != null && OreBreakdown.isIronIngotListing(listing.getTemplate());
    }

    private boolean isCopperIngotListing(AggregatedListing listing) {
        return listing != null && OreBreakdown.isCopperIngotListing(listing.getTemplate());
    }

    private boolean isGoldIngotListing(AggregatedListing listing) {
        return listing != null && OreBreakdown.isGoldIngotListing(listing.getTemplate());
    }

    private boolean isRawIronListing(AggregatedListing listing) {
        return listing != null && OreBreakdown.isRawIronListing(listing.getTemplate());
    }

    private boolean isRawGoldListing(AggregatedListing listing) {
        return listing != null && OreBreakdown.isRawGoldListing(listing.getTemplate());
    }

    private boolean isRawCopperListing(AggregatedListing listing) {
        return listing != null && OreBreakdown.isRawCopperListing(listing.getTemplate());
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
            lore.add(ChatColor.GRAY + "Price: " + ChatColor.GOLD + CurrencyFormatter.format(pricePerBook));
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
                    int page = currentPage.getOrDefault(player, 1);
                    currentPage.put(player, Math.max(1, page - 1));
                    openMarketPage(player);
                } else if (rawSlot == 50 && clickedItem.getType() == Material.ARROW) {
                    int page = currentPage.getOrDefault(player, 1);
                    currentPage.put(player, page + 1);
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
        } else if (title.startsWith("Select Unit")) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && !clickedItem.getType().isAir()) {
                AggregatedListing selected = selectedMarketItem.get(player);
                if (selected == null) {
                    return;
                }
                if (clickedItem.getType() == Material.DIAMOND) {
                    openQuantityMenu(player, selected, selected.getTemplate(), BigInteger.ONE, 1);
                } else if (clickedItem.getType() == Material.DIAMOND_BLOCK) {
                    openQuantityMenu(player, selected, new ItemStack(Material.DIAMOND_BLOCK), OreBreakdown.DIAMOND_BLOCK_RATIO, 1);
                } else if (clickedItem.getType() == Material.IRON_NUGGET) {
                    player.sendMessage(ChatColor.GRAY + "Iron nuggets are queued and not for sale.");
                } else if (clickedItem.getType() == Material.IRON_INGOT) {
                    openQuantityMenu(player, selected, new ItemStack(Material.IRON_INGOT), BigInteger.ONE, 1);
                } else if (clickedItem.getType() == Material.IRON_BLOCK) {
                    openQuantityMenu(player, selected, new ItemStack(Material.IRON_BLOCK), OreBreakdown.IRON_BLOCK_RATIO, 1);
                } else if (OreBreakdown.isCopperNugget(clickedItem.getType())) {
                    player.sendMessage(ChatColor.GRAY + "Copper nuggets are queued and not for sale.");
                } else if (clickedItem.getType() == Material.COPPER_INGOT) {
                    openQuantityMenu(player, selected, new ItemStack(Material.COPPER_INGOT), BigInteger.ONE, 1);
                } else if (clickedItem.getType() == Material.COPPER_BLOCK) {
                    openQuantityMenu(player, selected, new ItemStack(Material.COPPER_BLOCK), OreBreakdown.COPPER_BLOCK_RATIO, 1);
                } else if (clickedItem.getType() == Material.GOLD_NUGGET) {
                    player.sendMessage(ChatColor.GRAY + "Gold nuggets are queued and not for sale.");
                } else if (clickedItem.getType() == Material.GOLD_INGOT) {
                    openQuantityMenu(player, selected, new ItemStack(Material.GOLD_INGOT), BigInteger.ONE, 1);
                } else if (clickedItem.getType() == Material.GOLD_BLOCK) {
                    openQuantityMenu(player, selected, new ItemStack(Material.GOLD_BLOCK), OreBreakdown.GOLD_BLOCK_RATIO, 1);
                } else if (clickedItem.getType() == Material.RAW_IRON) {
                    openQuantityMenu(player, selected, new ItemStack(Material.RAW_IRON), BigInteger.ONE, 1);
                } else if (clickedItem.getType() == Material.RAW_IRON_BLOCK) {
                    openQuantityMenu(player, selected, new ItemStack(Material.RAW_IRON_BLOCK), OreBreakdown.RAW_IRON_BLOCK_RATIO, 1);
                } else if (clickedItem.getType() == Material.RAW_GOLD) {
                    openQuantityMenu(player, selected, new ItemStack(Material.RAW_GOLD), BigInteger.ONE, 1);
                } else if (clickedItem.getType() == Material.RAW_GOLD_BLOCK) {
                    openQuantityMenu(player, selected, new ItemStack(Material.RAW_GOLD_BLOCK), OreBreakdown.RAW_GOLD_BLOCK_RATIO, 1);
                } else if (clickedItem.getType() == Material.RAW_COPPER) {
                    openQuantityMenu(player, selected, new ItemStack(Material.RAW_COPPER), BigInteger.ONE, 1);
                } else if (clickedItem.getType() == Material.RAW_COPPER_BLOCK) {
                    openQuantityMenu(player, selected, new ItemStack(Material.RAW_COPPER_BLOCK), OreBreakdown.RAW_COPPER_BLOCK_RATIO, 1);
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
                        Integer unitSize = selectedOreUnitSize.remove(player);
                        Integer outputMultiplier = selectedOreOutputMultiplier.remove(player);
                        ItemStack unitTemplate = selectedOreTemplate.remove(player);
                        if (unitSize != null && outputMultiplier != null && unitTemplate != null) {
                            int baseQuantity = outputMultiplier == 0 ? quantity : quantity / outputMultiplier;
                            if (baseQuantity <= 0) {
                                player.sendMessage(ChatColor.RED + "Invalid purchase amount.");
                            } else {
                                plugin.getMarketManager().buyConvertedItem(player, selected.getItemData(), selected.getTemplate(), unitTemplate, unitSize, outputMultiplier, baseQuantity);
                            }
                        } else {
                            plugin.getMarketManager().buyStackedItem(player, selected.getItemData(), selected.getTemplate(), quantity);
                        }
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
        if (title.startsWith("Market Page") || title.equals("Select Quantity") || title.equals("Select Enchant Level") || title.equals("Select Unit")) {
            event.setCancelled(true);
        }
    }
}
