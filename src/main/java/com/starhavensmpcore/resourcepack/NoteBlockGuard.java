package com.starhavensmpcore.resourcepack;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.items.BlockDefinition;
import com.starhavensmpcore.items.CustomBlockRegistry;
import com.starhavensmpcore.items.CustomItemManager;
import com.starhavensmpcore.items.ItemList;
import org.bukkit.Bukkit;
import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class NoteBlockGuard implements Listener {
    private final StarhavenSMPCore plugin;
    private final CustomBlockRegistry customBlockRegistry;
    private final CustomItemManager customItemManager;
    private final Map<NoteKey, BlockDefinition> reservedNotes;
    private final Set<BlockKey> redstoneTriggered;

    public NoteBlockGuard(StarhavenSMPCore plugin, CustomBlockRegistry customBlockRegistry, CustomItemManager customItemManager) {
        this.plugin = plugin;
        this.customBlockRegistry = customBlockRegistry;
        this.customItemManager = customItemManager;
        this.reservedNotes = new HashMap<>();
        this.redstoneTriggered = new HashSet<>();
        loadReservedNotes();
    }

    private void loadReservedNotes() {
        for (BlockDefinition definition : ItemList.customBlocks()) {
            String noteBlockState = definition.getNoteBlockState();
            BlockDefinition type = definition;
            if (noteBlockState == null || noteBlockState.isEmpty()) {
                continue;
            }
            try {
                BlockData data = Bukkit.createBlockData(noteBlockState);
                if (data instanceof NoteBlock) {
                    NoteBlock noteBlock = (NoteBlock) data;
                    reservedNotes.put(new NoteKey(noteBlock.getInstrument(), noteBlock.getNote()), type);
                }
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid note block state for " + type.getId() + ": " + noteBlockState);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockRedstone(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.NOTE_BLOCK) {
            return;
        }
        BlockData data = block.getBlockData();
        if (!(data instanceof NoteBlock)) {
            return;
        }
        NoteBlock noteBlock = (NoteBlock) data;
        BlockKey key = BlockKey.from(block);
        if (event.getNewCurrent() > 0) {
            redstoneTriggered.add(key);
            Bukkit.getScheduler().runTask(plugin, () -> redstoneTriggered.remove(key));
        }
        if (customBlockRegistry.isCustom(block)) {
            event.setNewCurrent(0);
            applyCustomState(block, noteBlock);
            return;
        }

        if (event.getNewCurrent() > 0) {
            event.setNewCurrent(0);
            Bukkit.getScheduler().runTask(plugin, () -> ensureUnpowered(block));
            ensureUnpowered(block);
            block.getWorld().playNote(block.getLocation(), noteBlock.getInstrument(), noteBlock.getNote());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.NOTE_BLOCK) {
            return;
        }
        BlockData data = block.getBlockData();
        if (!(data instanceof NoteBlock)) {
            return;
        }
        NoteBlock noteBlock = (NoteBlock) data;
        if (!customBlockRegistry.isCustom(block)) {
            ensureUnpowered(block);
            return;
        }
        event.setCancelled(true);
        applyCustomState(block, noteBlock);
    }

    @EventHandler(ignoreCancelled = true)
    public void onNotePlay(NotePlayEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.NOTE_BLOCK) {
            return;
        }
        BlockData data = block.getBlockData();
        if (!(data instanceof NoteBlock)) {
            return;
        }
        NoteBlock noteBlock = (NoteBlock) data;
        if (customBlockRegistry.isCustom(block) || isCustomBlock(block, noteBlock)) {
            event.setCancelled(true);
            return;
        }
        BlockKey key = BlockKey.from(block);
        if (redstoneTriggered.remove(key)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.NOTE_BLOCK) {
            return;
        }
        BlockData data = block.getBlockData();
        if (!(data instanceof NoteBlock)) {
            return;
        }
        NoteBlock noteBlock = (NoteBlock) data;
        boolean isCustom = customBlockRegistry.isCustom(block);
        if (!isCustom && isReserved(block, noteBlock)) {
            BlockDefinition type = getReservedType(noteBlock);
            if (type != null) {
                customBlockRegistry.mark(block, type, noteBlock);
                isCustom = true;
            }
        }
        if (!isCustom) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        BlockDefinition heldDefinition = customItemManager.getCustomItemDefinition(held);

        if (heldDefinition != null && heldDefinition.getNoteBlockState() != null) {
            Block target = block.getRelative(event.getBlockFace());
            if (canReplace(target)) {
                event.setCancelled(true);
                target.setType(Material.NOTE_BLOCK, false);
                try {
                    BlockData customData = Bukkit.createBlockData(heldDefinition.getNoteBlockState());
                    target.setBlockData(customData, false);
                    if (customData instanceof NoteBlock) {
                        customBlockRegistry.mark(target, heldDefinition, (NoteBlock) customData);
                    }
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Invalid note block state for " + heldDefinition.getId()
                            + ": " + heldDefinition.getNoteBlockState());
                }
                if (player.getGameMode() != GameMode.CREATIVE) {
                    consumeItem(getHandStack(player, event.getHand()));
                }
                return;
            }
        }

        if (held != null && held.getType().isBlock()) {
            Block target = block.getRelative(event.getBlockFace());
            if (canReplace(target)) {
                event.setCancelled(true);
                target.setType(held.getType(), false);
                if (player.getGameMode() != GameMode.CREATIVE) {
                    consumeItem(getHandStack(player, event.getHand()));
                }
                return;
            }
        }

        // Re-apply custom state next tick to prevent note changes while still allowing placement.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (block.getType() != Material.NOTE_BLOCK) {
                return;
            }
            BlockData latest = block.getBlockData();
            if (latest instanceof NoteBlock) {
                applyCustomState(block, (NoteBlock) latest);
            }
        });
    }

    private void consumeItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return;
        }
        if (stack.getAmount() <= 1) {
            stack.setAmount(0);
            stack.setType(Material.AIR);
        } else {
            stack.setAmount(stack.getAmount() - 1);
        }
    }

    private boolean canReplace(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        return type.isAir() || type == Material.WATER || type == Material.LAVA;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.NOTE_BLOCK) {
            return;
        }
        BlockData data = block.getBlockData();
        if (data instanceof NoteBlock && isCustomBlock(block, (NoteBlock) data)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Block block = event.getBlock();
        if (block == null || block.getType() != Material.NOTE_BLOCK) {
            return;
        }
        BlockData data = block.getBlockData();
        if (data instanceof NoteBlock && isCustomBlock(block, (NoteBlock) data)) {
            event.setCancelled(true);
        }
    }

    private ItemStack getHandStack(Player player, EquipmentSlot hand) {
        if (hand == EquipmentSlot.OFF_HAND) {
            return player.getInventory().getItemInOffHand();
        }
        return player.getInventory().getItemInMainHand();
    }

    private boolean isReserved(Block block, NoteBlock noteBlock) {
        if (!noteBlock.isPowered()) {
            return false;
        }
        if (block != null && block.getBlockPower() > 0) {
            return false;
        }
        if (block != null && redstoneTriggered.contains(BlockKey.from(block))) {
            return false;
        }
        return reservedNotes.containsKey(new NoteKey(noteBlock.getInstrument(), noteBlock.getNote()));
    }

    private BlockDefinition getReservedType(NoteBlock noteBlock) {
        return reservedNotes.get(new NoteKey(noteBlock.getInstrument(), noteBlock.getNote()));
    }

    private boolean isCustomBlock(Block block, NoteBlock noteBlock) {
        if (customBlockRegistry.isCustom(block)) {
            return true;
        }
        if (isReserved(block, noteBlock)) {
            BlockDefinition type = getReservedType(noteBlock);
            if (type != null) {
                customBlockRegistry.mark(block, type, noteBlock);
            }
            return true;
        }
        return false;
    }

    private void applyCustomState(Block block, NoteBlock noteBlock) {
        CustomBlockRegistry.CustomBlockData noteData = customBlockRegistry.getBlockData(block);
        boolean changed = false;
        if (noteData != null) {
            if (noteBlock.getInstrument() != noteData.getInstrument()) {
                noteBlock.setInstrument(noteData.getInstrument());
                changed = true;
            }
            if (!noteBlock.getNote().equals(noteData.getNote())) {
                noteBlock.setNote(noteData.getNote());
                changed = true;
            }
        }
        if (!noteBlock.isPowered()) {
            noteBlock.setPowered(true);
            changed = true;
        }
        if (changed) {
            block.setBlockData(noteBlock, false);
        }
    }

    private void ensureUnpowered(Block block) {
        if (block == null || block.getType() != Material.NOTE_BLOCK) {
            return;
        }
        BlockData data = block.getBlockData();
        if (!(data instanceof NoteBlock)) {
            return;
        }
        NoteBlock noteBlock = (NoteBlock) data;
        if (noteBlock.isPowered()) {
            noteBlock.setPowered(false);
            block.setBlockData(noteBlock, false);
        }
    }

    private static final class NoteKey {
        private final Instrument instrument;
        private final Note note;

        private NoteKey(Instrument instrument, Note note) {
            this.instrument = instrument;
            this.note = note;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            NoteKey noteKey = (NoteKey) o;
            return instrument == noteKey.instrument && Objects.equals(note, noteKey.note);
        }

        @Override
        public int hashCode() {
            return Objects.hash(instrument, note);
        }
    }

    private static final class BlockKey {
        private final String worldId;
        private final int x;
        private final int y;
        private final int z;

        private BlockKey(String worldId, int x, int y, int z) {
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private static BlockKey from(Block block) {
            return new BlockKey(block.getWorld().getUID().toString(), block.getX(), block.getY(), block.getZ());
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
}
