package com.starhavensmpcore.items;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class DropTable {
    private final String dropItemId;
    private final int minAmount;
    private final int maxAmount;
    private final boolean applyFortune;

    public DropTable(String dropItemId, int minAmount, int maxAmount) {
        this(dropItemId, minAmount, maxAmount, false);
    }

    public DropTable(String dropItemId, int minAmount, int maxAmount, boolean applyFortune) {
        this.dropItemId = dropItemId;
        this.minAmount = Math.max(0, minAmount);
        this.maxAmount = Math.max(this.minAmount, maxAmount);
        this.applyFortune = applyFortune;
    }

    public String getDropItemId() {
        return dropItemId;
    }

    public int rollAmount(Random random, int fortuneLevel) {
        Random rng = random == null ? ThreadLocalRandom.current() : random;
        int baseAmount = minAmount == maxAmount ? minAmount : rng.nextInt(maxAmount - minAmount + 1) + minAmount;
        if (!applyFortune || fortuneLevel <= 0 || baseAmount <= 0) {
            return baseAmount;
        }
        int extra = rng.nextInt(fortuneLevel + 2) - 1;
        if (extra < 0) {
            extra = 0;
        }
        return baseAmount * (extra + 1);
    }
}
