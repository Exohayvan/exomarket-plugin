/**
 * This is the package declaration for the com.exomarket namespace, which contains the classes and interfaces related to the ExoMarket plugin.
 * The selected code includes the necessary imports for the MarketManager class, including Bukkit classes for Materials, Players, and ItemStacks, as well as the ChatColor utility class.
 */
package com.exomarket;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.ChatColor;

import java.util.List;
import java.util.Map;
import java.util.HashMap;


public class MarketManager {

    private ExoMarketPlugin plugin;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;

    public MarketManager(ExoMarketPlugin plugin, DatabaseManager databaseManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.economyManager = economyManager;
    }

    public void sellItem(Player player, int amount) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "You must be holding an item to sell.");
            return;
        }

        if (itemInHand.getAmount() < amount) {
            player.sendMessage(ChatColor.RED + "You don't have enough items to sell.");
            return;
        }

        Material itemType = itemInHand.getType();
        MarketItem existingItem = databaseManager.getMarketItem(itemType);

        if (existingItem == null) {
            // Item doesn't exist in the market, create a new entry
            MarketItem newItem = new MarketItem(itemType, amount, 0, player.getUniqueId().toString());
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
        double totalCost = marketItem.getPrice() * quantity;
        if (economyManager.hasEnoughMoney(player, totalCost)) {
            if (marketItem.getQuantity() >= quantity) {
                // Withdraw money from buyer
                economyManager.withdrawMoney(player, totalCost);

                // Update market item quantity
                marketItem.setQuantity(marketItem.getQuantity() - quantity);

                // Add items to buyer's inventory
                player.getInventory().addItem(new ItemStack(marketItem.getType(), quantity));

                // Pay the seller
                economyManager.addMoney(marketItem.getSellerUUID(), totalCost);

                player.sendMessage(ChatColor.GREEN + "You have successfully bought " + quantity + " " + 
                                marketItem.getType().toString() + " for $" + String.format("%.2f", totalCost));

                // Log the transaction
                plugin.getLogger().info("Player " + player.getName() + " bought " + quantity + " " + 
                                marketItem.getType().toString() + " for $" + String.format("%.2f", totalCost) + 
                                " from seller " + marketItem.getSellerUUID());

                // Check if the item is now out of stock
                if (marketItem.getQuantity() == 0) {
                    // Remove the item from the database
                    databaseManager.removeMarketItem(marketItem);
                    plugin.getLogger().info("Removed " + marketItem.getType().toString() + " from the market as it's out of stock.");
                } else {
                    // Update the item in the database
                    databaseManager.updateMarketItem(marketItem);
                }

                // Recalculate prices after the purchase
                recalculatePrices();

            } else {
                player.sendMessage(ChatColor.RED + "There are not enough items in stock to fulfill your request.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "You do not have enough money to buy " + quantity + " " + 
                            marketItem.getType().toString());
        }
    }

    public void recalculatePrices() {
        List<MarketItem> marketItems = databaseManager.getMarketItems();
        double totalMoney = economyManager.getTotalMoney();
        int totalItems = marketItems.stream().mapToInt(MarketItem::getQuantity).sum();

        double totalMarketValue = totalMoney * 6;
        double maxPrice = totalMoney * 0.05;

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
            double finalPrice = Math.min(maxPrice, Math.max(0.01, calculatedPrice));
            
            item.setPrice(finalPrice);
            databaseManager.updateMarketItem(item);

            System.out.println("Item: " + item.getType().toString() + ", Calculated Price: " + calculatedPrice + ", Final Price: " + finalPrice);
        }
    }
}