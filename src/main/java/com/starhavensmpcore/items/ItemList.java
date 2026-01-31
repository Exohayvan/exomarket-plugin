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

    private static final List<BlockDefinition> ALL;
    private static final List<BlockDefinition> CUSTOM_BLOCKS;
    private static final List<BlockDefinition> GENERATION_BLOCKS;
    private static final Map<String, BlockDefinition> ITEMS_BY_ID;

    static {
        List<BlockDefinition> all = new ArrayList<>();
        List<BlockDefinition> customBlocks = new ArrayList<>();
        List<BlockDefinition> generationBlocks = new ArrayList<>();
        Map<String, BlockDefinition> byId = new HashMap<>();

        register(all, customBlocks, generationBlocks, byId, new BlockDefinition(
                "raw_tin",
                Material.RAW_IRON,
                69100,
                "Raw Tin",
                null,
                null,
                null,
                null,
                0,
                0,
                null
        ));

        register(all, customBlocks, generationBlocks, byId, new BlockDefinition(
                "tin_ingot",
                Material.IRON_INGOT,
                69101,
                "Tin Ingot",
                null,
                null,
                null,
                null,
                0,
                0,
                null
        ));
        register(all, customBlocks, generationBlocks, byId, new BlockDefinition(
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
        ));

        register(all, customBlocks, generationBlocks, byId, new BlockDefinition(
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
        ));

        register(all, customBlocks, generationBlocks, byId, new BlockDefinition(
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
        ));

        register(all, customBlocks, generationBlocks, byId, new BlockDefinition(
                "cobalt_ore",
                Material.STONE,
                69006,
                "Cobalt Ore",
                "minecraft:note_block[instrument=flute,note=2,powered=true]",
                ToolRequirement.IRON_PICKAXE_OR_BETTER,
                new DropTable("cobalt_ore", 1, 1, 0.0, 0, 0),
                null,
                0,
                0,
                null
        ));

        register(all, customBlocks, generationBlocks, byId, new BlockDefinition(
                "iridium_ore",
                Material.STONE,
                69007,
                "Iridium Ore",
                "minecraft:note_block[instrument=flute,note=3,powered=true]",
                ToolRequirement.IRON_PICKAXE_OR_BETTER,
                new DropTable("iridium_ore", 1, 1, 0.0, 0, 0),
                null,
                0,
                0,
                null
        ));

        register(all, customBlocks, generationBlocks, byId, new BlockDefinition(
                "nether_cobalt_ore",
                Material.STONE,
                69008,
                "Nether Cobalt Ore",
                "minecraft:note_block[instrument=flute,note=4,powered=true]",
                ToolRequirement.IRON_PICKAXE_OR_BETTER,
                new DropTable("nether_cobalt_ore", 1, 1, 0.0, 0, 0),
                null,
                0,
                0,
                null
        ));

        register(all, customBlocks, generationBlocks, byId, new BlockDefinition(
                "nether_obsidan_ore",
                Material.STONE,
                69009,
                "Nether Obsidan Ore",
                "minecraft:note_block[instrument=flute,note=5,powered=true]",
                ToolRequirement.IRON_PICKAXE_OR_BETTER,
                new DropTable("nether_obsidan_ore", 1, 1, 0.0, 0, 0),
                null,
                0,
                0,
                null
        ));

        register(all, customBlocks, generationBlocks, byId, new BlockDefinition(
                "nickle_ore",
                Material.STONE,
                69010,
                "Nickle Ore",
                "minecraft:note_block[instrument=flute,note=6,powered=true]",
                ToolRequirement.IRON_PICKAXE_OR_BETTER,
                new DropTable("nickle_ore", 1, 1, 0.0, 0, 0),
                null,
                0,
                0,
                null
        ));

        register(all, customBlocks, generationBlocks, byId, new BlockDefinition(
                "osmium_ore",
                Material.STONE,
                69011,
                "Osmium Ore",
                "minecraft:note_block[instrument=flute,note=7,powered=true]",
                ToolRequirement.IRON_PICKAXE_OR_BETTER,
                new DropTable("osmium_ore", 1, 1, 0.0, 0, 0),
                null,
                0,
                0,
                null
        ));

        register(all, customBlocks, generationBlocks, byId, new BlockDefinition(
                "platinum_ore",
                Material.STONE,
                69012,
                "Platinum Ore",
                "minecraft:note_block[instrument=flute,note=8,powered=true]",
                ToolRequirement.IRON_PICKAXE_OR_BETTER,
                new DropTable("platinum_ore", 1, 1, 0.0, 0, 0),
                null,
                0,
                0,
                null
        ));

        register(all, customBlocks, generationBlocks, byId, new BlockDefinition(
                "tin_ore",
                Material.STONE,
                69013,
                "Tin Ore",
                "minecraft:note_block[instrument=flute,note=9,powered=true]",
                ToolRequirement.IRON_PICKAXE_OR_BETTER,
                new DropTable("raw_tin", 2, 5, 1.0, 2, 2),
                new DropTable("tin_ore", 1, 1, 0.0, 0, 0),
                0,
                0,
                new GenerationRules(
                        environments(World.Environment.NORMAL),
                        -16,
                        112,
                        1.0,
                        0.15,
                        12,
                        32,
                        10,
                        0.45,
                        materials(Material.STONE)
                )
        ));

        register(all, customBlocks, generationBlocks, byId, new BlockDefinition(
                "tin_block",
                Material.STONE,
                69018,
                "Tin Block",
                "minecraft:note_block[instrument=bit,note=1,powered=true]",
                ToolRequirement.IRON_PICKAXE_OR_BETTER,
                new DropTable("tin_block", 1, 1, 0.0, 0, 0),
                null,
                0,
                0,
                null
        ));

        register(all, customBlocks, generationBlocks, byId, new BlockDefinition(
                "raw_tin_block",
                Material.STONE,
                69019,
                "Raw Tin Block",
                "minecraft:note_block[instrument=bit,note=2,powered=true]",
                ToolRequirement.IRON_PICKAXE_OR_BETTER,
                new DropTable("raw_tin_block", 1, 1, 0.0, 0, 0),
                null,
                0,
                0,
                null
        ));

        register(all, customBlocks, generationBlocks, byId, new BlockDefinition(
                "titanium_ore",
                Material.STONE,
                69014,
                "Titanium Ore",
                "minecraft:note_block[instrument=flute,note=10,powered=true]",
                ToolRequirement.IRON_PICKAXE_OR_BETTER,
                new DropTable("titanium_ore", 1, 1, 0.0, 0, 0),
                null,
                0,
                0,
                null
        ));

        register(all, customBlocks, generationBlocks, byId, new BlockDefinition(
                "tungsten_ore",
                Material.STONE,
                69015,
                "Tungsten Ore",
                "minecraft:note_block[instrument=flute,note=11,powered=true]",
                ToolRequirement.IRON_PICKAXE_OR_BETTER,
                new DropTable("tungsten_ore", 1, 1, 0.0, 0, 0),
                null,
                0,
                0,
                null
        ));

        register(all, customBlocks, generationBlocks, byId, new BlockDefinition(
                "vanadium_ore",
                Material.STONE,
                69016,
                "Vanadium Ore",
                "minecraft:note_block[instrument=flute,note=13,powered=true]",
                ToolRequirement.IRON_PICKAXE_OR_BETTER,
                new DropTable("vanadium_ore", 1, 1, 0.0, 0, 0),
                null,
                0,
                0,
                null
        ));

        register(all, customBlocks, generationBlocks, byId, new BlockDefinition(
                "zinc_ore",
                Material.STONE,
                69017,
                "Zinc Ore",
                "minecraft:note_block[instrument=flute,note=14,powered=true]",
                ToolRequirement.IRON_PICKAXE_OR_BETTER,
                new DropTable("zinc_ore", 1, 1, 0.0, 0, 0),
                null,
                0,
                0,
                null
        ));

        register(all, customBlocks, generationBlocks, byId, new BlockDefinition(
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
        ));

        register(all, customBlocks, generationBlocks, byId, new BlockDefinition(
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
        ));

        ALL = Collections.unmodifiableList(all);
        CUSTOM_BLOCKS = Collections.unmodifiableList(customBlocks);
        GENERATION_BLOCKS = Collections.unmodifiableList(generationBlocks);
        ITEMS_BY_ID = Collections.unmodifiableMap(byId);
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

    private static void register(List<BlockDefinition> all,
                                 List<BlockDefinition> customBlocks,
                                 List<BlockDefinition> generationBlocks,
                                 Map<String, BlockDefinition> byId,
                                 BlockDefinition definition) {
        if (definition == null) {
            return;
        }
        all.add(definition);
        if (definition.getId() != null) {
            byId.put(definition.getId(), definition);
        }
        String noteState = definition.getNoteBlockState();
        if (noteState != null && !noteState.isEmpty()) {
            customBlocks.add(definition);
        }
        if (definition.hasGenerationRules()) {
            generationBlocks.add(definition);
        }
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
