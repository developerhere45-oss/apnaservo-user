package com.fixmate.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RainConfirmationPolicyTest {
    private static final long MINUTE = 60_000L;

    @Test public void rainRequiresMeasuredPrecipitationAndRainCode() {
        assertFalse(RainConfirmationPolicy.isMeasuredRain(0d, 61));
        assertFalse(RainConfirmationPolicy.isMeasuredRain(.4d, 3));
        assertTrue(RainConfirmationPolicy.isMeasuredRain(.1d, 61));
    }

    @Test public void customerAdvisoryRequiresConfirmedLiquidRain() {
        assertFalse(RainConfirmationPolicy.isMeasuredRain(.4d, 0d, 0d, 61));
        assertFalse(RainConfirmationPolicy.isMeasuredRain(.19d, .19d, 0d, 61));
        assertFalse(RainConfirmationPolicy.isMeasuredRain(.4d, .4d, 0d, 3));
        assertTrue(RainConfirmationPolicy.isMeasuredRain(.20d, .10d, 0d, 61));
        assertTrue(RainConfirmationPolicy.isMeasuredRain(.35d, 0d, .12d, 80));
    }

    @Test public void continuousRainIsHiddenUntilTenMinutes() {
        RainConfirmationPolicy.Result nineMinutes = RainConfirmationPolicy.observe(true,
                10 * MINUTE, MINUTE, 9 * MINUTE, true, 10 * MINUTE, 150_000L);
        assertFalse(nineMinutes.confirmedRain);
        RainConfirmationPolicy.Result tenMinutes = RainConfirmationPolicy.observe(true,
                11 * MINUTE, MINUTE, 10 * MINUTE, true, 10 * MINUTE, 150_000L);
        assertTrue(tenMinutes.confirmedRain);
    }

    @Test public void clearReadingOrLongGapResetsRainSequence() {
        RainConfirmationPolicy.Result clear = RainConfirmationPolicy.observe(false,
                12 * MINUTE, MINUTE, 11 * MINUTE, true, 10 * MINUTE, 150_000L);
        assertTrue(clear.reset);
        RainConfirmationPolicy.Result gap = RainConfirmationPolicy.observe(true,
                15 * MINUTE, MINUTE, 11 * MINUTE, true, 10 * MINUTE, 150_000L);
        assertFalse(gap.confirmedRain);
        assertTrue(gap.candidateSince == 15 * MINUTE);
    }
}
