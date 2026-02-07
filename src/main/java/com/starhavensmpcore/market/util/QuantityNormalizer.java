package com.starhavensmpcore.market.util;

import java.math.BigInteger;

public final class QuantityNormalizer {
    private QuantityNormalizer() {
    }

    public static BigInteger normalize(BigInteger value) {
        if (value == null) {
            return BigInteger.ZERO;
        }
        return value.max(BigInteger.ZERO);
    }
}
