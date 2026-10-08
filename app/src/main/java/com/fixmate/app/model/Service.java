package com.fixmate.app.model;

public final class Service {
    private final String id;
    private final String shortCode;
    private final String name;
    private final String category;
    private final String description;
    private final int price;
    private final String arrival;
    private final String rating;

    public Service(String id, String shortCode, String name, String category, String description, int price, String arrival, String rating) {
        this.id = clean(id, 80);
        this.shortCode = clean(shortCode, 12);
        this.name = clean(name, 120);
        this.category = clean(category, 80);
        this.description = clean(description, 600);
        this.price = Math.max(0, Math.min(1_000_000, price));
        this.arrival = clean(arrival, 80);
        this.rating = clean(rating, 24);
    }

    public String getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public int getPrice() {
        return price;
    }

    public String getArrival() {
        return arrival;
    }

    public String getRating() {
        return rating;
    }

    private static String clean(String value, int maximumLength) {
        String clean = value == null ? "" : value.trim();
        return clean.length() <= maximumLength ? clean : clean.substring(0, maximumLength);
    }
}
