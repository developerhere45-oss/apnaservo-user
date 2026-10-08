package com.fixmate.app.model;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RemoteModelSafetyTest {
    @Test public void customerAndServiceFieldsAreBounded() {
        StringBuilder source = new StringBuilder();
        for (int index = 0; index < 2000; index++) source.append('x');
        CustomerDetails customer = new CustomerDetails(source.toString(), "1234567890", "", source.toString(), source.toString(), source.toString());
        Service service = new Service(source.toString(), source.toString(), source.toString(), source.toString(), source.toString(), Integer.MAX_VALUE, source.toString(), source.toString());
        assertTrue(customer.getName().length() <= 120);
        assertTrue(customer.getAddress().length() <= 600);
        assertTrue(customer.getIssue().length() <= 1200);
        assertTrue(service.getName().length() <= 120);
        assertEquals(1_000_000, service.getPrice());
    }

    @Test public void bookingQuantitiesAndTotalsCannotOverflow() {
        Service service = new Service("id", "ID", "Service", "Category", "", 1_000_000, "", "");
        BookingItem item = new BookingItem(service, Integer.MAX_VALUE);
        assertEquals(99, item.getQuantity());
        assertEquals(99_000_000, item.getLineTotal());
    }

    @Test public void bookingGeneratesCanonicalIdOnceAndPersistsItAcrossRetries() throws Exception {
        CustomerDetails customer = new CustomerDetails("Test", "9876543210", "Address", "Issue", "");
        Service service = new Service("ac", "AC", "AC Repair", "Repair", "", 499, "", "");
        Booking pending = new Booking(customer, Collections.singletonList(new BookingItem(service, 1)));

        assertTrue(pending.getId().matches("^ASB-\\d{8}-[A-Z0-9]{8}$"));
        assertEquals(pending.getId(), pending.withStatus("assigned").getId());
        assertEquals(pending.getId(), Booking.fromJson(pending.toJson()).getId());
    }
}
