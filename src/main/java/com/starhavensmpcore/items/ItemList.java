package com.starhavensmpcore.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ItemList {
    public static final CustomItemType ECHO_SHARD = CustomItemType.ECHO_SHARD;
    public static final CustomItemType VOIDSTONE_ORE = CustomItemType.VOIDSTONE_ORE;
    public static final CustomItemType VOID_BLOCK = CustomItemType.VOID_BLOCK;

    private static final List<CustomItemType> ALL;
    private static final List<CustomItemType> CUSTOM_BLOCKS;
    private static final List<CustomItemType> GENERATION_ORES;

    static {
        List<CustomItemType> all = new ArrayList<>();
        Collections.addAll(all, CustomItemType.values());
        ALL = Collections.unmodifiableList(all);

        List<CustomItemType> blocks = new ArrayList<>();
        for (CustomItemType type : ALL) {
            String noteState = type.getNoteBlockState();
            if (noteState != null && !noteState.isEmpty()) {
                blocks.add(type);
            }
        }
        CUSTOM_BLOCKS = Collections.unmodifiableList(blocks);

        List<CustomItemType> ores = new ArrayList<>();
        ores.add(VOIDSTONE_ORE);
        GENERATION_ORES = Collections.unmodifiableList(ores);
    }

    private ItemList() {
    }

    public static List<CustomItemType> all() {
        return ALL;
    }

    public static List<CustomItemType> customBlocks() {
        return CUSTOM_BLOCKS;
    }

    public static List<CustomItemType> generationOres() {
        return GENERATION_ORES;
    }

    public static CustomItemType fromArgument(String argument) {
        return CustomItemType.fromArgument(argument);
    }
}
