package com.myanimewatchledger.application.service;

import com.myanimewatchledger.domain.catalogue.Season;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/** Converts dates to MAL seasons and navigates the seasonal timeline. */
public final class SeasonNavigator {
    private SeasonNavigator() {
    }

    public record SeasonYear(Season season, int year) {
        public SeasonYear {
            Objects.requireNonNull(season, "season");
            if (year < 1900 || year > 9999) {
                throw new IllegalArgumentException("Year is out of range.");
            }
        }
    }

    public static SeasonYear current(Clock clock) {
        return fromDate(LocalDate.now(Objects.requireNonNull(clock, "clock")));
    }

    public static SeasonYear fromDate(LocalDate date) {
        Objects.requireNonNull(date, "date");
        int month = date.getMonthValue();
        Season season = switch (month) {
            case 1, 2, 3 -> Season.WINTER;
            case 4, 5, 6 -> Season.SPRING;
            case 7, 8, 9 -> Season.SUMMER;
            default -> Season.FALL;
        };
        return new SeasonYear(season, date.getYear());
    }

    public static SeasonYear next(SeasonYear current) {
        return shift(current, 1);
    }

    public static SeasonYear previous(SeasonYear current) {
        return shift(current, -1);
    }

    private static SeasonYear shift(SeasonYear current, int amount) {
        Objects.requireNonNull(current, "current");
        int month = switch (current.season()) {
            case WINTER -> 1;
            case SPRING -> 4;
            case SUMMER -> 7;
            case FALL -> 10;
        };
        YearMonth shifted = YearMonth.of(current.year(), month).plusMonths(3L * amount);
        return fromDate(shifted.atDay(1));
    }
}
