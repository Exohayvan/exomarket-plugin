package com.starhavensmpcore.placeholderapi;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.market.db.DatabaseManager;
import com.starhavensmpcore.market.economy.CurrencyFormatter;
import com.starhavensmpcore.seasons.SeasonUtil;
import com.starhavensmpcore.team.TeamService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.PluginDescriptionFile;

/**
 * PlaceholderAPI expansion exposing basic market stats.
 */
public class Placeholders extends PlaceholderExpansion {

    private final StarhavenSMPCore plugin;
    private final DatabaseManager databaseManager;

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
        DatabaseManager.Stats global = databaseManager.getStats("global");
        DatabaseManager.Stats playerStats = databaseManager.getStats(playerKey(player));
        TeamService teamService = plugin.getTeamService();
        TeamService.TeamTotals teamTotals = teamService == null ? TeamService.TeamTotals.empty() : teamService.getTeamTotals(player);

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
                return String.valueOf(databaseManager.getTotalItemsInShop());
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
                return String.valueOf(databaseManager.getTotalItemsInShopForSeller(playerKey(player)));
            case "team_money":
                return rawMoney(teamTotals.getTotalEco());
            case "team_money_formatted":
                return formatMoney(teamTotals.getTotalEco());
            case "team_items":
                return teamTotals.getTotalItems().toString();
            default:
                return "Not Valid";
        }
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
}
