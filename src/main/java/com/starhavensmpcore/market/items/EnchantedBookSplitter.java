package com.starhavensmpcore.market.items;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class EnchantedBookSplitter {
    private EnchantedBookSplitter() {
    }

    public static List<SplitEntry> split(ItemStack stack) {
        if (stack == null) {
            return new ArrayList<>();
        }
        return split(stack, BigInteger.valueOf(stack.getAmount()));
    }

    public static List<SplitEntry> split(ItemStack stack, BigInteger amount) {
        List<SplitEntry> entries = new ArrayList<>();
        if (stack == null || stack.getType().isAir()) {
            return entries;
        }

        BigInteger safeAmount = normalizeQuantity(amount);
        if (safeAmount.signum() <= 0) {
            return entries;
        }

        if (stack.getType() != Material.ENCHANTED_BOOK) {
            entries.add(new SplitEntry(cloneSingle(stack), safeAmount));
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
            BigInteger perBook = countForLevel(level);
            BigInteger total = safeAmount.multiply(perBook);
            if (total.signum() <= 0) {
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

    public static List<SplitEntry> splitWithEnchantmentBooks(ItemStack stack) {
        if (stack == null) {
            return new ArrayList<>();
        }
        return splitWithEnchantmentBooks(stack, BigInteger.valueOf(stack.getAmount()));
    }

    public static List<SplitEntry> splitWithEnchantmentBooks(ItemStack stack, BigInteger amount) {
        List<SplitEntry> entries = new ArrayList<>();
        if (stack == null || stack.getType().isAir()) {
            return entries;
        }

        BigInteger safeAmount = normalizeQuantity(amount);
        if (safeAmount.signum() <= 0) {
            return entries;
        }

        if (stack.getType() == Material.ENCHANTED_BOOK) {
            return split(stack, safeAmount);
        }

        Map<Enchantment, Integer> enchants = stack.getEnchantments();
        if (enchants.isEmpty()) {
            return split(stack, safeAmount);
        }

        ItemStack base = stack.clone();
        base.setAmount(1);
        for (Enchantment enchantment : enchants.keySet()) {
            base.removeEnchantment(enchantment);
        }
        entries.addAll(split(base, safeAmount));

        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            int level = Math.max(1, entry.getValue());
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            book.setAmount(1);
            EnchantmentStorageMeta newMeta = (EnchantmentStorageMeta) book.getItemMeta();
            if (newMeta != null) {
                newMeta.addStoredEnchant(entry.getKey(), level, true);
                book.setItemMeta(newMeta);
            }
            entries.addAll(split(book, safeAmount));
        }

        return entries;
    }

    private static ItemStack cloneSingle(ItemStack stack) {
        ItemStack clone = stack.clone();
        clone.setAmount(1);
        return clone;
    }

    private static BigInteger countForLevel(int level) {
        int safeLevel = Math.max(1, level);
        return BigInteger.ONE.shiftLeft(safeLevel - 1);
    }

    private static BigInteger normalizeQuantity(BigInteger value) {
        if (value == null) {
            return BigInteger.ZERO;
        }
        return value.max(BigInteger.ZERO);
    }

    public static final class SplitEntry {
        private final ItemStack itemStack;
        private final BigInteger quantity;

        private SplitEntry(ItemStack itemStack, BigInteger quantity) {
            this.itemStack = itemStack;
            this.quantity = normalizeQuantity(quantity);
        }

        public ItemStack getItemStack() {
            return itemStack;
        }

        public BigInteger getQuantity() {
            return quantity;
        }
    }
}
