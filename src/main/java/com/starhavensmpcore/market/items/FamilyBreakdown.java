package com.starhavensmpcore.market.items;

import com.starhavensmpcore.items.CustomItemManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FamilyBreakdown {
    public static final BigInteger DIAMOND_BLOCK_RATIO = BigInteger.valueOf(9);
    public static final BigInteger RAW_IRON_BLOCK_RATIO = BigInteger.valueOf(9);
    public static final BigInteger RAW_GOLD_BLOCK_RATIO = BigInteger.valueOf(9);
    public static final BigInteger RAW_COPPER_BLOCK_RATIO = BigInteger.valueOf(9);

    private static CustomItemManager customItemManager;

    private FamilyBreakdown() {
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

        FamilyList.Family family = getFamilyForLarge(stack);
        if (family != null) {
            BigInteger ratio = family.getLargeRatio();
            if (ratio != null && ratio.signum() > 0) {
                BigInteger baseUnits = safeAmount.multiply(ratio);
                if (baseUnits.signum() > 0) {
                    ItemStack base = createItemFromId(family.getBaseId());
                    if (base != null) {
                        entries.add(new SplitEntry(base, baseUnits));
                    }
                }
                return entries;
            }
        }

        entries.add(new SplitEntry(cloneSingle(stack), safeAmount));
        return entries;
    }

    public static boolean isDiamondListing(ItemStack stack) {
        return stack != null && stack.getType() == Material.DIAMOND;
    }

    public static boolean isIronIngotListing(ItemStack stack) {
        return isBaseOfFamily(stack, "iron_ingot");
    }

    public static boolean isCopperIngotListing(ItemStack stack) {
        return isBaseOfFamily(stack, "copper_ingot");
    }

    public static boolean isGoldIngotListing(ItemStack stack) {
        return isBaseOfFamily(stack, "gold_ingot");
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

    public static BigInteger getSmallRatio(ItemStack stack) {
        FamilyList.Family family = getFamilyForSmall(stack);
        return family == null ? null : family.getSmallRatio();
    }

    public static boolean isFamilySmall(ItemStack stack) {
        return getFamilyForSmall(stack) != null;
    }

    public static boolean isFamilyLarge(ItemStack stack) {
        return getFamilyForLarge(stack) != null;
    }

    public static FamilyList.Family getFamilyForBase(ItemStack stack) {
        return getFamilyForRole(stack, FamilyRole.BASE);
    }

    public static FamilyList.Family getFamilyForSmall(ItemStack stack) {
        FamilyList.Family family = getFamilyForRole(stack, FamilyRole.SMALL);
        return family != null && family.hasSmall() ? family : null;
    }

    public static FamilyList.Family getFamilyForLarge(ItemStack stack) {
        FamilyList.Family family = getFamilyForRole(stack, FamilyRole.LARGE);
        return family != null && family.hasLarge() ? family : null;
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
        FamilyList.Family family = getFamilyForBase(stack);
        return family != null && family.getId().equalsIgnoreCase(familyId);
    }

    public static boolean isSameFamilyItem(ItemStack a, ItemStack b) {
        if (a == null || b == null) {
            return false;
        }
        return matchesId(a, getCustomId(b)) || matchesId(b, getCustomId(a))
                || matchesId(a, materialId(b)) || matchesId(b, materialId(a));
    }

    private static FamilyList.Family getFamilyForRole(ItemStack stack, FamilyRole role) {
        if (stack == null) {
            return null;
        }
        for (FamilyList.Family family : FamilyList.getFamilies()) {
            String id = role == FamilyRole.SMALL ? family.getSmallId()
                    : role == FamilyRole.LARGE ? family.getLargeId()
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
            return normalizeId(customId).equalsIgnoreCase(normalizeId(id));
        }
        String normalized = normalizeId(id);
        String materialName = stack.getType().name();
        return materialName.equalsIgnoreCase(normalized);
    }

    private static String materialId(ItemStack stack) {
        return stack == null ? null : stack.getType().name();
    }

    private static String getCustomId(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        if (customItemManager != null) {
            String id = customItemManager.getCustomItemId(stack);
            if (id != null && !id.isEmpty()) {
                return id;
            }
        }
        return getCustomIdFromSerialized(stack);
    }

    private static String normalizeId(String id) {
        String trimmed = id.trim();
        int colon = trimmed.indexOf(':');
        if (colon >= 0 && colon + 1 < trimmed.length()) {
            trimmed = trimmed.substring(colon + 1);
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private static String getCustomIdFromSerialized(ItemStack stack) {
        Map<String, Object> serialized = stack.serialize();
        Object metaObj = serialized.get("meta");
        if (!(metaObj instanceof Map)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> metaMap = (Map<String, Object>) metaObj;
        String id = getCustomIdFromContainer(metaMap.get("PublicBukkitValues"));
        if (id != null) {
            return id;
        }
        id = getCustomIdFromContainer(metaMap.get("PersistentDataContainer"));
        if (id != null) {
            return id;
        }
        return getCustomIdFromContainer(metaMap.get("persistentDataContainer"));
    }

    private static String getCustomIdFromContainer(Object containerObj) {
        if (!(containerObj instanceof Map)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> container = (Map<String, Object>) containerObj;
        for (Map.Entry<String, Object> entry : container.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            if (key.toLowerCase(Locale.ROOT).endsWith(":custom_item")) {
                Object value = entry.getValue();
                if (value instanceof String) {
                    return (String) value;
                }
            }
        }
        return null;
    }

    private enum FamilyRole {
        SMALL,
        BASE,
        LARGE
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
