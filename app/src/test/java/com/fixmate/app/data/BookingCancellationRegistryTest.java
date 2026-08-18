package com.fixmate.app.data;

import static org.junit.Assert.*;

import org.junit.Test;

public class BookingCancellationRegistryTest {
    @Test public void cancelA_doesNotAffectPendingB() {
        BookingCancellationRegistry state = new BookingCancellationRegistry();
        long a = state.begin("A", "pending");
        assertTrue(state.isInFlight("A"));
        assertFalse(state.isInFlight("B"));
        assertTrue(state.shouldAcceptIncoming("B", "pending"));
        assertTrue(state.complete("A", a));
    }

    @Test public void cancelA_doesNotAffectAcceptedB() {
        BookingCancellationRegistry state = new BookingCancellationRegistry();
        state.begin("A", "pending");
        assertTrue(state.shouldAcceptIncoming("B", "accepted"));
    }

    @Test public void cancellingMiddleBooking_isIsolated() {
        BookingCancellationRegistry state = new BookingCancellationRegistry();
        state.begin("B", "pending");
        assertFalse(state.isInFlight("A"));
        assertTrue(state.isInFlight("B"));
        assertFalse(state.isInFlight("C"));
    }

    @Test public void slowNetwork_staleRealtimeCannotOverwriteOptimisticState() {
        BookingCancellationRegistry state = new BookingCancellationRegistry();
        state.begin("A", "pending");
        assertFalse(state.shouldAcceptIncoming("A", "pending"));
        assertTrue(state.shouldAcceptIncoming("A", "cancelled"));
    }

    @Test public void doubleTap_isRejected() {
        BookingCancellationRegistry state = new BookingCancellationRegistry();
        assertTrue(state.begin("A", "pending") > 0L);
        assertEquals(-1L, state.begin("A", "pending"));
    }

    @Test public void failure_rollsBackOnlyMatchingBooking() {
        BookingCancellationRegistry state = new BookingCancellationRegistry();
        long a = state.begin("A", "accepted");
        long b = state.begin("B", "pending");
        assertEquals("accepted", state.rollback("A", a));
        assertFalse(state.isInFlight("A"));
        assertTrue(state.isInFlight("B"));
        assertEquals("pending", state.rollback("B", b));
    }

    @Test public void staleCallback_afterNewerOperation_isIgnored() {
        BookingCancellationRegistry state = new BookingCancellationRegistry();
        long oldToken = state.begin("A", "pending");
        assertEquals("pending", state.rollback("A", oldToken));
        long newToken = state.begin("A", "accepted");
        assertFalse(state.complete("A", oldToken));
        assertTrue(state.isCurrent("A", newToken));
    }

    @Test public void simultaneousBookings_haveIndependentTokens() {
        BookingCancellationRegistry state = new BookingCancellationRegistry();
        long a = state.begin("A", "pending");
        long b = state.begin("B", "accepted");
        assertNotEquals(a, b);
        assertTrue(state.complete("B", b));
        assertTrue(state.isInFlight("A"));
    }
}
