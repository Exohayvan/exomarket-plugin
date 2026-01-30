package com.starhavensmpcore.items;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum CustomItemType {
    ECHO_SHARD("void_shard", Material.ECHO_SHARD, 69001, "Void Shard", null),
    VOIDSTONE_ORE("voidstone_ore", Material.STONE, 69002, "Voidstone Ore",
            "minecraft:note_block[instrument=flute,note=12,powered=true]"),
    VOID_BLOCK("void_block", Material.STONE, 69003, "Block of Void",
            "minecraft:note_block[instrument=bit,note=0,powered=true]");

    private static final String NAMESPACE = "starhaven";
    private static final Map<String, CustomItemType> BY_ID = new HashMap<>();

    static {
        for (CustomItemType type : values()) {
            BY_ID.put(type.id, type);
        }
    }

    private final String id;
    private final Material baseMaterial;
    private final int customModelData;
    private final String displayName;
    private final String noteBlockState;

    CustomItemType(String id, Material baseMaterial, int customModelData, String displayName, String noteBlockState) {
        this.id = id;
        this.baseMaterial = baseMaterial;
        this.customModelData = customModelData;
        this.displayName = displayName;
        this.noteBlockState = noteBlockState;
    }

    public String getId() {
        return id;
    }

    public Material getBaseMaterial() {
        return baseMaterial;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getNoteBlockState() {
        return noteBlockState;
    }

    public static CustomItemType fromArgument(String argument) {
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
        return BY_ID.get(id);
    }
}
