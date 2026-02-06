package com.starhavensmpcore.help;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class HelpMenu implements Listener {

    private static final String ROOT_TITLE = ChatColor.DARK_AQUA + "Starhaven Help";
    private static final String MARKET_TITLE = ChatColor.DARK_AQUA + "Help - Market";
    private static final String WAYSTONE_TITLE = ChatColor.DARK_AQUA + "Help - Waystones";
    private static final String ITEMS_TITLE = ChatColor.DARK_AQUA + "Help - Custom Items";
    private static final String COMMANDS_TITLE = ChatColor.DARK_AQUA + "Help - Commands";
    private static final String CLAIMS_TITLE = ChatColor.DARK_AQUA + "Help - Claims";
    private static final String QUESTS_TITLE = ChatColor.DARK_AQUA + "Help - Quests";
    private static final String COMMUNITY_TITLE = ChatColor.DARK_AQUA + "Help - Community";

    private static final String MENU_MARKET = "Market & Economy";
    private static final String MENU_WAYSTONES = "Waystones & Travel";
    private static final String MENU_ITEMS = "Custom Items";
    private static final String MENU_COMMANDS = "Commands";
    private static final String MENU_CLAIMS = "Claims & Protection";
    private static final String MENU_QUESTS = "Quests & Progression";
    private static final String MENU_COMMUNITY = "Community & Rewards";
    private static final String BACK_LABEL = "Back";
    private static final String CLOSE_LABEL = "Close";

    private final ItemStack filler;

    public HelpMenu() {
        this.filler = createFiller();
    }

    public void openRoot(Player player) {
        if (player == null) {
            return;
        }
        Inventory inventory = createMenu(player, ROOT_TITLE);
        inventory.setItem(10, createMenuItem(Material.EMERALD, ChatColor.GOLD + MENU_MARKET,
                ChatColor.GRAY + "Market listings, buying, selling",
                ChatColor.GRAY + "Click to open"));
        inventory.setItem(11, createMenuItem(Material.COMPASS, ChatColor.GOLD + MENU_WAYSTONES,
                ChatColor.GRAY + "Waystone travel and access",
                ChatColor.GRAY + "Click to open"));
        inventory.setItem(12, createMenuItem(Material.ANVIL, ChatColor.GOLD + MENU_ITEMS,
                ChatColor.GRAY + "Custom items, crafting, ores",
                ChatColor.GRAY + "Click to open"));
        inventory.setItem(13, createMenuItem(Material.BOOK, ChatColor.GOLD + MENU_COMMANDS,
                ChatColor.GRAY + "Quick command reference",
                ChatColor.GRAY + "Click to open"));
        inventory.setItem(14, createMenuItem(Material.GOLDEN_SHOVEL, ChatColor.GOLD + MENU_CLAIMS,
                ChatColor.GRAY + "Land claims and protection",
                ChatColor.GRAY + "Click to open"));
        inventory.setItem(15, createMenuItem(Material.WRITABLE_BOOK, ChatColor.GOLD + MENU_QUESTS,
                ChatColor.GRAY + "Quests, levels, progression",
                ChatColor.GRAY + "Click to open"));
        inventory.setItem(16, createMenuItem(Material.NETHER_STAR, ChatColor.GOLD + MENU_COMMUNITY,
                ChatColor.GRAY + "Voting, Discord, web map",
                ChatColor.GRAY + "Click to open"));
        inventory.setItem(22, createMenuItem(Material.BARRIER, ChatColor.RED + CLOSE_LABEL,
                ChatColor.GRAY + "Close this menu"));
        player.openInventory(inventory);
    }

    private void openMarket(Player player) {
        Inventory inventory = createMenu(player, MARKET_TITLE);
        inventory.setItem(10, createMenuItem(Material.EMERALD, ChatColor.GOLD + "/market",
                ChatColor.GRAY + "Browse live listings",
                ChatColor.GRAY + "Use /market buy <item> to filter"));
        inventory.setItem(11, createMenuItem(Material.GOLD_INGOT, ChatColor.GOLD + "/market sell",
                ChatColor.GRAY + "List the item in your hand"));
        inventory.setItem(12, createMenuItem(Material.CHEST, ChatColor.GOLD + "/market items",
                ChatColor.GRAY + "View your listings",
                ChatColor.GRAY + "Filter: /market items <name>"));
        inventory.setItem(13, createMenuItem(Material.PAPER, ChatColor.GOLD + "/market info",
                ChatColor.GRAY + "See market totals and stats"));
        inventory.setItem(14, createMenuItem(Material.IRON_INGOT, ChatColor.GOLD + "/sellhand",
                ChatColor.GRAY + "Quick sell the item in your hand"));
        inventory.setItem(15, createMenuItem(Material.HOPPER, ChatColor.GOLD + "/autosell",
                ChatColor.GRAY + "Open the autosell GUI",
                ChatColor.GRAY + "Click items to toggle autosell"));
        addBackAndClose(inventory);
        player.openInventory(inventory);
    }

    private void openWaystones(Player player) {
        Inventory inventory = createMenu(player, WAYSTONE_TITLE);
        inventory.setItem(10, createMenuItem(Material.OBSIDIAN, ChatColor.GOLD + "Craft a Waystone",
                ChatColor.GRAY + "Craft Obsidian Waystones",
                ChatColor.GRAY + "Iron Waystones are admin-only"));
        inventory.setItem(11, createMenuItem(Material.NAME_TAG, ChatColor.GOLD + "Name Your Waystone",
                ChatColor.GRAY + "After placing, name it in chat",
                ChatColor.GRAY + "You have 60 seconds"));
        inventory.setItem(12, createMenuItem(Material.COMPASS, ChatColor.GOLD + "Open Waystone List",
                ChatColor.GRAY + "Right-click a waystone",
                ChatColor.GRAY + "Select one to teleport"));
        inventory.setItem(13, createMenuItem(Material.BOOK, ChatColor.GOLD + "Save a Waystone",
                ChatColor.GRAY + "Right-click a waystone you don't own",
                ChatColor.GRAY + "Choose a name to save it"));
        inventory.setItem(14, createMenuItem(Material.BARRIER, ChatColor.GOLD + "Spacing Rule",
                ChatColor.GRAY + "Waystones must be 4 blocks apart"));
        addBackAndClose(inventory);
        player.openInventory(inventory);
    }

    private void openItems(Player player) {
        Inventory inventory = createMenu(player, ITEMS_TITLE);
        inventory.setItem(10, createMenuItem(Material.AMETHYST_SHARD, ChatColor.GOLD + "Custom Ores",
                ChatColor.GRAY + "Custom ores spawn in the world",
                ChatColor.GRAY + "Mine them for rare materials"));
        inventory.setItem(11, createMenuItem(Material.CRAFTING_TABLE, ChatColor.GOLD + "Crafting Recipes",
                ChatColor.GRAY + "New recipes are in your recipe book",
                ChatColor.GRAY + "Look for waystones and custom items"));
        inventory.setItem(12, createMenuItem(Material.FURNACE, ChatColor.GOLD + "Smelting",
                ChatColor.GRAY + "Some ores smelt into custom ingots"));
        inventory.setItem(13, createMenuItem(Material.ENCHANTING_TABLE, ChatColor.GOLD + "Enchantment Splitter",
                ChatColor.GRAY + "Split enchanted books at a table",
                ChatColor.GRAY + "Separate unwanted enchants"));
        inventory.setItem(14, createMenuItem(Material.ENCHANTED_BOOK, ChatColor.GOLD + "Unsafe Enchants",
                ChatColor.GRAY + "Enchants can go above normal",
                ChatColor.GRAY + "Levels can reach 255"));
        inventory.setItem(15, createMenuItem(Material.GRINDSTONE, ChatColor.GOLD + "Grindstone Removal",
                ChatColor.GRAY + "Remove enchants from tools",
                ChatColor.GRAY + "Use a grindstone"));
        inventory.setItem(16, createMenuItem(Material.SPAWNER, ChatColor.GOLD + "Silk Touch Spawners",
                ChatColor.GRAY + "Pick up spawners with Silk Touch",
                ChatColor.GRAY + "Use a Silk Touch pickaxe"));
        addBackAndClose(inventory);
        player.openInventory(inventory);
    }

    private void openCommands(Player player) {
        Inventory inventory = createMenu(player, COMMANDS_TITLE);
        inventory.setItem(10, createMenuItem(Material.EMERALD, ChatColor.GOLD + "Market",
                ChatColor.GRAY + "/market, /market buy, /market sell",
                ChatColor.GRAY + "/market items, /market info"));
        inventory.setItem(11, createMenuItem(Material.HOPPER, ChatColor.GOLD + "Autosell",
                ChatColor.GRAY + "/autosell",
                ChatColor.GRAY + "Click items to toggle autosell"));
        inventory.setItem(12, createMenuItem(Material.IRON_INGOT, ChatColor.GOLD + "Quick Sell",
                ChatColor.GRAY + "/sellhand"));
        inventory.setItem(13, createMenuItem(Material.RED_BED, ChatColor.GOLD + "Homes",
                ChatColor.GRAY + "/sethome to save a home",
                ChatColor.GRAY + "/home to return"));
        inventory.setItem(14, createMenuItem(Material.NAME_TAG, ChatColor.GOLD + "Custom Tags",
                ChatColor.GRAY + "Pick a tag with DeluxeTags",
                ChatColor.GRAY + "Use /tags if enabled"));
        addBackAndClose(inventory);
        player.openInventory(inventory);
    }

    private void openClaims(Player player) {
        Inventory inventory = createMenu(player, CLAIMS_TITLE);
        inventory.setItem(10, createMenuItem(Material.WOODEN_SHOVEL, ChatColor.GOLD + "Claim Land",
                ChatColor.GRAY + "Use a wooden shovel to claim",
                ChatColor.GRAY + "Right-click two corners"));
        inventory.setItem(11, createMenuItem(Material.NAME_TAG, ChatColor.GOLD + "Trust Friends",
                ChatColor.GRAY + "Allow others in your claim",
                ChatColor.GRAY + "Use /trust <player>"));
        inventory.setItem(12, createMenuItem(Material.BARRIER, ChatColor.GOLD + "Abandon Claims",
                ChatColor.GRAY + "Remove a claim when needed",
                ChatColor.GRAY + "Use /abandonclaim"));
        inventory.setItem(13, createMenuItem(Material.SHIELD, ChatColor.GOLD + "Protected Areas",
                ChatColor.GRAY + "Some regions are staff protected",
                ChatColor.GRAY + "Follow posted rules"));
        addBackAndClose(inventory);
        player.openInventory(inventory);
    }

    private void openQuests(Player player) {
        Inventory inventory = createMenu(player, QUESTS_TITLE);
        inventory.setItem(10, createMenuItem(Material.WRITABLE_BOOK, ChatColor.GOLD + "Quests",
                ChatColor.GRAY + "Pick up quests from NPCs",
                ChatColor.GRAY + "Try /quests if enabled"));
        inventory.setItem(11, createMenuItem(Material.EXPERIENCE_BOTTLE, ChatColor.GOLD + "Progression",
                ChatColor.GRAY + "LevelTools tracks your progress",
                ChatColor.GRAY + "Higher levels unlock more"));
        inventory.setItem(12, createMenuItem(Material.ZOMBIE_SPAWN_EGG, ChatColor.GOLD + "Levelled Mobs",
                ChatColor.GRAY + "Mobs scale with difficulty",
                ChatColor.GRAY + "Bring better gear"));
        addBackAndClose(inventory);
        player.openInventory(inventory);
    }

    private void openCommunity(Player player) {
        Inventory inventory = createMenu(player, COMMUNITY_TITLE);
        inventory.setItem(10, createMenuItem(Material.NETHER_STAR, ChatColor.GOLD + "Voting Rewards",
                ChatColor.GRAY + "Vote for rewards and perks",
                ChatColor.GRAY + "Use /vote to see links"));
        inventory.setItem(11, createMenuItem(Material.MAP, ChatColor.GOLD + "BlueMap",
                ChatColor.GRAY + "Web map of the world",
                ChatColor.GRAY + "Ask staff for the link"));
        inventory.setItem(12, createMenuItem(Material.PAPER, ChatColor.GOLD + "Discord",
                ChatColor.GRAY + "Join Discord for updates",
                ChatColor.GRAY + "Use /discord if enabled"));
        inventory.setItem(13, createMenuItem(Material.GRASS_BLOCK, ChatColor.GOLD + "Bedrock Support",
                ChatColor.GRAY + "Geyser/Floodgate enabled",
                ChatColor.GRAY + "Bedrock players can join"));
        addBackAndClose(inventory);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onHelpClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        String title = event.getView().getTitle();
        if (!isHelpMenu(title)) {
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        String label = getLabel(clicked);
        if (label == null || label.isEmpty()) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        if (ROOT_TITLE.equals(title)) {
            if (MENU_MARKET.equals(label)) {
                openMarket(player);
                return;
            }
            if (MENU_WAYSTONES.equals(label)) {
                openWaystones(player);
                return;
            }
            if (MENU_ITEMS.equals(label)) {
                openItems(player);
                return;
            }
            if (MENU_COMMANDS.equals(label)) {
                openCommands(player);
                return;
            }
            if (MENU_CLAIMS.equals(label)) {
                openClaims(player);
                return;
            }
            if (MENU_QUESTS.equals(label)) {
                openQuests(player);
                return;
            }
            if (MENU_COMMUNITY.equals(label)) {
                openCommunity(player);
                return;
            }
            if (CLOSE_LABEL.equals(label)) {
                player.closeInventory();
            }
            return;
        }

        if (BACK_LABEL.equals(label)) {
            openRoot(player);
            return;
        }
        if (CLOSE_LABEL.equals(label)) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onHelpDrag(InventoryDragEvent event) {
        if (!isHelpMenu(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
    }

    private Inventory createMenu(Player player, String title) {
        Inventory inventory = Bukkit.createInventory(player, 27, title);
        fillBackground(inventory);
        return inventory;
    }

    private void addBackAndClose(Inventory inventory) {
        inventory.setItem(18, createMenuItem(Material.ARROW, ChatColor.YELLOW + BACK_LABEL,
                ChatColor.GRAY + "Return to main menu"));
        inventory.setItem(26, createMenuItem(Material.BARRIER, ChatColor.RED + CLOSE_LABEL,
                ChatColor.GRAY + "Close this menu"));
    }

    private void fillBackground(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private boolean isHelpMenu(String title) {
        return ROOT_TITLE.equals(title)
                || MARKET_TITLE.equals(title)
                || WAYSTONE_TITLE.equals(title)
                || ITEMS_TITLE.equals(title)
                || COMMANDS_TITLE.equals(title)
                || CLAIMS_TITLE.equals(title)
                || QUESTS_TITLE.equals(title)
                || COMMUNITY_TITLE.equals(title);
    }

    private ItemStack createFiller() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createMenuItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (loreLines != null && loreLines.length > 0) {
                List<String> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(line);
                }
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private String getLabel(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }
        return ChatColor.stripColor(meta.getDisplayName()).trim();
    }
}
