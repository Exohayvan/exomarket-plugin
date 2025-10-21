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
import java.io.File;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

public class ExoMarketPlugin extends JavaPlugin implements TabCompleter {

    private MarketManager marketManager;
    private GUIManager guiManager;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private AutoSellManager autoSellManager;
    private double marketValueMultiplier;
    private double maxPricePercent;
    private double minPrice;

    public DatabaseManager getDatabaseManager() {
        return this.databaseManager;
    }

    public MarketManager getMarketManager() {
        return this.marketManager;
    }

    private File configFile;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveDefaultConfig();
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        marketValueMultiplier = config.getDouble("MarketManager.MarketValueMultipier");
        maxPricePercent = config.getDouble("MarketManager.MaxPricePercent") / 100;
        minPrice = config.getDouble("MarketManager.MinPrice");
        
        databaseManager = new DatabaseManager(this);
        economyManager = new EconomyManager(this);
        marketManager = new MarketManager(this, databaseManager, economyManager, marketValueMultiplier, maxPricePercent, minPrice);
        guiManager = new GUIManager(this);
        autoSellManager = new AutoSellManager(this);

        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(autoSellManager, this);
        
        // Register commands and set tab completers
        getCommand("market").setExecutor(this);
        getCommand("market").setTabCompleter(this);
        getCommand("sellhand").setExecutor(this);
        getCommand("sellhand").setTabCompleter(this);
        getCommand("marketreload").setExecutor(this);
        getCommand("marketreload").setTabCompleter(this);
        getCommand("autosell").setExecutor(autoSellManager);
        getCommand("autosell").setTabCompleter(this);
    }

    public void reloadConfig() {
        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveDefaultConfig();
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        marketValueMultiplier = config.getDouble("MarketManager.MarketValueMultipier");
        maxPricePercent = config.getDouble("MarketManager.MaxPricePercent") / 100;
        minPrice = config.getDouble("MarketManager.MinPrice");
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
            guiManager.openMarketGUI(player);
            return true;
        } else if (command.getName().equalsIgnoreCase("sellhand")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be executed by a player.");
                return true;
            }
            Player player = (Player) sender;
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            if (itemInHand.getType() != Material.AIR) {
                marketManager.sellItem(player, itemInHand.getAmount());
            } else {
                player.sendMessage(ChatColor.RED + "You must be holding an item to sell.");
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("marketreload")) {
            reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "Market configuration reloaded.");
            return true;
        } else if (command.getName().equalsIgnoreCase("autosell")) {
            return autoSellManager.onCommand(sender, command, label, args);
        }
    
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (command.getName().equalsIgnoreCase("market")) {
            if (args.length == 1) {
                completions.addAll(Arrays.asList("buy", "sell", "info"));
            }
        } else if (command.getName().equalsIgnoreCase("sellhand")) {
            // No arguments for sell hand command
        } else if (command.getName().equalsIgnoreCase("marketreload")) {
            // No arguments for marketreload command
        } else if (command.getName().equalsIgnoreCase("autosell")) {
            // No arguments for autosell command
        }

        return completions;
    }
}
