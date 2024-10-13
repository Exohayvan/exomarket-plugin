/**
 * The main class for the ExoMarket plugin, which provides a market system for players to buy and sell items.
 * The plugin includes a market manager, GUI manager, database manager, and economy manager to handle the various aspects of the market system.
 * Players can use the /market command to access the market GUI, sell items, and buy items from the market.
 * The /sellhand command allows players to quickly sell the item they are currently holding.
 */
package com.exomarket;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.command.TabCompleter;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;


public class ExoMarketPlugin extends JavaPlugin implements TabCompleter {

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
        databaseManager = new DatabaseManager(this);
        economyManager = new EconomyManager(this);
        marketManager = new MarketManager(this, databaseManager, economyManager);
        guiManager = new GUIManager(this);

        getServer().getPluginManager().registerEvents(guiManager, this);
        
        // Register commands and set tab completers
        getCommand("market").setExecutor(this);
        getCommand("market").setTabCompleter(this);
        getCommand("sellhand").setExecutor(this);
        getCommand("sellhand").setTabCompleter(this);
        getCommand("marketreload").setExecutor(this);
        getCommand("marketreload").setTabCompleter(this);
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
        } else if (command.getName().equalsIgnoreCase("sellhand")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be executed by a player.");
                return true;
            }

            Player player = (Player) sender;
            ItemStack itemInHand = player.getInventory().getItemInMainHand();

            if (itemInHand.getType() == Material.AIR) {
                player.sendMessage(ChatColor.RED + "You must be holding an item to sell.");
                return true;
            }

            int amount = itemInHand.getAmount();
            marketManager.sellItem(player, amount);
            return true;
        } else if (command.getName().equalsIgnoreCase("marketreload")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be executed by a player.");
                return true;
            }

            Player player = (Player) sender;

            if (!player.hasPermission("exomarket.admin")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to reload the market plugin.");
                return true;
            }

            getServer().getPluginManager().disablePlugin(this);
            getServer().getPluginManager().enablePlugin(this);

            player.sendMessage(ChatColor.GREEN + "Market plugin reloaded successfully.");
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (command.getName().equalsIgnoreCase("market")) {
            if (args.length == 1) {
                completions.addAll(Arrays.asList("sell", "buy"));
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("sell")) {
                    completions.add("<amount>");
                } else if (args[0].equalsIgnoreCase("buy")) {
                    completions.addAll(Arrays.stream(Material.values())
                            .map(Material::name)
                            .collect(Collectors.toList()));
                }
            } else if (args.length == 3 && args[0].equalsIgnoreCase("buy")) {
                completions.add("<quantity>");
            }
        } else if (command.getName().equalsIgnoreCase("sellhand")) {
            // No arguments for sellhand command
        }
        
        return completions;
    }
}