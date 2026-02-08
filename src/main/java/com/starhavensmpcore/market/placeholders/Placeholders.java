package com.starhavensmpcore.market.placeholders;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.market.db.DatabaseManager;
import com.starhavensmpcore.market.economy.CurrencyFormatter;
import com.starhavensmpcore.seasons.SeasonUtil;
import com.starhavensmpcore.team.TeamService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.PluginDescriptionFile;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PlaceholderAPI expansion exposing basic market stats.
 */
public class Placeholders extends PlaceholderExpansion {

    private final StarhavenSMPCore plugin;
    private final DatabaseManager databaseManager;
    private static final long CACHE_TTL_MS = 2_000L;
    private volatile DatabaseManager.Stats globalStatsCache = new DatabaseManager.Stats();
    private volatile BigInteger globalTotalItemsCache = BigInteger.ZERO;
    private volatile long globalCacheLastRefresh = 0L;
    private final AtomicBoolean globalRefreshing = new AtomicBoolean(false);
    private final Map<UUID, DatabaseManager.Stats> playerStatsCache = new ConcurrentHashMap<>();
    private final Map<UUID, BigInteger> playerTotalItemsCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerCacheLastRefresh = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicBoolean> playerRefreshing = new ConcurrentHashMap<>();

    public Placeholders(StarhavenSMPCore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @Override
    public String getIdentifier() {
        return "starhaven";
    }

    @Override
    public String getAuthor() {
        PluginDescriptionFile desc = plugin.getDescription();
        return String.join(", ", desc.getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) {
            return "";
        }

        String lower = params.toLowerCase();
        refreshGlobalCache();
        refreshPlayerCache(player);
        DatabaseManager.Stats global = globalStatsCache;
        DatabaseManager.Stats playerStats = getPlayerStats(player);
        BigInteger totalItems = globalTotalItemsCache;
        BigInteger playerItems = getPlayerTotalItems(player);

        switch (lower) {
            case "season":
            case "season_formatted":
                return SeasonUtil.getCurrentSeasonFormatted();
            case "season_lower":
                return SeasonUtil.getCurrentSeasonLower();
            case "season_caps":
                return SeasonUtil.getCurrentSeasonCaps();
            case "total_items_bought":
                return String.valueOf(global.itemsBought);
            case "total_items_sold":
                return String.valueOf(global.itemsSold);
            case "total_money_spent":
                return rawMoney(global.moneySpent);
            case "total_money_earned":
                return rawMoney(global.moneyEarned);
            case "total_money_spent_formatted":
                return formatMoney(global.moneySpent);
            case "total_money_earned_formatted":
                return formatMoney(global.moneyEarned);
            case "total_listed_items":
                return String.valueOf(totalItems);
            case "player_items_bought":
                return String.valueOf(playerStats.itemsBought);
            case "player_items_sold":
                return String.valueOf(playerStats.itemsSold);
            case "player_money_spent":
                return rawMoney(playerStats.moneySpent);
            case "player_money_earned":
                return rawMoney(playerStats.moneyEarned);
            case "player_money_spent_formatted":
                return formatMoney(playerStats.moneySpent);
            case "player_money_earned_formatted":
                return formatMoney(playerStats.moneyEarned);
            case "player_listed_items":
                return String.valueOf(playerItems);
            case "team_money":
                return rawMoney(getTeamTotals(player).getTotalEco());
            case "team_money_formatted":
                return formatMoney(getTeamTotals(player).getTotalEco());
            case "team_items":
                return getTeamTotals(player).getTotalItems().toString();
            default:
                return "Not Valid";
        }
    }

    private void refreshGlobalCache() {
        long now = System.currentTimeMillis();
        if (now - globalCacheLastRefresh < CACHE_TTL_MS) {
            return;
        }
        if (!globalRefreshing.compareAndSet(false, true)) {
            return;
        }
        databaseManager.runOnDbThread(() -> {
            DatabaseManager.Stats stats = databaseManager.getStats("global");
            BigInteger totalItems = databaseManager.getTotalItemsInShop();
            globalStatsCache = stats;
            globalTotalItemsCache = totalItems;
            globalCacheLastRefresh = System.currentTimeMillis();
            globalRefreshing.set(false);
        });
    }

    private void refreshPlayerCache(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        long last = playerCacheLastRefresh.getOrDefault(playerId, 0L);
        if (now - last < CACHE_TTL_MS) {
            return;
        }
        AtomicBoolean refreshing = playerRefreshing.computeIfAbsent(playerId, ignored -> new AtomicBoolean(false));
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        String key = playerKey(player);
        databaseManager.runOnDbThread(() -> {
            DatabaseManager.Stats stats = databaseManager.getStats(key);
            BigInteger totalItems = databaseManager.getTotalItemsInShopForSeller(key);
            playerStatsCache.put(playerId, stats);
            playerTotalItemsCache.put(playerId, totalItems);
            playerCacheLastRefresh.put(playerId, System.currentTimeMillis());
            refreshing.set(false);
        });
    }

    private DatabaseManager.Stats getPlayerStats(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) {
            return new DatabaseManager.Stats();
        }
        return playerStatsCache.getOrDefault(player.getUniqueId(), new DatabaseManager.Stats());
    }

    private BigInteger getPlayerTotalItems(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) {
            return BigInteger.ZERO;
        }
        return playerTotalItemsCache.getOrDefault(player.getUniqueId(), BigInteger.ZERO);
    }

    private String playerKey(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) {
            return "";
        }
        return player.getUniqueId().toString();
    }

    private String formatMoney(double amount) {
        return CurrencyFormatter.format(amount);
    }

    private String rawMoney(double amount) {
        return String.valueOf(amount);
    }

    private TeamService.TeamTotals getTeamTotals(OfflinePlayer player) {
        TeamService teamService = plugin.getTeamService();
        if (teamService == null) {
            return TeamService.TeamTotals.empty();
        }
        return teamService.getTeamTotals(player);
    }
}
