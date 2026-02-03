package com.starhavensmpcore.market.gui;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.market.DemandMetric;
import com.starhavensmpcore.market.MarketItem;
import com.starhavensmpcore.market.db.DatabaseManager;
import com.starhavensmpcore.market.economy.CurrencyFormatter;
import com.starhavensmpcore.market.economy.QuantityFormatter;
import com.starhavensmpcore.market.items.DurabilityQueue;
import com.starhavensmpcore.market.items.ItemDisplayNameFormatter;
import com.starhavensmpcore.market.items.ItemSanitizer;
import com.starhavensmpcore.market.items.OreBreakdown;
import com.starhavensmpcore.market.items.OreFamilyList;
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
import java.util.concurrent.ConcurrentHashMap;

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
    private Map<Player, OreUnitOptions> selectedOreUnitOptions = new HashMap<>();
    private static final long QUEUED_NUGGET_CACHE_MS = 2_000L;
    private final Map<UUID, Map<String, BigInteger>> queuedNuggetCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> queuedNuggetCacheLastRefresh = new ConcurrentHashMap<>();
    private final Set<UUID> queuedNuggetRefreshing = ConcurrentHashMap.newKeySet();

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
            OreFamilyList.OreFamily family = OreBreakdown.getFamilyForBase(template);
            if (family != null && family.getLabel() != null && !family.getLabel().isEmpty()) {
                return family.getLabel();
            }
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

    private static final class OreUnitOptions {
        private final ItemStack nugget;
        private final ItemStack base;
        private final ItemStack block;
        private final BigInteger blockRatio;

        private OreUnitOptions(ItemStack nugget, ItemStack base, ItemStack block, BigInteger blockRatio) {
            this.nugget = nugget;
            this.base = base;
            this.block = block;
            this.blockRatio = blockRatio;
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
        selectedOreUnitOptions.remove(player);
        openMarketPage(player);
    }

    public void openQuantityMenu(Player player, AggregatedListing listing) {
        if (isEnchantedBookListing(listing)) {
            openEnchantLevelMenu(player, listing);
            return;
        }
        if (OreBreakdown.getFamilyForBase(listing.getTemplate()) != null) {
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
        selectedOreUnitOptions.remove(player);
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
        selectedOreUnitOptions.remove(player);

        OreFamilyList.OreFamily family = OreBreakdown.getFamilyForBase(listing.getTemplate());
        if (family != null) {
            populateOreFamilyUnitMenu(inventory, player, listing, family);
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
                                       ItemStack nuggetItem,
                                       ItemStack ingotItem,
                                       ItemStack blockItem,
                                       BigInteger nuggetRatio,
                                       BigInteger blockRatio,
                                       String label) {
        BigInteger availableIngots = listing.getTotalQuantity();
        ItemStack nuggetInfo = createNuggetInfoItem(player, nuggetItem, nuggetRatio, label);
        if (nuggetInfo != null) {
            inventory.setItem(2, nuggetInfo);
        }

        ItemStack ingotOption = createOreUnitItem(
                ingotItem,
                "Buy " + label + " Ingots",
                availableIngots,
                listing.getPricePerItem());
        inventory.setItem(4, ingotOption);

        if (blockItem != null && availableIngots.compareTo(blockRatio) >= 0) {
            BigInteger availableBlocks = availableIngots.divide(blockRatio);
            ItemStack blockOption = createOreUnitItem(
                    blockItem,
                    "Buy " + label + " Blocks",
                    availableBlocks,
                    listing.getPricePerItem() * blockRatio.doubleValue());
            inventory.setItem(6, blockOption);
        }
    }

    private void populateOreFamilyUnitMenu(Inventory inventory,
                                           Player player,
                                           AggregatedListing listing,
                                           OreFamilyList.OreFamily family) {
        ItemStack baseItem = listing.getTemplate();
        baseItem.setAmount(1);
        ItemStack nuggetItem = OreBreakdown.createItemFromId(family.getNuggetId());
        ItemStack blockItem = OreBreakdown.createItemFromId(family.getBlockId());

        selectedOreUnitOptions.put(player, new OreUnitOptions(nuggetItem, baseItem, blockItem, family.getBlockRatio()));

        populateIngotUnitMenu(
                inventory,
                player,
                listing,
                nuggetItem,
                baseItem,
                blockItem,
                family.getNuggetRatio(),
                family.getBlockRatio(),
                family.getLabel());
    }


    private ItemStack createNuggetInfoItem(Player player, ItemStack nuggetItem, BigInteger ratio, String label) {
        if (nuggetItem == null) {
            return null;
        }

        ItemStack item = nuggetItem.clone();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            BigInteger queued = getQueuedNuggets(player, nuggetItem);
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

    private BigInteger getQueuedNuggets(Player player, ItemStack nuggetItem) {
        if (player == null || nuggetItem == null) {
            return BigInteger.ZERO;
        }
        refreshQueuedNuggetCache(player);
        Map<String, BigInteger> cached = queuedNuggetCache.get(player.getUniqueId());
        if (cached == null || cached.isEmpty()) {
            return BigInteger.ZERO;
        }
        String key = ItemSanitizer.serializeToString(nuggetItem);
        return cached.getOrDefault(key, BigInteger.ZERO);
    }

    private void refreshQueuedNuggetCache(Player player) {
        if (player == null || player.getUniqueId() == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastRefresh = queuedNuggetCacheLastRefresh.get(playerId);
        if (lastRefresh != null && now - lastRefresh < QUEUED_NUGGET_CACHE_MS) {
            return;
        }
        if (!queuedNuggetRefreshing.add(playerId)) {
            return;
        }
        plugin.getDatabaseManager().runOnDbThread(() -> {
            List<MarketItem> listings = plugin.getDatabaseManager()
                    .getMarketItemsByOwner(playerId.toString());
            Map<String, BigInteger> totals = new HashMap<>();
            for (MarketItem listing : listings) {
                String itemData = listing.getItemData();
                if (itemData == null || itemData.isEmpty()) {
                    continue;
                }
                totals.merge(itemData, listing.getQuantity().max(BigInteger.ZERO), BigInteger::add);
            }
            queuedNuggetCache.put(playerId, totals);
            queuedNuggetCacheLastRefresh.put(playerId, System.currentTimeMillis());
            queuedNuggetRefreshing.remove(playerId);
        });
    }

    private boolean matchesTemplate(ItemStack item, ItemStack template) {
        if (item == null || template == null) {
            return false;
        }
        String itemId = plugin.getCustomItemManager().getCustomItemId(item);
        String templateId = plugin.getCustomItemManager().getCustomItemId(template);
        if (itemId != null || templateId != null) {
            return itemId != null && templateId != null && itemId.equalsIgnoreCase(templateId);
        }
        return item.getType() == template.getType();
    }

    private ItemStack createOreUnitItem(ItemStack template, String name, BigInteger available, double unitPrice) {
        ItemStack item = template == null ? new ItemStack(Material.PAPER) : template.clone();
        item.setAmount(1);
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
        if (player == null) {
            return;
        }
        selectedMarketItem.remove(player);
        selectedEnchantLevel.remove(player);
        selectedOreUnitSize.remove(player);
        selectedOreOutputMultiplier.remove(player);
        selectedOreTemplate.remove(player);
        selectedOreUnitOptions.remove(player);
        String filter = currentFilter.get(player);
        int requestedPage = currentPage.getOrDefault(player, 1);
        DatabaseManager databaseManager = plugin.getDatabaseManager();
        databaseManager.runOnDbThread(() -> {
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
            if (filter != null) {
                aggregatedListings.removeIf(listing -> !matchesFilter(listing, filter));
            }
            aggregatedListings.sort(Comparator.comparing(AggregatedListing::getSortKey, String.CASE_INSENSITIVE_ORDER));
            int totalListings = aggregatedListings.size();
            int maxPage = Math.max(1, (int) Math.ceil(totalListings / 45.0));
            int pageNumber = Math.min(Math.max(1, requestedPage), maxPage);
            int startIndex = (pageNumber - 1) * 45;
            List<AggregatedListing> pageListings = new ArrayList<>();
            for (int index = startIndex; index < aggregatedListings.size() && pageListings.size() < 45; index++) {
                AggregatedListing listing = aggregatedListings.get(index);
                listing.setDemand(databaseManager.getDemandForItem(listing.getItemData()));
                pageListings.add(listing);
            }

            plugin.getServer().getScheduler().runTask(plugin, () ->
                    renderMarketPage(player, pageNumber, maxPage, pageListings));
        });
    }

    private void renderMarketPage(Player player, int pageNumber, int maxPage, List<AggregatedListing> listings) {
        if (player == null || !player.isOnline()) {
            return;
        }
        currentPage.put(player, pageNumber);

        Inventory inventory = Bukkit.createInventory(null, 54, "Market Page " + pageNumber);
        Map<Integer, AggregatedListing> slotsToItems = new HashMap<>();
        int slot = 0;
        for (AggregatedListing listing : listings) {
            if (slot >= 45) {
                break;
            }
            ItemStack displayItem = listing.createDisplayItem();
            inventory.setItem(slot, displayItem);
            slotsToItems.put(slot, listing);
            slot++;
        }
        pageItems.put(player, slotsToItems);

        inventory.setItem(48, createNavigationItem(Material.ARROW, ChatColor.GREEN + "Previous Page"));
        inventory.setItem(50, createNavigationItem(Material.ARROW, ChatColor.GREEN + "Next Page"));
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
            OreFamilyList.OreFamily family = OreBreakdown.getFamilyForBlock(listing.getItemStack());
            if (family != null && family.getId().toLowerCase(Locale.ROOT).startsWith("raw_")) {
                ItemStack baseItem = OreBreakdown.createItemFromId(family.getBaseId());
                if (baseItem != null) {
                    normalized.add(convertRawBlockListing(listing, baseItem, family.getBlockRatio()));
                    continue;
                }
            }
            normalized.add(listing);
        }
        return normalized;
    }

    private MarketItem convertRawBlockListing(MarketItem listing, ItemStack baseItem, BigInteger ratio) {
        BigInteger quantity = listing.getQuantity().max(BigInteger.ZERO);
        if (quantity.signum() <= 0) {
            return listing;
        }
        BigInteger rawAmount = quantity.multiply(ratio);
        double pricePerRaw = listing.getPrice() / ratio.doubleValue();
        return new MarketItem(baseItem, rawAmount, pricePerRaw, listing.getSellerUUID());
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
        return listing != null && (OreBreakdown.isOreFamilyNugget(listing.getItemStack())
                || DurabilityQueue.isQueueItem(plugin, listing.getItemStack()));
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
                OreUnitOptions options = selectedOreUnitOptions.get(player);
                if (options != null) {
                    if (options.nugget != null && matchesTemplate(clickedItem, options.nugget)) {
                        player.sendMessage(ChatColor.GRAY + "Nuggets are queued and not for sale.");
                        return;
                    }
                    if (options.base != null && matchesTemplate(clickedItem, options.base)) {
                        openQuantityMenu(player, selected, options.base, BigInteger.ONE, 1);
                        return;
                    }
                    if (options.block != null && options.blockRatio != null && matchesTemplate(clickedItem, options.block)) {
                        openQuantityMenu(player, selected, options.block, options.blockRatio, 1);
                        return;
                    }
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
