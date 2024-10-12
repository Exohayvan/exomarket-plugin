package com.exomarket;

import org.bukkit.Material;

public class MarketItem {
    private Material type;
    private int quantity;
    private double price;
    private String sellerUUID;

    public MarketItem(Material type, int quantity, double price, String sellerUUID) {
        this.type = type;
        this.quantity = quantity;
        this.price = price;
        this.sellerUUID = sellerUUID;
    }

    public Material getType() {
        return type;
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
        return this.type.toString(); 
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