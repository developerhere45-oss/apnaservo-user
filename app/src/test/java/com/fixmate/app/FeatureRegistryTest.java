package com.fixmate.app;

import org.junit.Test;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class FeatureRegistryTest {
    @Test public void registryContainsOnlyUniqueRealFeatureIds() {
        Set<String> ids = new HashSet<>();
        for (FeatureRegistry.Entry entry : FeatureRegistry.entries()) {
            assertTrue(entry.id.matches("[a-z][a-z0-9_]*"));
            assertFalse(entry.description.trim().isEmpty());
            assertTrue(ids.add(entry.id));
        }
        assertEquals(10, ids.size());
        assertTrue(ids.contains(FeatureRegistry.SERVICE_BOOKING));
        assertTrue(ids.contains(FeatureRegistry.BOOKING_CHAT));
        assertTrue(ids.contains(FeatureRegistry.PAYMENT));
        assertFalse(ids.contains("profile"));
        assertFalse(ids.contains("legal"));
        assertFalse(ids.contains("authentication"));
        assertFalse(ids.contains("referral"));
        assertFalse(ids.contains("laundry"));
    }

    @Test public void everyRegisteredFeatureCanBeSwitchedOnAndOff() throws Exception {
        for (FeatureRegistry.Entry entry : FeatureRegistry.entries()) {
            JSONObject disabled = new JSONObject("{\"config\":{\"features\":{\""
                    + entry.id + "\":{\"enabled\":false,\"audience\":\"users\"}}}}}");
            assertFalse(entry.id, AppControlConfig.parse(disabled).featureEnabled(entry.id));

            JSONObject enabled = new JSONObject("{\"config\":{\"features\":{\""
                    + entry.id + "\":{\"enabled\":true,\"audience\":\"users\"}}}}}");
            assertTrue(entry.id, AppControlConfig.parse(enabled).featureEnabled(entry.id));
        }
    }

    @Test public void missingOrMalformedFeatureDefaultsToEnabledForOfflineSafety() throws Exception {
        assertTrue(AppControlConfig.DEFAULT.featureEnabled(FeatureRegistry.SERVICE_BOOKING));
        JSONObject malformed = new JSONObject("{\"config\":{\"features\":{\"search\":\"bad\"}}}");
        assertTrue(AppControlConfig.parse(malformed).featureEnabled(FeatureRegistry.SEARCH));
    }
}
