package com.starhavensmpcore.market.items;

import com.starhavensmpcore.core.StarhavenSMPCore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class DurabilityQueue {
    private static final String QUEUE_KEY = "durability_queue";

    private DurabilityQueue() {
    }

    public static boolean isQueueCandidate(ItemStack stack) {
        return getMaxDurability(stack) > 0;
    }

    public static int getMaxDurability(ItemStack stack) {
        if (stack == null) {
            return 0;
        }
        Material type = stack.getType();
        if (type == null) {
            return 0;
        }
        return Math.max(0, type.getMaxDurability());
    }

    public static int getRemainingDurability(ItemStack stack) {
        int max = getMaxDurability(stack);
        if (max <= 0 || stack == null) {
            return 0;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof Damageable) {
            int damage = Math.max(0, ((Damageable) meta).getDamage());
            return Math.max(0, max - damage);
        }
        return max;
    }

    public static boolean isQueueItem(StarhavenSMPCore plugin, ItemStack stack) {
        if (plugin == null || stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        NamespacedKey key = new NamespacedKey(plugin, QUEUE_KEY);
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public static ItemStack createQueueTemplate(StarhavenSMPCore plugin, ItemStack stack) {
        ItemStack base = createPristineTemplate(stack);
        if (plugin == null || base == null || base.getType() == Material.AIR) {
            return base;
        }
        ItemMeta meta = base.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, QUEUE_KEY), PersistentDataType.BYTE, (byte) 1);
            base.setItemMeta(meta);
        }
        return ItemSanitizer.sanitizeForMarket(base);
    }

    public static ItemStack createListingTemplate(ItemStack stack) {
        return createPristineTemplate(stack);
    }

    public static ItemStack stripQueueMarker(StarhavenSMPCore plugin, ItemStack stack) {
        if (plugin == null || stack == null || stack.getType() == Material.AIR) {
            return stack;
        }
        ItemStack clone = stack.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().remove(new NamespacedKey(plugin, QUEUE_KEY));
            clone.setItemMeta(meta);
        }
        return ItemSanitizer.sanitizeForMarket(clone);
    }

    public static ItemStack applyRemainingDurability(ItemStack stack, int remaining) {
        int max = getMaxDurability(stack);
        if (max <= 0 || stack == null || stack.getType() == Material.AIR) {
            return stack;
        }
        int safeRemaining = Math.max(0, Math.min(remaining, max));
        int damage = Math.max(0, max - safeRemaining);
        ItemStack clone = stack.clone();
        clone.setAmount(1);
        ItemMeta meta = clone.getItemMeta();
        if (meta instanceof Damageable) {
            ((Damageable) meta).setDamage(damage);
            clone.setItemMeta(meta);
        }
        return clone;
    }

    private static ItemStack createPristineTemplate(ItemStack stack) {
        if (stack == null) {
            return new ItemStack(Material.AIR);
        }
        ItemStack clone = stack.clone();
        clone.setAmount(1);
        ItemMeta meta = clone.getItemMeta();
        if (meta instanceof Damageable) {
            Damageable damageable = (Damageable) meta;
            if (damageable.getDamage() != 0) {
                damageable.setDamage(0);
            }
            clone.setItemMeta(meta);
        }
        return ItemSanitizer.sanitizeForMarket(clone);
    }
}
