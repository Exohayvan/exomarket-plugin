package com.exomarket;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
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

        ItemStack sanitized = new ItemStack(original.getType());
        sanitized.setAmount(1);

        ItemMeta originalMeta = original.getItemMeta();
        ItemMeta sanitizedMeta = sanitized.getItemMeta();

        if (originalMeta instanceof EnchantmentStorageMeta && sanitizedMeta instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta originalStorage = (EnchantmentStorageMeta) originalMeta;
            EnchantmentStorageMeta sanitizedStorage = (EnchantmentStorageMeta) sanitizedMeta;
            for (Map.Entry<Enchantment, Integer> entry : originalStorage.getStoredEnchants().entrySet()) {
                sanitizedStorage.addStoredEnchant(entry.getKey(), entry.getValue(), true);
            }
            sanitized.setItemMeta(sanitizedStorage);
        }

        if (!original.getEnchantments().isEmpty()) {
            sanitized.addUnsafeEnchantments(original.getEnchantments());
        }

        if (originalMeta instanceof Damageable && sanitizedMeta instanceof Damageable) {
            Damageable originalDamageable = (Damageable) originalMeta;
            Damageable sanitizedDamageable = (Damageable) sanitizedMeta;
            sanitizedDamageable.setDamage(originalDamageable.getDamage());
            sanitized.setItemMeta((ItemMeta) sanitizedDamageable);
        } else if (sanitizedMeta != null && !(sanitizedMeta instanceof EnchantmentStorageMeta)) {
            sanitized.setItemMeta(sanitizedMeta);
        }

        return sanitized;
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
        StringBuilder builder = new StringBuilder(sanitized.getType().name());

        ItemMeta meta = sanitized.getItemMeta();
        int damage = 0;
        if (meta instanceof Damageable) {
            damage = ((Damageable) meta).getDamage();
        }
        builder.append("|d:").append(damage);

        List<String> enchantParts = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : sanitized.getEnchantments().entrySet()) {
            enchantParts.add("E:" + entry.getKey().getKey().toString() + "=" + entry.getValue());
        }

        if (meta instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta storageMeta = (EnchantmentStorageMeta) meta;
            for (Map.Entry<Enchantment, Integer> entry : storageMeta.getStoredEnchants().entrySet()) {
                enchantParts.add("S:" + entry.getKey().getKey().toString() + "=" + entry.getValue());
            }
        }

        enchantParts.sort(String::compareTo);
        for (String part : enchantParts) {
            builder.append("|").append(part);
        }

        return builder.toString();
    }

}
