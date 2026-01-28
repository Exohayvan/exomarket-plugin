package com.starhavensmpcore.market.gui;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.market.MarketManager;
import com.starhavensmpcore.market.items.ItemSanitizer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class MarketSellGUI implements Listener {

    private static final String INVENTORY_TITLE = "Market Sell";
    private final StarhavenSMPCore plugin;
    private final MarketManager marketManager;
    private final Map<UUID, Inventory> openInventories = new HashMap<>();
    private final ItemStack infoItem;

    public MarketSellGUI(StarhavenSMPCore plugin, MarketManager marketManager) {
        this.plugin = plugin;
        this.marketManager = marketManager;
        this.infoItem = createInfoItem();
    }

    public void openSellGUI(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 27, INVENTORY_TITLE);
        inventory.setItem(26, infoItem.clone());
        openInventories.put(player.getUniqueId(), inventory);
        player.openInventory(inventory);
        player.sendMessage(ChatColor.YELLOW + "Move the items you want to list into the top inventory, then close it to submit.");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Inventory trackedInventory = openInventories.get(player.getUniqueId());
        if (trackedInventory == null) {
            return;
        }

        if (!event.getView().getTitle().equals(INVENTORY_TITLE)) {
            return;
        }

        if (event.getClickedInventory() == null) {
            return;
        }

        if (event.getClickedInventory().equals(trackedInventory)) {
            ItemStack currentItem = event.getCurrentItem();
            if (currentItem != null && currentItem.isSimilar(infoItem)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        Inventory trackedInventory = openInventories.remove(player.getUniqueId());
        if (trackedInventory == null) {
            return;
        }

        if (!event.getView().getTitle().equals(INVENTORY_TITLE)) {
            return;
        }

        Map<String, Integer> summary = new LinkedHashMap<>();
        boolean returnedDamagedItems = false;
        for (int slot = 0; slot < trackedInventory.getSize(); slot++) {
            ItemStack stack = trackedInventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (stack.isSimilar(infoItem)) {
                continue;
            }

            ItemStack toSell = stack.clone();
            trackedInventory.setItem(slot, null);

            if (ItemSanitizer.isDamaged(toSell)) {
                returnItemToPlayer(player, toSell);
                returnedDamagedItems = true;
                continue;
            }

            boolean sold = marketManager.sellItem(player, toSell, false);
            if (!sold) {
                returnItemToPlayer(player, toSell);
                continue;
            }

            String key = ItemSanitizer.sanitize(toSell).getType().toString();
            summary.merge(key, toSell.getAmount(), Integer::sum);
        }

        if (returnedDamagedItems) {
            player.sendMessage(ChatColor.RED + "Damaged items were returned and cannot be listed on the market.");
        }

        if (summary.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "No items were submitted to the market.");
        } else {
            player.sendMessage(ChatColor.GREEN + "Submitted items to the market:");
            summary.forEach((name, amount) ->
                    player.sendMessage(ChatColor.YELLOW + "- " + amount + "x " + name));
            plugin.getServer().broadcastMessage(ChatColor.YELLOW + player.getName() + " has added items to the market!");
            marketManager.recalculatePrices();
        }
    }

    private ItemStack createInfoItem() {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setDisplayName(ChatColor.GOLD + "Submit Instructions");
        meta.setLore(java.util.Arrays.asList(
                ChatColor.GRAY + "Move items here to list them.",
                ChatColor.GRAY + "Close the inventory to submit."
        ));
        paper.setItemMeta(meta);
        return paper;
    }

    private void returnItemToPlayer(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(leftover ->
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        player.updateInventory();
    }
}
