package com.starhavensmpcore.seasons;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Month;

public final class SeasonUtil {

    private SeasonUtil() {
    }

    public enum Season {
        WINTER,
        SPRING,
        SUMMER,
        FALL
    }

    public static Season getCurrentSeason() {
        return fromDate(LocalDate.now(ZoneId.systemDefault()));
    }

    public static String getCurrentSeasonLower() {
        return getCurrentSeason().name().toLowerCase();
    }

    public static String getCurrentSeasonCaps() {
        return getCurrentSeason().name();
    }

    public static String getCurrentSeasonFormatted() {
        String lower = getCurrentSeasonLower();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public static Season fromDate(LocalDate date) {
        Month month = date.getMonth();
        switch (month) {
            case DECEMBER:
            case JANUARY:
            case FEBRUARY:
                return Season.WINTER;
            case MARCH:
            case APRIL:
            case MAY:
                return Season.SPRING;
            case JUNE:
            case JULY:
            case AUGUST:
                return Season.SUMMER;
            case SEPTEMBER:
            case OCTOBER:
            case NOVEMBER:
            default:
                return Season.FALL;
        }
    }
}
