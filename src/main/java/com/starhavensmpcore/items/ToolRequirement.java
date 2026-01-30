package com.starhavensmpcore.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public enum ToolRequirement {
    NONE(null),
    IRON_PICKAXE_OR_BETTER("You need at least an iron pickaxe to get drops from this.");

    private final String failureMessage;

    ToolRequirement(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public boolean isSatisfied(ItemStack tool) {
        if (this == NONE) {
            return true;
        }
        if (tool == null) {
            return false;
        }
        Material material = tool.getType();
        switch (this) {
            case IRON_PICKAXE_OR_BETTER:
                return material == Material.IRON_PICKAXE
                        || material == Material.DIAMOND_PICKAXE
                        || material == Material.NETHERITE_PICKAXE;
            default:
                return false;
        }
    }

    public String getFailureMessage() {
        return failureMessage;
    }
}
