/**
 * This is the package declaration for the com.exomarket namespace, which contains the classes and interfaces related to the ExoMarket plugin.
 * The selected code includes the necessary imports for the MarketManager class, including Bukkit classes for Materials, Players, and ItemStacks, as well as the ChatColor utility class.
 */
package com.exomarket;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.ChatColor;

import java.util.List;


public class MarketManager {

    private ExoMarketPlugin plugin;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private double marketValueMultiplier;
    private double maxPricePercent;
    private double minPrice;

    public MarketManager(ExoMarketPlugin plugin, DatabaseManager databaseManager, EconomyManager economyManager, double marketValueMultiplier, double maxPricePercent, double minPrice) {
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

        ItemStack template = ItemSanitizer.sanitize(itemInHand);
        MarketItem existingItem = databaseManager.getMarketItem(template, player.getUniqueId().toString());

        if (existingItem == null) {
            // Item doesn't exist in the market, create a new entry
            MarketItem newItem = new MarketItem(template, amount, 0, player.getUniqueId().toString());
            databaseManager.addMarketItem(newItem);
            player.sendMessage(ChatColor.GREEN + "Item added to the market.");
        } else {
            // Item exists, update the quantity
            existingItem.addQuantity(amount);
            databaseManager.updateMarketItem(existingItem);
            player.sendMessage(ChatColor.GREEN + "Added " + amount + " items to existing market listing.");
        }

        // Remove the items from the player's inventory
        itemInHand.setAmount(itemInHand.getAmount() - amount);

        // Send a message to all players
        plugin.getServer().broadcastMessage(ChatColor.YELLOW + player.getName() + " has added something to the market!");
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

        int totalAvailable = listings.stream().mapToInt(MarketItem::getQuantity).sum();
        if (totalAvailable < quantity) {
            player.sendMessage(ChatColor.RED + "There are not enough items in stock to fulfill your request.");
            return;
        }

        int remaining = quantity;
        double totalCost = 0;
        for (MarketItem listing : listings) {
            int take = Math.min(remaining, listing.getQuantity());
            totalCost += take * listing.getPrice();
            remaining -= take;
            if (remaining <= 0) {
                break;
            }
        }

        if (!economyManager.hasEnoughMoney(player, totalCost)) {
            player.sendMessage(ChatColor.RED + "You do not have enough money to buy " + quantity + " " +
                    template.getType().toString());
            return;
        }

        economyManager.withdrawMoney(player, totalCost);

        remaining = quantity;
        for (MarketItem listing : listings) {
            int take = Math.min(remaining, listing.getQuantity());
            if (take <= 0) {
                continue;
            }

            listing.setQuantity(listing.getQuantity() - take);
            double payout = listing.getPrice() * take;
            economyManager.addMoney(listing.getSellerUUID(), payout);

            if (listing.getQuantity() == 0) {
                databaseManager.removeMarketItem(listing);
            } else {
                databaseManager.updateMarketItem(listing);
            }

            remaining -= take;

            plugin.getLogger().info("Player " + player.getName() + " bought " + take + " " +
                    listing.getType().toString() + " for $" + String.format("%.2f", payout) +
                    " from seller " + listing.getSellerUUID());

            if (remaining <= 0) {
                break;
            }
        }

        ItemStack itemToGive = ItemSanitizer.sanitize(template);
        itemToGive.setAmount(quantity);
        player.getInventory().addItem(itemToGive);

        player.sendMessage(ChatColor.GREEN + "You have successfully bought " + quantity + " " +
                template.getType().toString() + " for $" + String.format("%.2f", totalCost));

        recalculatePrices();
    }

    public void recalculatePrices() {
        List<MarketItem> marketItems = databaseManager.getMarketItems();
        double totalMoney = economyManager.getTotalMoney();
        int totalItems = marketItems.stream().mapToInt(MarketItem::getQuantity).sum();

        double totalMarketValue = totalMoney * marketValueMultiplier;
        double maxPrice = totalMoney * maxPricePercent;

        System.out.println("Total Market Value: " + totalMarketValue);
        System.out.println("Max Price (5% of Total Market Value): " + maxPrice);

        double totalInverseProportions = 0;
        for (MarketItem item : marketItems) {
            totalInverseProportions += (double) totalItems / item.getQuantity();
        }

        for (MarketItem item : marketItems) {
            double inverseProportion = (double) totalItems / item.getQuantity();
            double calculatedPrice = (totalMarketValue / totalInverseProportions) * inverseProportion / item.getQuantity();
            
            // Ensure the price doesn't exceed 5% of total market value
            double finalPrice = Math.min(maxPrice, Math.max(minPrice, calculatedPrice));
            
            item.setPrice(finalPrice);
            databaseManager.updateMarketItem(item);

            System.out.println("Item: " + item.getType().toString() + ", Calculated Price: " + calculatedPrice + ", Final Price: " + finalPrice);
        }
    }
}
