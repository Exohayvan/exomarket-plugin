package com.starhavensmpcore.items;

import com.starhavensmpcore.core.StarhavenSMPCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.block.data.BlockData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class CustomItemManager implements Listener, CommandExecutor {
    private static final String GIVE_PERMISSION = "starhaven.items.give";

    private final StarhavenSMPCore plugin;
    private final CustomBlockRegistry customBlockRegistry;
    private final NamespacedKey itemKey;

    public CustomItemManager(StarhavenSMPCore plugin, CustomBlockRegistry customBlockRegistry) {
        this.plugin = plugin;
        this.customBlockRegistry = customBlockRegistry;
        this.itemKey = new NamespacedKey(plugin, "custom_item");
    }

    public ItemStack createItem(CustomItemType type, int amount) {
        ItemStack item = new ItemStack(type.getBaseMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + type.getDisplayName());
            try {
                meta.setCustomModelData(type.getCustomModelData());
            } catch (NoSuchMethodError ignored) {
                // Older API versions won't have custom model data support.
            }
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, type.getId());
            item.setItemMeta(meta);
        }
        item.setAmount(Math.max(1, amount));
        return item;
    }

    public CustomItemType getCustomItemType(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String id = container.get(itemKey, PersistentDataType.STRING);
        return ItemList.fromArgument(id);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        CustomItemType type = getCustomItemType(item);
        if (type == null) {
            return;
        }
        String noteState = type.getNoteBlockState();
        if (noteState == null || noteState.isEmpty()) {
            return;
        }
        Block block = event.getBlockPlaced();
        block.setType(Material.NOTE_BLOCK, false);
        try {
            BlockData data = Bukkit.createBlockData(noteState);
            block.setBlockData(data, false);
            if (data instanceof org.bukkit.block.data.type.NoteBlock) {
                customBlockRegistry.mark(block, type, (org.bukkit.block.data.type.NoteBlock) data);
            }
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid note block state for " + type.getId() + ": " + noteState);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.NOTE_BLOCK) {
            return;
        }
        CustomBlockRegistry.CustomBlockData blockData = customBlockRegistry.getBlockData(block);
        if (blockData == null) {
            return;
        }
        if (!handleCustomBlockBreak(event, blockData.getType())) {
            return;
        }
        customBlockRegistry.unmark(block);
    }

    // Crafting recipes for custom items are currently disabled.


    private boolean handleCustomBlockBreak(org.bukkit.event.block.BlockBreakEvent event, CustomItemType type) {
        Player player = event.getPlayer();
        if (player == null) {
            return false;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean validTool = isValidTool(tool);
        if (!validTool) {
            player.sendMessage(ChatColor.RED + "You need at least an iron pickaxe to get drops from this.");
        }

        event.setDropItems(false);
        event.setExpToDrop(0);

        if (!validTool) {
            return true;
        }

        if (type == CustomItemType.VOID_BLOCK) {
            dropCustomItemAt(event.getBlock().getLocation(), CustomItemType.VOID_BLOCK, 1);
            return true;
        }

        if (type == CustomItemType.VOIDSTONE_ORE) {
            if (tool.hasItemMeta() && tool.getItemMeta().hasEnchant(Enchantment.SILK_TOUCH)) {
                dropCustomItemAt(event.getBlock().getLocation(), CustomItemType.VOIDSTONE_ORE, 1);
                return true;
            }
            event.setExpToDrop(ThreadLocalRandom.current().nextInt(12, 20));
            int fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE);
            int amount = 1 + getLowFortuneBonus(fortune);
            dropCustomItemAt(event.getBlock().getLocation(), CustomItemType.ECHO_SHARD, amount);
            return true;
        }

        return true;
    }

    private void dropCustomItemAt(Location location, CustomItemType type, int amount) {
        int remaining = Math.max(1, amount);
        int maxStack = type.getBaseMaterial().getMaxStackSize();
        while (remaining > 0) {
            int stackAmount = Math.min(remaining, maxStack);
            ItemStack stack = createItem(type, stackAmount);
            if (location != null) {
                Location dropLocation = location.clone().add(0.5, 0.5, 0.5);
                location.getWorld().dropItem(dropLocation, stack).setVelocity(new Vector(0, 0, 0));
            }
            remaining -= stackAmount;
        }
    }

    private boolean isValidTool(ItemStack tool) {
        if (tool == null) {
            return false;
        }
        Material material = tool.getType();
        switch (material) {
            case IRON_PICKAXE:
            case DIAMOND_PICKAXE:
            case NETHERITE_PICKAXE:
                return true;
            default:
                return false;
        }
    }

    private int getLowFortuneBonus(int fortuneLevel) {
        if (fortuneLevel <= 0) {
            return 0;
        }
        double chance = 0.15 * fortuneLevel;
        return ThreadLocalRandom.current().nextDouble() < chance ? 1 : 0;
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || !message.startsWith("/")) {
            return;
        }
        String[] parts = message.substring(1).trim().split("\\s+");
        if (parts.length < 3) {
            return;
        }
        String command = parts[0].toLowerCase(Locale.ROOT);
        if (!command.equals("give") && !command.equals("minecraft:give")) {
            return;
        }
        CustomItemType type = ItemList.fromArgument(parts[2]);
        if (type == null) {
            return;
        }
        event.setCancelled(true);
        handleGive(event.getPlayer(), parts);
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        String commandLine = event.getCommand();
        if (commandLine == null) {
            return;
        }
        String[] parts = commandLine.trim().split("\\s+");
        if (parts.length < 3) {
            return;
        }
        String command = parts[0].toLowerCase(Locale.ROOT);
        if (!command.equals("give") && !command.equals("minecraft:give")) {
            return;
        }
        CustomItemType type = ItemList.fromArgument(parts[2]);
        if (type == null) {
            return;
        }
        event.setCancelled(true);
        handleGive(event.getSender(), parts);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("starhavengive")) {
            return false;
        }
        if (!hasGivePermission(sender)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /starhavengive <player> <starhaven:item> [amount]");
            return true;
        }
        String[] parts = new String[args.length + 1];
        parts[0] = "give";
        System.arraycopy(args, 0, parts, 1, args.length);
        handleGive(sender, parts);
        return true;
    }

    private void handleGive(CommandSender sender, String[] parts) {
        if (!hasGivePermission(sender)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return;
        }
        if (parts.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /give <player> <starhaven:item> [amount]");
            return;
        }
        CustomItemType type = ItemList.fromArgument(parts[2]);
        if (type == null) {
            sender.sendMessage(ChatColor.RED + "Unknown custom item: " + parts[2]);
            return;
        }
        int amount = parseAmount(parts.length >= 4 ? parts[3] : null);
        List<Player> targets = resolveTargets(sender, parts[1]);
        if (targets.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No matching players for: " + parts[1]);
            return;
        }
        for (Player target : targets) {
            giveItem(target, type, amount);
            sender.sendMessage(ChatColor.GREEN + "Gave " + amount + "x " + type.getId()
                    + " to " + target.getName());
        }
    }

    private boolean hasGivePermission(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return true;
        }
        return sender.hasPermission(GIVE_PERMISSION) || sender.isOp();
    }

    private int parseAmount(String raw) {
        if (raw == null) {
            return 1;
        }
        try {
            int value = Integer.parseInt(raw);
            return Math.max(1, value);
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private List<Player> resolveTargets(CommandSender sender, String targetSelector) {
        List<Player> players = new ArrayList<>();
        try {
            for (Entity entity : Bukkit.selectEntities(sender, targetSelector)) {
                if (entity instanceof Player) {
                    players.add((Player) entity);
                }
            }
        } catch (IllegalArgumentException ex) {
            Player player = Bukkit.getPlayerExact(targetSelector);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    private void giveItem(Player player, CustomItemType type, int amount) {
        int remaining = amount;
        int maxStack = type.getBaseMaterial().getMaxStackSize();
        while (remaining > 0) {
            int stackAmount = Math.min(remaining, maxStack);
            ItemStack stack = createItem(type, stackAmount);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
            remaining -= stackAmount;
        }
    }
}
