package com.starhavensmpcore.oregeneration;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.items.BlockDefinition;
import com.starhavensmpcore.items.CustomBlockRegistry;
import com.starhavensmpcore.items.GenerationRules;
import com.starhavensmpcore.items.ItemList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.Chunk;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final CustomBlockRegistry customBlockRegistry;
    private final Random random = new Random();
    private Connection connection;
    private final AtomicBoolean repairRunning = new AtomicBoolean(false);
    private static final int REPAIR_CHUNKS_PER_TICK = 2;

    public OreGenerationManager(StarhavenSMPCore plugin, CustomBlockRegistry customBlockRegistry) {
        this.plugin = plugin;
        this.customBlockRegistry = customBlockRegistry;
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

    public void repairOreGeneration(CommandSender sender, BlockDefinition definition) {
        if (sender == null || definition == null) {
            return;
        }
        GenerationRules rules = definition.getGenerationRules();
        if (rules == null) {
            sender.sendMessage("That block does not have generation rules.");
            return;
        }
        String noteState = definition.getNoteBlockState();
        if (noteState == null || noteState.isEmpty()) {
            sender.sendMessage("That block does not have a note block state.");
            return;
        }
        if (!repairRunning.compareAndSet(false, true)) {
            sender.sendMessage("Ore repair is already running.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<ChunkEntry> entries = loadGeneratedChunks(definition.getId());
            Bukkit.getScheduler().runTask(plugin, () ->
                    runRepairScan(sender, definition, rules, entries));
        });
    }

    private void runRepairScan(CommandSender sender,
                               BlockDefinition definition,
                               GenerationRules rules,
                               List<ChunkEntry> entries) {
        if (sender == null) {
            repairRunning.set(false);
            return;
        }
        if (entries == null || entries.isEmpty()) {
            sender.sendMessage("Ore generation fix complete: no generated chunks found for " + definition.getId() + ".");
            repairRunning.set(false);
            return;
        }

        BlockData targetData;
        try {
            targetData = Bukkit.createBlockData(definition.getNoteBlockState());
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("Invalid note block state for " + definition.getId() + ".");
            repairRunning.set(false);
            return;
        }

        Map<String, BlockDefinition> noteStateMap = buildNoteStateMap();
        List<ChunkEntry> chunksToRemove = new ArrayList<>();
        RepairStats stats = new RepairStats();

        debug("Ore repair started for " + definition.getId() + ". Entries: " + entries.size());
        sender.sendMessage("Ore repair started for " + definition.getId() + ". Entries: " + entries.size() + ".");

        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                int processed = 0;
                while (processed < REPAIR_CHUNKS_PER_TICK && index < entries.size()) {
                    ChunkEntry entry = entries.get(index++);
                    processed++;

                    UUID worldId;
                    try {
                        worldId = UUID.fromString(entry.worldId);
                    } catch (IllegalArgumentException ex) {
                        stats.missingWorld++;
                        continue;
                    }
                    World world = Bukkit.getWorld(worldId);
                    if (world == null) {
                        stats.missingWorld++;
                        continue;
                    }

                    boolean wasLoaded = world.isChunkLoaded(entry.chunkX, entry.chunkZ);
                    if (!wasLoaded) {
                        stats.forcedLoaded++;
                    }
                    Chunk chunk = world.getChunkAt(entry.chunkX, entry.chunkZ);
                    stats.checked++;
                    debug("Repair scanning chunk " + world.getName() + " " + entry.chunkX + "," + entry.chunkZ);

                    RepairScanResult scan = scanChunkForRepair(chunk, targetData, rules, noteStateMap);
                    if (scan.registeredBlocks > 0) {
                        stats.registeredBlocks += scan.registeredBlocks;
                        debug("Re-registered " + scan.registeredBlocks + " custom blocks in chunk " +
                                entry.chunkX + "," + entry.chunkZ);
                    } else {
                        debug("No custom blocks to re-register in chunk " + entry.chunkX + "," + entry.chunkZ);
                    }

                    if (scan.targetMatches > 0) {
                        debug("Found " + scan.targetMatches + " target ore blocks in chunk " +
                                entry.chunkX + "," + entry.chunkZ);
                    } else {
                        stats.removedChunks++;
                        chunksToRemove.add(entry);
                        debug("No target ore blocks found; cleared generated chunk " + entry.chunkX + "," + entry.chunkZ);
                    }

                    if (!wasLoaded) {
                        world.unloadChunkRequest(entry.chunkX, entry.chunkZ);
                    }
                }

                if (index >= entries.size()) {
                    if (!chunksToRemove.isEmpty()) {
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            for (ChunkEntry entry : chunksToRemove) {
                                removeGenerated(definition.getId(), entry.worldId, entry.chunkX, entry.chunkZ);
                            }
                        });
                    }
                    sender.sendMessage("Ore generation fix complete.");
                    sender.sendMessage("Removed generated chunks: " + stats.removedChunks + ".");
                    sender.sendMessage("Re-registered ore blocks: " + stats.registeredBlocks + ".");
                    sender.sendMessage("Removed note blocks: " + stats.removedNoteBlocks + ".");
                    sender.sendMessage("Checked chunks: " + stats.checked + " | Forced loads: " + stats.forcedLoaded +
                            " | Missing worlds: " + stats.missingWorld + ".");
                    debug("Ore repair finished. Removed chunks=" + stats.removedChunks +
                            ", re-registered=" + stats.registeredBlocks +
                            ", removed note blocks=" + stats.removedNoteBlocks);
                    repairRunning.set(false);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private RepairScanResult scanChunkForRepair(Chunk chunk,
                                                BlockData targetData,
                                                GenerationRules rules,
                                                Map<String, BlockDefinition> noteStateMap) {
        if (chunk == null || targetData == null) {
            return new RepairScanResult();
        }
        World world = chunk.getWorld();
        int minY = rules == null ? world.getMinHeight() : rules.getMinY();
        int maxY = rules == null ? world.getMaxHeight() - 1 : rules.getMaxY();
        int worldMin = world.getMinHeight();
        int worldMax = world.getMaxHeight() - 1;
        if (minY == -1) {
            minY = worldMin;
        }
        if (maxY == -1) {
            maxY = worldMax;
        }
        minY = Math.max(worldMin, minY);
        maxY = Math.min(worldMax, maxY);
        if (minY > maxY) {
            return new RepairScanResult();
        }

        RepairScanResult result = new RepairScanResult();
        for (int y = worldMin; y <= worldMax; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() != Material.NOTE_BLOCK) {
                        continue;
                    }
                    BlockData data = block.getBlockData();
                    if (data == null) {
                        continue;
                    }
                    if (y >= minY && y <= maxY && data.matches(targetData)) {
                        result.targetMatches++;
                    }

                    if (noteStateMap != null) {
                        BlockDefinition definition = noteStateMap.get(data.getAsString());
                        if (definition != null && data instanceof org.bukkit.block.data.type.NoteBlock) {
                            CustomBlockRegistry.CustomBlockData existing = customBlockRegistry.getBlockData(block);
                            if (existing == null || existing.getDefinition() != definition) {
                                customBlockRegistry.mark(block, definition, (org.bukkit.block.data.type.NoteBlock) data);
                                result.registeredBlocks++;
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private Map<String, BlockDefinition> buildNoteStateMap() {
        Map<String, BlockDefinition> map = new HashMap<>();
        for (BlockDefinition definition : ItemList.customBlocks()) {
            String state = definition.getNoteBlockState();
            if (state == null || state.isEmpty()) {
                continue;
            }
            try {
                BlockData data = Bukkit.createBlockData(state);
                map.put(data.getAsString(), definition);
            } catch (IllegalArgumentException ex) {
                debug("Invalid note block state for " + definition.getId() + ": " + ex.getMessage());
            }
        }
        return map;
    }

    private List<ChunkEntry> loadGeneratedChunks(String oreId) {
        List<ChunkEntry> entries = new ArrayList<>();
        if (connection == null || oreId == null || oreId.isEmpty()) {
            return entries;
        }
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT world, chunk_x, chunk_z FROM " + ORE_TABLE + " WHERE ore_id = ?")) {
            select.setString(1, oreId);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    entries.add(new ChunkEntry(
                            rs.getString("world"),
                            rs.getInt("chunk_x"),
                            rs.getInt("chunk_z")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to query ore generation database: " + e.getMessage());
        }
        return entries;
    }

    private synchronized void removeGenerated(String oreId, String worldId, int chunkX, int chunkZ) {
        if (connection == null || oreId == null || oreId.isEmpty() || worldId == null) {
            return;
        }
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + ORE_TABLE + " WHERE world = ? AND chunk_x = ? AND chunk_z = ? AND ore_id = ?")) {
            delete.setString(1, worldId);
            delete.setInt(2, chunkX);
            delete.setInt(3, chunkZ);
            delete.setString(4, oreId);
            delete.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to remove ore generation entry: " + e.getMessage());
        }
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
        int attempts = resolveChunkAttempts(rules.getChunkChance());
        if (attempts <= 0) {
            debug("Skip " + definition.getId() + " in chunk " + chunk.getX() + "," + chunk.getZ() + " (chance)");
            return;
        }
        for (int attempt = 0; attempt < attempts; attempt++) {
            boolean surfacePlacement = random.nextDouble() < rules.getSurfaceChance();

            if (surfacePlacement) {
                Block surface = findSurfaceBlock(chunk, rules);
                if (surface != null) {
                    int placed = placeVein(surface, definition, rules);
                    debug("Placed " + placed + " " + definition.getId() + " at surface " + locationString(surface));
                    continue;
                }
            }

            Block buried = findBuriedBlock(chunk, rules);
            if (buried != null) {
                int placed = placeVein(buried, definition, rules);
                debug("Placed " + placed + " " + definition.getId() + " buried at " + locationString(buried));
            } else {
                debug("No valid spawn for " + definition.getId() + " in chunk " + chunk.getX() + "," + chunk.getZ());
            }
        }
    }

    private int resolveChunkAttempts(double chance) {
        if (chance <= 0) {
            return 0;
        }
        int guaranteed = (int) Math.floor(chance);
        double remainder = chance - guaranteed;
        int attempts = guaranteed;
        if (remainder > 0 && random.nextDouble() < remainder) {
            attempts++;
        }
        return attempts;
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
                    && isExposedToAirOrLiquid(block)) {
                return block;
            }
        }

        int minY = rules.getMinY() == -1 ? world.getMinHeight() : Math.max(world.getMinHeight(), rules.getMinY());
        int maxY = rules.getMaxY() == -1 ? world.getMaxHeight() - 1 : Math.min(world.getMaxHeight() - 1, rules.getMaxY());
        if (maxY < minY) {
            return null;
        }
        for (int i = 0; i < attempts; i++) {
            int x = baseX + random.nextInt(16);
            int y = minY + random.nextInt(Math.max(1, maxY - minY + 1));
            int z = baseZ + random.nextInt(16);
            Block block = world.getBlockAt(x, y, z);
            if (rules.getReplaceableBlocks().contains(block.getType())
                    && isExposedToAirOrLiquid(block)) {
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

    private int placeVein(Block start, BlockDefinition definition, GenerationRules rules) {
        if (!rules.getReplaceableBlocks().contains(start.getType())) {
            return 0;
        }
        placeBlock(start, definition);
        int maxVeinSize = rules.getMaxVeinSize();
        if (maxVeinSize <= 1) {
            return 1;
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
        return placed;
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

    private boolean isExposedToAirOrLiquid(Block block) {
        for (BlockFace face : NEIGHBOR_FACES) {
            Block adjacent = block.getRelative(face);
            if (adjacent.getType().isAir() || adjacent.isLiquid()) {
                return true;
            }
        }
        return false;
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
            org.bukkit.block.data.BlockData data = Bukkit.createBlockData(noteBlockState);
            block.setBlockData(data, false);
            if (data instanceof org.bukkit.block.data.type.NoteBlock) {
                customBlockRegistry.mark(block, definition, (org.bukkit.block.data.type.NoteBlock) data);
            }
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

    private void debug(String message) {
        if (plugin.isDebugOreGeneration()) {
            plugin.getLogger().info("[OreGen] " + message);
        }
    }

    private static final class ChunkEntry {
        private final String worldId;
        private final int chunkX;
        private final int chunkZ;

        private ChunkEntry(String worldId, int chunkX, int chunkZ) {
            this.worldId = worldId;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }

    private static final class RepairStats {
        private int checked;
        private int removedChunks;
        private int registeredBlocks;
        private int removedNoteBlocks;
        private int forcedLoaded;
        private int missingWorld;
    }

    private static final class RepairScanResult {
        private int targetMatches;
        private int registeredBlocks;
    }

    private static String locationString(Block block) {
        return block.getWorld().getName() + "@" + block.getX() + "," + block.getY() + "," + block.getZ();
    }
}
