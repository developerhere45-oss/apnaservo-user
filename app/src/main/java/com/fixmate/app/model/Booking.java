package com.fixmate.app.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

public final class Booking {
    private final String id;
    private final long createdAt;
    private final CustomerDetails customerDetails;
    private final List<BookingItem> items;
    private final int subtotal;
    private final int platformFee;
    private final int total;
    private final String status;

    public Booking(CustomerDetails customerDetails, List<BookingItem> items) {
        this(createBookingId(), System.currentTimeMillis(), customerDetails, copyItems(items), "Technician assignment pending");
    }

    private Booking(String id, long createdAt, CustomerDetails customerDetails, List<BookingItem> items, String status) {
        this.id = id;
        this.createdAt = createdAt;
        this.customerDetails = customerDetails;
        this.items = items;
        this.subtotal = calculateSubtotal(items);
        this.platformFee = subtotal > 0 && subtotal < 1499 ? 49 : 0;
        this.total = subtotal + platformFee;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getCreatedAtLabel() {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(new Date(createdAt));
    }

    public long getCreatedAtMillis() {
        return createdAt;
    }

    public CustomerDetails getCustomerDetails() {
        return customerDetails;
    }

    public List<BookingItem> getItems() {
        return new ArrayList<>(items);
    }

    public int getSubtotal() {
        return subtotal;
    }

    public int getPlatformFee() {
        return platformFee;
    }

    public int getTotal() {
        return total;
    }

    public String getStatus() {
        return status;
    }

    public Booking withStatus(String nextStatus) {
        String safeStatus = nextStatus == null || nextStatus.trim().isEmpty() ? status : nextStatus.trim();
        return new Booking(id, createdAt, customerDetails, copyItems(items), safeStatus);
    }

    public Booking withCustomerDetails(CustomerDetails nextDetails) {
        return new Booking(id, createdAt, nextDetails == null ? customerDetails : nextDetails, copyItems(items), status);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("createdAt", createdAt);
        object.put("status", status);
        object.put("name", customerDetails.getName());
        object.put("phone", customerDetails.getPhone());
        object.put("alternatePhone", customerDetails.getAlternatePhone());
        object.put("address", customerDetails.getAddress());
        object.put("issue", customerDetails.getIssue());
        object.put("slot", customerDetails.getSlot());
        object.put("subtotal", subtotal);
        object.put("platformFee", platformFee);
        object.put("total", total);

        JSONArray itemArray = new JSONArray();
        for (BookingItem item : items) {
            itemArray.put(item.toJson());
        }
        object.put("items", itemArray);
        return object;
    }

    public static Booking fromJson(JSONObject object) throws JSONException {
        CustomerDetails customer = new CustomerDetails(
                object.optString("name"),
                object.optString("phone"),
                object.optString("alternatePhone"),
                object.optString("address"),
                object.optString("issue"),
                object.optString("slot")
        );

        List<BookingItem> parsedItems = new ArrayList<>();
        JSONArray itemArray = object.optJSONArray("items");
        if (itemArray != null) {
            for (int index = 0; index < itemArray.length(); index++) {
                JSONObject item = itemArray.getJSONObject(index);
                Service service = new Service(
                        item.optString("serviceId"),
                        item.optString("serviceName").substring(0, Math.min(2, item.optString("serviceName").length())).toUpperCase(Locale.ROOT),
                        item.optString("serviceName"),
                        "Service",
                        "",
                        item.optInt("unitPrice"),
                        "",
                        ""
                );
                parsedItems.add(new BookingItem(service, item.optInt("quantity", 1)));
            }
        }

        return new Booking(
                object.optString("id"),
                object.optLong("createdAt"),
                customer,
                parsedItems,
                object.optString("status", "Technician assignment pending")
        );
    }

    public static Booking fromBackendJson(JSONObject object) throws JSONException {
        String bookingCode = object.optString("publicId", object.optString("bookingCode",
                object.optString("bookingId", object.optString("_id", ""))));
        String serviceName = object.optString("serviceName", "Service");
        if (serviceName.trim().isEmpty()) {
            serviceName = "Service";
        }
        String serviceId = object.optString("serviceCategory", "service");
        int amount = object.optInt("finalAmount", object.optInt("price", 0));
        Service service = new Service(
                serviceId,
                serviceName.substring(0, Math.min(2, serviceName.length())).toUpperCase(Locale.ROOT),
                serviceName,
                "Service",
                "",
                Math.max(0, amount),
                "",
                ""
        );
        List<BookingItem> backendItems = new ArrayList<>();
        backendItems.add(new BookingItem(service, 1));
        String customerPhone = object.optString("userPhone", "");
        if (customerPhone.isEmpty()) customerPhone = object.optString("customerPhone", "");
        if (customerPhone.isEmpty()) customerPhone = object.optString("primaryPhone", "");
        if (customerPhone.isEmpty()) customerPhone = object.optString("phone", "");
        CustomerDetails customer = new CustomerDetails(
                object.optString("userName", "Customer"),
                customerPhone,
                object.optString("alternatePhone", ""),
                object.optString("address", ""),
                object.optString("issue", "Service request"),
                object.optString("slot", "")
        );
        long createdAt = parseBackendCreatedAtMillis(object);
        if (createdAt <= 0L) {
            createdAt = System.currentTimeMillis();
        }
        return new Booking(
                bookingCode,
                createdAt,
                customer,
                backendItems,
                object.optString("status", "pending")
        );
    }

    private static long parseBackendCreatedAtMillis(JSONObject object) {
        if (object == null) {
            return 0L;
        }
        long millis = object.optLong("createdAtMillis", 0L);
        if (millis > 0L) {
            return millis;
        }
        String raw = object.optString("createdAt", "");
        if (raw.trim().isEmpty()) {
            raw = object.optString("bookedAt", "");
        }
        if (raw.trim().isEmpty()) {
            return 0L;
        }
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd HH:mm:ss"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                if (pattern.endsWith("'Z'")) {
                    format.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                Date parsed = format.parse(raw);
                if (parsed != null) {
                    return parsed.getTime();
                }
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }

    public String itemSummary() {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < items.size(); index++) {
            BookingItem item = items.get(index);
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(item.getService().getName()).append(" x ").append(item.getQuantity());
        }
        return builder.toString();
    }

    private static int calculateSubtotal(List<BookingItem> items) {
        int sum = 0;
        for (BookingItem item : items) {
            sum += item.getLineTotal();
        }
        return sum;
    }

    private static List<BookingItem> copyItems(List<BookingItem> source) {
        List<BookingItem> copy = new ArrayList<>();
        for (BookingItem item : source) {
            copy.add(new BookingItem(item.getService(), item.getQuantity()));
        }
        return copy;
    }

    private static String createBookingId() {
        // Client correlation key only. The backend returns the displayed ASB ID.
        return "LOCAL-" + UUID.randomUUID();
    }
}
