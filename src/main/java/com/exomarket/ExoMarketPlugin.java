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
    private MarketSellGUI marketSellGUI;
    private MarketItemsGUI marketItemsGUI;
    private MarketWebServer marketWebServer;
    private ExoMarketPlaceholders placeholders;
    private double marketValueMultiplier;
    private double maxPricePercent;
    private double minPrice;
    private static final int WEB_PORT = 6969;

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
        marketSellGUI = new MarketSellGUI(this, marketManager);
        marketItemsGUI = new MarketItemsGUI(this, marketManager, databaseManager);
        marketWebServer = new MarketWebServer(this, databaseManager, WEB_PORT);
        placeholders = new ExoMarketPlaceholders(this, databaseManager);

        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(autoSellManager, this);
        getServer().getPluginManager().registerEvents(marketSellGUI, this);
        getServer().getPluginManager().registerEvents(marketItemsGUI, this);
        
        // Register commands and set tab completers
        getCommand("market").setExecutor(this);
        getCommand("market").setTabCompleter(this);
        getCommand("sellhand").setExecutor(this);
        getCommand("sellhand").setTabCompleter(this);
        getCommand("marketreload").setExecutor(this);
        getCommand("marketreload").setTabCompleter(this);
        getCommand("autosell").setExecutor(autoSellManager);
        getCommand("autosell").setTabCompleter(this);

        marketWebServer.start();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholders.register();
            getLogger().info("PlaceholderAPI found. Registered ExoMarket placeholders.");
        } else {
            getLogger().info("PlaceholderAPI not found. Skipping placeholder registration.");
        }
    }

    @Override
    public void onDisable() {
        if (marketWebServer != null) {
            marketWebServer.stop();
        }
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
            if (args[0].equalsIgnoreCase("sell")) {
                marketSellGUI.openSellGUI(player);
                return true;
            } else if (args[0].equalsIgnoreCase("items")) {
                marketItemsGUI.openListings(player);
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
                String prefix = args[0].toLowerCase();
                for (String option : Arrays.asList("buy", "sell", "items", "info")) {
                    if (option.startsWith(prefix)) {
                        completions.add(option);
                    }
                }
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
