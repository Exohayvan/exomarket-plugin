package com.starhavensmpcore.items;

import org.bukkit.Material;

public final class BlockDefinition {
    private final String id;
    private final Material baseMaterial;
    private final int customModelData;
    private final String displayName;
    private final String noteBlockState;
    private final ToolRequirement toolRequirement;
    private final DropTable normalDrops;
    private final DropTable silkDrops;
    private final int xpMin;
    private final int xpMax;
    private final GenerationRules generationRules;

    public BlockDefinition(String id,
                           Material baseMaterial,
                           int customModelData,
                           String displayName,
                           String noteBlockState,
                           ToolRequirement toolRequirement,
                           DropTable normalDrops,
                           DropTable silkDrops,
                           int xpMin,
                           int xpMax,
                           GenerationRules generationRules) {
        this.id = id;
        this.baseMaterial = baseMaterial;
        this.customModelData = customModelData;
        this.displayName = displayName;
        this.noteBlockState = noteBlockState;
        this.toolRequirement = toolRequirement;
        this.normalDrops = normalDrops;
        this.silkDrops = silkDrops;
        this.xpMin = Math.max(0, xpMin);
        this.xpMax = Math.max(this.xpMin, xpMax);
        this.generationRules = generationRules;
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

    public ToolRequirement getToolRequirement() {
        return toolRequirement;
    }

    public DropTable getNormalDrops() {
        return normalDrops;
    }

    public DropTable getSilkDrops() {
        return silkDrops;
    }

    public int getXpMin() {
        return xpMin;
    }

    public int getXpMax() {
        return xpMax;
    }

    public GenerationRules getGenerationRules() {
        return generationRules;
    }

    public DropTable getDropsForTool(boolean silkTouch) {
        if (silkTouch && silkDrops != null) {
            return silkDrops;
        }
        return normalDrops;
    }

    public boolean hasGenerationRules() {
        return generationRules != null;
    }
}
