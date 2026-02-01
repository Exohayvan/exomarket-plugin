package com.starhavensmpcore.placeholderapi;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.market.db.DatabaseManager;
import com.starhavensmpcore.market.economy.CurrencyFormatter;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.PluginDescriptionFile;

/**
 * PlaceholderAPI expansion exposing basic market stats.
 */
public class MarketPlaceholder extends PlaceholderExpansion {

    private final StarhavenSMPCore plugin;
    private final DatabaseManager databaseManager;

    public MarketPlaceholder(StarhavenSMPCore plugin, DatabaseManager databaseManager) {
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

        switch (lower) {
            case "total_items_bought":
                return String.valueOf(global.itemsBought);
            case "total_items_sold":
                return String.valueOf(global.itemsSold);
            case "total_money_spent":
                return formatMoney(global.moneySpent);
            case "total_money_earned":
                return formatMoney(global.moneyEarned);
            case "total_listed_items":
                return String.valueOf(databaseManager.getTotalItemsInShop());
            case "player_items_bought":
                return String.valueOf(playerStats.itemsBought);
            case "player_items_sold":
                return String.valueOf(playerStats.itemsSold);
            case "player_money_spent":
                return formatMoney(playerStats.moneySpent);
            case "player_money_earned":
                return formatMoney(playerStats.moneyEarned);
            case "player_listed_items":
                return String.valueOf(databaseManager.getTotalItemsInShopForSeller(playerKey(player)));
            default:
                return "";
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
}
