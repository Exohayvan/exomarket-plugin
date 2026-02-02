package com.starhavensmpcore.market.economy;

import com.starhavensmpcore.core.StarhavenSMPCore;
import org.bukkit.entity.Player;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.ChatColor;
import java.util.UUID;
import java.io.File;

public class EconomyManager {

    private StarhavenSMPCore plugin;
    private Economy economy;

    public EconomyManager(StarhavenSMPCore plugin) {
        this.plugin = plugin;
        this.economy = Bukkit.getServicesManager().getRegistration(Economy.class).getProvider();
    }

    public boolean hasEnoughMoney(Player player, double amount) {
        return economy.has(player, amount);
    }

    public void withdrawMoney(Player player, double amount) {
        economy.withdrawPlayer(player, amount);
        plugin.getLogger().info("Withdrew " + CurrencyFormatter.format(amount) + " from " + player.getName());
    }

    public void addMoney(String playerUUID, double amount) {
        try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(playerUUID));
            ensureAccountExists(offlinePlayer);
            double oldBalance = economy.getBalance(offlinePlayer);
            economy.depositPlayer(offlinePlayer, amount);
            double newBalance = economy.getBalance(offlinePlayer);
            
            // Log the transaction
            plugin.getLogger().info("Added " + CurrencyFormatter.format(amount) + " to player " + offlinePlayer.getName() +
                                    ". Old balance: " + CurrencyFormatter.format(oldBalance) +
                                    ", New balance: " + CurrencyFormatter.format(newBalance));
            
            // Check if the player is online and send a message
            Player onlinePlayer = offlinePlayer.getPlayer();
            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                onlinePlayer.sendMessage(ChatColor.GREEN + "You received " + CurrencyFormatter.format(amount) +
                                        " from the market! Your new balance is " + CurrencyFormatter.format(newBalance));
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error adding money to player " + playerUUID + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void ensureAccountExists(OfflinePlayer player) {
        if (!economy.hasAccount(player)) {
            economy.createPlayerAccount(player);
        }
    }

    public double getBalance(Player player) {
        return economy.getBalance(player);
    }

    public double getBalance(OfflinePlayer player) {
        return economy.getBalance(player);
    }

    public double getTotalMoney() {
        double totalMoney = 0;
        
        // Get all player files from the server data
        File playerDataFolder = new File(Bukkit.getServer().getWorldContainer(), Bukkit.getWorlds().get(0).getName() + "/playerdata");
        File[] playerFiles = playerDataFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".dat"));
        
        if (playerFiles != null) {
            for (File playerFile : playerFiles) {
                // Convert file name to UUID
                String fileName = playerFile.getName();
                String uuidString = fileName.substring(0, fileName.length() - 4); // Remove ".dat"
                UUID playerUUID = UUID.fromString(uuidString);
                
                // Get the OfflinePlayer object
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
                
                // Add the player's balance to the total
                totalMoney += economy.getBalance(offlinePlayer);
            }
        }
        
        return totalMoney;
    }
}
