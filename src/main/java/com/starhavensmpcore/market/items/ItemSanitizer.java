package com.starhavensmpcore.market.items;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Utility helpers to normalise ItemStacks so that amount is consistent while preserving metadata.
 * This keeps items matching their in-world variants for stacking and display.
 */
public final class ItemSanitizer {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private ItemSanitizer() {
    }

    public static ItemStack sanitize(ItemStack original) {
        if (original == null || original.getType() == Material.AIR) {
            return new ItemStack(Material.AIR);
        }

        ItemStack sanitized = original.clone();
        sanitized.setAmount(1);

        ItemMeta meta = sanitized.getItemMeta();
        if (meta == null || !hasSignificantMeta(meta)) {
            ItemStack clean = new ItemStack(original.getType());
            clean.setAmount(1);
            return stripZeroDamageComponent(clean);
        }

        sanitized.setItemMeta(meta);
        if (sanitized.getType() == Material.ENCHANTED_BOOK) {
            return cleanEnchantedBook(sanitized);
        }
        return stripZeroDamageComponent(sanitized);
    }

    private static ItemStack stripZeroDamageComponent(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof Damageable && ((Damageable) meta).getDamage() > 0) {
            return stack;
        }

        Map<String, Object> serialized = stack.serialize();
        boolean removed = normalizeSerializedMap(serialized);

        if (!removed) {
            return stack;
        }

        return ItemStack.deserialize(serialized);
    }

    private static boolean removeZeroDamageKey(Map<String, Object> map) {
        if (map == null) {
            return false;
        }
        Object damage = map.get("damage");
        if (damage instanceof Number && ((Number) damage).intValue() == 0) {
            map.remove("damage");
            return true;
        }
        Object damageUpper = map.get("Damage");
        if (damageUpper instanceof Number && ((Number) damageUpper).intValue() == 0) {
            map.remove("Damage");
            return true;
        }
        return false;
    }

    private static boolean removeZeroDamageComponent(Map<String, Object> map) {
        if (map == null) {
            return false;
        }
        Object components = map.get("components");
        if (!(components instanceof Map)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> componentMap = (Map<String, Object>) components;
        Object damage = componentMap.get("minecraft:damage");
        if (damage instanceof Number && ((Number) damage).intValue() == 0) {
            componentMap.remove("minecraft:damage");
            if (componentMap.isEmpty()) {
                map.remove("components");
            }
            return true;
        }
        return false;
    }

    private static boolean normalizeSerializedMap(Map<String, Object> map) {
        if (map == null) {
            return false;
        }
        boolean removed = removeZeroDamageKey(map);
        removed = removeZeroDamageComponent(map) || removed;
        Object metaObj = map.get("meta");
        if (metaObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metaMap = (Map<String, Object>) metaObj;
            removed = removeZeroDamageKey(metaMap) || removed;
            removed = removeZeroDamageComponent(metaMap) || removed;
        }
        return removed;
    }

    private static boolean hasSignificantMeta(ItemMeta meta) {
        if (meta == null) {
            return false;
        }

        if (meta.hasDisplayName() || meta.hasLore() || meta.hasEnchants()) {
            return true;
        }

        if (meta instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta storageMeta = (EnchantmentStorageMeta) meta;
            if (!storageMeta.getStoredEnchants().isEmpty()) {
                return true;
            }
        }

        if (meta instanceof Damageable && ((Damageable) meta).getDamage() > 0) {
            return true;
        }

        try {
            if (meta.hasCustomModelData()) {
                return true;
            }
        } catch (NoSuchMethodError ignored) {
            // API level without custom model data
        }

        try {
            if (meta.hasAttributeModifiers()) {
                return true;
            }
        } catch (NoSuchMethodError ignored) {
            // API level without attribute modifiers
        }

        if (!meta.getItemFlags().isEmpty()) {
            return true;
        }

        try {
            if (meta.isUnbreakable()) {
                return true;
            }
        } catch (NoSuchMethodError ignored) {
            // API level without unbreakable support
        }

        if (meta instanceof Repairable && ((Repairable) meta).getRepairCost() > 0) {
            return true;
        }

        try {
            if (!meta.getPersistentDataContainer().getKeys().isEmpty()) {
                return true;
            }
        } catch (NoSuchMethodError ignored) {
            // API level without persistent data containers
        }

        return false;
    }

    private static ItemStack cleanEnchantedBook(ItemStack stack) {
        if (stack == null || stack.getType() != Material.ENCHANTED_BOOK) {
            return stack;
        }

        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof EnchantmentStorageMeta)) {
            return stripZeroDamageComponent(stack);
        }

        EnchantmentStorageMeta source = (EnchantmentStorageMeta) meta;
        ItemStack cleaned = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta cleanedMeta = (EnchantmentStorageMeta) cleaned.getItemMeta();
        if (cleanedMeta != null) {
            source.getStoredEnchants().forEach((enchant, level) ->
                    cleanedMeta.addStoredEnchant(enchant, level, true));
            if (source.hasDisplayName()) {
                cleanedMeta.setDisplayName(source.getDisplayName());
            }
            if (source.hasLore()) {
                cleanedMeta.setLore(source.getLore());
            }
            cleaned.setItemMeta(cleanedMeta);
        }

        return stripZeroDamageComponent(cleaned);
    }

    public static boolean isDamaged(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return false;
        }

        ItemMeta meta = itemStack.getItemMeta();
        if (meta instanceof Damageable) {
            return ((Damageable) meta).getDamage() > 0;
        }

        return false;
    }

    public static boolean matches(ItemStack stack, ItemStack template) {
        ItemStack sanitizedStack = sanitize(stack);
        ItemStack sanitizedTemplate = sanitize(template);
        return sanitizedStack.isSimilar(sanitizedTemplate);
    }

    public static String serializeToString(ItemStack itemStack) {
        ItemStack sanitized = sanitize(itemStack);
        Map<String, Object> serialized = sanitized.serialize();
        normalizeSerializedMap(serialized);
        return GSON.toJson(serialized);
    }

    public static ItemStack deserializeFromString(String data) {
        if (data == null || data.isEmpty()) {
            return new ItemStack(Material.AIR);
        }
        Map<String, Object> map = GSON.fromJson(data, MAP_TYPE);
        normalizeSerializedMap(map);
        return sanitize(ItemStack.deserialize(map));
    }

    public static String createAggregationKey(ItemStack itemStack) {
        ItemStack sanitized = sanitize(itemStack);
        return serializeToString(sanitized);
    }

}
