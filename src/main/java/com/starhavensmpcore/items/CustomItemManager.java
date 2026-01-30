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

    public ItemStack createItem(BlockDefinition definition, int amount) {
        ItemStack item = new ItemStack(definition.getBaseMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + definition.getDisplayName());
            try {
                meta.setCustomModelData(definition.getCustomModelData());
            } catch (NoSuchMethodError ignored) {
                // Older API versions won't have custom model data support.
            }
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, definition.getId());
            item.setItemMeta(meta);
        }
        item.setAmount(Math.max(1, amount));
        return item;
    }

    public BlockDefinition getCustomItemDefinition(ItemStack item) {
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
        BlockDefinition definition = getCustomItemDefinition(item);
        if (definition == null) {
            return;
        }
        String noteState = definition.getNoteBlockState();
        if (noteState == null || noteState.isEmpty()) {
            return;
        }
        Block block = event.getBlockPlaced();
        block.setType(Material.NOTE_BLOCK, false);
        try {
            BlockData data = Bukkit.createBlockData(noteState);
            block.setBlockData(data, false);
            if (data instanceof org.bukkit.block.data.type.NoteBlock) {
                customBlockRegistry.mark(block, definition, (org.bukkit.block.data.type.NoteBlock) data);
            }
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid note block state for " + definition.getId() + ": " + noteState);
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
        if (!handleCustomBlockBreak(event, blockData.getDefinition())) {
            return;
        }
        customBlockRegistry.unmark(block);
    }

    // Crafting recipes for custom items are currently disabled.


    private boolean handleCustomBlockBreak(org.bukkit.event.block.BlockBreakEvent event, BlockDefinition definition) {
        if (definition == null) {
            return true;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return false;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        ToolRequirement toolRequirement = definition.getToolRequirement();
        boolean validTool = toolRequirement == null || toolRequirement.isSatisfied(tool);
        if (!validTool) {
            String failureMessage = toolRequirement == null ? null : toolRequirement.getFailureMessage();
            if (failureMessage != null && !failureMessage.isEmpty()) {
                player.sendMessage(ChatColor.RED + failureMessage);
            }
        }

        event.setDropItems(false);
        event.setExpToDrop(0);

        if (!validTool) {
            return true;
        }

        boolean silkTouch = tool != null
                && tool.hasItemMeta()
                && tool.getItemMeta().hasEnchant(Enchantment.SILK_TOUCH);
        DropTable drops = definition.getDropsForTool(silkTouch);
        if (drops != null && drops.getDropItemId() != null) {
            int fortune = tool == null ? 0 : tool.getEnchantmentLevel(Enchantment.FORTUNE);
            int amount = drops.rollAmount(ThreadLocalRandom.current(), silkTouch ? 0 : fortune);
            if (amount > 0) {
                BlockDefinition dropDefinition = ItemList.getBlockDefinition(drops.getDropItemId());
                if (dropDefinition != null) {
                    dropCustomItemAt(event.getBlock().getLocation(), dropDefinition, amount);
                }
            }
        }
        if (!silkTouch && definition.getXpMax() > 0) {
            int xpMin = definition.getXpMin();
            int xpMax = definition.getXpMax();
            int xp = xpMin == xpMax ? xpMin : ThreadLocalRandom.current().nextInt(xpMin, xpMax + 1);
            event.setExpToDrop(xp);
        }
        return true;
    }

    private void dropCustomItemAt(Location location, BlockDefinition type, int amount) {
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
        BlockDefinition type = ItemList.fromArgument(parts[2]);
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
        BlockDefinition type = ItemList.fromArgument(parts[2]);
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
        BlockDefinition type = ItemList.fromArgument(parts[2]);
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

    private void giveItem(Player player, BlockDefinition type, int amount) {
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
