package com.starhavensmpcore.market;

import com.starhavensmpcore.market.db.DatabaseManager;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public final class DemandMetric {
    private static final BigDecimal HOURS_DAY = new BigDecimal("24");
    private static final BigDecimal HOURS_MONTH = new BigDecimal("720");
    private static final BigDecimal HOURS_YEAR = new BigDecimal("8760");
    private static final BigDecimal WEIGHT_HOUR = new BigDecimal("0.50");
    private static final BigDecimal WEIGHT_DAY = new BigDecimal("0.25");
    private static final BigDecimal WEIGHT_MONTH = new BigDecimal("0.15");
    private static final BigDecimal WEIGHT_YEAR = new BigDecimal("0.10");
    private static final int RATE_SCALE = 8;

    private DemandMetric() {
    }

    public static BigInteger summarize(DatabaseManager.DemandStats stats) {
        if (stats == null) {
            return BigInteger.ZERO;
        }
        BigDecimal hourRate = new BigDecimal(stats.hour.max(BigInteger.ZERO));
        BigDecimal dayRate = toRate(stats.day, HOURS_DAY);
        BigDecimal monthRate = toRate(stats.month, HOURS_MONTH);
        BigDecimal yearRate = toRate(stats.year, HOURS_YEAR);
        BigDecimal weighted = hourRate.multiply(WEIGHT_HOUR)
                .add(dayRate.multiply(WEIGHT_DAY))
                .add(monthRate.multiply(WEIGHT_MONTH))
                .add(yearRate.multiply(WEIGHT_YEAR));
        if (weighted.signum() <= 0) {
            return BigInteger.ZERO;
        }
        return weighted.setScale(0, RoundingMode.HALF_UP).toBigInteger();
    }

    private static BigDecimal toRate(BigInteger value, BigDecimal hours) {
        if (value == null || value.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value).divide(hours, RATE_SCALE, RoundingMode.HALF_UP);
    }
}
