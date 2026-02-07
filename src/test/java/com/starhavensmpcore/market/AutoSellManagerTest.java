package com.starhavensmpcore.market;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import java.util.function.BiPredicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AutoSellManagerTest {

    private static final BiPredicate<ItemStack, ItemStack> MATCHES_TYPE =
            (item, template) -> item != null && template != null && item.getType() == template.getType();

    @Test
    public void countMatchingItemsSumsAmounts() {
        ItemStack diamondThree = new ItemStack(Material.DIAMOND, 3);
        ItemStack dirtTwo = new ItemStack(Material.DIRT, 2);
        ItemStack diamondOne = new ItemStack(Material.DIAMOND, 1);
        ItemStack template = new ItemStack(Material.DIAMOND, 1);

        ItemStack[] contents = new ItemStack[] { diamondThree, dirtTwo, diamondOne };

        assertEquals(4, AutoSellManager.countMatchingItems(contents, template, MATCHES_TYPE));
    }

    @Test
    public void removeMatchingItemsRemovesAcrossStacks() {
        ItemStack diamondTwo = new ItemStack(Material.DIAMOND, 2);
        ItemStack diamondThree = new ItemStack(Material.DIAMOND, 3);
        ItemStack dirtOne = new ItemStack(Material.DIRT, 1);
        ItemStack template = new ItemStack(Material.DIAMOND, 1);

        ItemStack[] contents = new ItemStack[] { diamondTwo, diamondThree, dirtOne };

        AutoSellManager.removeMatchingItems(contents, template, 4, MATCHES_TYPE);

        assertNull(contents[0]);
        assertEquals(1, contents[1].getAmount());
        assertEquals(Material.DIRT, contents[2].getType());
        assertEquals(1, contents[2].getAmount());
    }
}
