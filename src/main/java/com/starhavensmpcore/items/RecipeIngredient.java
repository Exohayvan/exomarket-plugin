package com.starhavensmpcore.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

public final class RecipeIngredient {
    private final String customId;
    private final Material material;

    private RecipeIngredient(String customId, Material material) {
        this.customId = customId;
        this.material = material;
    }

    public static RecipeIngredient custom(String id) {
        return new RecipeIngredient(id, null);
    }

    public static RecipeIngredient material(Material material) {
        return new RecipeIngredient(null, material);
    }

    public RecipeChoice toChoice(CustomItemManager customItemManager) {
        if (customId != null) {
            BlockDefinition definition = ItemList.getBlockDefinition(customId);
            if (definition == null) {
                return null;
            }
            ItemStack stack = customItemManager.createItem(definition, 1);
            return new RecipeChoice.ExactChoice(stack);
        }
        if (material != null) {
            return new RecipeChoice.MaterialChoice(material);
        }
        return null;
    }

    public boolean isCustom() {
        return customId != null;
    }

    public String getCustomId() {
        return customId;
    }

    public Material getMaterial() {
        return material;
    }
}
