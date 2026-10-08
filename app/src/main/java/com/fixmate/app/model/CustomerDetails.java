package com.fixmate.app.model;

public final class CustomerDetails {
    private final String name;
    private final String phone;
    private final String alternatePhone;
    private final String address;
    private final String issue;
    private final String slot;

    public CustomerDetails(String name, String phone, String address, String issue, String slot) {
        this(name, phone, "", address, issue, slot);
    }

    public CustomerDetails(String name, String phone, String alternatePhone, String address, String issue, String slot) {
        this.name = clean(name, 120);
        this.phone = clean(phone, 32).replaceAll("\\D", "");
        this.alternatePhone = clean(alternatePhone, 32).replaceAll("\\D", "");
        this.address = clean(address, 600);
        this.issue = clean(issue, 1200);
        this.slot = clean(slot, 120);
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getAlternatePhone() {
        return alternatePhone;
    }

    public String getAddress() {
        return address;
    }

    public String getIssue() {
        return issue;
    }

    public String getSlot() {
        return slot;
    }

    private static String clean(String value) {
        return clean(value, 1200);
    }

    private static String clean(String value, int maximumLength) {
        String clean = value == null ? "" : value.trim();
        return clean.length() <= maximumLength ? clean : clean.substring(0, maximumLength);
    }
}
