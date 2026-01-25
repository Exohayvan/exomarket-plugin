package com.exomarket;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public final class QuantityFormatter {
    private static final String[] SUFFIXES = {
            "",
            "K", "M", "B", "T",
            "Qa", "Qi", "Sx", "Sp", "Oc", "No",
            "Dc", "Ud", "Dd", "Td", "Qad", "Qid", "Sxd", "Spd", "Ocd", "Nod",
            "Vg", "Uvg", "Dvg", "Tvg", "Qavg", "Qivg", "Sxvg", "Spvg", "Ocvg", "Novg",
            "Tg", "Utg", "Dtg", "Ttg", "Qatg", "Qitg", "Sxtg", "Sptg", "Octg", "Notg",
            "Qag", "Uqag", "Dqag", "Tqag", "Qaqag", "Qiqag", "Sxqag", "Spqag", "Ocqag", "Noqag",
            "Qig", "Uqig", "Dqig", "Tqig", "Qaqig", "Qiqig", "Sxqig", "Spqig", "Ocqig", "Noqig",
            "Sxg", "Usxg", "Dsxg", "Tsxg", "Qasxg", "Qisxg", "Sxsxg", "Spsxg", "Ocsxg", "Nosxg",
            "Spg", "Uspg", "Dspg", "Tspg", "Qaspg", "Qispg", "Sxspg", "Spspg", "Ocspg", "Nospg",
            "Og", "Uog", "Dog", "Tog", "Qaog", "Qiog", "Sxog", "Spog", "Ocog", "Noog",
            "Ng", "Ung", "Dng", "Tng", "Qang", "Qing", "Sxng", "Spng", "Ocng", "Nong",
            "Ce"
    };
    private static final BigInteger THOUSAND = BigInteger.valueOf(1000L);

    private QuantityFormatter() {
    }

    public static String format(BigInteger value) {
        if (value == null) {
            return "0";
        }
        if (value.signum() == 0) {
            return "0";
        }

        boolean negative = value.signum() < 0;
        BigInteger abs = value.abs();
        if (abs.compareTo(THOUSAND) < 0) {
            return (negative ? "-" : "") + abs.toString();
        }

        int digits = abs.toString().length();
        int scale = (digits - 1) / 3;
        if (scale >= SUFFIXES.length) {
            scale = SUFFIXES.length - 1;
        }

        BigDecimal scaled = new BigDecimal(abs)
                .divide(BigDecimal.TEN.pow(scale * 3), 2, RoundingMode.DOWN)
                .stripTrailingZeros();
        return (negative ? "-" : "") + scaled.toPlainString() + SUFFIXES[scale];
    }
}
