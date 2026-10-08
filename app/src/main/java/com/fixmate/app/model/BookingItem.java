package com.fixmate.app.model;

import org.json.JSONException;
import org.json.JSONObject;

public final class BookingItem {
    private final Service service;
    private int quantity;

    public BookingItem(Service service) {
        this(service, 1);
    }

    public BookingItem(Service service, int quantity) {
        this.service = service;
        this.quantity = Math.max(1, Math.min(99, quantity));
    }

    public Service getService() {
        return service;
    }

    public int getQuantity() {
        return quantity;
    }

    public void increase() {
        quantity++;
    }

    public void decrease() {
        quantity--;
    }

    public int getLineTotal() {
        if (service == null) return 0;
        long total = (long) service.getPrice() * quantity;
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("serviceId", service.getId());
        object.put("serviceName", service.getName());
        object.put("quantity", quantity);
        object.put("unitPrice", service.getPrice());
        object.put("lineTotal", getLineTotal());
        return object;
    }
}
