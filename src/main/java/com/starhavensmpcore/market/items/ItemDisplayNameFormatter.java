package com.starhavensmpcore.market.items;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemDisplayNameFormatter {
    private ItemDisplayNameFormatter() {
    }

    public static String format(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return "AIR";
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String stripped = ChatColor.stripColor(meta.getDisplayName());
            if (stripped != null && !stripped.trim().isEmpty()) {
                return stripped;
            }
        }

        if (stack.getType() == Material.ENCHANTED_BOOK) {
            String bookName = formatEnchantedBook(meta);
            if (bookName != null) {
                return bookName;
            }
        }

        return formatMaterialName(stack.getType().toString());
    }

    public static String formatMaterialName(String materialName) {
        if (materialName == null || materialName.isEmpty()) {
            return "UNKNOWN";
        }
        return materialName.replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    private static String formatEnchantedBook(ItemMeta meta) {
        if (!(meta instanceof EnchantmentStorageMeta)) {
            return formatMaterialName("ENCHANTED_BOOK");
        }

        EnchantmentStorageMeta storageMeta = (EnchantmentStorageMeta) meta;
        Map<Enchantment, Integer> stored = storageMeta.getStoredEnchants();
        if (stored.isEmpty()) {
            return formatMaterialName("ENCHANTED_BOOK");
        }

        List<Map.Entry<Enchantment, Integer>> entries = new ArrayList<>(stored.entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getKey().getKey().getKey()));

        List<String> parts = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : entries) {
            String name = formatEnchantmentName(entry.getKey().getKey().getKey());
            String level = toRoman(entry.getValue());
            if (level.isEmpty()) {
                parts.add(name);
            } else {
                parts.add(name + " " + level);
            }
        }

        return String.join(", ", parts);
    }

    private static String formatEnchantmentName(String key) {
        if (key == null || key.isEmpty()) {
            return "UNKNOWN";
        }
        return key.replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    private static String toRoman(int value) {
        int level = Math.max(1, value);
        int[] numbers = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder builder = new StringBuilder();
        int remaining = level;
        for (int i = 0; i < numbers.length; i++) {
            while (remaining >= numbers[i]) {
                builder.append(numerals[i]);
                remaining -= numbers[i];
            }
        }
        return builder.toString();
    }
}
