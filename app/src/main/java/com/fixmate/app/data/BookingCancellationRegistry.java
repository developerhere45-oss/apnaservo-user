package com.fixmate.app.data;

import java.util.HashMap;
import java.util.Map;

/** Booking-ID-scoped cancellation operations. No UI or network dependency. */
public final class BookingCancellationRegistry {
    private final Map<String, Operation> operations = new HashMap<>();
    private long version;

    public synchronized long begin(String bookingId, String previousStatus) {
        if (bookingId == null || bookingId.trim().isEmpty() || operations.containsKey(bookingId)) return -1L;
        long token = ++version;
        operations.put(bookingId, new Operation(token, previousStatus));
        return token;
    }

    public synchronized boolean isInFlight(String bookingId) {
        return bookingId != null && operations.containsKey(bookingId);
    }

    public synchronized boolean isCurrent(String bookingId, long token) {
        Operation operation = operations.get(bookingId);
        return operation != null && operation.token == token;
    }

    public synchronized boolean shouldAcceptIncoming(String bookingId, String normalizedStatus) {
        return !isInFlight(bookingId) || "cancelled".equals(normalizedStatus);
    }

    public synchronized boolean complete(String bookingId, long token) {
        if (!isCurrent(bookingId, token)) return false;
        operations.remove(bookingId);
        return true;
    }

    public synchronized String rollback(String bookingId, long token) {
        Operation operation = operations.get(bookingId);
        if (operation == null || operation.token != token) return null;
        operations.remove(bookingId);
        return operation.previousStatus;
    }

    private static final class Operation {
        final long token;
        final String previousStatus;

        Operation(long token, String previousStatus) {
            this.token = token;
            this.previousStatus = previousStatus == null ? "pending" : previousStatus;
        }
    }
}
