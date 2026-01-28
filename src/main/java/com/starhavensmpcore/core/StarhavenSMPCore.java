package com.starhavensmpcore.core;

import com.starhavensmpcore.market.AutoSellManager;
import com.starhavensmpcore.market.MarketItem;
import com.starhavensmpcore.market.MarketManager;
import com.starhavensmpcore.market.db.DatabaseManager;
import com.starhavensmpcore.market.economy.CurrencyFormatter;
import com.starhavensmpcore.market.economy.EconomyManager;
import com.starhavensmpcore.market.economy.QuantityFormatter;
import com.starhavensmpcore.market.gui.GUIManager;
import com.starhavensmpcore.market.gui.MarketItemsGUI;
import com.starhavensmpcore.market.gui.MarketSellGUI;
import com.starhavensmpcore.market.placeholders.ExoMarketPlaceholders;
import com.starhavensmpcore.market.web.MarketWebServer;
import com.starhavensmpcore.resourcepack.ResourcePackManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StarhavenSMPCore extends JavaPlugin {

    private MarketManager marketManager;
    private GUIManager guiManager;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private AutoSellManager autoSellManager;
    private MarketSellGUI marketSellGUI;
    private MarketItemsGUI marketItemsGUI;
    private MarketWebServer marketWebServer;
    private ExoMarketPlaceholders placeholders;
    private ResourcePackManager resourcePackManager;
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
        CurrencyFormatter.setSymbol(config.getString("CurrencySymbol", "⚚Ɍ"));
        
        databaseManager = new DatabaseManager(this);
        economyManager = new EconomyManager(this);
        marketManager = new MarketManager(this, databaseManager, economyManager, marketValueMultiplier, maxPricePercent, minPrice);
        guiManager = new GUIManager(this);
        autoSellManager = new AutoSellManager(this);
        marketSellGUI = new MarketSellGUI(this, marketManager);
        marketItemsGUI = new MarketItemsGUI(this, marketManager, databaseManager);
        marketWebServer = new MarketWebServer(this, databaseManager, WEB_PORT);
        placeholders = new ExoMarketPlaceholders(this, databaseManager);
        resourcePackManager = new ResourcePackManager(this);

        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(autoSellManager, this);
        getServer().getPluginManager().registerEvents(marketSellGUI, this);
        getServer().getPluginManager().registerEvents(marketItemsGUI, this);
        getServer().getPluginManager().registerEvents(resourcePackManager, this);
        
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
        CurrencyFormatter.setSymbol(config.getString("CurrencySymbol", "⚚Ɍ"));
        if (resourcePackManager != null) {
            resourcePackManager.reload();
        }
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
                openMarketWithRecalculation(player, null);
                return true;
            }
            if (args[0].equalsIgnoreCase("sell")) {
                marketSellGUI.openSellGUI(player);
                return true;
            }
            if (args[0].equalsIgnoreCase("buy")) {
                String filter = joinArgs(args, 1);
                openMarketWithRecalculation(player, filter);
                return true;
            } else if (args[0].equalsIgnoreCase("items")) {
                String filter = joinArgs(args, 1);
                marketItemsGUI.openListings(player, filter);
                return true;
            } else if (args[0].equalsIgnoreCase("info")) {
                sendMarketInfo(player);
                return true;
            }
            openMarketWithRecalculation(player, null);
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
            } else if (args.length >= 2) {
                String subcommand = args[0].toLowerCase();
                String prefix = args[args.length - 1].toLowerCase();
                if (subcommand.equals("buy")) {
                    for (String suggestion : getMarketItemSuggestions(prefix)) {
                        completions.add(suggestion);
                    }
                } else if (subcommand.equals("items") && sender instanceof Player) {
                    Player player = (Player) sender;
                    for (String suggestion : getOwnedItemSuggestions(player, prefix)) {
                        completions.add(suggestion);
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

    private List<String> getMarketItemSuggestions(String prefix) {
        List<String> suggestions = new ArrayList<>();
        for (MarketItem item : databaseManager.getMarketItems()) {
            String name = item.getType().toString().toLowerCase();
            if (name.startsWith(prefix) && !suggestions.contains(name)) {
                suggestions.add(name);
            }
        }
        return suggestions;
    }

    private List<String> getOwnedItemSuggestions(Player player, String prefix) {
        List<String> suggestions = new ArrayList<>();
        List<MarketItem> listings = databaseManager.getMarketItemsByOwner(player.getUniqueId().toString());
        for (MarketItem item : listings) {
            String name = item.getType().toString().toLowerCase();
            if (name.startsWith(prefix) && !suggestions.contains(name)) {
                suggestions.add(name);
            }
        }
        return suggestions;
    }

    private void openMarketWithRecalculation(Player player, String filter) {
        boolean willRecalculate = marketManager.recalculatePricesIfNeeded(
                () -> guiManager.openMarketGUI(player, filter),
                () -> player.sendMessage(ChatColor.RED + "There was an error recalculating market prices. " +
                        "Please contact ExoHayvan on Discord or report an issue on GitHub.")
        );
        if (willRecalculate) {
            player.sendMessage(ChatColor.YELLOW + "Recalculating market prices...");
        }
    }

    private void sendMarketInfo(Player player) {
        List<MarketItem> items = databaseManager.getMarketItems();
        int totalListings = items.size();
        BigInteger totalQuantity = BigInteger.ZERO;
        double totalValue = 0d;
        Set<String> uniqueItems = new HashSet<>();
        for (MarketItem item : items) {
            BigInteger qty = item.getQuantity().max(BigInteger.ZERO);
            totalQuantity = totalQuantity.add(qty);
            totalValue += item.getPrice() * toDoubleCapped(qty);
            uniqueItems.add(item.getItemData());
        }

        DatabaseManager.Stats global = databaseManager.getStats("global");

        player.sendMessage(ChatColor.GOLD + "Market Totals");
        player.sendMessage(ChatColor.GRAY + "Listings: " + totalListings +
                " | Unique items: " + uniqueItems.size());
        player.sendMessage(ChatColor.GRAY + "Supply listed: " + QuantityFormatter.format(totalQuantity));
        player.sendMessage(ChatColor.GRAY + "Listed value: " + CurrencyFormatter.format(totalValue));
        player.sendMessage(ChatColor.GRAY + "Items traded: " + QuantityFormatter.format(global.itemsSold) +
                " | Value traded: " + CurrencyFormatter.format(global.moneyEarned));
        DatabaseManager.DemandStats demandTotals = databaseManager.getDemandTotals();
        player.sendMessage(ChatColor.GRAY + "Demand 1h: " + QuantityFormatter.format(demandTotals.hour) +
                " | 1d: " + QuantityFormatter.format(demandTotals.day) +
                " | 1mo: " + QuantityFormatter.format(demandTotals.month) +
                " | 1y: " + QuantityFormatter.format(demandTotals.year));

        List<MarketItem> owned = databaseManager.getMarketItemsByOwner(player.getUniqueId().toString());
        int ownedListings = owned.size();
        BigInteger ownedQuantity = BigInteger.ZERO;
        double ownedValue = 0d;
        Set<String> ownedUnique = new HashSet<>();
        for (MarketItem item : owned) {
            BigInteger qty = item.getQuantity().max(BigInteger.ZERO);
            ownedQuantity = ownedQuantity.add(qty);
            ownedValue += item.getPrice() * toDoubleCapped(qty);
            ownedUnique.add(item.getItemData());
        }

        DatabaseManager.Stats personal = databaseManager.getStats(player.getUniqueId().toString());

        player.sendMessage(ChatColor.GOLD + "Your Market Stats");
        player.sendMessage(ChatColor.GRAY + "Listings: " + ownedListings +
                " | Unique items: " + ownedUnique.size());
        player.sendMessage(ChatColor.GRAY + "Supply listed: " + QuantityFormatter.format(ownedQuantity));
        player.sendMessage(ChatColor.GRAY + "Listed value: " + CurrencyFormatter.format(ownedValue));
        player.sendMessage(ChatColor.GRAY + "Items sold: " + QuantityFormatter.format(personal.itemsSold) +
                " | Earned: " + CurrencyFormatter.format(personal.moneyEarned));
        player.sendMessage(ChatColor.GRAY + "Items bought: " + QuantityFormatter.format(personal.itemsBought) +
                " | Spent: " + CurrencyFormatter.format(personal.moneySpent));
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

    private String joinArgs(String[] args, int startIndex) {
        if (args == null || startIndex >= args.length) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (args[i] == null || args[i].isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        if (builder.length() == 0) {
            return null;
        }
        return builder.toString();
    }
}
