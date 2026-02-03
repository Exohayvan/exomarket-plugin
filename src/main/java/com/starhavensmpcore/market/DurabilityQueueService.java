package com.starhavensmpcore.market;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.market.db.DatabaseManager;
import com.starhavensmpcore.market.items.DurabilityQueue;
import org.bukkit.inventory.ItemStack;

import java.math.BigInteger;

public final class DurabilityQueueService {

    private DurabilityQueueService() {
    }

    public static Result queueDurability(StarhavenSMPCore plugin,
                                         DatabaseManager databaseManager,
                                         String sellerUuid,
                                         ItemStack stack,
                                         BigInteger quantity) {
        if (plugin == null || databaseManager == null || sellerUuid == null || sellerUuid.isEmpty()) {
            return Result.notQueued();
        }
        if (stack == null || quantity == null || quantity.signum() <= 0) {
            return Result.notQueued();
        }
        if (!DurabilityQueue.isQueueCandidate(stack)) {
            return Result.notQueued();
        }
        int maxDurability = DurabilityQueue.getMaxDurability(stack);
        if (maxDurability <= 0) {
            return Result.notQueued();
        }
        int remaining = DurabilityQueue.getRemainingDurability(stack);
        if (remaining >= maxDurability) {
            return Result.notQueued();
        }

        BigInteger pointsAdded = BigInteger.valueOf(remaining).multiply(quantity);
        if (pointsAdded.signum() <= 0) {
            return Result.queued(pointsAdded, BigInteger.ZERO, BigInteger.ZERO, maxDurability);
        }

        ItemStack queueTemplate = DurabilityQueue.createQueueTemplate(plugin, stack);
        MarketItem existingQueue = databaseManager.getMarketItem(queueTemplate, sellerUuid);
        BigInteger totalPoints = pointsAdded;
        if (existingQueue != null) {
            totalPoints = totalPoints.add(existingQueue.getQuantity());
        }

        BigInteger maxPerItem = BigInteger.valueOf(maxDurability);
        BigInteger fullItems = totalPoints.divide(maxPerItem);
        BigInteger remainder = totalPoints.remainder(maxPerItem);

        if (existingQueue != null) {
            if (remainder.signum() <= 0) {
                databaseManager.removeMarketItem(existingQueue);
            } else {
                existingQueue.setQuantity(remainder);
                databaseManager.updateMarketItem(existingQueue);
            }
        } else if (remainder.signum() > 0) {
            MarketItem newQueue = new MarketItem(queueTemplate, remainder, 0, sellerUuid);
            databaseManager.addMarketItem(newQueue);
        }

        if (fullItems.signum() > 0) {
            ItemStack listingTemplate = DurabilityQueue.createListingTemplate(stack);
            MarketItem existingListing = databaseManager.getMarketItem(listingTemplate, sellerUuid);
            if (existingListing == null) {
                MarketItem newListing = new MarketItem(listingTemplate, fullItems, 0, sellerUuid);
                databaseManager.addMarketItem(newListing);
            } else {
                existingListing.addQuantity(fullItems);
                databaseManager.updateMarketItem(existingListing);
            }
        }

        return Result.queued(pointsAdded, remainder, fullItems, maxDurability);
    }

    public static final class Result {
        private static final Result NOT_QUEUED = new Result(false, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, 0);

        private final boolean queued;
        private final BigInteger pointsAdded;
        private final BigInteger remainder;
        private final BigInteger fullItems;
        private final int maxDurability;

        private Result(boolean queued, BigInteger pointsAdded, BigInteger remainder, BigInteger fullItems, int maxDurability) {
            this.queued = queued;
            this.pointsAdded = pointsAdded == null ? BigInteger.ZERO : pointsAdded;
            this.remainder = remainder == null ? BigInteger.ZERO : remainder;
            this.fullItems = fullItems == null ? BigInteger.ZERO : fullItems;
            this.maxDurability = Math.max(0, maxDurability);
        }

        public static Result notQueued() {
            return NOT_QUEUED;
        }

        public static Result queued(BigInteger pointsAdded, BigInteger remainder, BigInteger fullItems, int maxDurability) {
            return new Result(true, pointsAdded, remainder, fullItems, maxDurability);
        }

        public boolean isQueued() {
            return queued;
        }

        public BigInteger getPointsAdded() {
            return pointsAdded;
        }

        public BigInteger getRemainder() {
            return remainder;
        }

        public BigInteger getFullItems() {
            return fullItems;
        }

        public int getMaxDurability() {
            return maxDurability;
        }
    }
}
