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
        // Each release compares against the published target dynamically;
        // there is no version-specific limit baked into the customer app.
        assertTrue(AppControlConfig.compareVersions("1.0.15", "1.0.16") < 0);
        assertTrue(AppControlConfig.compareVersions("1.0.16", "1.0.17") < 0);
        assertTrue(AppControlConfig.compareVersions("1.0.100", "1.0.99") > 0);
    }

    @Test public void updateAndFeatureFlagsParseSafely() throws Exception {
        AppControlConfig value = AppControlConfig.parse(new JSONObject("{\"config\":{\"appStatus\":{\"mode\":\"LIVE\"},\"update\":{\"enabled\":true,\"type\":\"force\",\"latestVersion\":\"2.0.0\",\"minimumVersion\":\"1.5.0\"},\"features\":{\"commercial_services\":{\"enabled\":false,\"audience\":\"users\"}}}}"));
        assertTrue(value.update.enabled);
        assertTrue(value.update.force);
        assertFalse(value.featureEnabled("commercial-services"));
    }

    @Test public void malformedMandatoryUpdateCannotLockCustomers() throws Exception {
        AppControlConfig value = AppControlConfig.parse(new JSONObject(
                "{\"config\":{\"update\":{\"enabled\":true,\"type\":\"force\",\"latestVersion\":\"tomorrow\",\"minimumVersion\":\"999\"}}}"));
        assertFalse(value.update.enabled);
        assertFalse(value.update.force);

        AppControlConfig valid = AppControlConfig.parse(new JSONObject(
                "{\"config\":{\"update\":{\"enabled\":true,\"type\":\"force\",\"latestVersion\":\"1.0.17\",\"minimumVersion\":\"1.0.17\"}}}"));
        assertTrue(valid.update.enabled);
        assertTrue(valid.update.force);
    }

    @Test public void remotePayloadRequiresPublishedConfigAndStaysBounded() throws Exception {
        assertFalse(AppControlConfig.isSafeRemotePayload(new JSONObject()));
        assertTrue(AppControlConfig.isSafeRemotePayload(new JSONObject("{\"config\":{}}")));
        StringBuilder oversized = new StringBuilder("{\"config\":{\"ui\":{\"homeTitle\":\"");
        for (int index = 0; index < AppControlConfig.MAX_REMOTE_CONFIG_CHARS; index++) oversized.append('x');
        oversized.append("\"}}}");
        assertFalse(AppControlConfig.isSafeRemotePayload(new JSONObject(oversized.toString())));
    }

    @Test public void partiallyAvailableKeepsAppOpenAndBookingEnabled() throws Exception {
        AppControlConfig value = AppControlConfig.parse(new JSONObject("{\"config\":{\"appStatus\":{\"mode\":\"PARTIALLY_AVAILABLE\"},\"services\":{\"ac-repair\":{\"status\":\"TEMPORARILY_UNAVAILABLE\"}}}}"));
        assertFalse(value.blocksBooking());
        assertFalse(value.serviceRule("ac", "Home Repair", "AC Repair & Service").bookable());
    }

    @Test public void serviceHighDemandBlocksOnlyThatService() throws Exception {
        AppControlConfig value = AppControlConfig.parse(new JSONObject("{\"config\":{\"appStatus\":{\"mode\":\"LIVE\"},\"services\":{\"ac\":{\"status\":\"HIGH_DEMAND\"},\"electrician\":{\"status\":\"AVAILABLE\"}}}}"));
        assertFalse(value.blocksBooking());
        assertEquals(AppControlConfig.ServiceStatus.HIGH_DEMAND, value.serviceRule("ac", "", "").status);
        assertFalse(value.serviceRule("ac", "", "").bookable());
        assertTrue(value.serviceRule("electrician", "", "").bookable());
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
        for (String mode : new String[]{"LIVE", "PARTIALLY_AVAILABLE", "HIGH_DEMAND", "MAINTENANCE"}) {
            AppControlConfig value = AppControlConfig.parse(new JSONObject(
                    "{\"config\":{\"appStatus\":{\"mode\":\"" + mode + "\"}},"
                            + "\"announcements\":[{\"id\":\"notice-1\",\"title\":\"Notice\",\"priority\":5},{\"title\":\"Missing id\"}],"
                            + "\"banners\":[{\"id\":\"banner-1\",\"message\":\"Safe banner\",\"ctaAction\":\"support\"}]}"));
            assertEquals(AppControlConfig.AppMode.valueOf(mode), value.mode);
            assertEquals(mode.equals("MAINTENANCE") || mode.equals("HIGH_DEMAND"), value.blocksBooking());
            assertEquals(1, value.announcements.size());
            assertEquals(1, value.banners.size());
        }
    }

    @Test public void mediaAllowsOnlyHttpsAndNormalizesServiceKeys() throws Exception {
        AppControlConfig value = AppControlConfig.parse(new JSONObject(
                "{\"config\":{\"media\":{\"hero\":{\"imageUrl\":\"https://cdn.example.com/hero.webp\",\"slides\":{\"AC Repair\":{\"imageUrl\":\"https://cdn.example.com/hero-ac.webp\"}}},"
                        + "\"services\":{\"AC Repair\":{\"imageUrl\":\"https://cdn.example.com/ac.jpg\"},"
                        + "\"plumbing\":{\"imageUrl\":\"http://unsafe.example.com/p.jpg\"}}}}}"));
        assertEquals("https://cdn.example.com/hero.webp", value.media.heroImageUrl);
        assertEquals("https://cdn.example.com/hero-ac.webp", value.media.heroImageForService("ac"));
        assertEquals("https://cdn.example.com/ac.jpg", value.media.imageForService("ac"));
        assertEquals("", value.media.imageForService("plumber"));
    }
}
