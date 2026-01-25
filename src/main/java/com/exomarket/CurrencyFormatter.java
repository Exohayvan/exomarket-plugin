package com.exomarket;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Locale;

public final class CurrencyFormatter {
    private static final String DEFAULT_SYMBOL = "⚚Ɍ";
    private static volatile String symbol = DEFAULT_SYMBOL;

    private CurrencyFormatter() {
    }

    public static void setSymbol(String newSymbol) {
        if (newSymbol == null || newSymbol.trim().isEmpty()) {
            symbol = DEFAULT_SYMBOL;
        } else {
            symbol = newSymbol;
        }
    }

    public static String getSymbol() {
        return symbol;
    }

    public static String format(double amount) {
        BigDecimal value = BigDecimal.valueOf(amount);
        if (value.signum() < 0) {
            return "-" + format(value.abs().doubleValue());
        }

        BigDecimal thousand = BigDecimal.valueOf(1000d);
        if (value.compareTo(thousand) < 0) {
            return symbol + String.format(Locale.US, "%.2f", value);
        }

        BigInteger whole = value.setScale(0, RoundingMode.DOWN).toBigInteger();
        return symbol + QuantityFormatter.format(whole);
    }
}
