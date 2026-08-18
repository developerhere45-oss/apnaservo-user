package com.fixmate.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Authoritative registry of remotely controllable customer capabilities that really exist in
 * the Android app. Authentication, legal/account deletion, profile access, safety support,
 * calling/tracking and individual service categories are intentionally not remotely gated.
 */
final class FeatureRegistry {
    static final String HOME_BANNERS = "home_banners";
    static final String BOOKING_HISTORY = "booking_history";
    static final String COMMERCIAL_SERVICES = "commercial_services";
    static final String SERVICE_BOOKING = "service_booking";
    static final String SEARCH = "search";
    static final String NOTIFICATIONS = "notifications";
    static final String BOOKING_CHAT = "booking_chat";
    static final String ADDRESS_MANAGEMENT = "address_management";
    static final String PAYMENT = "payment";
    static final String REVIEWS = "reviews";

    private static final List<Entry> ENTRIES = Collections.unmodifiableList(Arrays.asList(
            entry("home_banners", "Published home banners and announcements", "home"),
            entry("booking_history", "Customer booking history list", "bookings"),
            entry("commercial_services", "Commercial-services discovery and enquiry", "home"),
            entry("service_booking", "Creation of new customer service bookings", "booking"),
            entry("search", "Service search and discovery sheet", "home"),
            entry("notifications", "In-app customer notification inbox", "notifications"),
            entry("booking_chat", "Customer-to-assigned-partner booking chat", "booking"),
            entry("address_management", "Saved service-address management", "profile"),
            entry("payment", "Customer quote approval and payment submission UI", "booking"),
            entry("reviews", "Completed-service rating and review submission", "booking")
    ));

    private FeatureRegistry() {}

    static List<Entry> entries() { return ENTRIES; }

    static boolean contains(String id) {
        if (id == null) return false;
        for (Entry entry : ENTRIES) if (entry.id.equals(id)) return true;
        return false;
    }

    static final class Entry {
        final String id;
        final String description;
        final String surface;
        Entry(String id, String description, String surface) {
            this.id = id;
            this.description = description;
            this.surface = surface;
        }
    }

    private static Entry entry(String id, String description, String surface) {
        return new Entry(id, description, surface);
    }
}
