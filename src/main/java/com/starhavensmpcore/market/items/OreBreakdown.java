package com.starhavensmpcore.market.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public final class OreBreakdown {
    public static final BigInteger DIAMOND_BLOCK_RATIO = BigInteger.valueOf(9);
    public static final BigInteger IRON_NUGGET_RATIO = BigInteger.valueOf(9);
    public static final BigInteger IRON_BLOCK_RATIO = BigInteger.valueOf(9);
    public static final BigInteger COPPER_NUGGET_RATIO = BigInteger.valueOf(9);
    public static final BigInteger COPPER_BLOCK_RATIO = BigInteger.valueOf(9);
    public static final BigInteger GOLD_NUGGET_RATIO = BigInteger.valueOf(9);
    public static final BigInteger GOLD_BLOCK_RATIO = BigInteger.valueOf(9);
    public static final BigInteger RAW_IRON_BLOCK_RATIO = BigInteger.valueOf(9);
    public static final BigInteger RAW_GOLD_BLOCK_RATIO = BigInteger.valueOf(9);
    public static final BigInteger RAW_COPPER_BLOCK_RATIO = BigInteger.valueOf(9);

    private OreBreakdown() {
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

        if (stack.getType() == Material.DIAMOND_BLOCK) {
            BigInteger diamonds = safeAmount.multiply(DIAMOND_BLOCK_RATIO);
            if (diamonds.signum() > 0) {
                entries.add(new SplitEntry(new ItemStack(Material.DIAMOND), diamonds));
            }
            return entries;
        }

        if (stack.getType() == Material.IRON_BLOCK) {
            BigInteger ingots = safeAmount.multiply(IRON_BLOCK_RATIO);
            if (ingots.signum() > 0) {
                entries.add(new SplitEntry(new ItemStack(Material.IRON_INGOT), ingots));
            }
            return entries;
        }

        if (stack.getType() == Material.COPPER_BLOCK) {
            BigInteger ingots = safeAmount.multiply(COPPER_BLOCK_RATIO);
            if (ingots.signum() > 0) {
                entries.add(new SplitEntry(new ItemStack(Material.COPPER_INGOT), ingots));
            }
            return entries;
        }

        if (stack.getType() == Material.GOLD_BLOCK) {
            BigInteger ingots = safeAmount.multiply(GOLD_BLOCK_RATIO);
            if (ingots.signum() > 0) {
                entries.add(new SplitEntry(new ItemStack(Material.GOLD_INGOT), ingots));
            }
            return entries;
        }

        if (stack.getType() == Material.RAW_IRON_BLOCK) {
            BigInteger rawIron = safeAmount.multiply(RAW_IRON_BLOCK_RATIO);
            if (rawIron.signum() > 0) {
                entries.add(new SplitEntry(new ItemStack(Material.RAW_IRON), rawIron));
            }
            return entries;
        }

        if (stack.getType() == Material.RAW_GOLD_BLOCK) {
            BigInteger rawGold = safeAmount.multiply(RAW_GOLD_BLOCK_RATIO);
            if (rawGold.signum() > 0) {
                entries.add(new SplitEntry(new ItemStack(Material.RAW_GOLD), rawGold));
            }
            return entries;
        }

        if (stack.getType() == Material.RAW_COPPER_BLOCK) {
            BigInteger rawCopper = safeAmount.multiply(RAW_COPPER_BLOCK_RATIO);
            if (rawCopper.signum() > 0) {
                entries.add(new SplitEntry(new ItemStack(Material.RAW_COPPER), rawCopper));
            }
            return entries;
        }

        entries.add(new SplitEntry(cloneSingle(stack), safeAmount));
        return entries;
    }

    public static boolean isDiamondListing(ItemStack stack) {
        return stack != null && stack.getType() == Material.DIAMOND;
    }

    public static boolean isIronIngotListing(ItemStack stack) {
        return stack != null && stack.getType() == Material.IRON_INGOT;
    }

    public static boolean isCopperIngotListing(ItemStack stack) {
        return stack != null && stack.getType() == Material.COPPER_INGOT;
    }

    public static boolean isGoldIngotListing(ItemStack stack) {
        return stack != null && stack.getType() == Material.GOLD_INGOT;
    }

    public static boolean isRawIronListing(ItemStack stack) {
        return stack != null && stack.getType() == Material.RAW_IRON;
    }

    public static boolean isRawGoldListing(ItemStack stack) {
        return stack != null && stack.getType() == Material.RAW_GOLD;
    }

    public static boolean isRawCopperListing(ItemStack stack) {
        return stack != null && stack.getType() == Material.RAW_COPPER;
    }

    public static BigInteger getNuggetRatio(Material nuggetType) {
        if (nuggetType == Material.IRON_NUGGET) {
            return IRON_NUGGET_RATIO;
        }
        if (nuggetType == Material.GOLD_NUGGET) {
            return GOLD_NUGGET_RATIO;
        }
        if (isCopperNugget(nuggetType)) {
            return COPPER_NUGGET_RATIO;
        }
        return null;
    }

    public static Material getCopperNuggetMaterial() {
        Material direct = Material.matchMaterial("COPPER_NUGGET");
        if (direct != null) {
            return direct;
        }
        return Material.matchMaterial("minecraft:copper_nugget");
    }

    public static boolean isCopperNugget(Material material) {
        return material != null && material.name().equals("COPPER_NUGGET");
    }

    private static ItemStack cloneSingle(ItemStack stack) {
        ItemStack clone = stack.clone();
        clone.setAmount(1);
        return clone;
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
