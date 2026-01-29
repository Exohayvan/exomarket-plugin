package com.starhavensmpcore.items;

import org.bukkit.Instrument;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.NoteBlock;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public class CustomBlockRegistry {
    private final Map<BlockKey, CustomBlockData> customBlocks = new ConcurrentHashMap<>();

    public void mark(Block block, CustomItemType type, NoteBlock noteBlock) {
        if (block == null || noteBlock == null || type == null) {
            return;
        }
        customBlocks.put(BlockKey.from(block), new CustomBlockData(type, noteBlock.getInstrument(), noteBlock.getNote()));
    }

    public void unmark(Block block) {
        customBlocks.remove(BlockKey.from(block));
    }

    public boolean isCustom(Block block) {
        return customBlocks.containsKey(BlockKey.from(block));
    }

    public CustomBlockData getBlockData(Block block) {
        return customBlocks.get(BlockKey.from(block));
    }

    private static final class BlockKey {
        private final UUID worldId;
        private final int x;
        private final int y;
        private final int z;

        private BlockKey(UUID worldId, int x, int y, int z) {
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private static BlockKey from(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            BlockKey blockKey = (BlockKey) o;
            return x == blockKey.x && y == blockKey.y && z == blockKey.z
                    && Objects.equals(worldId, blockKey.worldId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(worldId, x, y, z);
        }
    }

    public static final class CustomBlockData {
        private final CustomItemType type;
        private final Instrument instrument;
        private final Note note;

        public CustomBlockData(CustomItemType type, Instrument instrument, Note note) {
            this.type = type;
            this.instrument = instrument;
            this.note = note;
        }

        public CustomItemType getType() {
            return type;
        }

        public Instrument getInstrument() {
            return instrument;
        }

        public Note getNote() {
            return note;
        }
    }
}
