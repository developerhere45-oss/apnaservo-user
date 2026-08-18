package com.fixmate.app;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class AppControlConfigTest {
    @Test public void malformedAndUnknownValuesUseSafeDefaults() throws Exception {
        AppControlConfig value = AppControlConfig.parse(new JSONObject("{\"config\":{\"appStatus\":{\"mode\":\"ALIEN\"},\"services\":{\"ac\":{\"status\":\"BAD\"}}}}"));
        assertEquals(AppControlConfig.AppMode.LIVE, value.mode);
        assertTrue(value.serviceRule("ac", "", "").bookable());
    }

    @Test public void maintenanceBlocksBookingAndServiceRulesParse() throws Exception {
        AppControlConfig value = AppControlConfig.parse(new JSONObject("{\"version\":\"9\",\"config\":{\"appStatus\":{\"mode\":\"MAINTENANCE\"},\"services\":{\"AC\":{\"status\":\"PREPARING\",\"message\":\"Temporarily unavailable\"}}}}"));
        assertTrue(value.blocksBooking());
        assertFalse(value.serviceRule("ac", "", "").bookable());
    }

    @Test public void semanticVersionComparisonIsNumeric() {
        assertTrue(AppControlConfig.compareVersions("1.0.10", "1.0.9") > 0);
        assertEquals(0, AppControlConfig.compareVersions("v2.0", "2.0.0"));
        assertTrue(AppControlConfig.compareVersions("1.2", "2") < 0);
    }

    @Test public void updateAndFeatureFlagsParseSafely() throws Exception {
        AppControlConfig value = AppControlConfig.parse(new JSONObject("{\"config\":{\"appStatus\":{\"mode\":\"LIVE\"},\"update\":{\"enabled\":true,\"type\":\"force\",\"latestVersion\":\"2.0.0\",\"minimumVersion\":\"1.5.0\"},\"features\":{\"commercial_services\":{\"enabled\":false,\"audience\":\"users\"}}}}"));
        assertTrue(value.update.enabled);
        assertTrue(value.update.force);
        assertFalse(value.featureEnabled("commercial-services"));
    }

    @Test public void partiallyUnavailableKeepsAppOpenButGatesService() throws Exception {
        AppControlConfig value = AppControlConfig.parse(new JSONObject("{\"config\":{\"appStatus\":{\"mode\":\"PARTIALLY_UNAVAILABLE\"},\"services\":{\"ac-repair\":{\"status\":\"TEMPORARILY_UNAVAILABLE\"}}}}"));
        assertFalse(value.blocksBooking());
        assertFalse(value.serviceRule("ac", "Home Repair", "AC Repair & Service").bookable());
    }

    @Test public void launchRemovalImmediatelyRepresentsLiveBookingState() throws Exception {
        AppControlConfig value = AppControlConfig.parse(new JSONObject("{\"config\":{\"appStatus\":{\"mode\":\"LIVE\"},\"launch\":{\"enabled\":false}}}"));
        assertFalse(value.blocksBooking());
        assertFalse(value.launch.enabled);
    }

    @Test public void updateUrlAllowsOnlyThisAppOnOfficialPlayStore() {
        assertTrue(AppControlConfig.isAllowedPlayStoreUrl(
                "https://play.google.com/store/apps/details?id=com.apnaservo.user", "com.apnaservo.user"));
        assertTrue(AppControlConfig.isAllowedPlayStoreUrl(
                "market://details?id=com.apnaservo.user", "com.apnaservo.user"));
        assertFalse(AppControlConfig.isAllowedPlayStoreUrl(
                "https://example.com/store?id=com.apnaservo.user", "com.apnaservo.user"));
        assertFalse(AppControlConfig.isAllowedPlayStoreUrl(
                "https://play.google.com/store/apps/details?id=com.attacker.app", "com.apnaservo.user"));
    }

    @Test public void supportedModesAndDynamicContentAreParsedSafely() throws Exception {
        for (String mode : new String[]{"LIVE", "PARTIALLY_UNAVAILABLE", "MAINTENANCE", "TEMPORARILY_UNAVAILABLE"}) {
            AppControlConfig value = AppControlConfig.parse(new JSONObject(
                    "{\"config\":{\"appStatus\":{\"mode\":\"" + mode + "\"}},"
                            + "\"announcements\":[{\"id\":\"notice-1\",\"title\":\"Notice\",\"priority\":5},{\"title\":\"Missing id\"}],"
                            + "\"banners\":[{\"id\":\"banner-1\",\"message\":\"Safe banner\",\"ctaAction\":\"support\"}]}"));
            assertEquals(AppControlConfig.AppMode.valueOf(mode), value.mode);
            assertEquals(mode.equals("MAINTENANCE") || mode.equals("TEMPORARILY_UNAVAILABLE"), value.blocksBooking());
            assertEquals(1, value.announcements.size());
            assertEquals(1, value.banners.size());
        }
    }
}
