package com.starhavensmpcore.market;

import com.starhavensmpcore.market.items.ItemDisplayNameFormatter;
import com.starhavensmpcore.market.items.ItemSanitizer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.math.BigInteger;

public class MarketItem {
    private final ItemStack itemStack;
    private final String itemData;
    private BigInteger quantity;
    private double price;
    private final String sellerUUID;

    public MarketItem(ItemStack itemStack, int quantity, double price, String sellerUUID) {
        this(itemStack, BigInteger.valueOf(quantity), price, sellerUUID);
    }

    public MarketItem(ItemStack itemStack, String itemData, int quantity, double price, String sellerUUID) {
        this(itemStack, itemData, BigInteger.valueOf(quantity), price, sellerUUID);
    }

    public MarketItem(ItemStack itemStack, BigInteger quantity, double price, String sellerUUID) {
        ItemStack sanitized = ItemSanitizer.sanitize(itemStack);
        this.itemStack = sanitized;
        this.itemData = ItemSanitizer.serializeToString(sanitized);
        this.quantity = normalizeQuantity(quantity);
        this.price = price;
        this.sellerUUID = sellerUUID;
    }

    public MarketItem(ItemStack itemStack, String itemData, BigInteger quantity, double price, String sellerUUID) {
        ItemStack sanitized = ItemSanitizer.sanitize(itemStack);
        this.itemStack = sanitized;
        this.itemData = itemData != null && !itemData.isEmpty() ? itemData : ItemSanitizer.serializeToString(sanitized);
        this.quantity = normalizeQuantity(quantity);
        this.price = price;
        this.sellerUUID = sellerUUID;
    }

    public Material getType() {
        return itemStack.getType();
    }

    public ItemStack getItemStack() {
        return itemStack.clone();
    }

    public String getItemData() {
        return itemData;
    }

    public BigInteger getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }

    public String getSellerUUID() {
        return sellerUUID;
    }

    public String getDisplayName() {
        return ItemDisplayNameFormatter.format(itemStack);
    }

    public void addQuantity(BigInteger amount) {
        this.quantity = normalizeQuantity(this.quantity.add(normalizeQuantity(amount)));
    }

    public void setQuantity(BigInteger quantity) {
        this.quantity = normalizeQuantity(quantity);
    }

    public void setPrice(double price) {
        this.price = price;
    }

    private BigInteger normalizeQuantity(BigInteger value) {
        if (value == null) {
            return BigInteger.ZERO;
        }
        return value.max(BigInteger.ZERO);
    }
}
