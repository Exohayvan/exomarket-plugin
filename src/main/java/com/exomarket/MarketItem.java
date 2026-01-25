package com.exomarket;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class MarketItem {
    private final ItemStack itemStack;
    private final String itemData;
    private int quantity;
    private double price;
    private final String sellerUUID;

    public MarketItem(ItemStack itemStack, int quantity, double price, String sellerUUID) {
        ItemStack sanitized = ItemSanitizer.sanitize(itemStack);
        this.itemStack = sanitized;
        this.itemData = ItemSanitizer.serializeToString(sanitized);
        this.quantity = quantity;
        this.price = price;
        this.sellerUUID = sellerUUID;
    }

    public MarketItem(ItemStack itemStack, String itemData, int quantity, double price, String sellerUUID) {
        ItemStack sanitized = ItemSanitizer.sanitize(itemStack);
        this.itemStack = sanitized;
        this.itemData = itemData != null && !itemData.isEmpty() ? itemData : ItemSanitizer.serializeToString(sanitized);
        this.quantity = quantity;
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

    public int getQuantity() {
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

    public void addQuantity(int amount) {
        this.quantity += amount;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setPrice(double price) {
        this.price = price;
    }
}
