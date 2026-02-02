package com.starhavensmpcore.market;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.market.db.DatabaseManager;
import com.starhavensmpcore.market.economy.CurrencyFormatter;
import com.starhavensmpcore.market.economy.EconomyManager;
import com.starhavensmpcore.market.items.EnchantedBookSplitter;
import com.starhavensmpcore.market.items.ItemDisplayNameFormatter;
import com.starhavensmpcore.market.items.ItemSanitizer;
import com.starhavensmpcore.market.items.OreBreakdown;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.OfflinePlayer;

public class MarketManager {

    private StarhavenSMPCore plugin;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private double marketValueMultiplier;
    private double maxPricePercent;
    private double minPrice;
    private static final long RECALCULATION_COOLDOWN_MS = 5_000L;
    private final AtomicBoolean recalculationRunning = new AtomicBoolean(false);
    private final AtomicBoolean recalculationQueued = new AtomicBoolean(false);
    private volatile long lastSuccessfulRecalculation = 0L;
    private volatile BigInteger lastRecalculationItemCount = BigInteger.valueOf(-1L);
    private final Object recalculationCallbackLock = new Object();
    private final List<RecalculationCallback> recalculationCallbacks = new ArrayList<>();

    public MarketManager(StarhavenSMPCore plugin, DatabaseManager databaseManager, EconomyManager economyManager, double marketValueMultiplier, double maxPricePercent, double minPrice) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.economyManager = economyManager;
        this.marketValueMultiplier = marketValueMultiplier;
        this.maxPricePercent = maxPricePercent;
        this.minPrice = minPrice;
    }

    public void sellItem(Player player, int amount) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "You must be holding an item to sell.");
            return;
        }

        if (itemInHand.getAmount() < amount) {
            player.sendMessage(ChatColor.RED + "You don't have enough items to sell.");
            return;
        }

        if (ItemSanitizer.isDamaged(itemInHand)) {
            player.sendMessage(ChatColor.RED + "Damaged items cannot be listed on the market.");
            return;
        }

        ItemStack toSell = itemInHand.clone();
        toSell.setAmount(amount);

        if (!sellItem(player, toSell, true)) {
            return;
        }

        databaseManager.recordPlayerName(player.getUniqueId(), player.getName());

        // Remove the items from the player's inventory
        itemInHand.setAmount(itemInHand.getAmount() - amount);
        if (itemInHand.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }
        player.updateInventory();
    }

    public boolean sellItem(Player player, ItemStack stack) {
        return sellItem(player, stack, true);
    }

    public boolean sellItem(Player player, ItemStack stack, boolean broadcast) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }

        if (ItemSanitizer.isDamaged(stack)) {
            player.sendMessage(ChatColor.RED + "Damaged items cannot be listed on the market.");
            return false;
        }

        List<EnchantedBookSplitter.SplitEntry> entries = EnchantedBookSplitter.splitWithEnchantmentBooks(stack);
        if (entries.isEmpty()) {
            return false;
        }

        boolean listedAny = false;
        for (EnchantedBookSplitter.SplitEntry entry : entries) {
            List<OreBreakdown.SplitEntry> oreEntries = OreBreakdown.split(entry.getItemStack(), entry.getQuantity());
            for (OreBreakdown.SplitEntry oreEntry : oreEntries) {
                BigInteger amount = oreEntry.getQuantity();
                if (amount.signum() <= 0) {
                    continue;
                }

                ItemStack template = ItemSanitizer.sanitize(oreEntry.getItemStack());
                MarketItem existingItem = databaseManager.getMarketItem(template, player.getUniqueId().toString());

                if (existingItem == null) {
                    MarketItem newItem = new MarketItem(template, amount, 0, player.getUniqueId().toString());
                    databaseManager.addMarketItem(newItem);
                    player.sendMessage(ChatColor.GREEN + "Added " + amount.toString() + " " + template.getType().toString() + " to the market.");
                } else {
                    existingItem.addQuantity(amount);
                    databaseManager.updateMarketItem(existingItem);
                    player.sendMessage(ChatColor.GREEN + "Added " + amount.toString() + " " + template.getType().toString() + " to existing market listing.");
                }
                listedAny = true;
            }
        }

        if (broadcast && listedAny) {
            plugin.getServer().broadcastMessage(ChatColor.YELLOW + player.getName() + " has added something to the market!");
        }

        return listedAny;
    }

    public void buyItem(Player player, MarketItem marketItem, int quantity) {
        buyStackedItem(player, marketItem.getItemData(), marketItem.getItemStack(), quantity);
    }

    public void buyStackedItem(Player player, String itemData, ItemStack template, int quantity) {
        List<MarketItem> listings = databaseManager.getMarketItemsByItemData(itemData);
        if (listings.isEmpty()) {
            player.sendMessage(ChatColor.RED + "That listing is no longer available.");
            return;
        }

        BigInteger requested = BigInteger.valueOf(quantity);
        BigInteger totalAvailable = listings.stream()
                .map(MarketItem::getQuantity)
                .reduce(BigInteger.ZERO, BigInteger::add);
        if (totalAvailable.compareTo(requested) < 0) {
            player.sendMessage(ChatColor.RED + "There are not enough items in stock to fulfill your request.");
            return;
        }

        List<ListingAllocation> allocations = buildProportionalAllocations(listings, requested, totalAvailable);

        double totalCost = allocations.stream()
                .mapToDouble(a -> toDoubleCapped(a.allocated) * a.listing.getPrice())
                .sum();

        if (!economyManager.hasEnoughMoney(player, totalCost)) {
            player.sendMessage(ChatColor.RED + "You do not have enough money to buy " + quantity + " " +
                    template.getType().toString());
            return;
        }

        economyManager.withdrawMoney(player, totalCost);
        databaseManager.recordPlayerName(player.getUniqueId(), player.getName());

        for (ListingAllocation allocation : allocations) {
            if (allocation.allocated.signum() <= 0) {
                continue;
            }

            MarketItem listing = allocation.listing;
            BigInteger take = allocation.allocated;
            listing.setQuantity(listing.getQuantity().subtract(take));
            double payout = listing.getPrice() * toDoubleCapped(take);
            economyManager.addMoney(listing.getSellerUUID(), payout);
            databaseManager.recordSale(listing.getSellerUUID(), player.getUniqueId().toString(), take, payout);
            recordSellerName(listing.getSellerUUID());

            if (listing.getQuantity().signum() == 0) {
                databaseManager.removeMarketItem(listing);
            } else {
                databaseManager.updateMarketItem(listing);
            }

            plugin.getLogger().info("Player " + player.getName() + " bought " + take.toString() + " " +
                    listing.getType().toString() + " for " + CurrencyFormatter.format(payout) +
                    " from seller " + listing.getSellerUUID());
        }

        ItemStack itemToGive = ItemSanitizer.sanitize(template);
        itemToGive.setAmount(quantity);
        player.getInventory().addItem(itemToGive);

        databaseManager.recordDemand(itemData, requested);

        player.sendMessage(ChatColor.GREEN + "You have successfully bought " + quantity + " " +
                template.getType().toString() + " for " + CurrencyFormatter.format(totalCost));

        recalculatePrices();
    }

    public void buyConvertedItem(Player player, String baseItemData, ItemStack baseTemplate, ItemStack outputTemplate, int unitSize, int outputMultiplier, int quantity) {
        if (unitSize <= 0 || outputMultiplier <= 0 || quantity <= 0) {
            player.sendMessage(ChatColor.RED + "Invalid purchase amount.");
            return;
        }

        BigInteger requestedUnits = BigInteger.valueOf(quantity).multiply(BigInteger.valueOf(unitSize));
        List<MarketItem> listings = databaseManager.getMarketItemsByItemData(baseItemData);
        if (listings.isEmpty()) {
            player.sendMessage(ChatColor.RED + "That listing is no longer available.");
            return;
        }

        BigInteger totalAvailable = listings.stream()
                .map(MarketItem::getQuantity)
                .reduce(BigInteger.ZERO, BigInteger::add);
        if (totalAvailable.compareTo(requestedUnits) < 0) {
            player.sendMessage(ChatColor.RED + "There are not enough items in stock to fulfill your request.");
            return;
        }

        List<ListingAllocation> allocations = buildProportionalAllocations(listings, requestedUnits, totalAvailable);

        double totalCost = allocations.stream()
                .mapToDouble(a -> toDoubleCapped(a.allocated) * a.listing.getPrice())
                .sum();

        if (!economyManager.hasEnoughMoney(player, totalCost)) {
            player.sendMessage(ChatColor.RED + "You do not have enough money to buy " + quantity + " " +
                    ItemDisplayNameFormatter.format(outputTemplate));
            return;
        }

        economyManager.withdrawMoney(player, totalCost);
        databaseManager.recordPlayerName(player.getUniqueId(), player.getName());

        for (ListingAllocation allocation : allocations) {
            if (allocation.allocated.signum() <= 0) {
                continue;
            }

            MarketItem listing = allocation.listing;
            BigInteger take = allocation.allocated;
            listing.setQuantity(listing.getQuantity().subtract(take));
            double payout = listing.getPrice() * toDoubleCapped(take);
            economyManager.addMoney(listing.getSellerUUID(), payout);
            databaseManager.recordSale(listing.getSellerUUID(), player.getUniqueId().toString(), take, payout);
            recordSellerName(listing.getSellerUUID());

            if (listing.getQuantity().signum() == 0) {
                databaseManager.removeMarketItem(listing);
            } else {
                databaseManager.updateMarketItem(listing);
            }

            plugin.getLogger().info("Player " + player.getName() + " bought " + take.toString() + " " +
                    baseTemplate.getType().toString() + " for " + CurrencyFormatter.format(payout) +
                    " from seller " + listing.getSellerUUID());
        }

        int outputAmount = Math.max(0, outputMultiplier * quantity);
        ItemStack itemToGive = ItemSanitizer.sanitize(outputTemplate);
        itemToGive.setAmount(outputAmount);
        player.getInventory().addItem(itemToGive);

        databaseManager.recordDemand(baseItemData, requestedUnits);

        player.sendMessage(ChatColor.GREEN + "You have successfully bought " + outputAmount + " " +
                ItemDisplayNameFormatter.format(itemToGive) + " for " + CurrencyFormatter.format(totalCost));

        recalculatePrices();
    }

    public void buyEnchantedBookLevel(Player player, String itemData, ItemStack template, int level, int quantity) {
        BigInteger perBook = countForLevel(level);
        if (perBook.signum() <= 0) {
            player.sendMessage(ChatColor.RED + "That enchantment level is not available.");
            return;
        }

        BigInteger requiredUnits = perBook.multiply(BigInteger.valueOf(quantity));

        List<MarketItem> listings = databaseManager.getMarketItemsByItemData(itemData);
        if (listings.isEmpty()) {
            player.sendMessage(ChatColor.RED + "That listing is no longer available.");
            return;
        }

        BigInteger totalAvailable = listings.stream()
                .map(MarketItem::getQuantity)
                .reduce(BigInteger.ZERO, BigInteger::add);
        if (totalAvailable.compareTo(requiredUnits) < 0) {
            player.sendMessage(ChatColor.RED + "There are not enough books in stock to fulfill your request.");
            return;
        }

        List<ListingAllocation> allocations = buildProportionalAllocations(listings, requiredUnits, totalAvailable);

        double totalCost = allocations.stream()
                .mapToDouble(a -> toDoubleCapped(a.allocated) * a.listing.getPrice())
                .sum();

        ItemStack preview = buildEnchantedBook(template, level, 1);
        if (!economyManager.hasEnoughMoney(player, totalCost)) {
            player.sendMessage(ChatColor.RED + "You do not have enough money to buy " + quantity + " " +
                    ItemDisplayNameFormatter.format(preview));
            return;
        }

        economyManager.withdrawMoney(player, totalCost);
        databaseManager.recordPlayerName(player.getUniqueId(), player.getName());

        for (ListingAllocation allocation : allocations) {
            if (allocation.allocated.signum() <= 0) {
                continue;
            }

            MarketItem listing = allocation.listing;
            BigInteger take = allocation.allocated;
            listing.setQuantity(listing.getQuantity().subtract(take));
            double payout = listing.getPrice() * toDoubleCapped(take);
            economyManager.addMoney(listing.getSellerUUID(), payout);
            databaseManager.recordSale(listing.getSellerUUID(), player.getUniqueId().toString(), take, payout);
            recordSellerName(listing.getSellerUUID());

            if (listing.getQuantity().signum() == 0) {
                databaseManager.removeMarketItem(listing);
            } else {
                databaseManager.updateMarketItem(listing);
            }

            plugin.getLogger().info("Player " + player.getName() + " bought " + take.toString() + " " +
                    listing.getType().toString() + " for " + CurrencyFormatter.format(payout) +
                    " from seller " + listing.getSellerUUID());
        }

        ItemStack itemToGive = buildEnchantedBook(template, level, quantity);
        player.getInventory().addItem(itemToGive);

        databaseManager.recordDemand(itemData, requiredUnits);

        player.sendMessage(ChatColor.GREEN + "You have successfully bought " + quantity + " " +
                ItemDisplayNameFormatter.format(itemToGive) + " for " + CurrencyFormatter.format(totalCost));

        recalculatePrices();
    }

    public void recalculatePrices() {
        recalculatePricesIfNeeded(null, null);
    }

    public void forceRecalculatePrices() {
        scheduleRecalculation(true);
    }

    public boolean recalculatePricesIfNeeded(Runnable onSuccess, Runnable onFailure) {
        BigInteger currentItemCount = getVisibleItemCount();
        if (currentItemCount.equals(lastRecalculationItemCount) && !recalculationRunning.get() && !hasDirtyListingData()) {
            if (onSuccess != null) {
                plugin.getServer().getScheduler().runTask(plugin, onSuccess);
            }
            return false;
        }

        if (onSuccess != null || onFailure != null) {
            synchronized (recalculationCallbackLock) {
                recalculationCallbacks.add(new RecalculationCallback(onSuccess, onFailure));
            }
        }

        if (recalculationRunning.get()) {
            return true;
        }

        scheduleRecalculation(true);
        return true;
    }

    private BigInteger getVisibleItemCount() {
        BigInteger total = BigInteger.ZERO;
        List<MarketItem> items = databaseManager.getMarketItems();
        for (MarketItem item : items) {
            if (item.getType() == Material.IRON_NUGGET
                    || OreBreakdown.isCopperNugget(item.getType())
                    || item.getType() == Material.GOLD_NUGGET) {
                continue;
            }
            total = total.add(item.getQuantity().max(BigInteger.ZERO));
        }
        return total;
    }

    private boolean hasDirtyListingData() {
        List<MarketItem> items = databaseManager.getMarketItems();
        Map<String, BigInteger> nuggetTotals = new HashMap<>();
        Map<String, Material> nuggetTypes = new HashMap<>();
        for (MarketItem listing : items) {
            if (listing.getType() != Material.ENCHANTED_BOOK && !listing.getItemStack().getEnchantments().isEmpty()) {
                return true;
            }
            if (listing.getType() == Material.DIAMOND_BLOCK
                    || listing.getType() == Material.IRON_BLOCK
                    || listing.getType() == Material.COPPER_BLOCK
                    || listing.getType() == Material.GOLD_BLOCK) {
                return true;
            }
            if (listing.getType() == Material.IRON_NUGGET
                    || OreBreakdown.isCopperNugget(listing.getType())
                    || listing.getType() == Material.GOLD_NUGGET) {
                String key = listing.getSellerUUID() + "|" + listing.getType().toString();
                nuggetTotals.merge(key, listing.getQuantity().max(BigInteger.ZERO), BigInteger::add);
                nuggetTypes.put(key, listing.getType());
            }
            ItemStack normalizedStack = ItemSanitizer.sanitizeForMarket(listing.getItemStack());
            String normalized = ItemSanitizer.serializeToString(normalizedStack);
            if (!normalized.equals(listing.getItemData())) {
                return true;
            }
        }
        for (Map.Entry<String, BigInteger> entry : nuggetTotals.entrySet()) {
            Material nuggetType = nuggetTypes.get(entry.getKey());
            BigInteger ratio = OreBreakdown.getNuggetRatio(nuggetType);
            if (ratio != null && entry.getValue().compareTo(ratio) >= 0) {
                return true;
            }
        }
        return false;
    }

    private void scheduleRecalculation(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && !recalculationRunning.get() && now - lastSuccessfulRecalculation < RECALCULATION_COOLDOWN_MS) {
            return;
        }

        if (recalculationRunning.compareAndSet(false, true)) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean success = false;
                try {
                    performPriceRecalculation();
                    lastSuccessfulRecalculation = System.currentTimeMillis();
                    success = true;
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to recalculate market prices", ex);
                } finally {
                    recalculationRunning.set(false);
                    if (recalculationQueued.getAndSet(false)) {
                        scheduleRecalculation(true);
                        return;
                    }
                    notifyRecalculationCallbacks(success);
                }
            });
        } else {
            recalculationQueued.set(true);
        }
    }

    private void performPriceRecalculation() {
        List<MarketItem> marketItems = databaseManager.getMarketItems();
        marketItems = normalizeOreListings(marketItems);
        marketItems = normalizeNuggetListings(marketItems);
        marketItems = normalizeEnchantedItemListings(marketItems);
        marketItems = normalizeEnchantedBookListings(marketItems);
        marketItems = normalizeListingItemData(marketItems);
        marketItems = filterHiddenListings(marketItems);
        if (marketItems.isEmpty()) {
            lastRecalculationItemCount = BigInteger.ZERO;
            return;
        }

        double totalMoney = economyManager.getTotalMoney();
        double totalMarketValue = totalMoney * marketValueMultiplier;
        double maxPrice = totalMoney * maxPricePercent;
        double commodityCap = totalMoney * 0.20; // cap any single commodity at 20% of total economy value

        Map<String, Aggregate> aggregates = new HashMap<>();
        BigInteger totalItems = BigInteger.ZERO;
        int totalListings = 0;

        for (MarketItem listing : marketItems) {
            totalItems = totalItems.add(listing.getQuantity().max(BigInteger.ZERO));
            totalListings++;
            aggregates.computeIfAbsent(listing.getItemData(), key -> new Aggregate()).addListing(listing);
        }

        BigInteger actualTotalItems = totalItems;
        if (totalItems.signum() <= 0) {
            totalItems = BigInteger.valueOf(aggregates.size());
        }

        double averageQuantity = Math.max(1d, toDoubleCapped(totalItems) / aggregates.size());
        double averageListings = Math.max(1d, (double) totalListings / aggregates.size());
        BigInteger totalDemand = BigInteger.ZERO;
        for (Aggregate aggregate : aggregates.values()) {
            String itemData = aggregate.getItemData();
            if (itemData != null && !itemData.isEmpty()) {
                DatabaseManager.DemandStats demand = databaseManager.getDemandForItem(itemData);
                aggregate.demandMetric = DemandMetric.summarize(demand);
            } else {
                aggregate.demandMetric = BigInteger.ZERO;
            }
            totalDemand = totalDemand.add(aggregate.demandMetric);
        }
        double averageDemand = Math.max(1d, toDoubleCapped(totalDemand) / aggregates.size());

        double quantityExponent = 0.65;
        double listingExponent = 0.35;
        double demandExponent = 0.50;
        double minWeight = 1e-3;
        double maxWeight = 5.0;

        for (Aggregate aggregate : aggregates.values()) {
            double aggregateQuantity = Math.max(1d, toDoubleCapped(aggregate.totalQuantity));
            double quantityFactor = Math.pow(averageQuantity / aggregateQuantity, quantityExponent);
            double listingFactor = Math.pow(averageListings / Math.max(1d, aggregate.listingCount), listingExponent);
            double demandValue = Math.max(0d, toDoubleCapped(aggregate.demandMetric));
            double demandFactor = Math.pow((demandValue + 1d) / (averageDemand + 1d), demandExponent);
            double weight = quantityFactor * listingFactor * demandFactor;
            if (!Double.isFinite(weight) || weight < minWeight) {
                weight = minWeight;
            } else if (weight > maxWeight) {
                weight = maxWeight;
            }
            aggregate.weight = weight;
        }

        double remainingValue = totalMarketValue;
        List<Aggregate> adjustable = new ArrayList<>(aggregates.values());

        while (!adjustable.isEmpty() && remainingValue > 0.0001) {
            double weightSum = 0d;
            for (Aggregate aggregate : adjustable) {
                weightSum += aggregate.weight;
            }

            if (weightSum <= 0) {
                double equalWeight = 1d / adjustable.size();
                for (Aggregate aggregate : adjustable) {
                    aggregate.weight = equalWeight;
                }
                weightSum = 1d;
            }

            double allocatedThisRound = 0d;
            List<Aggregate> cappedThisRound = new ArrayList<>();

            for (Aggregate aggregate : adjustable) {
                double share = aggregate.weight / weightSum;
                double proposed = remainingValue * share;
                double remainingCap = commodityCap - aggregate.assignedValue;
                double allocation = Math.max(0d, Math.min(proposed, remainingCap));

                if (allocation > 0) {
                    aggregate.assignedValue += allocation;
                    allocatedThisRound += allocation;
                    if (aggregate.assignedValue >= commodityCap - 1e-6) {
                        cappedThisRound.add(aggregate);
                    }
                } else if (remainingCap <= 0) {
                    cappedThisRound.add(aggregate);
                }
            }

            if (allocatedThisRound <= 0.0001) {
                break;
            }

            remainingValue -= allocatedThisRound;
            adjustable.removeAll(cappedThisRound);
        }

        double totalAppliedValue = 0d;
        for (Aggregate aggregate : aggregates.values()) {
            double quantity = Math.max(1d, toDoubleCapped(aggregate.totalQuantity));
            double basePrice = aggregate.assignedValue / quantity;
            double finalPrice = Math.max(minPrice, Math.min(maxPrice, basePrice));

            for (MarketItem listing : aggregate.listings) {
                listing.setPrice(finalPrice);
                databaseManager.updateMarketItem(listing);
            }

            double commodityValue = finalPrice * quantity;
            totalAppliedValue += commodityValue;
            double marketShare = totalMarketValue > 0 ? (commodityValue / totalMarketValue) * 100 : 0;
            if (plugin.isDebugMarket()) {
                plugin.getLogger().info("Updated price for " + aggregate.getCommodityName() + " to " + CurrencyFormatter.format(finalPrice) +
                        " (supply: " + aggregate.totalQuantity.toString() + ", market share: " +
                    String.format("%.2f%%", marketShare) + ")");
            }
        }

        if (totalAppliedValue > 0 && Math.abs(totalAppliedValue - totalMarketValue) / totalMarketValue > 0.25) {
            plugin.getLogger().warning("Applied market value deviates significantly from target. Applied: " + totalAppliedValue + " Target: " + totalMarketValue);
        }

        lastRecalculationItemCount = actualTotalItems.max(BigInteger.ZERO);
    }

    private static class Aggregate {
        private BigInteger totalQuantity = BigInteger.ZERO;
        private int listingCount = 0;
        private final List<MarketItem> listings = new ArrayList<>();
        private MarketItem representative;
        private double weight = 1d;
        private double assignedValue = 0d;
        private BigInteger demandMetric = BigInteger.ZERO;

        void addListing(MarketItem item) {
            if (representative == null) {
                representative = item;
            }
            totalQuantity = totalQuantity.add(item.getQuantity());
            listingCount++;
            listings.add(item);
        }

        String getCommodityName() {
            if (representative == null) {
                return "Unknown";
            }
            return representative.getType().toString();
        }

        String getItemData() {
            if (representative == null) {
                return "";
            }
            return representative.getItemData();
        }
    }

    private void recordSellerName(String sellerUuid) {
        if (sellerUuid == null || sellerUuid.isEmpty()) {
            return;
        }
        try {
            OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(java.util.UUID.fromString(sellerUuid));
            if (offlinePlayer != null && offlinePlayer.getName() != null) {
                databaseManager.recordPlayerName(offlinePlayer.getUniqueId(), offlinePlayer.getName());
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid UUID format
        }
    }

    private List<ListingAllocation> buildProportionalAllocations(List<MarketItem> listings, BigInteger requested, BigInteger totalAvailable) {
        List<ListingAllocation> allocations = new ArrayList<>();
        if (requested == null || totalAvailable == null) {
            return allocations;
        }
        if (requested.signum() <= 0 || totalAvailable.signum() <= 0) {
            return allocations;
        }

        BigInteger baseAllocated = BigInteger.ZERO;

        for (MarketItem listing : listings) {
            BigInteger available = listing.getQuantity();
            BigInteger numerator = available.multiply(requested);
            BigInteger alloc = numerator.divide(totalAvailable);
            if (alloc.compareTo(available) > 0) {
                alloc = available;
            }
            BigInteger remainder = numerator.remainder(totalAvailable);
            allocations.add(new ListingAllocation(listing, alloc, remainder));
            baseAllocated = baseAllocated.add(alloc);
        }

        BigInteger remaining = requested.subtract(baseAllocated);
        allocations.sort(Comparator.comparing((ListingAllocation a) -> a.fractionalRemainder).reversed());

        int remainingInt = remaining.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
        while (remainingInt > 0) {
            boolean progressed = false;
            for (ListingAllocation allocation : allocations) {
                if (remainingInt <= 0) {
                    break;
                }
                BigInteger capacity = allocation.listing.getQuantity().subtract(allocation.allocated);
                if (capacity.signum() <= 0) {
                    continue;
                }
                allocation.allocated = allocation.allocated.add(BigInteger.ONE);
                remainingInt -= 1;
                progressed = true;
            }
            if (!progressed) {
                break;
            }
        }

        return allocations;
    }

    private static class ListingAllocation {
        private final MarketItem listing;
        private BigInteger allocated;
        private final BigInteger fractionalRemainder;

        ListingAllocation(MarketItem listing, BigInteger allocated, BigInteger fractionalRemainder) {
            this.listing = listing;
            this.allocated = allocated;
            this.fractionalRemainder = fractionalRemainder;
        }
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

    private void notifyRecalculationCallbacks(boolean success) {
        List<RecalculationCallback> callbacks;
        synchronized (recalculationCallbackLock) {
            if (recalculationCallbacks.isEmpty()) {
                return;
            }
            callbacks = new ArrayList<>(recalculationCallbacks);
            recalculationCallbacks.clear();
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (RecalculationCallback callback : callbacks) {
                if (success) {
                    if (callback.onSuccess != null) {
                        callback.onSuccess.run();
                    }
                } else if (callback.onFailure != null) {
                    callback.onFailure.run();
                }
            }
        });
    }

    private static class RecalculationCallback {
        private final Runnable onSuccess;
        private final Runnable onFailure;

        RecalculationCallback(Runnable onSuccess, Runnable onFailure) {
            this.onSuccess = onSuccess;
            this.onFailure = onFailure;
        }
    }

    private List<MarketItem> normalizeEnchantedBookListings(List<MarketItem> marketItems) {
        boolean changed = false;
        for (MarketItem listing : marketItems) {
            ItemStack stack = listing.getItemStack();
            if (!needsEnchantedBookNormalization(stack)) {
                continue;
            }

            changed = true;
            BigInteger quantity = listing.getQuantity();
            if (quantity.signum() <= 0) {
                databaseManager.removeMarketItem(listing);
                continue;
            }

            ItemStack toSplit = stack.clone();
            toSplit.setAmount(1);
            List<EnchantedBookSplitter.SplitEntry> entries = EnchantedBookSplitter.split(toSplit, quantity);
            String sellerUuid = listing.getSellerUUID();

            for (EnchantedBookSplitter.SplitEntry entry : entries) {
                BigInteger splitQuantity = entry.getQuantity();
                if (splitQuantity.signum() <= 0) {
                    continue;
                }
                ItemStack template = ItemSanitizer.sanitize(entry.getItemStack());
                MarketItem existing = databaseManager.getMarketItem(template, sellerUuid);
                if (existing == null) {
                    MarketItem newItem = new MarketItem(template, splitQuantity, 0, sellerUuid);
                    databaseManager.addMarketItem(newItem);
                } else {
                    existing.addQuantity(splitQuantity);
                    databaseManager.updateMarketItem(existing);
                }
            }

            databaseManager.removeMarketItem(listing);
        }

        if (changed) {
            return databaseManager.getMarketItems();
        }
        return marketItems;
    }

    private List<MarketItem> normalizeOreListings(List<MarketItem> marketItems) {
        boolean changed = false;
        for (MarketItem listing : marketItems) {
            if (listing.getType() == Material.DIAMOND_BLOCK) {
                changed = true;
                BigInteger quantity = listing.getQuantity();
                if (quantity.signum() <= 0) {
                    databaseManager.removeMarketItem(listing);
                    continue;
                }

                BigInteger diamonds = quantity.multiply(OreBreakdown.DIAMOND_BLOCK_RATIO);
                String sellerUuid = listing.getSellerUUID();
                ItemStack diamondStack = new ItemStack(Material.DIAMOND);
                MarketItem existing = databaseManager.getMarketItem(diamondStack, sellerUuid);
                double pricePerDiamond = listing.getPrice() / OreBreakdown.DIAMOND_BLOCK_RATIO.doubleValue();
                if (existing == null) {
                    MarketItem newItem = new MarketItem(diamondStack, diamonds, pricePerDiamond, sellerUuid);
                    databaseManager.addMarketItem(newItem);
                } else {
                    existing.addQuantity(diamonds);
                    databaseManager.updateMarketItem(existing);
                }

                databaseManager.removeMarketItem(listing);
                continue;
            }

            if (listing.getType() == Material.IRON_BLOCK) {
                changed = true;
                BigInteger quantity = listing.getQuantity();
                if (quantity.signum() <= 0) {
                    databaseManager.removeMarketItem(listing);
                    continue;
                }

                BigInteger ingots = quantity.multiply(OreBreakdown.IRON_BLOCK_RATIO);
                String sellerUuid = listing.getSellerUUID();
                ItemStack ingotStack = new ItemStack(Material.IRON_INGOT);
                MarketItem existing = databaseManager.getMarketItem(ingotStack, sellerUuid);
                double pricePerIngot = listing.getPrice() / OreBreakdown.IRON_BLOCK_RATIO.doubleValue();
                if (existing == null) {
                    MarketItem newItem = new MarketItem(ingotStack, ingots, pricePerIngot, sellerUuid);
                    databaseManager.addMarketItem(newItem);
                } else {
                    existing.addQuantity(ingots);
                    databaseManager.updateMarketItem(existing);
                }

                databaseManager.removeMarketItem(listing);
                continue;
            }

            if (listing.getType() == Material.COPPER_BLOCK) {
                changed = true;
                BigInteger quantity = listing.getQuantity();
                if (quantity.signum() <= 0) {
                    databaseManager.removeMarketItem(listing);
                    continue;
                }

                BigInteger ingots = quantity.multiply(OreBreakdown.COPPER_BLOCK_RATIO);
                String sellerUuid = listing.getSellerUUID();
                ItemStack ingotStack = new ItemStack(Material.COPPER_INGOT);
                MarketItem existing = databaseManager.getMarketItem(ingotStack, sellerUuid);
                double pricePerIngot = listing.getPrice() / OreBreakdown.COPPER_BLOCK_RATIO.doubleValue();
                if (existing == null) {
                    MarketItem newItem = new MarketItem(ingotStack, ingots, pricePerIngot, sellerUuid);
                    databaseManager.addMarketItem(newItem);
                } else {
                    existing.addQuantity(ingots);
                    databaseManager.updateMarketItem(existing);
                }

                databaseManager.removeMarketItem(listing);
                continue;
            }

            if (listing.getType() == Material.GOLD_BLOCK) {
                changed = true;
                BigInteger quantity = listing.getQuantity();
                if (quantity.signum() <= 0) {
                    databaseManager.removeMarketItem(listing);
                    continue;
                }

                BigInteger ingots = quantity.multiply(OreBreakdown.GOLD_BLOCK_RATIO);
                String sellerUuid = listing.getSellerUUID();
                ItemStack ingotStack = new ItemStack(Material.GOLD_INGOT);
                MarketItem existing = databaseManager.getMarketItem(ingotStack, sellerUuid);
                double pricePerIngot = listing.getPrice() / OreBreakdown.GOLD_BLOCK_RATIO.doubleValue();
                if (existing == null) {
                    MarketItem newItem = new MarketItem(ingotStack, ingots, pricePerIngot, sellerUuid);
                    databaseManager.addMarketItem(newItem);
                } else {
                    existing.addQuantity(ingots);
                    databaseManager.updateMarketItem(existing);
                }

                databaseManager.removeMarketItem(listing);
                continue;
            }

            if (listing.getType() == Material.RAW_IRON_BLOCK) {
                changed = true;
                BigInteger quantity = listing.getQuantity();
                if (quantity.signum() <= 0) {
                    databaseManager.removeMarketItem(listing);
                    continue;
                }

                BigInteger rawIron = quantity.multiply(OreBreakdown.RAW_IRON_BLOCK_RATIO);
                String sellerUuid = listing.getSellerUUID();
                ItemStack rawStack = new ItemStack(Material.RAW_IRON);
                MarketItem existing = databaseManager.getMarketItem(rawStack, sellerUuid);
                double pricePerRaw = listing.getPrice() / OreBreakdown.RAW_IRON_BLOCK_RATIO.doubleValue();
                if (existing == null) {
                    MarketItem newItem = new MarketItem(rawStack, rawIron, pricePerRaw, sellerUuid);
                    databaseManager.addMarketItem(newItem);
                } else {
                    existing.addQuantity(rawIron);
                    databaseManager.updateMarketItem(existing);
                }

                databaseManager.removeMarketItem(listing);
                continue;
            }

            if (listing.getType() == Material.RAW_GOLD_BLOCK) {
                changed = true;
                BigInteger quantity = listing.getQuantity();
                if (quantity.signum() <= 0) {
                    databaseManager.removeMarketItem(listing);
                    continue;
                }

                BigInteger rawGold = quantity.multiply(OreBreakdown.RAW_GOLD_BLOCK_RATIO);
                String sellerUuid = listing.getSellerUUID();
                ItemStack rawStack = new ItemStack(Material.RAW_GOLD);
                MarketItem existing = databaseManager.getMarketItem(rawStack, sellerUuid);
                double pricePerRaw = listing.getPrice() / OreBreakdown.RAW_GOLD_BLOCK_RATIO.doubleValue();
                if (existing == null) {
                    MarketItem newItem = new MarketItem(rawStack, rawGold, pricePerRaw, sellerUuid);
                    databaseManager.addMarketItem(newItem);
                } else {
                    existing.addQuantity(rawGold);
                    databaseManager.updateMarketItem(existing);
                }

                databaseManager.removeMarketItem(listing);
                continue;
            }

            if (listing.getType() == Material.RAW_COPPER_BLOCK) {
                changed = true;
                BigInteger quantity = listing.getQuantity();
                if (quantity.signum() <= 0) {
                    databaseManager.removeMarketItem(listing);
                    continue;
                }

                BigInteger rawCopper = quantity.multiply(OreBreakdown.RAW_COPPER_BLOCK_RATIO);
                String sellerUuid = listing.getSellerUUID();
                ItemStack rawStack = new ItemStack(Material.RAW_COPPER);
                MarketItem existing = databaseManager.getMarketItem(rawStack, sellerUuid);
                double pricePerRaw = listing.getPrice() / OreBreakdown.RAW_COPPER_BLOCK_RATIO.doubleValue();
                if (existing == null) {
                    MarketItem newItem = new MarketItem(rawStack, rawCopper, pricePerRaw, sellerUuid);
                    databaseManager.addMarketItem(newItem);
                } else {
                    existing.addQuantity(rawCopper);
                    databaseManager.updateMarketItem(existing);
                }

                databaseManager.removeMarketItem(listing);
            }
        }

        if (changed) {
            return databaseManager.getMarketItems();
        }
        return marketItems;
    }

    private List<MarketItem> normalizeNuggetListings(List<MarketItem> marketItems) {
        boolean changed = false;
        changed |= normalizeNuggetListingsFor(marketItems, Material.IRON_NUGGET, Material.IRON_INGOT, OreBreakdown.IRON_NUGGET_RATIO);
        Material copperNugget = OreBreakdown.getCopperNuggetMaterial();
        if (copperNugget != null) {
            changed |= normalizeNuggetListingsFor(marketItems, copperNugget, Material.COPPER_INGOT, OreBreakdown.COPPER_NUGGET_RATIO);
        }
        changed |= normalizeNuggetListingsFor(marketItems, Material.GOLD_NUGGET, Material.GOLD_INGOT, OreBreakdown.GOLD_NUGGET_RATIO);

        if (changed) {
            return databaseManager.getMarketItems();
        }
        return marketItems;
    }

    private boolean normalizeNuggetListingsFor(List<MarketItem> marketItems, Material nuggetType, Material ingotType, BigInteger ratio) {
        Map<String, List<MarketItem>> bySeller = new HashMap<>();
        for (MarketItem listing : marketItems) {
            if (listing.getType() != nuggetType) {
                continue;
            }
            bySeller.computeIfAbsent(listing.getSellerUUID(), ignored -> new ArrayList<>()).add(listing);
        }

        if (bySeller.isEmpty()) {
            return false;
        }

        boolean changed = false;
        for (Map.Entry<String, List<MarketItem>> entry : bySeller.entrySet()) {
            String sellerUuid = entry.getKey();
            List<MarketItem> nuggets = entry.getValue();
            BigInteger totalNuggets = BigInteger.ZERO;
            for (MarketItem listing : nuggets) {
                totalNuggets = totalNuggets.add(listing.getQuantity().max(BigInteger.ZERO));
            }

            BigInteger ingots = totalNuggets.divide(ratio);
            BigInteger remainder = totalNuggets.remainder(ratio);

            if (ingots.signum() <= 0 && remainder.equals(totalNuggets)) {
                continue;
            }

            changed = true;
            for (MarketItem listing : nuggets) {
                databaseManager.removeMarketItem(listing);
            }

            if (remainder.signum() > 0) {
                ItemStack nuggetStack = new ItemStack(nuggetType);
                MarketItem remainderItem = new MarketItem(nuggetStack, remainder, 0, sellerUuid);
                databaseManager.addMarketItem(remainderItem);
            }

            if (ingots.signum() > 0) {
                ItemStack ingotStack = new ItemStack(ingotType);
                MarketItem existing = databaseManager.getMarketItem(ingotStack, sellerUuid);
                if (existing == null) {
                    MarketItem newItem = new MarketItem(ingotStack, ingots, 0, sellerUuid);
                    databaseManager.addMarketItem(newItem);
                } else {
                    existing.addQuantity(ingots);
                    databaseManager.updateMarketItem(existing);
                }
            }
        }

        return changed;
    }

    private List<MarketItem> filterHiddenListings(List<MarketItem> marketItems) {
        List<MarketItem> visible = new ArrayList<>();
        for (MarketItem listing : marketItems) {
            if (listing.getType() == Material.IRON_NUGGET
                    || OreBreakdown.isCopperNugget(listing.getType())
                    || listing.getType() == Material.GOLD_NUGGET) {
                continue;
            }
            visible.add(listing);
        }
        return visible;
    }

    private List<MarketItem> normalizeEnchantedItemListings(List<MarketItem> marketItems) {
        boolean changed = false;
        for (MarketItem listing : marketItems) {
            ItemStack stack = listing.getItemStack();
            if (stack.getType() == Material.ENCHANTED_BOOK) {
                continue;
            }
            if (stack.getEnchantments().isEmpty()) {
                continue;
            }

            changed = true;
            BigInteger quantity = listing.getQuantity();
            if (quantity.signum() <= 0) {
                databaseManager.removeMarketItem(listing);
                continue;
            }

            ItemStack toSplit = stack.clone();
            toSplit.setAmount(1);
            List<EnchantedBookSplitter.SplitEntry> entries = EnchantedBookSplitter.splitWithEnchantmentBooks(toSplit, quantity);
            String sellerUuid = listing.getSellerUUID();

            for (EnchantedBookSplitter.SplitEntry entry : entries) {
                BigInteger amount = entry.getQuantity();
                if (amount.signum() <= 0) {
                    continue;
                }
                ItemStack template = ItemSanitizer.sanitize(entry.getItemStack());
                MarketItem existing = databaseManager.getMarketItem(template, sellerUuid);
                if (existing == null) {
                    MarketItem newItem = new MarketItem(template, amount, listing.getPrice(), sellerUuid);
                    databaseManager.addMarketItem(newItem);
                } else {
                    existing.addQuantity(amount);
                    databaseManager.updateMarketItem(existing);
                }
            }

            databaseManager.removeMarketItem(listing);
        }

        if (changed) {
            return databaseManager.getMarketItems();
        }
        return marketItems;
    }

    private List<MarketItem> normalizeListingItemData(List<MarketItem> marketItems) {
        boolean changed = false;
        for (MarketItem listing : marketItems) {
            BigInteger quantity = listing.getQuantity();
            if (quantity.signum() <= 0) {
                databaseManager.removeMarketItem(listing);
                changed = true;
                continue;
            }

            ItemStack normalizedStack = ItemSanitizer.sanitizeForMarket(listing.getItemStack());
            String normalizedData = ItemSanitizer.serializeToString(normalizedStack);
            if (normalizedData.equals(listing.getItemData())) {
                continue;
            }

            changed = true;
            String sellerUuid = listing.getSellerUUID();
            MarketItem existing = databaseManager.getMarketItem(normalizedStack, sellerUuid);
            if (existing == null) {
                MarketItem newItem = new MarketItem(normalizedStack, normalizedData, quantity, listing.getPrice(), sellerUuid);
                databaseManager.addMarketItem(newItem);
            } else {
                existing.addQuantity(quantity);
                databaseManager.updateMarketItem(existing);
            }

            databaseManager.removeMarketItem(listing);
        }

        if (changed) {
            return databaseManager.getMarketItems();
        }
        return marketItems;
    }

    private boolean needsEnchantedBookNormalization(ItemStack stack) {
        if (stack == null || stack.getType() != Material.ENCHANTED_BOOK) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof EnchantmentStorageMeta)) {
            return false;
        }
        Map<org.bukkit.enchantments.Enchantment, Integer> stored = ((EnchantmentStorageMeta) meta).getStoredEnchants();
        if (stored.isEmpty()) {
            return false;
        }
        if (stored.size() != 1) {
            return true;
        }
        int level = stored.values().iterator().next();
        return level != 1;
    }
}
