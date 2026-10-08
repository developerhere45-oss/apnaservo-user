package com.fixmate.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ServiceAreaPolicyTest {
    @Test public void guwahatiCenterIsAvailable() {
        assertEquals(ServiceAreaPolicy.State.AVAILABLE,
                ServiceAreaPolicy.evaluate(26.1445d, 91.7362d));
    }

    @Test public void delhiIsUnavailable() {
        assertEquals(ServiceAreaPolicy.State.UNAVAILABLE,
                ServiceAreaPolicy.evaluate(28.6139d, 77.2090d));
    }

    @Test public void invalidCoordinatesAreUnknown() {
        assertEquals(ServiceAreaPolicy.State.UNKNOWN, ServiceAreaPolicy.evaluate(0d, 0d));
        assertTrue(ServiceAreaPolicy.distanceKm(26.1445d, 91.7362d, 26.1445d, 91.7362d) < 0.001d);
    }
}
