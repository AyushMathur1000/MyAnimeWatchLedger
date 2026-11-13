package com.myanimewatchledger.application.service;

import com.myanimewatchledger.domain.catalogue.Season;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class SeasonNavigatorTest {
    @Test
    void mapsCalendarMonthsToMyAnimeListSeasons() {
        assertEquals(Season.WINTER, SeasonNavigator.fromDate(LocalDate.of(2026, 3, 31)).season());
        assertEquals(Season.SPRING, SeasonNavigator.fromDate(LocalDate.of(2026, 4, 1)).season());
        assertEquals(Season.SUMMER, SeasonNavigator.fromDate(LocalDate.of(2026, 7, 1)).season());
        assertEquals(Season.FALL, SeasonNavigator.fromDate(LocalDate.of(2026, 10, 1)).season());
    }

    @Test
    void previousAndNextNavigateAcrossYearBoundary() {
        var winter = new SeasonNavigator.SeasonYear(Season.WINTER, 2026);
        assertEquals(new SeasonNavigator.SeasonYear(Season.FALL, 2025), SeasonNavigator.previous(winter));
        assertEquals(new SeasonNavigator.SeasonYear(Season.SPRING, 2026), SeasonNavigator.next(winter));
    }
}
