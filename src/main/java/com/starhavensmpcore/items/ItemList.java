package com.starhavensmpcore.items;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ItemList {
    private static final String NAMESPACE = "starhaven";

    public static final BlockDefinition ECHO_SHARD;
    public static final BlockDefinition ALUMINUM_ORE;
    public static final BlockDefinition CHROMIUM_ORE;
    public static final BlockDefinition COBALT_ORE;
    public static final BlockDefinition VOIDSTONE_ORE;
    public static final BlockDefinition VOID_BLOCK;

    private static final List<BlockDefinition> ALL;
    private static final List<BlockDefinition> CUSTOM_BLOCKS;
    private static final List<BlockDefinition> GENERATION_BLOCKS;
    private static final Map<String, BlockDefinition> ITEMS_BY_ID;

    static {
        ECHO_SHARD = new BlockDefinition(
                "void_shard",
                Material.ECHO_SHARD,
                69001,
                "Void Shard",
                null,
                null,
                null,
                null,
                0,
                0,
                null
        );

        ALUMINUM_ORE = new BlockDefinition(
                "aluminum_ore",
                Material.STONE,
                69004,
                "Aluminum Ore",
                "minecraft:note_block[instrument=flute,note=0,powered=true]",
                ToolRequirement.IRON_PICKAXE_OR_BETTER,
                new DropTable("aluminum_ore", 1, 1, 0.0, 0, 0),
                null,
                0,
                0,
                null
        );

        CHROMIUM_ORE = new BlockDefinition(
                "chromium_ore",
                Material.STONE,
                69005,
                "Chromium Ore",
                "minecraft:note_block[instrument=flute,note=1,powered=true]",
                ToolRequirement.IRON_PICKAXE_OR_BETTER,
                new DropTable("chromium_ore", 1, 1, 0.0, 0, 0),
                null,
                0,
                0,
                null
        );

        COBALT_ORE = new BlockDefinition(
                "cobalt_ore",
                Material.STONE,
                69006,
                "Cobalt Ore",
                "minecraft:note_block[instrument=flute,note=2,powered=true]",
                ToolRequirement.IRON_PICKAXE_OR_BETTER,
                new DropTable("cabalt_ore", 1, 1, 0.0, 0, 0),
                null,
                0,
                0,
                null
        );

        VOIDSTONE_ORE = new BlockDefinition(
                "voidstone_ore",
                Material.STONE,
                69002,
                "Voidstone Ore",
                "minecraft:note_block[instrument=flute,note=12,powered=true]",
                ToolRequirement.IRON_PICKAXE_OR_BETTER,
                new DropTable("void_shard", 1, 1, 0.15, 1, 1),
                new DropTable("voidstone_ore", 1, 1, 0.0, 0, 0),
                12,
                20,
                new GenerationRules(
                        environments(World.Environment.THE_END),
                        -1,
                        -1,
                        1.0,
                        0.01,
                        12,
                        24,
                        2,
                        0.20,
                        materials(Material.END_STONE)
                )
        );

        VOID_BLOCK = new BlockDefinition(
                "void_block",
                Material.STONE,
                69003,
                "Block of Void",
                "minecraft:note_block[instrument=bit,note=0,powered=true]",
                ToolRequirement.IRON_PICKAXE_OR_BETTER,
                new DropTable("void_block", 1, 1, 0.0, 0, 0),
                null,
                0,
                0,
                null
        );

        List<BlockDefinition> all = new ArrayList<>();
        Collections.addAll(all, ECHO_SHARD, ALUMINUM_ORE, CHROMIUM_ORE, VOIDSTONE_ORE, VOID_BLOCK);
        ALL = Collections.unmodifiableList(all);

        List<BlockDefinition> blocks = new ArrayList<>();
        for (BlockDefinition definition : ALL) {
            String noteState = definition.getNoteBlockState();
            if (noteState != null && !noteState.isEmpty()) {
                blocks.add(definition);
            }
        }
        CUSTOM_BLOCKS = Collections.unmodifiableList(blocks);

        Map<String, BlockDefinition> byId = new HashMap<>();
        for (BlockDefinition definition : ALL) {
            byId.put(definition.getId(), definition);
        }
        ITEMS_BY_ID = Collections.unmodifiableMap(byId);

        List<BlockDefinition> generation = new ArrayList<>();
        for (BlockDefinition definition : CUSTOM_BLOCKS) {
            if (definition.hasGenerationRules()) {
                generation.add(definition);
            }
        }
        GENERATION_BLOCKS = Collections.unmodifiableList(generation);
    }

    private ItemList() {
    }

    public static List<BlockDefinition> all() {
        return ALL;
    }

    public static List<BlockDefinition> customBlocks() {
        return CUSTOM_BLOCKS;
    }

    public static List<BlockDefinition> generationBlocks() {
        return GENERATION_BLOCKS;
    }

    public static BlockDefinition getBlockDefinition(String id) {
        return ITEMS_BY_ID.get(id);
    }

    public static BlockDefinition fromArgument(String argument) {
        if (argument == null || argument.isEmpty()) {
            return null;
        }
        String normalized = argument.toLowerCase(Locale.ROOT);
        String id = normalized;
        int colonIndex = normalized.indexOf(':');
        if (colonIndex >= 0) {
            String namespace = normalized.substring(0, colonIndex);
            if (!NAMESPACE.equals(namespace)) {
                return null;
            }
            id = normalized.substring(colonIndex + 1);
        }
        return ITEMS_BY_ID.get(id);
    }

    private static Set<World.Environment> environments(World.Environment... environments) {
        Set<World.Environment> set = new HashSet<>();
        if (environments != null) {
            Collections.addAll(set, environments);
        }
        return set;
    }

    private static Set<Material> materials(Material... materials) {
        Set<Material> set = new HashSet<>();
        if (materials != null) {
            Collections.addAll(set, materials);
        }
        return set;
    }
}
