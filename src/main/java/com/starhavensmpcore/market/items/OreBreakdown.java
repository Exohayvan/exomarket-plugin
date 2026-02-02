package com.starhavensmpcore.market.items;

import com.starhavensmpcore.items.CustomItemManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class OreBreakdown {
    public static final BigInteger DIAMOND_BLOCK_RATIO = BigInteger.valueOf(9);
    public static final BigInteger RAW_IRON_BLOCK_RATIO = BigInteger.valueOf(9);
    public static final BigInteger RAW_GOLD_BLOCK_RATIO = BigInteger.valueOf(9);
    public static final BigInteger RAW_COPPER_BLOCK_RATIO = BigInteger.valueOf(9);

    private static CustomItemManager customItemManager;

    private OreBreakdown() {
    }

    public static void setCustomItemManager(CustomItemManager manager) {
        customItemManager = manager;
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

        OreFamilyList.OreFamily family = getFamilyForBlock(stack);
        if (family != null) {
            BigInteger ingots = safeAmount.multiply(family.getBlockRatio());
            if (ingots.signum() > 0) {
                ItemStack base = createItemFromId(family.getBaseId());
                if (base != null) {
                    entries.add(new SplitEntry(base, ingots));
                }
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
        return isBaseOfFamily(stack, "iron");
    }

    public static boolean isCopperIngotListing(ItemStack stack) {
        return isBaseOfFamily(stack, "copper");
    }

    public static boolean isGoldIngotListing(ItemStack stack) {
        return isBaseOfFamily(stack, "gold");
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

    public static BigInteger getNuggetRatio(ItemStack stack) {
        OreFamilyList.OreFamily family = getFamilyForNugget(stack);
        return family == null ? null : family.getNuggetRatio();
    }

    public static boolean isOreFamilyNugget(ItemStack stack) {
        return getFamilyForNugget(stack) != null;
    }

    public static boolean isOreFamilyBlock(ItemStack stack) {
        return getFamilyForBlock(stack) != null;
    }

    public static OreFamilyList.OreFamily getFamilyForBase(ItemStack stack) {
        return getFamilyForRole(stack, OreFamilyRole.BASE);
    }

    public static OreFamilyList.OreFamily getFamilyForNugget(ItemStack stack) {
        return getFamilyForRole(stack, OreFamilyRole.NUGGET);
    }

    public static OreFamilyList.OreFamily getFamilyForBlock(ItemStack stack) {
        return getFamilyForRole(stack, OreFamilyRole.BLOCK);
    }

    public static ItemStack createItemFromId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        if (customItemManager != null) {
            ItemStack custom = customItemManager.createItem(id, 1);
            if (custom != null) {
                return custom;
            }
        }
        Material material = Material.matchMaterial(id);
        if (material == null) {
            material = Material.matchMaterial("minecraft:" + id.toLowerCase(Locale.ROOT));
        }
        if (material == null) {
            return null;
        }
        return new ItemStack(material);
    }

    public static boolean isBaseOfFamily(ItemStack stack, String familyId) {
        OreFamilyList.OreFamily family = getFamilyForBase(stack);
        return family != null && family.getId().equalsIgnoreCase(familyId);
    }

    public static boolean isSameFamilyItem(ItemStack a, ItemStack b) {
        if (a == null || b == null) {
            return false;
        }
        return matchesId(a, getCustomId(b)) || matchesId(b, getCustomId(a))
                || matchesId(a, materialId(b)) || matchesId(b, materialId(a));
    }

    private static OreFamilyList.OreFamily getFamilyForRole(ItemStack stack, OreFamilyRole role) {
        if (stack == null) {
            return null;
        }
        for (OreFamilyList.OreFamily family : OreFamilyList.getFamilies()) {
            String id = role == OreFamilyRole.NUGGET ? family.getNuggetId()
                    : role == OreFamilyRole.BLOCK ? family.getBlockId()
                    : family.getBaseId();
            if (id == null || id.isEmpty()) {
                continue;
            }
            if (matchesId(stack, id)) {
                return family;
            }
        }
        return null;
    }

    private static boolean matchesId(ItemStack stack, String id) {
        if (stack == null || id == null || id.isEmpty()) {
            return false;
        }
        String customId = getCustomId(stack);
        if (customId != null) {
            return customId.equalsIgnoreCase(id);
        }
        String normalized = normalizeId(id);
        String materialName = stack.getType().name();
        return materialName.equalsIgnoreCase(normalized);
    }

    private static String materialId(ItemStack stack) {
        return stack == null ? null : stack.getType().name();
    }

    private static String getCustomId(ItemStack stack) {
        if (customItemManager == null || stack == null) {
            return null;
        }
        return customItemManager.getCustomItemId(stack);
    }

    private static String normalizeId(String id) {
        String trimmed = id.trim();
        int colon = trimmed.indexOf(':');
        if (colon >= 0 && colon + 1 < trimmed.length()) {
            trimmed = trimmed.substring(colon + 1);
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private enum OreFamilyRole {
        NUGGET,
        BASE,
        BLOCK
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
