package com.starhavensmpcore.oregeneration;

import com.starhavensmpcore.core.StarhavenSMPCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class OreGenerationManager implements Listener {
    private static final String DB_NAME = "ore_generation.db";
    private static final String VOIDSTONE_TABLE = "voidstone_ore_chunks";
    private static final double SURFACE_CHANCE = 0.05;

    private final StarhavenSMPCore plugin;
    private final Random random = new Random();
    private Connection connection;

    public OreGenerationManager(StarhavenSMPCore plugin) {
        this.plugin = plugin;
        initDatabase();
    }

    public void shutdown() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
            connection = null;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();
        if (world.getEnvironment() != World.Environment.THE_END) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean shouldGenerate = markChunkGenerated(VOIDSTONE_TABLE, world.getUID(), chunk.getX(), chunk.getZ());
            if (!shouldGenerate) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> generateVoidstoneOre(chunk));
        });
    }

    private void initDatabase() {
        File dataDir = plugin.getDataFolder();
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create plugin data folder for ore generation.");
            return;
        }
        File dbFile = new File(dataDir, DB_NAME);
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS " + VOIDSTONE_TABLE + " (" +
                                "world TEXT NOT NULL," +
                                "chunk_x INTEGER NOT NULL," +
                                "chunk_z INTEGER NOT NULL," +
                                "generated_at INTEGER NOT NULL," +
                                "PRIMARY KEY (world, chunk_x, chunk_z)" +
                                ")"
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to initialize ore generation database: " + e.getMessage());
        }
    }

    private synchronized boolean markChunkGenerated(String table, UUID worldId, int chunkX, int chunkZ) {
        if (connection == null) {
            return false;
        }
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT 1 FROM " + table + " WHERE world = ? AND chunk_x = ? AND chunk_z = ?")) {
            select.setString(1, worldId.toString());
            select.setInt(2, chunkX);
            select.setInt(3, chunkZ);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    return false;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to query ore generation database: " + e.getMessage());
            return false;
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + table + " (world, chunk_x, chunk_z, generated_at) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, worldId.toString());
            insert.setInt(2, chunkX);
            insert.setInt(3, chunkZ);
            insert.setLong(4, System.currentTimeMillis());
            insert.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to write ore generation database: " + e.getMessage());
            return false;
        }
    }

    private void generateVoidstoneOre(Chunk chunk) {
        if (!chunk.isLoaded()) {
            return;
        }
        int veinSize = 1 + random.nextInt(2);
        boolean surfacePlacement = random.nextDouble() < SURFACE_CHANCE;

        if (surfacePlacement) {
            Block surface = findSurfaceEndstone(chunk);
            if (surface != null) {
                placeVein(surface, veinSize);
                return;
            }
        }

        Block buried = findBuriedEndstone(chunk, 24);
        if (buried != null) {
            placeVein(buried, veinSize);
        }
    }

    private Block findSurfaceEndstone(Chunk chunk) {
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        for (int i = 0; i < 12; i++) {
            int x = baseX + random.nextInt(16);
            int z = baseZ + random.nextInt(16);
            int y = world.getHighestBlockYAt(x, z) - 1;
            if (y <= world.getMinHeight()) {
                continue;
            }
            Block block = world.getBlockAt(x, y, z);
            if (block.getType() == Material.END_STONE && block.getRelative(BlockFace.UP).getType().isAir()) {
                return block;
            }
        }
        return null;
    }

    private Block findBuriedEndstone(Chunk chunk, int attempts) {
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        for (int i = 0; i < attempts; i++) {
            int x = baseX + random.nextInt(16);
            int y = minY + random.nextInt(Math.max(1, maxY - minY));
            int z = baseZ + random.nextInt(16);
            Block block = world.getBlockAt(x, y, z);
            if (block.getType() == Material.END_STONE) {
                return block;
            }
        }
        return null;
    }

    private void placeVein(Block start, int veinSize) {
        if (start.getType() != Material.END_STONE) {
            return;
        }
        start.setType(Material.NOTE_BLOCK, false);
        applyVoidstoneState(start);
        if (veinSize <= 1) {
            return;
        }
        List<Block> candidates = new ArrayList<>();
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
            Block adjacent = start.getRelative(face);
            if (adjacent.getType() == Material.END_STONE) {
                candidates.add(adjacent);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        Block second = candidates.get(random.nextInt(candidates.size()));
        second.setType(Material.NOTE_BLOCK, false);
        applyVoidstoneState(second);
    }

    private void applyVoidstoneState(Block block) {
        try {
            block.setBlockData(Bukkit.createBlockData("minecraft:note_block[instrument=flute,note=12,powered=true]"), false);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid voidstone ore note block state: " + ex.getMessage());
        }
    }
}
