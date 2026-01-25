package com.exomarket;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class EnchantedBookSplitter {
    private EnchantedBookSplitter() {
    }

    public static List<SplitEntry> split(ItemStack stack) {
        List<SplitEntry> entries = new ArrayList<>();
        if (stack == null || stack.getType().isAir()) {
            return entries;
        }

        int amount = stack.getAmount();
        if (amount <= 0) {
            return entries;
        }

        if (stack.getType() != Material.ENCHANTED_BOOK) {
            entries.add(new SplitEntry(cloneSingle(stack), amount));
            return entries;
        }

        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof EnchantmentStorageMeta)) {
            entries.add(new SplitEntry(cloneSingle(stack), amount));
            return entries;
        }

        Map<Enchantment, Integer> stored = ((EnchantmentStorageMeta) meta).getStoredEnchants();
        if (stored.isEmpty()) {
            entries.add(new SplitEntry(cloneSingle(stack), amount));
            return entries;
        }

        for (Map.Entry<Enchantment, Integer> entry : stored.entrySet()) {
            int level = Math.max(1, entry.getValue());
            int perBook = countForLevel(level);
            int total = safeMultiply(amount, perBook);
            if (total <= 0) {
                continue;
            }
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta newMeta = (EnchantmentStorageMeta) book.getItemMeta();
            if (newMeta != null) {
                newMeta.addStoredEnchant(entry.getKey(), 1, true);
                book.setItemMeta(newMeta);
            }
            entries.add(new SplitEntry(book, total));
        }

        return entries;
    }

    private static ItemStack cloneSingle(ItemStack stack) {
        ItemStack clone = stack.clone();
        clone.setAmount(1);
        return clone;
    }

    private static int countForLevel(int level) {
        int safeLevel = Math.max(1, level);
        if (safeLevel >= 31) {
            return Integer.MAX_VALUE;
        }
        return 1 << (safeLevel - 1);
    }

    private static int safeMultiply(int left, int right) {
        long total = (long) left * (long) right;
        if (total > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) total;
    }

    public static final class SplitEntry {
        private final ItemStack itemStack;
        private final int quantity;

        private SplitEntry(ItemStack itemStack, int quantity) {
            this.itemStack = itemStack;
            this.quantity = quantity;
        }

        public ItemStack getItemStack() {
            return itemStack;
        }

        public int getQuantity() {
            return quantity;
        }
    }
}
