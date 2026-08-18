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
        this.id = id;
        this.shortCode = shortCode;
        this.name = name;
        this.category = category;
        this.description = description;
        this.price = price;
        this.arrival = arrival;
        this.rating = rating;
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
}
