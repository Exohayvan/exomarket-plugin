package com.starhavensmpcore.waypoint;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.items.CustomItemManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class WaypointManager implements Listener {

    private final StarhavenSMPCore plugin;
    private final WaypointDatabase database;
    private final CustomItemManager customItemManager;
    private final Map<LocationKey, WaypointEntry> waypoints = new HashMap<>();
    private final Map<UUID, PendingSelection> pendingSelections = new HashMap<>();
    private final Map<UUID, PendingName> pendingNames = new ConcurrentHashMap<>();
    private final Map<UUID, PendingAccess> pendingAccessMenus = new HashMap<>();
    private final Map<UUID, WaypointListView> waypointLists = new HashMap<>();
    private final int forceTaskId;
    private static final int WAYPOINT_OBSIDIAN_CMD = 69022;
    private static final int WAYPOINT_GLOWSTONE_CMD = 69023;
    private static final long FORCE_INTERVAL_TICKS = 1L;
    private static final String MENU_TITLE = ChatColor.DARK_AQUA + "Waystone Type";
    private static final String ACCESS_MENU_TITLE = ChatColor.DARK_GREEN + "Waypoint Access";
    private static final String LIST_MENU_TITLE_PREFIX = ChatColor.DARK_PURPLE + "Waystones";
    private static final long NAME_TIMEOUT_TICKS = 20L * 60L;
    private static final int LIST_PAGE_SIZE = 45;

    public WaypointManager(StarhavenSMPCore plugin, CustomItemManager customItemManager) {
        this.plugin = plugin;
        this.database = new WaypointDatabase(plugin);
        this.customItemManager = customItemManager;
        for (WaypointDatabase.WaypointRecord record : database.loadWaypoints()) {
            WaypointType type = WaypointType.fromId(record.type);
            if (type == null) {
                continue;
            }
            WaystoneCategory category = WaystoneCategory.fromId(record.category);
            UUID ownerId = null;
            if (record.owner != null && !record.owner.trim().isEmpty()) {
                try {
                    ownerId = UUID.fromString(record.owner);
                } catch (IllegalArgumentException ignored) {
                    ownerId = null;
                }
            }
            waypoints.put(
                    new LocationKey(record.worldId, record.x, record.y, record.z),
                    new WaypointEntry(type, category, ownerId, record.name)
            );
        }
        this.forceTaskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(
                plugin,
                this::forceWaypointStates,
                FORCE_INTERVAL_TICKS,
                FORCE_INTERVAL_TICKS
        );
    }

    @EventHandler(ignoreCancelled = true)
    public void onWaypointPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        WaypointType type = resolveWaypointType(item);
        if (type == null) {
            return;
        }
        Block block = event.getBlockPlaced();
        if (!canPlaceWaypoint(block)) {
            event.setCancelled(true);
            return;
        }
        if (!canPlaceWaypointSpacing(block)) {
            event.setCancelled(true);
            if (event.getPlayer() != null) {
                event.getPlayer().sendMessage(ChatColor.RED + "Waystones must be at least 4 blocks apart.");
            }
            return;
        }
        placeWaypointBlocks(block, type);
        LocationKey key = new LocationKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        WaystoneCategory defaultCategory = WaystoneCategory.OTHER;
        UUID ownerId = event.getPlayer() == null ? null : event.getPlayer().getUniqueId();
        waypoints.put(key, new WaypointEntry(type, defaultCategory, ownerId, null));
        database.saveWaypoint(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ(),
                type.id,
                defaultCategory.id,
                null,
                ownerId
        );
        if (event.getPlayer() != null) {
            Player player = event.getPlayer();
            plugin.getServer().getScheduler().runTask(plugin, () -> openTypeMenu(player, key, type));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onWaypointBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isWaypointBlock(block) && !isWaypointBlock(block.getRelative(BlockFace.DOWN)) && !isWaypointBlock(block.getRelative(BlockFace.UP))) {
            return;
        }
        Block bottom = block;
        Block below = block.getRelative(BlockFace.DOWN);
        if (isWaypointBlock(below)) {
            bottom = below;
        }
        Block top = bottom.getRelative(BlockFace.UP);
        if (isWaypointBlock(top)) {
            top.setType(Material.AIR, false);
        }
        if (bottom != block) {
            bottom.setType(Material.AIR, false);
        }
        LocationKey key = new LocationKey(bottom.getWorld().getUID(), bottom.getX(), bottom.getY(), bottom.getZ());
        WaypointEntry entry = waypoints.remove(key);
        database.deleteWaypoint(bottom.getWorld().getUID(), bottom.getX(), bottom.getY(), bottom.getZ());
        removePendingSelection(key);
        removePendingNameForKey(key);
        removePendingAccessForKey(key);
        event.setDropItems(false);
        if (event.getPlayer() != null && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            WaypointType type = entry == null ? guessWaypointType(bottom) : entry.type;
            dropWaypointItem(bottom, type);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCampfirePlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (isCampfire(placed.getType())) {
            removeHayIfUnlitCampfire(placed);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHayPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.HAY_BLOCK) {
            return;
        }
        Block above = event.getBlockPlaced().getRelative(BlockFace.UP);
        removeHayIfUnlitCampfire(above);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCampfireInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (isCampfire(clicked.getType())) {
            plugin.getServer().getScheduler().runTask(plugin, () -> removeHayIfUnlitCampfire(clicked));
        }
        LocationKey key = resolveWaypointKey(clicked);
        if (key == null) {
            return;
        }
        WaypointEntry entry = waypoints.get(key);
        if (entry == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        event.setCancelled(true);
        UUID playerId = player.getUniqueId();
        if (entry.ownerId != null && entry.ownerId.equals(playerId)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> openKnownWaypointsMenu(player, 0));
            return;
        }
        if (!isWaypointKnown(playerId, key, entry)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> openAccessMenu(player, key, entry));
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> openKnownWaypointsMenu(player, 0));
    }

    @EventHandler(ignoreCancelled = true)
    public void onCampfirePhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (isCampfire(block.getType())) {
            removeHayIfUnlitCampfire(block);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCampfireFlow(BlockFromToEvent event) {
        Block to = event.getToBlock();
        if (isCampfire(to.getType())) {
            removeHayIfUnlitCampfire(to);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onWaypointMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (isWaypointMenu(event.getView().getTitle())) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            PendingSelection pending = pendingSelections.get(player.getUniqueId());
            if (pending == null) {
                return;
            }
            WaystoneCategory category = pending.slotToCategory.get(event.getRawSlot());
            if (category == null) {
                return;
            }
            applyCategorySelection(player, pending, category);
            player.closeInventory();
            return;
        }
        if (isAccessMenu(event.getView().getTitle())) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            PendingAccess pending = pendingAccessMenus.get(player.getUniqueId());
            if (pending == null) {
                return;
            }
            AccessChoice choice = pending.slotToChoice.get(event.getRawSlot());
            if (choice == null) {
                return;
            }
            handleAccessChoice(player, pending, choice);
            player.closeInventory();
            return;
        }
        if (!isListMenu(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        WaypointListView view = waypointLists.get(player.getUniqueId());
        if (view == null) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 45) {
            openKnownWaypointsMenu(player, view.page - 1);
            return;
        }
        if (slot == 53) {
            openKnownWaypointsMenu(player, view.page + 1);
            return;
        }
        if (slot < 0 || slot >= LIST_PAGE_SIZE) {
            return;
        }
        int index = view.page * LIST_PAGE_SIZE + slot;
        if (index < 0 || index >= view.entries.size()) {
            return;
        }
        KnownWaypointEntry entry = view.entries.get(index);
        LocationKey key = entry.key;
        World world = plugin.getServer().getWorld(key.worldId);
        if (world == null) {
            player.sendMessage(ChatColor.RED + "That waypoint world is not loaded.");
            return;
        }
        org.bukkit.Location safe = findSafeTeleportLocation(world, key.x, key.y, key.z);
        if (safe == null) {
            player.sendMessage(ChatColor.RED + "No safe spot found near that waypoint.");
            return;
        }
        player.closeInventory();
        player.teleport(safe);
        player.sendMessage(ChatColor.GREEN + "Teleported to " + entry.displayName + ".");
    }

    @EventHandler
    public void onWaypointMenuClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        if (isWaypointMenu(event.getView().getTitle())) {
            Player player = (Player) event.getPlayer();
            PendingSelection pending = pendingSelections.get(player.getUniqueId());
            if (pending == null || pending.selected) {
                return;
            }
            applyCategorySelection(player, pending, WaystoneCategory.OTHER);
            return;
        }
        if (isAccessMenu(event.getView().getTitle())) {
            Player player = (Player) event.getPlayer();
            pendingAccessMenus.remove(player.getUniqueId());
            return;
        }
        if (isListMenu(event.getView().getTitle())) {
            Player player = (Player) event.getPlayer();
            waypointLists.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onWaypointMenuQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        PendingSelection pending = pendingSelections.remove(playerId);
        if (pending != null && !pending.selected) {
            applyCategorySelection(null, pending, WaystoneCategory.OTHER);
        }
        PendingName pendingName = pendingNames.get(playerId);
        if (pendingName != null && pendingName.reason == NamePromptReason.VISITOR_CUSTOM) {
            pendingNames.remove(playerId);
            if (pendingName.timeoutTask != null) {
                pendingName.timeoutTask.cancel();
            }
        }
        pendingAccessMenus.remove(playerId);
        waypointLists.remove(playerId);
    }

    @EventHandler
    public void onWaypointNameChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PendingName pending = pendingNames.get(player.getUniqueId());
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        String raw = event.getMessage();
        plugin.getServer().getScheduler().runTask(plugin, () -> handleNameInput(player, pending, raw));
    }

    private void removeHayIfUnlitCampfire(Block block) {
        if (block == null || !isCampfire(block.getType())) {
            return;
        }
        BlockData data = block.getBlockData();
        if (!(data instanceof Campfire)) {
            return;
        }
        Campfire campfire = (Campfire) data;
        if (campfire.isLit()) {
            return;
        }
        Block below = block.getRelative(BlockFace.DOWN);
        if (below.getType() != Material.HAY_BLOCK) {
            return;
        }
        below.breakNaturally();
    }

    private boolean isCampfire(Material material) {
        return material == Material.CAMPFIRE || material == Material.SOUL_CAMPFIRE;
    }

    private boolean isWaypointBlock(Block block) {
        if (block == null || !isCampfire(block.getType())) {
            return false;
        }
        BlockData data = block.getBlockData();
        if (!(data instanceof Campfire)) {
            return false;
        }
        Campfire campfire = (Campfire) data;
        return !campfire.isLit() && campfire.isSignalFire();
    }

    private WaypointType resolveWaypointType(ItemStack item) {
        if (item == null || item.getType() != Material.STONE) {
            return null;
        }
        Integer cmd = getCustomModelData(item);
        if (cmd == null) {
            return null;
        }
        if (cmd == WAYPOINT_OBSIDIAN_CMD) {
            return WaypointType.OBSIDIAN;
        }
        if (cmd == WAYPOINT_GLOWSTONE_CMD) {
            return WaypointType.IRON;
        }
        return null;
    }

    private Integer getCustomModelData(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        try {
            if (meta.hasCustomModelData()) {
                return meta.getCustomModelData();
            }
        } catch (NoSuchMethodError ignored) {
            // API without custom model data support.
        }
        try {
            Method method = meta.getClass().getMethod("getCustomModelDataComponent");
            Object component = method.invoke(meta);
            if (component == null) {
                return null;
            }
            Method floatsMethod = component.getClass().getMethod("getFloats");
            Object floatsObj = floatsMethod.invoke(component);
            if (!(floatsObj instanceof List)) {
                return null;
            }
            List<?> floats = (List<?>) floatsObj;
            if (floats.isEmpty()) {
                return null;
            }
            Object first = floats.get(0);
            if (first instanceof Number) {
                return ((Number) first).intValue();
            }
        } catch (ReflectiveOperationException ignored) {
            // Component API not available.
        }
        return null;
    }

    private boolean canPlaceWaypoint(Block block) {
        if (block == null) {
            return false;
        }
        Block above = block.getRelative(BlockFace.UP);
        return above.getType() == Material.AIR;
    }

    private boolean canPlaceWaypointSpacing(Block block) {
        if (block == null || waypoints.isEmpty()) {
            return true;
        }
        UUID worldId = block.getWorld().getUID();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        for (LocationKey key : waypoints.keySet()) {
            if (!key.worldId.equals(worldId)) {
                continue;
            }
            if (Math.abs(key.x - x) <= 3 && Math.abs(key.y - y) <= 3 && Math.abs(key.z - z) <= 3) {
                return false;
            }
        }
        return true;
    }

    private void placeWaypointBlocks(Block block, WaypointType type) {
        BlockFace bottomFacing = type == WaypointType.OBSIDIAN ? BlockFace.EAST : BlockFace.SOUTH;
        BlockFace topFacing = type == WaypointType.OBSIDIAN ? BlockFace.NORTH : BlockFace.WEST;
        placeCampfireBlock(block, bottomFacing);
        placeCampfireBlock(block.getRelative(BlockFace.UP), topFacing);
    }

    private void placeCampfireBlock(Block block, BlockFace facing) {
        String dataString = "minecraft:campfire[facing=" + facing.name().toLowerCase() + ",lit=false,signal_fire=true]";
        block.setType(Material.CAMPFIRE, false);
        BlockData data = Bukkit.createBlockData(dataString);
        block.setBlockData(data, false);
    }

    public void shutdown() {
        if (forceTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(forceTaskId);
        }
        database.shutdown();
    }

    private void forceWaypointStates() {
        if (waypoints.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<LocationKey, WaypointEntry>> iterator = waypoints.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<LocationKey, WaypointEntry> entry = iterator.next();
            LocationKey key = entry.getKey();
            World world = plugin.getServer().getWorld(key.worldId);
            if (world == null || !world.isChunkLoaded(key.x >> 4, key.z >> 4)) {
                continue;
            }
            Block bottom = world.getBlockAt(key.x, key.y, key.z);
            if (bottom.getType() != Material.CAMPFIRE) {
                iterator.remove();
                database.deleteWaypoint(key.worldId, key.x, key.y, key.z);
                debugWaystone("Removed waypoint entry at " + world.getName() + " " + key.x + "," + key.y + "," + key.z + " (missing campfire)");
                continue;
            }
            WaypointType type = entry.getValue().type;
            BlockFace bottomFacing = type == WaypointType.OBSIDIAN ? BlockFace.EAST : BlockFace.SOUTH;
            BlockFace topFacing = type == WaypointType.OBSIDIAN ? BlockFace.NORTH : BlockFace.WEST;
            boolean bottomUpdated = forceCampfireState(bottom, bottomFacing);
            if (bottomUpdated) {
                debugWaystone("Updated bottom waypoint campfire at " + formatLocation(bottom) + " -> " + bottomFacing.name().toLowerCase());
            }
            Block top = bottom.getRelative(BlockFace.UP);
            if (top.getType() == Material.CAMPFIRE) {
                boolean topUpdated = forceCampfireState(top, topFacing);
                if (topUpdated) {
                    debugWaystone("Updated top waypoint campfire at " + formatLocation(top) + " -> " + topFacing.name().toLowerCase());
                }
            }
        }
    }

    private boolean forceCampfireState(Block block, BlockFace facing) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Campfire)) {
            return false;
        }
        Campfire campfire = (Campfire) data;
        boolean changed = false;
        if (campfire.isLit()) {
            campfire.setLit(false);
            changed = true;
        }
        if (!campfire.isSignalFire()) {
            campfire.setSignalFire(true);
            changed = true;
        }
        if (campfire.getFacing() != facing) {
            campfire.setFacing(facing);
            changed = true;
        }
        if (changed) {
            block.setBlockData(campfire, false);
        }
        return changed;
    }

    private void openTypeMenu(Player player, LocationKey key, WaypointType type) {
        if (player == null) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(player, 9, MENU_TITLE);
        List<WaystoneCategory> categories = WaystoneCategory.forType(type);
        Map<Integer, WaystoneCategory> slotToCategory = new HashMap<>();
        for (int i = 0; i < categories.size(); i++) {
            WaystoneCategory category = categories.get(i);
            inventory.setItem(i, createCategoryItem(category));
            slotToCategory.put(i, category);
        }
        PendingSelection pending = new PendingSelection(key, type, slotToCategory);
        pendingSelections.put(player.getUniqueId(), pending);
        player.openInventory(inventory);
    }

    private ItemStack createCategoryItem(WaystoneCategory category) {
        ItemStack item = new ItemStack(category.material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + category.display);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void applyCategorySelection(Player player, PendingSelection pending, WaystoneCategory category) {
        if (pending == null || pending.key == null) {
            return;
        }
        WaypointEntry entry = waypoints.get(pending.key);
        if (entry != null) {
            entry.category = category;
        }
        database.updateWaypointCategory(pending.key.worldId, pending.key.x, pending.key.y, pending.key.z, category.id);
        pending.selected = true;
        if (player != null) {
            player.sendMessage(ChatColor.GREEN + "Waystone type set to " + category.display + ".");
            pendingSelections.remove(player.getUniqueId());
            if (entry != null && entry.ownerId != null && entry.ownerId.equals(player.getUniqueId())) {
                startNamePrompt(player, pending.key, NamePromptReason.OWNER_CREATE);
            }
        }
    }

    private void startNamePrompt(Player player, LocationKey key, NamePromptReason reason) {
        if (player == null || key == null) {
            return;
        }
        PendingName existing = pendingNames.remove(player.getUniqueId());
        if (existing != null && existing.timeoutTask != null) {
            existing.timeoutTask.cancel();
        }
        WaypointEntry entry = waypoints.get(key);
        if (entry == null) {
            return;
        }
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> handleNameTimeout(player.getUniqueId()),
                NAME_TIMEOUT_TICKS
        );
        PendingName pending = new PendingName(key, reason, player.getUniqueId(), entry.type, task);
        pendingNames.put(player.getUniqueId(), pending);
        player.sendMessage(ChatColor.AQUA + "Enter a name for this waystone in chat. (60 seconds)");
        player.sendMessage(ChatColor.GRAY + "Type \"cancel\" to abort.");
    }

    private void handleNameTimeout(UUID playerId) {
        PendingName pending = pendingNames.remove(playerId);
        if (pending == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (pending.reason == NamePromptReason.OWNER_CREATE) {
            removeWaypointAt(pending.key, true, player);
            if (player != null) {
                player.sendMessage(ChatColor.RED + "Waystone not named in time. It was returned.");
            }
        } else if (player != null) {
            player.sendMessage(ChatColor.GRAY + "Waypoint naming timed out.");
        }
    }

    private void handleNameInput(Player player, PendingName pending, String raw) {
        if (player == null || pending == null) {
            return;
        }
        PendingName current = pendingNames.get(player.getUniqueId());
        if (current == null || current != pending) {
            return;
        }
        String trimmed = raw == null ? "" : ChatColor.stripColor(raw).trim();
        if (trimmed.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Name cannot be empty. Please try again.");
            return;
        }
        if ("cancel".equalsIgnoreCase(trimmed)) {
            pendingNames.remove(player.getUniqueId());
            if (pending.timeoutTask != null) {
                pending.timeoutTask.cancel();
            }
            if (pending.reason == NamePromptReason.OWNER_CREATE) {
                removeWaypointAt(pending.key, true, player);
                player.sendMessage(ChatColor.RED + "Waystone placement cancelled. It was returned.");
            } else {
                player.sendMessage(ChatColor.GRAY + "Waypoint not registered.");
            }
            return;
        }
        if (trimmed.length() > 32) {
            player.sendMessage(ChatColor.RED + "Name is too long (max 32 characters).");
            return;
        }
        pendingNames.remove(player.getUniqueId());
        if (pending.timeoutTask != null) {
            pending.timeoutTask.cancel();
        }
        WaypointEntry entry = waypoints.get(pending.key);
        if (entry == null) {
            player.sendMessage(ChatColor.RED + "That waystone no longer exists.");
            return;
        }
        if (pending.reason == NamePromptReason.OWNER_CREATE) {
            entry.name = trimmed;
            database.updateWaypointName(pending.key.worldId, pending.key.x, pending.key.y, pending.key.z, trimmed);
            if (entry.ownerId != null) {
                database.upsertKnownWaypoint(entry.ownerId, pending.key.worldId, pending.key.x, pending.key.y, pending.key.z, trimmed);
            }
            player.sendMessage(ChatColor.GREEN + "Waystone named \"" + trimmed + "\" and registered.");
        } else {
            database.upsertKnownWaypoint(player.getUniqueId(), pending.key.worldId, pending.key.x, pending.key.y, pending.key.z, trimmed);
            player.sendMessage(ChatColor.GREEN + "Waypoint saved as \"" + trimmed + "\".");
        }
    }

    private void openAccessMenu(Player player, LocationKey key, WaypointEntry entry) {
        if (player == null || key == null || entry == null) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(player, 9, ACCESS_MENU_TITLE);
        Map<Integer, AccessChoice> slotToChoice = new HashMap<>();
        inventory.setItem(0, createAccessItem(Material.NAME_TAG, "Use Owner Name"));
        slotToChoice.put(0, AccessChoice.USE_OWNER_NAME);
        inventory.setItem(1, createAccessItem(Material.ANVIL, "Set Custom Name"));
        slotToChoice.put(1, AccessChoice.CUSTOM_NAME);
        inventory.setItem(8, createAccessItem(Material.BARRIER, "Cancel"));
        slotToChoice.put(8, AccessChoice.CANCEL);
        pendingAccessMenus.put(player.getUniqueId(), new PendingAccess(key, slotToChoice));
        player.openInventory(inventory);
    }

    private ItemStack createAccessItem(Material material, String label) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + label);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void handleAccessChoice(Player player, PendingAccess pending, AccessChoice choice) {
        if (player == null || pending == null) {
            return;
        }
        pendingAccessMenus.remove(player.getUniqueId());
        WaypointEntry entry = waypoints.get(pending.key);
        if (entry == null) {
            player.sendMessage(ChatColor.RED + "That waystone no longer exists.");
            return;
        }
        if (choice == AccessChoice.CANCEL) {
            player.sendMessage(ChatColor.GRAY + "Waypoint not registered.");
            return;
        }
        if (choice == AccessChoice.USE_OWNER_NAME) {
            String ownerName = entry.name;
            if (ownerName == null || ownerName.trim().isEmpty()) {
                player.sendMessage(ChatColor.RED + "Owner has not named this waystone yet.");
                return;
            }
            database.upsertKnownWaypoint(player.getUniqueId(), pending.key.worldId, pending.key.x, pending.key.y, pending.key.z, ownerName);
            player.sendMessage(ChatColor.GREEN + "Waypoint saved as \"" + ownerName + "\".");
            return;
        }
        startNamePrompt(player, pending.key, NamePromptReason.VISITOR_CUSTOM);
    }

    private boolean isWaypointKnown(UUID playerId, LocationKey key, WaypointEntry entry) {
        if (playerId == null || key == null || entry == null) {
            return false;
        }
        if (entry.ownerId != null && entry.ownerId.equals(playerId)) {
            return true;
        }
        if (entry.category == WaystoneCategory.SERVER) {
            return true;
        }
        return database.hasKnownWaypoint(playerId, key.worldId, key.x, key.y, key.z);
    }

    private void openKnownWaypointsMenu(Player player, int requestedPage) {
        if (player == null) {
            return;
        }
        List<KnownWaypointEntry> entries = buildKnownWaypoints(player.getUniqueId());
        int totalPages = Math.max(1, (entries.size() + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        WaypointListView view = new WaypointListView(entries, page, totalPages);
        waypointLists.put(player.getUniqueId(), view);
        Inventory inventory = Bukkit.createInventory(player, 54, LIST_MENU_TITLE_PREFIX + " (" + (page + 1) + "/" + totalPages + ")");
        int start = page * LIST_PAGE_SIZE;
        int end = Math.min(entries.size(), start + LIST_PAGE_SIZE);
        for (int i = start; i < end; i++) {
            KnownWaypointEntry entry = entries.get(i);
            inventory.setItem(i - start, createKnownWaypointItem(entry));
        }
        if (page > 0) {
            inventory.setItem(45, createAccessItem(Material.ARROW, "Previous Page"));
        }
        if (page < totalPages - 1) {
            inventory.setItem(53, createAccessItem(Material.ARROW, "Next Page"));
        }
        player.openInventory(inventory);
    }

    private List<KnownWaypointEntry> buildKnownWaypoints(UUID playerId) {
        List<KnownWaypointEntry> entries = new ArrayList<>();
        if (playerId == null) {
            return entries;
        }
        Set<LocationKey> seen = new HashSet<>();
        for (WaypointDatabase.KnownWaypointRecord record : database.loadKnownWaypoints(playerId)) {
            LocationKey key = new LocationKey(record.worldId, record.x, record.y, record.z);
            WaypointEntry entry = waypoints.get(key);
            if (entry == null) {
                database.deleteKnownWaypoint(playerId, record.worldId, record.x, record.y, record.z);
                continue;
            }
            entries.add(new KnownWaypointEntry(key, entry, record.name, entry.category == WaystoneCategory.SERVER));
            seen.add(key);
        }
        for (Map.Entry<LocationKey, WaypointEntry> entry : waypoints.entrySet()) {
            LocationKey key = entry.getKey();
            WaypointEntry value = entry.getValue();
            if (value == null || value.category != WaystoneCategory.SERVER) {
                continue;
            }
            if (seen.contains(key)) {
                continue;
            }
            entries.add(new KnownWaypointEntry(key, value, value.name, true));
        }
        entries.sort(Comparator
                .comparing((KnownWaypointEntry entry) -> entry.type == WaypointType.IRON ? 0 : 1)
                .thenComparing(entry -> entry.sortName, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private ItemStack createKnownWaypointItem(KnownWaypointEntry entry) {
        Material material = entry.type == WaypointType.IRON ? Material.IRON_BLOCK : Material.OBSIDIAN;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + entry.displayName);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Category: " + entry.categoryDisplay);
            if (entry.server) {
                lore.add(ChatColor.AQUA + "Server Waystone");
            }
            String worldName = "unknown";
            World world = plugin.getServer().getWorld(entry.key.worldId);
            if (world != null) {
                worldName = world.getName();
            }
            lore.add(ChatColor.DARK_GRAY + worldName + " " + entry.key.x + "," + entry.key.y + "," + entry.key.z);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private org.bukkit.Location findSafeTeleportLocation(World world, int baseX, int baseY, int baseZ) {
        if (world == null) {
            return null;
        }
        int[][] offsets = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };
        List<org.bukkit.Location> candidates = new ArrayList<>();
        for (int[] offset : offsets) {
            int x = baseX + offset[0];
            int z = baseZ + offset[1];
            if (baseY <= world.getMinHeight() + 1 || baseY >= world.getMaxHeight() - 2) {
                continue;
            }
            Block feet = world.getBlockAt(x, baseY, z);
            Block head = world.getBlockAt(x, baseY + 1, z);
            Block below = world.getBlockAt(x, baseY - 1, z);
            if (!feet.getType().isAir()) {
                continue;
            }
            if (!head.getType().isAir()) {
                continue;
            }
            if (!below.getType().isSolid()) {
                continue;
            }
            candidates.add(new org.bukkit.Location(world, x + 0.5, baseY, z + 0.5));
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private LocationKey resolveWaypointKey(Block block) {
        if (block == null) {
            return null;
        }
        Block candidate = block;
        if (!isWaypointBlock(candidate)) {
            Block below = block.getRelative(BlockFace.DOWN);
            if (isWaypointBlock(below)) {
                candidate = below;
            } else {
                Block above = block.getRelative(BlockFace.UP);
                if (isWaypointBlock(above)) {
                    candidate = above;
                } else {
                    return null;
                }
            }
        }
        Block bottom = candidate;
        Block below = candidate.getRelative(BlockFace.DOWN);
        if (isWaypointBlock(below)) {
            bottom = below;
        }
        return new LocationKey(bottom.getWorld().getUID(), bottom.getX(), bottom.getY(), bottom.getZ());
    }

    private void removeWaypointAt(LocationKey key, boolean returnItem, Player player) {
        if (key == null) {
            return;
        }
        World world = plugin.getServer().getWorld(key.worldId);
        if (world == null) {
            return;
        }
        Block bottom = world.getBlockAt(key.x, key.y, key.z);
        if (bottom.getType() == Material.CAMPFIRE) {
            bottom.setType(Material.AIR, false);
        }
        Block top = bottom.getRelative(BlockFace.UP);
        if (top.getType() == Material.CAMPFIRE) {
            top.setType(Material.AIR, false);
        }
        WaypointEntry entry = waypoints.remove(key);
        database.deleteWaypoint(key.worldId, key.x, key.y, key.z);
        removePendingSelection(key);
        removePendingNameForKey(key);
        removePendingAccessForKey(key);
        if (returnItem) {
            WaypointType type = entry == null ? guessWaypointType(bottom) : entry.type;
            returnWaypointItem(player, bottom, type);
        }
    }

    private void returnWaypointItem(Player player, Block block, WaypointType type) {
        if (block == null || type == null) {
            return;
        }
        ItemStack stack = customItemManager.createItem(type.id, 1);
        if (stack == null) {
            return;
        }
        if (player != null && player.isOnline()) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (leftover.isEmpty()) {
                return;
            }
        }
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), stack);
    }

    private boolean isWaypointMenu(String title) {
        return MENU_TITLE.equals(title);
    }

    private boolean isAccessMenu(String title) {
        return ACCESS_MENU_TITLE.equals(title);
    }

    private boolean isListMenu(String title) {
        return title != null && title.startsWith(LIST_MENU_TITLE_PREFIX);
    }

    private void removePendingSelection(LocationKey key) {
        if (pendingSelections.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, PendingSelection>> iterator = pendingSelections.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingSelection> entry = iterator.next();
            PendingSelection pending = entry.getValue();
            if (pending != null && pending.key.equals(key)) {
                iterator.remove();
            }
        }
    }

    private void removePendingNameForKey(LocationKey key) {
        if (pendingNames.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, PendingName> entry : pendingNames.entrySet()) {
            PendingName pending = entry.getValue();
            if (pending != null && pending.key.equals(key)) {
                if (pending.timeoutTask != null) {
                    pending.timeoutTask.cancel();
                }
                pendingNames.remove(entry.getKey());
            }
        }
    }

    private void removePendingAccessForKey(LocationKey key) {
        if (pendingAccessMenus.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, PendingAccess>> iterator = pendingAccessMenus.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingAccess> entry = iterator.next();
            PendingAccess pending = entry.getValue();
            if (pending != null && pending.key.equals(key)) {
                iterator.remove();
            }
        }
    }

    private void dropWaypointItem(Block block, WaypointType type) {
        if (block == null || type == null) {
            return;
        }
        ItemStack stack = customItemManager.createItem(type.id, 1);
        if (stack == null) {
            return;
        }
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), stack);
    }

    private WaypointType guessWaypointType(Block block) {
        if (block == null) {
            return WaypointType.OBSIDIAN;
        }
        BlockData data = block.getBlockData();
        if (!(data instanceof Campfire)) {
            return WaypointType.OBSIDIAN;
        }
        Campfire campfire = (Campfire) data;
        return campfire.getFacing() == BlockFace.SOUTH ? WaypointType.IRON : WaypointType.OBSIDIAN;
    }

    private enum WaypointType {
        OBSIDIAN("waystone_obsidian"),
        IRON("waystone_iron");

        private final String id;

        WaypointType(String id) {
            this.id = id;
        }

        private static WaypointType fromId(String id) {
            if (id == null) {
                return null;
            }
            for (WaypointType type : values()) {
                if (type.id.equalsIgnoreCase(id)) {
                    return type;
                }
            }
            return null;
        }
    }

    private enum WaystoneCategory {
        HOME("home", "Home", Material.RED_BED),
        RESOURCE("resource", "Resource", Material.IRON_PICKAXE),
        TRAVEL("travel", "Travel", Material.COMPASS),
        UTILITY("utility", "Utility", Material.ANVIL),
        DANGER("danger", "Danger", Material.TNT),
        COMMUNITY("community", "Community", Material.EMERALD),
        EVENT("event", "Event", Material.FIREWORK_ROCKET),
        SERVER("server", "Server", Material.NETHER_STAR),
        OTHER("other", "Other", Material.PAPER);

        private final String id;
        private final String display;
        private final Material material;

        WaystoneCategory(String id, String display, Material material) {
            this.id = id;
            this.display = display;
            this.material = material;
        }

        private static WaystoneCategory fromId(String id) {
            if (id == null || id.trim().isEmpty()) {
                return OTHER;
            }
            for (WaystoneCategory category : values()) {
                if (category.id.equalsIgnoreCase(id)) {
                    return category;
                }
            }
            return OTHER;
        }

        private static List<WaystoneCategory> forType(WaypointType type) {
            List<WaystoneCategory> categories = new java.util.ArrayList<>();
            categories.add(HOME);
            categories.add(RESOURCE);
            categories.add(TRAVEL);
            categories.add(UTILITY);
            categories.add(DANGER);
            if (type == WaypointType.IRON) {
                categories.add(COMMUNITY);
                categories.add(EVENT);
                categories.add(SERVER);
            }
            categories.add(OTHER);
            return categories;
        }
    }

    private static final class WaypointEntry {
        private final WaypointType type;
        private WaystoneCategory category;
        private final UUID ownerId;
        private String name;

        private WaypointEntry(WaypointType type, WaystoneCategory category, UUID ownerId, String name) {
            this.type = type;
            this.category = category == null ? WaystoneCategory.OTHER : category;
            this.ownerId = ownerId;
            this.name = name;
        }
    }

    private static final class PendingSelection {
        private final LocationKey key;
        private final WaypointType type;
        private final Map<Integer, WaystoneCategory> slotToCategory;
        private boolean selected;

        private PendingSelection(LocationKey key, WaypointType type, Map<Integer, WaystoneCategory> slotToCategory) {
            this.key = key;
            this.type = type;
            this.slotToCategory = slotToCategory;
        }
    }

    private enum NamePromptReason {
        OWNER_CREATE,
        VISITOR_CUSTOM
    }

    private static final class PendingName {
        private final LocationKey key;
        private final NamePromptReason reason;
        private final UUID playerId;
        private final WaypointType type;
        private final BukkitTask timeoutTask;

        private PendingName(LocationKey key, NamePromptReason reason, UUID playerId, WaypointType type, BukkitTask timeoutTask) {
            this.key = key;
            this.reason = reason;
            this.playerId = playerId;
            this.type = type;
            this.timeoutTask = timeoutTask;
        }
    }

    private enum AccessChoice {
        USE_OWNER_NAME,
        CUSTOM_NAME,
        CANCEL
    }

    private static final class PendingAccess {
        private final LocationKey key;
        private final Map<Integer, AccessChoice> slotToChoice;

        private PendingAccess(LocationKey key, Map<Integer, AccessChoice> slotToChoice) {
            this.key = key;
            this.slotToChoice = slotToChoice;
        }
    }

    private static final class KnownWaypointEntry {
        private final LocationKey key;
        private final WaypointType type;
        private final String displayName;
        private final String sortName;
        private final String categoryDisplay;
        private final String typeDisplay;
        private final boolean server;

        private KnownWaypointEntry(LocationKey key, WaypointEntry entry, String name, boolean server) {
            this.key = key;
            this.type = entry.type;
            String resolvedName = name == null || name.trim().isEmpty() ? "Unnamed" : name.trim();
            this.displayName = resolvedName;
            this.sortName = resolvedName;
            this.categoryDisplay = entry.category.display;
            this.typeDisplay = entry.type == WaypointType.IRON ? "Iron" : "Obsidian";
            this.server = server;
        }
    }

    private static final class WaypointListView {
        private final List<KnownWaypointEntry> entries;
        private final int page;
        private final int totalPages;

        private WaypointListView(List<KnownWaypointEntry> entries, int page, int totalPages) {
            this.entries = entries;
            this.page = page;
            this.totalPages = totalPages;
        }
    }

    private void debugWaystone(String message) {
        if (!plugin.isDebugWaystone()) {
            return;
        }
        plugin.getLogger().info("[Waystone] " + message);
    }

    private String formatLocation(Block block) {
        if (block == null || block.getWorld() == null) {
            return "unknown";
        }
        return block.getWorld().getName() + " " + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private static final class LocationKey {
        private final UUID worldId;
        private final int x;
        private final int y;
        private final int z;

        private LocationKey(UUID worldId, int x, int y, int z) {
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof LocationKey)) {
                return false;
            }
            LocationKey other = (LocationKey) obj;
            return x == other.x && y == other.y && z == other.z && worldId.equals(other.worldId);
        }

        @Override
        public int hashCode() {
            int result = worldId.hashCode();
            result = 31 * result + x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }
}
