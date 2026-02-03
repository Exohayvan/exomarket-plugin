package com.starhavensmpcore.items;

import com.starhavensmpcore.core.StarhavenSMPCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CraftingList implements Listener {
    private static final List<CraftingRecipeDefinition> RECIPES;

    static {
        List<CraftingRecipeDefinition> recipes = new ArrayList<>();

        recipes.add(CraftingRecipeDefinition.shaped(
                "raw_tin_block_from_raw_tin",
                "raw_tin_block",
                1,
                new String[]{"xxx", "xxx", "xxx"},
                mapOf('x', RecipeIngredient.custom("raw_tin")),
                RecipeIngredient.custom("raw_tin")
        ));

        recipes.add(CraftingRecipeDefinition.shaped(
                "raw_tin_from_raw_tin_block",
                "raw_tin",
                9,
                new String[]{"x"},
                mapOf('x', RecipeIngredient.custom("raw_tin_block")),
                RecipeIngredient.custom("raw_tin_block")
        ));

        recipes.add(CraftingRecipeDefinition.shaped(
                "tin_block_from_tin_ingot",
                "tin_block",
                1,
                new String[]{"xxx", "xxx", "xxx"},
                mapOf('x', RecipeIngredient.custom("tin_ingot")),
                RecipeIngredient.custom("tin_ingot")
        ));

        recipes.add(CraftingRecipeDefinition.shaped(
                "tin_ingot_from_tin_block",
                "tin_ingot",
                9,
                new String[]{"x"},
                mapOf('x', RecipeIngredient.custom("tin_block")),
                RecipeIngredient.custom("tin_block")
        ));

        recipes.add(CraftingRecipeDefinition.shaped(
                "cobalt_ingot_from_cobalt_block",
                "cobalt_ingot",
                9,
                new String[]{"x"},
                mapOf('x', RecipeIngredient.custom("cobalt_block")),
                RecipeIngredient.custom("cobalt_block")
        ));

        recipes.add(CraftingRecipeDefinition.shaped(
                "cobalt_block_from_cobalt_ingot",
                "cobalt_block",
                1,
                new String[]{"xxx", "xxx", "xxx"},
                mapOf('x', RecipeIngredient.custom("cobalt_ingot")),
                RecipeIngredient.custom("cobalt_ingot")
        ));

        recipes.add(CraftingRecipeDefinition.shaped(
                "raw_cobalt_block_from_cobalt_tin",
                "raw_cobalt_block",
                1,
                new String[]{"xxx", "xxx", "xxx"},
                mapOf('x', RecipeIngredient.custom("cobalt_tin")),
                RecipeIngredient.custom("cobalt_tin")
        ));

        recipes.add(CraftingRecipeDefinition.shaped(
                "raw_cobalt_from_raw_cobalt_block",
                "cobalt_tin",
                9,
                new String[]{"x"},
                mapOf('x', RecipeIngredient.custom("raw_cobalt_block")),
                RecipeIngredient.custom("raw_cobalt_block")
        ));

        RECIPES = Collections.unmodifiableList(recipes);
    }

    private final StarhavenSMPCore plugin;
    private final CustomItemManager customItemManager;

    public CraftingList(StarhavenSMPCore plugin, CustomItemManager customItemManager) {
        this.plugin = plugin;
        this.customItemManager = customItemManager;
    }

    public void registerAll() {
        for (CraftingRecipeDefinition definition : RECIPES) {
            BlockDefinition resultDefinition = ItemList.getBlockDefinition(definition.getResultId());
            if (resultDefinition == null) {
                debug("Skipping recipe " + definition.getKey() + " (missing result " + definition.getResultId() + ")");
                continue;
            }
            ItemStack result = customItemManager.createItem(resultDefinition, definition.getResultAmount());
            NamespacedKey key = new NamespacedKey(plugin, definition.getKey());
            if (definition.isShaped()) {
                ShapedRecipe recipe = new ShapedRecipe(key, result);
                recipe.shape(definition.getShape());
                for (Map.Entry<Character, RecipeIngredient> entry : definition.getIngredients().entrySet()) {
                    RecipeChoice choice = entry.getValue().toChoice(customItemManager);
                    if (choice == null) {
                        debug("Skipping recipe " + definition.getKey() + " (invalid ingredient)");
                        recipe = null;
                        break;
                    }
                    recipe.setIngredient(entry.getKey(), choice);
                }
                if (recipe != null) {
                    Bukkit.addRecipe(recipe);
                    debug("Registered shaped recipe " + definition.getKey());
                }
            } else {
                ShapelessRecipe recipe = new ShapelessRecipe(key, result);
                for (RecipeIngredient ingredient : definition.getIngredientsList()) {
                    RecipeChoice choice = ingredient.toChoice(customItemManager);
                    if (choice == null) {
                        debug("Skipping recipe " + definition.getKey() + " (invalid ingredient)");
                        recipe = null;
                        break;
                    }
                    recipe.addIngredient(choice);
                }
                if (recipe != null) {
                    Bukkit.addRecipe(recipe);
                    debug("Registered shapeless recipe " + definition.getKey());
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        tryDiscover(event.getPlayer());
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) {
            tryDiscover((Player) entity);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            tryDiscover((Player) event.getWhoClicked());
        }
    }

    private void tryDiscover(Player player) {
        if (player == null) {
            return;
        }
        for (CraftingRecipeDefinition definition : RECIPES) {
            RecipeIngredient unlock = definition.getUnlockRequirement();
            if (unlock == null) {
                continue;
            }
            NamespacedKey key = new NamespacedKey(plugin, definition.getKey());
            if (player.hasDiscoveredRecipe(key)) {
                continue;
            }
            if (playerHasIngredient(player, unlock)) {
                player.discoverRecipe(key);
                debug("Discovered recipe " + definition.getKey() + " for " + player.getName());
            }
        }
    }

    private boolean playerHasIngredient(Player player, RecipeIngredient unlock) {
        if (unlock.isCustom()) {
            String id = unlock.getCustomId();
            for (ItemStack stack : player.getInventory().getContents()) {
                BlockDefinition def = customItemManager.getCustomItemDefinition(stack);
                if (def != null && id.equals(def.getId())) {
                    return true;
                }
            }
            return false;
        }
        Material material = unlock.getMaterial();
        return material != null && player.getInventory().contains(material);
    }

    private void debug(String message) {
        if (plugin.isDebugCustomBlocks()) {
            plugin.getLogger().info("[CustomCrafting] " + message);
        }
    }

    private static Map<Character, RecipeIngredient> mapOf(char key, RecipeIngredient value) {
        Map<Character, RecipeIngredient> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    static final class CraftingRecipeDefinition {
        private final String key;
        private final String resultId;
        private final int resultAmount;
        private final String[] shape;
        private final Map<Character, RecipeIngredient> ingredients;
        private final List<RecipeIngredient> ingredientsList;
        private final RecipeIngredient unlockRequirement;

        private CraftingRecipeDefinition(String key,
                                         String resultId,
                                         int resultAmount,
                                         String[] shape,
                                         Map<Character, RecipeIngredient> ingredients,
                                         List<RecipeIngredient> ingredientsList,
                                         RecipeIngredient unlockRequirement) {
            this.key = key;
            this.resultId = resultId;
            this.resultAmount = resultAmount;
            this.shape = shape;
            this.ingredients = ingredients;
            this.ingredientsList = ingredientsList;
            this.unlockRequirement = unlockRequirement;
        }

        static CraftingRecipeDefinition shaped(String key,
                                               String resultId,
                                               int resultAmount,
                                               String[] shape,
                                               Map<Character, RecipeIngredient> ingredients,
                                               RecipeIngredient unlockRequirement) {
            return new CraftingRecipeDefinition(key, resultId, resultAmount, shape, ingredients, null, unlockRequirement);
        }

        static CraftingRecipeDefinition shapeless(String key,
                                                  String resultId,
                                                  int resultAmount,
                                                  List<RecipeIngredient> ingredients,
                                                  RecipeIngredient unlockRequirement) {
            return new CraftingRecipeDefinition(key, resultId, resultAmount, null, null, ingredients, unlockRequirement);
        }

        boolean isShaped() {
            return shape != null && shape.length > 0;
        }

        String getKey() {
            return key;
        }

        String getResultId() {
            return resultId;
        }

        int getResultAmount() {
            return resultAmount;
        }

        String[] getShape() {
            return shape;
        }

        Map<Character, RecipeIngredient> getIngredients() {
            return ingredients == null ? Collections.emptyMap() : ingredients;
        }

        List<RecipeIngredient> getIngredientsList() {
            return ingredientsList == null ? Collections.emptyList() : ingredientsList;
        }

        RecipeIngredient getUnlockRequirement() {
            return unlockRequirement;
        }
    }
}
