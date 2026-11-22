package com.fixmate.app.model;

/** A priced cleaning/home-help package shown during booking. */
public final class CleaningService {
    private final String id, name, durationLabel;
    private final int offerPrice, originalPrice;
    private final String[] included, excluded;
    public CleaningService(String id, String name, String durationLabel, int offerPrice, int originalPrice, String[] included, String[] excluded) {
        this.id = id == null ? "" : id.trim(); this.name = name == null ? "" : name.trim();
        this.durationLabel = durationLabel == null ? "" : durationLabel.trim();
        this.offerPrice = Math.max(0, offerPrice); this.originalPrice = Math.max(0, originalPrice);
        this.included = included == null ? new String[0] : included; this.excluded = excluded == null ? new String[0] : excluded;
    }
    public String getId() { return id; } public String getName() { return name; }
    public String getDurationLabel() { return durationLabel; } public int getOfferPrice() { return offerPrice; }
    public int getOriginalPrice() { return originalPrice; } public String[] getIncluded() { return included; }
    public String[] getExcluded() { return excluded; }
}
