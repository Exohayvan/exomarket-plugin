package com.exomarket;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;

public class ExoMarketPlugin extends JavaPlugin {

    private MarketManager marketManager;
    private GUIManager guiManager;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    public DatabaseManager getDatabaseManager() {
        return this.databaseManager;
    }

    public MarketManager getMarketManager() {
        return this.marketManager;
    }

    @Override
    public void onEnable() {
        marketManager = new MarketManager(this, new DatabaseManager(this), new EconomyManager(this));
        guiManager = new GUIManager(this);
        databaseManager = new DatabaseManager(this);
        economyManager = new EconomyManager(this);

        getServer().getPluginManager().registerEvents(guiManager, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("market")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be executed by a player.");
                return true;
            }

            Player player = (Player) sender;

            if (args.length == 0) {
                marketManager.recalculatePrices(); // Recalculate prices before opening GUI
                guiManager.openMarketGUI(player);
                return true;
            }

            if (args.length == 1) {
                if (args[0].isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "Suggestion: /market sell");
                    return true;
                }
                if (args[0].equalsIgnoreCase("sell")) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /market sell <amount>");
                    return true;
                }
            }

            if (args.length == 2) {
                if (args[0].equalsIgnoreCase("sell")) {
                    if (args[1].isEmpty()) {
                        player.sendMessage(ChatColor.YELLOW + "Usage: /market sell <amount>");
                        return true;
                    }
                    try {
                        int amount = Integer.parseInt(args[1]);
                        marketManager.sellItem(player, amount);
                        return true;
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Invalid amount. Please enter a valid number.");
                        return true;
                    }
                }
            }

            if (args.length == 3) {
                if (args[0].equalsIgnoreCase("buy")) {
                    try {
                        int quantity = Integer.parseInt(args[2]);
                        Material itemType = Material.getMaterial(args[1].toUpperCase());
                        MarketItem marketItem = databaseManager.getMarketItem(itemType);
                        if (marketItem != null) {
                            marketManager.buyItem(player, marketItem, quantity);
                            return true;
                        } else {
                            player.sendMessage(ChatColor.RED + "Item not found in the market.");
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Invalid quantity. Please enter a valid number.");
                        return true;
                    }
                }
            }

            player.sendMessage(ChatColor.RED + "Invalid command usage. Type /market for more information.");
            return true;
        }
        return false;
    }
}