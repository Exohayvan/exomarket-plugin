package com.starhavensmpcore.waypoint;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class WaypointManager implements Listener {

    private final JavaPlugin plugin;

    public WaypointManager(JavaPlugin plugin) {
        this.plugin = plugin;
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
        Block clicked = event.getClickedBlock();
        if (!isCampfire(clicked.getType())) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> removeHayIfUnlitCampfire(clicked));
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
}
