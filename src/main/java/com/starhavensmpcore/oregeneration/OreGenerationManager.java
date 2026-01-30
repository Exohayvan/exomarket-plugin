package com.starhavensmpcore.oregeneration;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.items.BlockDefinition;
import com.starhavensmpcore.items.GenerationRules;
import com.starhavensmpcore.items.ItemList;
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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class OreGenerationManager implements Listener {
    private static final String DB_NAME = "ore_generation.db";
    private static final String ORE_TABLE = "ore_generation_chunks";
    private static final String LEGACY_VOIDSTONE_TABLE = "voidstone_ore_chunks";
    private static final BlockFace[] NEIGHBOR_FACES = new BlockFace[]{
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST,
            BlockFace.UP,
            BlockFace.DOWN
    };

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
        List<BlockDefinition> generationBlocks = ItemList.generationBlocks();
        if (generationBlocks.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (BlockDefinition definition : generationBlocks) {
                GenerationRules rules = definition.getGenerationRules();
                if (rules == null || !rules.isWorldAllowed(world)) {
                    continue;
                }
                boolean shouldGenerate = markChunkGenerated(definition.getId(), world.getUID(), chunk.getX(), chunk.getZ());
                if (!shouldGenerate) {
                    continue;
                }
                Bukkit.getScheduler().runTask(plugin, () -> generateOre(chunk, definition));
            }
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
                        "CREATE TABLE IF NOT EXISTS " + ORE_TABLE + " (" +
                                "world TEXT NOT NULL," +
                                "chunk_x INTEGER NOT NULL," +
                                "chunk_z INTEGER NOT NULL," +
                                "ore_id TEXT NOT NULL," +
                                "generated_at INTEGER NOT NULL," +
                                "PRIMARY KEY (world, chunk_x, chunk_z, ore_id)" +
                                ")"
                );
                statement.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS " + LEGACY_VOIDSTONE_TABLE + " (" +
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

    private synchronized boolean markChunkGenerated(String oreId, UUID worldId, int chunkX, int chunkZ) {
        if (connection == null || oreId == null || oreId.isEmpty()) {
            return false;
        }
        if ("voidstone_ore".equalsIgnoreCase(oreId) && isLegacyGenerated(worldId, chunkX, chunkZ)) {
            insertGenerated(oreId, worldId, chunkX, chunkZ);
            return false;
        }
        if (isGenerated(oreId, worldId, chunkX, chunkZ)) {
            return false;
        }
        return insertGenerated(oreId, worldId, chunkX, chunkZ);
    }

    private boolean isGenerated(String oreId, UUID worldId, int chunkX, int chunkZ) {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT 1 FROM " + ORE_TABLE + " WHERE world = ? AND chunk_x = ? AND chunk_z = ? AND ore_id = ?")) {
            select.setString(1, worldId.toString());
            select.setInt(2, chunkX);
            select.setInt(3, chunkZ);
            select.setString(4, oreId);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to query ore generation database: " + e.getMessage());
            return false;
        }
    }

    private boolean insertGenerated(String oreId, UUID worldId, int chunkX, int chunkZ) {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT OR IGNORE INTO " + ORE_TABLE + " (world, chunk_x, chunk_z, ore_id, generated_at) VALUES (?, ?, ?, ?, ?)")) {
            insert.setString(1, worldId.toString());
            insert.setInt(2, chunkX);
            insert.setInt(3, chunkZ);
            insert.setString(4, oreId);
            insert.setLong(5, System.currentTimeMillis());
            insert.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to write ore generation database: " + e.getMessage());
            return false;
        }
    }

    private boolean isLegacyGenerated(UUID worldId, int chunkX, int chunkZ) {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT 1 FROM " + LEGACY_VOIDSTONE_TABLE + " WHERE world = ? AND chunk_x = ? AND chunk_z = ?")) {
            select.setString(1, worldId.toString());
            select.setInt(2, chunkX);
            select.setInt(3, chunkZ);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private void generateOre(Chunk chunk, BlockDefinition definition) {
        if (!chunk.isLoaded()) {
            return;
        }
        GenerationRules rules = definition.getGenerationRules();
        if (rules == null) {
            return;
        }
        if (random.nextDouble() > rules.getChunkChance()) {
            return;
        }
        boolean surfacePlacement = random.nextDouble() < rules.getSurfaceChance();

        if (surfacePlacement) {
            Block surface = findSurfaceBlock(chunk, rules);
            if (surface != null) {
                placeVein(surface, definition, rules);
                return;
            }
        }

        Block buried = findBuriedBlock(chunk, rules);
        if (buried != null) {
            placeVein(buried, definition, rules);
        }
    }

    private Block findSurfaceBlock(Chunk chunk, GenerationRules rules) {
        int attempts = rules.getSurfaceAttempts();
        if (attempts <= 0) {
            return null;
        }
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        for (int i = 0; i < attempts; i++) {
            int x = baseX + random.nextInt(16);
            int z = baseZ + random.nextInt(16);
            int y = world.getHighestBlockYAt(x, z) - 1;
            if (y <= world.getMinHeight()) {
                continue;
            }
            if (!rules.isInYRange(y)) {
                continue;
            }
            Block block = world.getBlockAt(x, y, z);
            if (rules.getReplaceableBlocks().contains(block.getType())
                    && block.getRelative(BlockFace.UP).getType().isAir()) {
                return block;
            }
        }
        return null;
    }

    private Block findBuriedBlock(Chunk chunk, GenerationRules rules) {
        int attempts = rules.getBuriedAttempts();
        if (attempts <= 0) {
            return null;
        }
        World world = chunk.getWorld();
        int minY = rules.getMinY() == -1 ? world.getMinHeight() : Math.max(world.getMinHeight(), rules.getMinY());
        int maxY = rules.getMaxY() == -1 ? world.getMaxHeight() - 1 : Math.min(world.getMaxHeight() - 1, rules.getMaxY());
        if (maxY < minY) {
            return null;
        }
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        for (int i = 0; i < attempts; i++) {
            int x = baseX + random.nextInt(16);
            int y = minY + random.nextInt(Math.max(1, maxY - minY + 1));
            int z = baseZ + random.nextInt(16);
            Block block = world.getBlockAt(x, y, z);
            if (rules.getReplaceableBlocks().contains(block.getType())) {
                return block;
            }
        }
        return null;
    }

    private void placeVein(Block start, BlockDefinition definition, GenerationRules rules) {
        if (!rules.getReplaceableBlocks().contains(start.getType())) {
            return;
        }
        placeBlock(start, definition);
        int maxVeinSize = rules.getMaxVeinSize();
        if (maxVeinSize <= 1) {
            return;
        }
        List<Block> candidates = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        addCandidates(start, rules, candidates, seen);
        int placed = 1;
        while (placed < maxVeinSize && !candidates.isEmpty() && random.nextDouble() < rules.getExtraVeinChance()) {
            Block next = candidates.remove(random.nextInt(candidates.size()));
            if (!rules.getReplaceableBlocks().contains(next.getType())) {
                continue;
            }
            if (!rules.isInYRange(next.getY())) {
                continue;
            }
            placeBlock(next, definition);
            placed++;
            addCandidates(next, rules, candidates, seen);
        }
    }

    private void addCandidates(Block origin, GenerationRules rules, List<Block> candidates, Set<BlockPos> seen) {
        for (BlockFace face : NEIGHBOR_FACES) {
            Block adjacent = origin.getRelative(face);
            if (!rules.getReplaceableBlocks().contains(adjacent.getType())) {
                continue;
            }
            if (!rules.isInYRange(adjacent.getY())) {
                continue;
            }
            BlockPos pos = BlockPos.from(adjacent);
            if (seen.add(pos)) {
                candidates.add(adjacent);
            }
        }
    }

    private void placeBlock(Block block, BlockDefinition definition) {
        block.setType(Material.NOTE_BLOCK, false);
        applyCustomState(block, definition);
    }

    private void applyCustomState(Block block, BlockDefinition definition) {
        String noteBlockState = definition.getNoteBlockState();
        if (noteBlockState == null || noteBlockState.isEmpty()) {
            plugin.getLogger().warning("Missing note block state for " + definition.getId());
            return;
        }
        try {
            block.setBlockData(Bukkit.createBlockData(noteBlockState), false);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid note block state for " + definition.getId() + ": " + ex.getMessage());
        }
    }

    private static final class BlockPos {
        private final String worldId;
        private final int x;
        private final int y;
        private final int z;

        private BlockPos(String worldId, int x, int y, int z) {
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private static BlockPos from(Block block) {
            return new BlockPos(block.getWorld().getUID().toString(), block.getX(), block.getY(), block.getZ());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            BlockPos blockPos = (BlockPos) o;
            return x == blockPos.x && y == blockPos.y && z == blockPos.z
                    && worldId.equals(blockPos.worldId);
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
