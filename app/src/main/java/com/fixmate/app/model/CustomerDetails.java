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
        this.name = clean(name);
        this.phone = clean(phone).replaceAll("\\D", "");
        this.alternatePhone = clean(alternatePhone).replaceAll("\\D", "");
        this.address = clean(address);
        this.issue = clean(issue);
        this.slot = clean(slot);
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
        return value == null ? "" : value.trim();
    }
}
