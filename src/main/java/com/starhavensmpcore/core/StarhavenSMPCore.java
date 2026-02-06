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
import com.starhavensmpcore.market.items.ItemSanitizer;
import com.starhavensmpcore.market.items.OreBreakdown;
import com.starhavensmpcore.team.TeamService;
import com.starhavensmpcore.help.HelpMenu;
import com.starhavensmpcore.notifier.NotifierManager;
import com.starhavensmpcore.placeholderapi.Placeholders;
import com.starhavensmpcore.placeholderapi.PlaceholdersSh;
import com.starhavensmpcore.market.web.MarketWebServer;
import com.starhavensmpcore.items.BlockDefinition;
import com.starhavensmpcore.items.CustomBlockRegistry;
import com.starhavensmpcore.items.CustomItemManager;
import com.starhavensmpcore.items.CraftingList;
import com.starhavensmpcore.items.ItemList;
import com.starhavensmpcore.items.SmeltingList;
import com.starhavensmpcore.oregeneration.OreGenerationManager;
import com.starhavensmpcore.resourcepack.NoteBlockGuard;
import com.starhavensmpcore.resourcepack.ResourcePackManager;
import com.starhavensmpcore.waypoint.WaypointManager;
import org.bukkit.NamespacedKey;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class StarhavenSMPCore extends JavaPlugin {

    private MarketManager marketManager;
    private GUIManager guiManager;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private AutoSellManager autoSellManager;
    private MarketSellGUI marketSellGUI;
    private MarketItemsGUI marketItemsGUI;
    private MarketWebServer marketWebServer;
    private TeamService teamService;
    private HelpMenu helpMenu;
    private Placeholders placeholders;
    private PlaceholdersSh placeholdersSh;
    private ResourcePackManager resourcePackManager;
    private CustomItemManager customItemManager;
    private NoteBlockGuard noteBlockGuard;
    private CustomBlockRegistry customBlockRegistry;
    private OreGenerationManager oreGenerationManager;
    private WaypointManager waypointManager;
    private NotifierManager notifierManager;
    private double marketValueMultiplier;
    private double maxPricePercent;
    private double minPrice;
    private static final int WEB_PORT = 6969;
    private static final long SUGGESTION_CACHE_MS = 5_000L;

    private volatile List<String> marketItemSuggestionCache = Collections.emptyList();
    private volatile long marketItemSuggestionLastRefresh = 0L;
    private final AtomicBoolean marketItemSuggestionRefreshing = new AtomicBoolean(false);
    private final Map<UUID, List<String>> ownedItemSuggestionCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> ownedItemSuggestionLastRefresh = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicBoolean> ownedItemSuggestionRefreshing = new ConcurrentHashMap<>();

    public DatabaseManager getDatabaseManager() {
        return this.databaseManager;
    }

    public MarketManager getMarketManager() {
        return this.marketManager;
    }

    public TeamService getTeamService() {
        return this.teamService;
    }

    public CustomItemManager getCustomItemManager() {
        return this.customItemManager;
    }

    private File configFile;
    private FileConfiguration config;
    private boolean debugMarket;
    private boolean debugCustomBlocks;
    private boolean debugOreGeneration;
    private boolean debugWaystone;

    @Override
    public void onEnable() {
        loadConfigValues();
        
        databaseManager = new DatabaseManager(this);
        economyManager = new EconomyManager(this);
        marketManager = new MarketManager(this, databaseManager, economyManager, marketValueMultiplier, maxPricePercent, minPrice);
        guiManager = new GUIManager(this);
        autoSellManager = new AutoSellManager(this);
        marketSellGUI = new MarketSellGUI(this, marketManager);
        marketItemsGUI = new MarketItemsGUI(this, marketManager, databaseManager);
        marketWebServer = new MarketWebServer(this, databaseManager, WEB_PORT);
        teamService = new TeamService(this, databaseManager, economyManager);
        helpMenu = new HelpMenu();
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                placeholders = new Placeholders(this, databaseManager);
                placeholdersSh = new PlaceholdersSh(this, databaseManager);
                placeholders.register();
                placeholdersSh.register();
            } catch (NoClassDefFoundError ex) {
                getLogger().warning("PlaceholderAPI detected but classes are missing; skipping placeholders.");
            }
        } else {
            getLogger().info("PlaceholderAPI not found; placeholders disabled.");
        }
        resourcePackManager = new ResourcePackManager(this);
        customBlockRegistry = new CustomBlockRegistry();
        customItemManager = new CustomItemManager(this, customBlockRegistry);
        OreBreakdown.setCustomItemManager(customItemManager);
        noteBlockGuard = new NoteBlockGuard(this, customBlockRegistry, customItemManager);
        oreGenerationManager = new OreGenerationManager(this, customBlockRegistry);
        waypointManager = new WaypointManager(this, customItemManager);
        notifierManager = new NotifierManager(this);

        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(helpMenu, this);
        getServer().getPluginManager().registerEvents(autoSellManager, this);
        getServer().getPluginManager().registerEvents(marketSellGUI, this);
        getServer().getPluginManager().registerEvents(marketItemsGUI, this);
        getServer().getPluginManager().registerEvents(resourcePackManager, this);
        getServer().getPluginManager().registerEvents(customItemManager, this);
        getServer().getPluginManager().registerEvents(noteBlockGuard, this);
        getServer().getPluginManager().registerEvents(oreGenerationManager, this);
        getServer().getPluginManager().registerEvents(waypointManager, this);
        getServer().getPluginManager().registerEvents(notifierManager, this);
        
        // Register commands and set tab completers
        getCommand("market").setExecutor(this);
        getCommand("market").setTabCompleter(this);
        getCommand("sellhand").setExecutor(this);
        getCommand("sellhand").setTabCompleter(this);
        getCommand("marketreload").setExecutor(this);
        getCommand("marketreload").setTabCompleter(this);
        getCommand("help").setExecutor(this);
        getCommand("help").setTabCompleter(this);
        getCommand("autosell").setExecutor(autoSellManager);
        getCommand("autosell").setTabCompleter(this);
        getCommand("starhavengive").setExecutor(customItemManager);
        getCommand("new").setExecutor(notifierManager);

        marketWebServer.start();

        registerRecipes();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholders.register();
            placeholdersSh.register();
            getLogger().info("PlaceholderAPI found. Registered Market placeholders.");
        } else {
            getLogger().info("PlaceholderAPI not found. Skipping placeholder registration.");
        }
    }

    private void registerRecipes() {
        CraftingList craftingList = new CraftingList(this, customItemManager);
        craftingList.registerAll();
        getServer().getPluginManager().registerEvents(craftingList, this);
        SmeltingList.registerAll(this, customItemManager);
    }

    public NamespacedKey getVoidShardRecipeKey() {
        return null;
    }

    @Override
    public void onDisable() {
        if (marketWebServer != null) {
            marketWebServer.stop();
        }
        if (oreGenerationManager != null) {
            oreGenerationManager.shutdown();
        }
        if (waypointManager != null) {
            waypointManager.shutdown();
        }
        if (notifierManager != null) {
            notifierManager.shutdown();
        }
    }

    public void reloadConfig() {
        loadConfigValues();
        if (resourcePackManager != null) {
            resourcePackManager.reload();
        }
    }

    private void loadConfigValues() {
        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveDefaultConfig();
        }
        config = ConfigHandler.loadAndUpdate(this, configFile);
        marketValueMultiplier = config.getDouble("MarketManager.MarketValueMultipier", 6);
        maxPricePercent = config.getDouble("MarketManager.MaxPricePercent", 5) / 100;
        minPrice = config.getDouble("MarketManager.MinPrice", 0.01);
        CurrencyFormatter.setSymbol(config.getString("CurrencySymbol", "⚚Ɍ"));
        debugMarket = config.getBoolean("Debug.Market", false);
        debugCustomBlocks = config.getBoolean("Debug.CustomBlocks", false);
        debugOreGeneration = config.getBoolean("Debug.OreGeneration", false);
        debugWaystone = config.getBoolean("Debug.Waystone", false);
    }

    public boolean isDebugMarket() {
        return debugMarket;
    }

    public boolean isDebugCustomBlocks() {
        return debugCustomBlocks;
    }

    public boolean isDebugOreGeneration() {
        return debugOreGeneration;
    }

    public boolean isDebugWaystone() {
        return debugWaystone;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("help")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be executed by a player.");
                return true;
            }
            Player player = (Player) sender;
            helpMenu.openRoot(player);
            return true;
        }
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
            } else if (args[0].equalsIgnoreCase("debug")) {
                if (args.length >= 2 && args[1].equalsIgnoreCase("item")) {
                    boolean full = args.length >= 3 && args[2].equalsIgnoreCase("full");
                    sendMarketDebugItem(player, full);
                    return true;
                }

                if (args.length >= 2) {
                    boolean full = args.length >= 3 && args[args.length - 1].equalsIgnoreCase("full");
                    int endIndex = full ? args.length - 1 : args.length;
                    String materialName = joinArgs(args, 1, endIndex);
                    Material material = resolveMaterial(materialName);
                    if (material == null) {
                        player.sendMessage(ChatColor.RED + "Unknown material: " + materialName);
                        return true;
                    }
                    sendMarketDebugListings(player, material, full);
                    return true;
                }

                player.sendMessage(ChatColor.RED + "Usage: /market debug item [full] or /market debug <material> [full]");
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
        } else if (command.getName().equalsIgnoreCase("ore")) {
            if (args.length >= 2 && args[0].equalsIgnoreCase("repair")) {
                if (oreGenerationManager == null) {
                    sender.sendMessage(ChatColor.RED + "Ore generation is not available.");
                    return true;
                }
                BlockDefinition definition = ItemList.fromArgument(args[1]);
                if (definition == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown custom ore: " + args[1]);
                    return true;
                }
                oreGenerationManager.repairOreGeneration(sender, definition);
                return true;
            }
            sender.sendMessage(ChatColor.RED + "Usage: /ore repair <custom_ore>");
            return true;
        }
    
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (command.getName().equalsIgnoreCase("market")) {
            if (args.length == 1) {
                String prefix = args[0].toLowerCase();
                for (String option : Arrays.asList("buy", "sell", "items", "info", "debug")) {
                    if (option.startsWith(prefix)) {
                        completions.add(option);
                    }
                }
            } else if (args.length >= 2) {
                String subcommand = args[0].toLowerCase();
                String prefix = args[args.length - 1].toLowerCase();
                if (subcommand.equals("debug") && args.length == 2) {
                    if ("item".startsWith(prefix)) {
                        completions.add("item");
                    }
                    for (Material material : Material.values()) {
                        String name = material.name().toLowerCase();
                        if (name.startsWith(prefix)) {
                            completions.add(name);
                        }
                    }
                } else if (subcommand.equals("debug") && args.length >= 3) {
                    if ("full".startsWith(prefix)) {
                        completions.add("full");
                    }
                } else if (subcommand.equals("buy")) {
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
        } else if (command.getName().equalsIgnoreCase("ore")) {
            if (args.length == 1) {
                String prefix = args[0].toLowerCase();
                if ("repair".startsWith(prefix)) {
                    completions.add("repair");
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("repair")) {
                String prefix = args[1].toLowerCase();
                for (BlockDefinition definition : ItemList.generationBlocks()) {
                    String id = definition.getId();
                    if (id != null && id.toLowerCase().startsWith(prefix)) {
                        completions.add(id);
                    }
                }
            }
        } else if (command.getName().equalsIgnoreCase("sellhand")) {
            // No arguments for sell hand command
        } else if (command.getName().equalsIgnoreCase("marketreload")) {
            // No arguments for marketreload command
        } else if (command.getName().equalsIgnoreCase("help")) {
            // No arguments for help command
        } else if (command.getName().equalsIgnoreCase("autosell")) {
            // No arguments for autosell command
        }

        return completions;
    }

    private List<String> getMarketItemSuggestions(String prefix) {
        refreshMarketItemSuggestionCache();
        List<String> suggestions = new ArrayList<>();
        for (String name : marketItemSuggestionCache) {
            if (name.startsWith(prefix)) {
                suggestions.add(name);
            }
        }
        return suggestions;
    }

    private List<String> getOwnedItemSuggestions(Player player, String prefix) {
        if (player == null) {
            return Collections.emptyList();
        }
        refreshOwnedItemSuggestionCache(player);
        List<String> cached = ownedItemSuggestionCache.get(player.getUniqueId());
        if (cached == null || cached.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> suggestions = new ArrayList<>();
        for (String name : cached) {
            if (name.startsWith(prefix)) {
                suggestions.add(name);
            }
        }
        return suggestions;
    }

    private void openMarketWithRecalculation(Player player, String filter) {
        if (player == null) {
            return;
        }
        marketManager.recalculatePricesIfNeededAsync(
                () -> guiManager.openMarketGUI(player, filter),
                () -> player.sendMessage(ChatColor.RED + "There was an error recalculating market prices. " +
                        "Please contact ExoHayvan on Discord or report an issue on GitHub."),
                willRecalculate -> {
                    if (willRecalculate) {
                        player.sendMessage(ChatColor.YELLOW + "Recalculating market prices...");
                    }
                }
        );
    }

    private void sendMarketInfo(Player player) {
        if (player == null) {
            return;
        }
        databaseManager.runOnDbThread(() -> {
            List<MarketItem> items = databaseManager.getMarketItems();
            int totalListings = 0;
            BigInteger totalQuantity = BigInteger.ZERO;
            double totalValue = 0d;
            Set<String> uniqueItems = new HashSet<>();
            for (MarketItem item : items) {
                if (OreBreakdown.isOreFamilyNugget(item.getItemStack())) {
                    continue;
                }
                totalListings++;
                BigInteger qty = item.getQuantity().max(BigInteger.ZERO);
                totalQuantity = totalQuantity.add(qty);
                totalValue += item.getPrice() * toDoubleCapped(qty);
                uniqueItems.add(item.getItemData());
            }

            DatabaseManager.Stats global = databaseManager.getStats("global");
            DatabaseManager.DemandStats demandTotals = databaseManager.getDemandTotals();

            List<MarketItem> owned = databaseManager.getMarketItemsByOwner(player.getUniqueId().toString());
            int ownedListings = 0;
            BigInteger ownedQuantity = BigInteger.ZERO;
            double ownedValue = 0d;
            Set<String> ownedUnique = new HashSet<>();
            for (MarketItem item : owned) {
                if (OreBreakdown.isOreFamilyNugget(item.getItemStack())) {
                    continue;
                }
                ownedListings++;
                BigInteger qty = item.getQuantity().max(BigInteger.ZERO);
                ownedQuantity = ownedQuantity.add(qty);
                ownedValue += item.getPrice() * toDoubleCapped(qty);
                ownedUnique.add(item.getItemData());
            }

            DatabaseManager.Stats personal = databaseManager.getStats(player.getUniqueId().toString());

            int finalTotalListings = totalListings;
            BigInteger finalTotalQuantity = totalQuantity;
            double finalTotalValue = totalValue;
            Set<String> finalUniqueItems = new HashSet<>(uniqueItems);
            DatabaseManager.Stats finalGlobal = global;
            DatabaseManager.DemandStats finalDemandTotals = demandTotals;
            int finalOwnedListings = ownedListings;
            BigInteger finalOwnedQuantity = ownedQuantity;
            double finalOwnedValue = ownedValue;
            Set<String> finalOwnedUnique = new HashSet<>(ownedUnique);
            DatabaseManager.Stats finalPersonal = personal;

            getServer().getScheduler().runTask(this, () -> {
                player.sendMessage(ChatColor.GOLD + "Market Totals");
                player.sendMessage(ChatColor.GRAY + "Listings: " + finalTotalListings +
                        " | Unique items: " + finalUniqueItems.size());
                player.sendMessage(ChatColor.GRAY + "Supply listed: " + QuantityFormatter.format(finalTotalQuantity));
                player.sendMessage(ChatColor.GRAY + "Listed value: " + CurrencyFormatter.format(finalTotalValue));
                player.sendMessage(ChatColor.GRAY + "Items traded: " + QuantityFormatter.format(finalGlobal.itemsSold) +
                        " | Value traded: " + CurrencyFormatter.format(finalGlobal.moneyEarned));
                player.sendMessage(ChatColor.GRAY + "Demand 1h: " + QuantityFormatter.format(finalDemandTotals.hour) +
                        " | 1d: " + QuantityFormatter.format(finalDemandTotals.day) +
                        " | 1mo: " + QuantityFormatter.format(finalDemandTotals.month) +
                        " | 1y: " + QuantityFormatter.format(finalDemandTotals.year));

                player.sendMessage(ChatColor.GOLD + "Your Market Stats");
                player.sendMessage(ChatColor.GRAY + "Listings: " + finalOwnedListings +
                        " | Unique items: " + finalOwnedUnique.size());
                player.sendMessage(ChatColor.GRAY + "Supply listed: " + QuantityFormatter.format(finalOwnedQuantity));
                player.sendMessage(ChatColor.GRAY + "Listed value: " + CurrencyFormatter.format(finalOwnedValue));
                player.sendMessage(ChatColor.GRAY + "Items sold: " + QuantityFormatter.format(finalPersonal.itemsSold) +
                        " | Earned: " + CurrencyFormatter.format(finalPersonal.moneyEarned));
                player.sendMessage(ChatColor.GRAY + "Items bought: " + QuantityFormatter.format(finalPersonal.itemsBought) +
                        " | Spent: " + CurrencyFormatter.format(finalPersonal.moneySpent));
            });
        });
    }

    private void refreshMarketItemSuggestionCache() {
        long now = System.currentTimeMillis();
        if (now - marketItemSuggestionLastRefresh < SUGGESTION_CACHE_MS) {
            return;
        }
        if (!marketItemSuggestionRefreshing.compareAndSet(false, true)) {
            return;
        }
        databaseManager.runOnDbThread(() -> {
            Set<String> unique = new LinkedHashSet<>();
            for (MarketItem item : databaseManager.getMarketItems()) {
                if (OreBreakdown.isOreFamilyNugget(item.getItemStack())) {
                    continue;
                }
                unique.add(item.getType().toString().toLowerCase());
            }
            marketItemSuggestionCache = new ArrayList<>(unique);
            marketItemSuggestionLastRefresh = System.currentTimeMillis();
            marketItemSuggestionRefreshing.set(false);
        });
    }

    private void refreshOwnedItemSuggestionCache(Player player) {
        UUID ownerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        long last = ownedItemSuggestionLastRefresh.getOrDefault(ownerId, 0L);
        if (now - last < SUGGESTION_CACHE_MS) {
            return;
        }
        AtomicBoolean refreshing = ownedItemSuggestionRefreshing.computeIfAbsent(ownerId, ignored -> new AtomicBoolean(false));
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        databaseManager.runOnDbThread(() -> {
            Set<String> unique = new LinkedHashSet<>();
            List<MarketItem> listings = databaseManager.getMarketItemsByOwner(ownerId.toString());
            for (MarketItem item : listings) {
                unique.add(item.getType().toString().toLowerCase());
            }
            ownedItemSuggestionCache.put(ownerId, new ArrayList<>(unique));
            ownedItemSuggestionLastRefresh.put(ownerId, System.currentTimeMillis());
            refreshing.set(false);
        });
    }

    private void sendMarketDebugItem(Player player, boolean full) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "You must be holding an item to debug.");
            return;
        }

        String defaultKey = ItemSanitizer.serializeToString(itemInHand);
        String marketKey = ItemSanitizer.serializeToString(ItemSanitizer.sanitizeForMarket(itemInHand));
        Set<String> keys = new LinkedHashSet<>();
        keys.add(defaultKey);
        keys.add(marketKey);

        player.sendMessage(ChatColor.GOLD + "Market Debug - Item");
        player.sendMessage(ChatColor.GRAY + "Type: " + itemInHand.getType());
        player.sendMessage(ChatColor.GRAY + "Key (default) len: " + defaultKey.length());
        if (!marketKey.equals(defaultKey)) {
            player.sendMessage(ChatColor.GRAY + "Key (market) len: " + marketKey.length());
        }

        int index = 0;
        for (String key : keys) {
            String label = index == 0 ? "default" : "market";
            List<MarketItem> listings = databaseManager.getMarketItemsByItemData(key);
            player.sendMessage(ChatColor.YELLOW + "Matches (" + label + "): " + listings.size());
            String preview = full ? key : previewItemData(key, 120);
            player.sendMessage(ChatColor.DARK_GRAY + "item_data: " + preview);
            if (!full && key.length() > preview.length()) {
                player.sendMessage(ChatColor.DARK_GRAY + "item_data truncated (use /market debug item full).");
            }
            for (MarketItem listing : listings) {
                player.sendMessage(ChatColor.GRAY + "seller=" + listing.getSellerUUID() +
                        " qty=" + listing.getQuantity().toString() +
                        " price=" + CurrencyFormatter.format(listing.getPrice()));
            }
            index++;
        }
    }

    private void sendMarketDebugListings(Player player, Material material, boolean full) {
        List<MarketItem> listings = databaseManager.getMarketItemsBySeller(material);
        player.sendMessage(ChatColor.GOLD + "Market Debug - " + material.toString().toLowerCase());
        player.sendMessage(ChatColor.GRAY + "Listings: " + listings.size());

        int index = 1;
        for (MarketItem listing : listings) {
            String data = listing.getItemData();
            String preview = full ? data : previewItemData(data, 120);
            player.sendMessage(ChatColor.YELLOW + "#" + index +
                    " seller=" + listing.getSellerUUID() +
                    " qty=" + listing.getQuantity().toString() +
                    " price=" + CurrencyFormatter.format(listing.getPrice()));
            player.sendMessage(ChatColor.DARK_GRAY + "item_data: " + preview);
            if (!full && data != null && data.length() > 120) {
                player.sendMessage(ChatColor.DARK_GRAY + "item_data truncated (use /market debug " +
                        material.toString().toLowerCase() + " full).");
            }
            index++;
        }
    }

    private Material resolveMaterial(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String normalized = name.trim().toUpperCase().replace(' ', '_');
        Material match = Material.matchMaterial(normalized);
        if (match != null) {
            return match;
        }
        return Material.matchMaterial(name.trim());
    }

    private String previewItemData(String data, int maxLen) {
        if (data == null) {
            return "";
        }
        if (maxLen <= 0 || data.length() <= maxLen) {
            return data;
        }
        return data.substring(0, maxLen) + "...";
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

    private String joinArgs(String[] args, int startIndex, int endExclusive) {
        if (args == null || startIndex >= args.length || endExclusive <= startIndex) {
            return null;
        }
        int end = Math.min(endExclusive, args.length);
        StringBuilder builder = new StringBuilder();
        for (int i = startIndex; i < end; i++) {
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
