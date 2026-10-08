package com.fixmate.app.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class Booking {
    private static final SecureRandom BOOKING_ID_RANDOM = new SecureRandom();
    private static final char[] BOOKING_ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final String id;
    private final long createdAt;
    private final CustomerDetails customerDetails;
    private final List<BookingItem> items;
    private final int subtotal;
    private final int platformFee;
    private final int total;
    private final String status;
    private final List<String> completedTasks;
    private final String additionalWork;
    private final String partnerNotes;
    private final String workSubmittedBy;
    private final long workSubmittedAt;

    public Booking(CustomerDetails customerDetails, List<BookingItem> items) {
        this(createBookingId(), System.currentTimeMillis(), customerDetails, copyItems(items), "Technician assignment pending");
    }

    private Booking(String id, long createdAt, CustomerDetails customerDetails, List<BookingItem> items, String status) {
        this(id, createdAt, customerDetails, items, status, new ArrayList<>(), "", "", "", 0L);
    }

    private Booking(String id, long createdAt, CustomerDetails customerDetails, List<BookingItem> items, String status,
                    List<String> completedTasks, String additionalWork, String partnerNotes,
                    String workSubmittedBy, long workSubmittedAt) {
        this.id = id;
        this.createdAt = createdAt;
        this.customerDetails = customerDetails;
        this.items = items;
        this.subtotal = calculateSubtotal(items);
        this.platformFee = subtotal > 0 && subtotal < 1499 ? 49 : 0;
        this.total = subtotal + platformFee;
        this.status = status;
        this.completedTasks = completedTasks == null ? new ArrayList<>() : new ArrayList<>(completedTasks);
        this.additionalWork = additionalWork == null ? "" : additionalWork;
        this.partnerNotes = partnerNotes == null ? "" : partnerNotes;
        this.workSubmittedBy = workSubmittedBy == null ? "" : workSubmittedBy;
        this.workSubmittedAt = workSubmittedAt;
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

    public List<String> getCompletedTasks() { return new ArrayList<>(completedTasks); }
    public String getAdditionalWork() { return additionalWork; }
    public String getPartnerNotes() { return partnerNotes; }
    public String getWorkSubmittedBy() { return workSubmittedBy; }
    public long getWorkSubmittedAtMillis() { return workSubmittedAt; }

    public Booking withStatus(String nextStatus) {
        String safeStatus = nextStatus == null || nextStatus.trim().isEmpty() ? status : nextStatus.trim();
        return new Booking(id, createdAt, customerDetails, copyItems(items), safeStatus, completedTasks, additionalWork, partnerNotes, workSubmittedBy, workSubmittedAt);
    }

    public Booking withId(String canonicalId) {
        String safeId = canonicalId == null ? "" : canonicalId.trim();
        if (safeId.isEmpty() || safeId.equals(id)) return this;
        return new Booking(safeId, createdAt, customerDetails, copyItems(items), status, completedTasks, additionalWork, partnerNotes, workSubmittedBy, workSubmittedAt);
    }

    public Booking withCustomerDetails(CustomerDetails nextDetails) {
        return new Booking(id, createdAt, nextDetails == null ? customerDetails : nextDetails, copyItems(items), status, completedTasks, additionalWork, partnerNotes, workSubmittedBy, workSubmittedAt);
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
        object.put("completedTasks", new JSONArray(completedTasks));
        object.put("additionalWork", additionalWork);
        object.put("partnerNotes", partnerNotes);
        object.put("workSubmittedBy", workSubmittedBy);
        object.put("workSubmittedAtMillis", workSubmittedAt);

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
            for (int index = 0; index < Math.min(itemArray.length(), 20); index++) {
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
                object.optString("status", "Technician assignment pending"),
                parseCompletedTasks(object),
                object.optString("additionalWork", ""),
                object.optString("partnerNotes", ""),
                object.optString("workSubmittedBy", ""),
                object.optLong("workSubmittedAtMillis", 0L)
        );
    }

    public static Booking fromBackendJson(JSONObject object) throws JSONException {
        String bookingCode = object.optString("canonicalBookingId",
                object.optString("publicId",
                        object.optString("bookingCode",
                                object.optString("bookingId", object.optString("_id", "")))));
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
                object.optString("status", "pending"),
                parseCompletedTasks(object),
                completionString(object, "additionalWork"),
                completionString(object, "partnerNotes"),
                completionString(object, "workSubmittedBy"),
                object.optLong("workSubmittedAtMillis", 0L)
        );
    }

    private static List<String> parseCompletedTasks(JSONObject object) {
        List<String> tasks = new ArrayList<>();
        JSONArray array = object.optJSONArray("completedTasks");
        JSONObject completion = object.optJSONObject("workCompletion");
        if (array == null && completion != null) array = completion.optJSONArray("completedTasks");
        if (array != null) {
            for (int index = 0; index < Math.min(array.length(), 30); index++) {
                String task = array.optString(index, "").trim();
                if (!task.isEmpty()) tasks.add(task);
            }
        }
        return tasks;
    }

    private static String completionString(JSONObject object, String key) {
        String value = object.optString(key, "").trim();
        JSONObject completion = object.optJSONObject("workCompletion");
        return value.isEmpty() && completion != null ? completion.optString(key.replace("workSubmittedBy", "submittedBy"), "").trim() : value;
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
        long sum = 0;
        if (items == null) return 0;
        for (BookingItem item : items) {
            if (item != null) sum = Math.min(Integer.MAX_VALUE, sum + item.getLineTotal());
        }
        return (int) sum;
    }

    private static List<BookingItem> copyItems(List<BookingItem> source) {
        List<BookingItem> copy = new ArrayList<>();
        if (source == null) return copy;
        for (BookingItem item : source) {
            if (item != null && item.getService() != null) {
                copy.add(new BookingItem(item.getService(), item.getQuantity()));
            }
        }
        return copy;
    }

    private static String createBookingId() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
        StringBuilder random = new StringBuilder(8);
        for (int index = 0; index < 8; index++) {
            random.append(BOOKING_ID_ALPHABET[BOOKING_ID_RANDOM.nextInt(BOOKING_ID_ALPHABET.length)]);
        }
        return "ASB-" + format.format(new Date()) + "-" + random;
    }
}
