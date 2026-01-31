package com.starhavensmpcore.items;

import com.starhavensmpcore.core.StarhavenSMPCore;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SmeltingList {
    private static final List<SmeltingRecipeDefinition> RECIPES;

    static {
        List<SmeltingRecipeDefinition> recipes = new ArrayList<>();

        recipes.add(new SmeltingRecipeDefinition(
                "tin_ingot_from_raw_tin",
                RecipeIngredient.custom("raw_tin"),
                "tin_ingot",
                0.7f,
                200
        ));

        RECIPES = Collections.unmodifiableList(recipes);
    }

    private SmeltingList() {
    }

    public static void registerAll(StarhavenSMPCore plugin, CustomItemManager customItemManager) {
        for (SmeltingRecipeDefinition definition : RECIPES) {
            BlockDefinition resultDefinition = ItemList.getBlockDefinition(definition.getResultId());
            if (resultDefinition == null) {
                debug(plugin, "Skipping smelting " + definition.getKey() + " (missing result " + definition.getResultId() + ")");
                continue;
            }
            RecipeChoice inputChoice = definition.getInput().toChoice(customItemManager);
            if (inputChoice == null) {
                debug(plugin, "Skipping smelting " + definition.getKey() + " (invalid input)");
                continue;
            }
            ItemStack result = customItemManager.createItem(resultDefinition, 1);
            FurnaceRecipe recipe = new FurnaceRecipe(new NamespacedKey(plugin, definition.getKey()),
                    result, inputChoice, definition.getExperience(), definition.getCookTime());
            Bukkit.addRecipe(recipe);
            debug(plugin, "Registered smelting recipe " + definition.getKey());
        }
    }

    private static void debug(StarhavenSMPCore plugin, String message) {
        if (plugin.isDebugCustomBlocks()) {
            plugin.getLogger().info("[CustomSmelting] " + message);
        }
    }

    static final class SmeltingRecipeDefinition {
        private final String key;
        private final RecipeIngredient input;
        private final String resultId;
        private final float experience;
        private final int cookTime;

        SmeltingRecipeDefinition(String key, RecipeIngredient input, String resultId, float experience, int cookTime) {
            this.key = key;
            this.input = input;
            this.resultId = resultId;
            this.experience = experience;
            this.cookTime = cookTime;
        }

        String getKey() {
            return key;
        }

        RecipeIngredient getInput() {
            return input;
        }

        String getResultId() {
            return resultId;
        }

        float getExperience() {
            return experience;
        }

        int getCookTime() {
            return cookTime;
        }
    }
}
