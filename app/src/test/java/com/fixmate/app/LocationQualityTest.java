package com.fixmate.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class LocationQualityTest {
    private static final long NOW = 200_000_000_000L;
    @Test public void acceptsRecentAccurateFix() {
        assertTrue(LocationQuality.valid(26.14, 91.74, 20, NOW - 2_000_000_000L, NOW));
    }
    @Test public void rejectsOldEvenWhenAccurate() {
        assertFalse(LocationQuality.valid(26.14, 91.74, 1, NOW - 61_000_000_000L, NOW));
    }
    @Test public void rejectsApproximateAndMissingAccuracy() {
        for (float accuracy : new float[]{0, -1, 51, 100, 3000, Float.NaN, Float.POSITIVE_INFINITY})
            assertFalse(LocationQuality.valid(26.14, 91.74, accuracy, NOW, NOW));
    }
    @Test public void rejectsInvalidCoordinatesAndTimestamps() {
        assertFalse(LocationQuality.valid(0, 0, 10, NOW, NOW));
        assertFalse(LocationQuality.valid(Double.NaN, 91, 10, NOW, NOW));
        assertFalse(LocationQuality.valid(91, 91, 10, NOW, NOW));
        assertFalse(LocationQuality.valid(26, 181, 10, NOW, NOW));
        assertFalse(LocationQuality.valid(26, 91, 10, NOW + 1, NOW));
        assertFalse(LocationQuality.valid(26, 91, 10, 0, NOW));
    }

    @Test public void keepsPreciseGpsInsteadOfWorseNetworkFix() {
        assertFalse(LocationQuality.betterFix(50, NOW, 8, NOW - 20_000_000_000L));
        assertTrue(LocationQuality.betterFix(10, NOW, 8, NOW - 20_000_000_000L));
    }

    @Test public void rejectsOutOfOrderButAllowsBetterRecentFix() {
        assertFalse(LocationQuality.betterFix(2, NOW - 6_000_000_000L, 10, NOW));
        assertTrue(LocationQuality.betterFix(2, NOW - 2_000_000_000L, 10, NOW));
    }
}
