package com.exomarket;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Utility helpers to normalise ItemStacks so that only material, damage, and enchantments are preserved.
 * This ensures player-facing customisations like names or lore do not impact persisted data.
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
        if (meta != null) {
            if (meta.hasDisplayName()) {
                meta.setDisplayName(null);
            }
            if (meta.hasLore()) {
                meta.setLore(null);
            }
            try {
                meta.setLocalizedName(null);
            } catch (NoSuchMethodError ignored) {
                // Older API - ignore
            }

            if (meta instanceof Damageable) {
                ItemMeta originalMeta = original.getItemMeta();
                if (originalMeta instanceof Damageable) {
                    ((Damageable) meta).setDamage(((Damageable) originalMeta).getDamage());
                }
            }

            sanitized.setItemMeta(meta);
        }

        return sanitized;
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
        return GSON.toJson(sanitized.serialize());
    }

    public static ItemStack deserializeFromString(String data) {
        if (data == null || data.isEmpty()) {
            return new ItemStack(Material.AIR);
        }
        Map<String, Object> map = GSON.fromJson(data, MAP_TYPE);
        return sanitize(ItemStack.deserialize(map));
    }

    public static String createAggregationKey(ItemStack itemStack) {
        ItemStack sanitized = sanitize(itemStack);
        return serializeToString(sanitized);
    }

}
