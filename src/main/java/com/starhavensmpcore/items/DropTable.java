package com.starhavensmpcore.items;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class DropTable {
    private final String dropItemId;
    private final int minAmount;
    private final int maxAmount;
    private final double fortuneBonusChancePerLevel;
    private final int fortuneBonusAmount;
    private final int fortuneBonusMax;

    public DropTable(String dropItemId, int minAmount, int maxAmount,
                     double fortuneBonusChancePerLevel, int fortuneBonusAmount, int fortuneBonusMax) {
        this.dropItemId = dropItemId;
        this.minAmount = Math.max(0, minAmount);
        this.maxAmount = Math.max(this.minAmount, maxAmount);
        this.fortuneBonusChancePerLevel = Math.max(0.0, fortuneBonusChancePerLevel);
        this.fortuneBonusAmount = Math.max(0, fortuneBonusAmount);
        this.fortuneBonusMax = Math.max(0, fortuneBonusMax);
    }

    public String getDropItemId() {
        return dropItemId;
    }

    public int rollAmount(Random random, int fortuneLevel) {
        Random rng = random == null ? ThreadLocalRandom.current() : random;
        int baseAmount = minAmount == maxAmount ? minAmount : rng.nextInt(maxAmount - minAmount + 1) + minAmount;
        if (fortuneLevel <= 0 || fortuneBonusChancePerLevel <= 0.0 || fortuneBonusAmount <= 0) {
            return baseAmount;
        }
        double chance = fortuneBonusChancePerLevel * fortuneLevel;
        if (rng.nextDouble() < chance) {
            int bonus = fortuneBonusAmount;
            if (fortuneBonusMax > 0) {
                bonus = Math.min(bonus, fortuneBonusMax);
            }
            return baseAmount + bonus;
        }
        return baseAmount;
    }
}
