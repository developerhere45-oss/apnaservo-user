package com.fixmate.app.data;

import android.content.Context;

import com.fixmate.app.model.Booking;
import com.fixmate.app.util.SecurePrefs;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public final class BookingStore {
    private static final String PREFS_NAME = "fixmate_bookings";
    private static final String KEY_BOOKINGS = "bookings";
    private static final String KEY_BOOKINGS_BACKUP = "bookings_backup";

    private final SecurePrefs preferences;

    public BookingStore(Context context) {
        preferences = new SecurePrefs(context, PREFS_NAME);
    }

    public void save(Booking booking) {
        List<Booking> bookings = load();
        for (int index = 0; index < bookings.size(); index++) {
            if (bookings.get(index).getId().equals(booking.getId())) {
                bookings.remove(index);
                break;
            }
        }
        bookings.add(0, booking);
        persist(bookings);
    }

    public void clear() {
        preferences.remove(KEY_BOOKINGS);
        preferences.remove(KEY_BOOKINGS_BACKUP);
    }

    public void remove(String bookingId) {
        if (bookingId == null || bookingId.trim().isEmpty()) {
            return;
        }
        List<Booking> bookings = load();
        for (int index = bookings.size() - 1; index >= 0; index--) {
            if (bookingId.equals(bookings.get(index).getId())) {
                bookings.remove(index);
            }
        }
        persist(bookings);
    }

    public void replaceAll(List<Booking> bookings) {
        List<Booking> safe = bookings == null ? new ArrayList<>() : new ArrayList<>(bookings);
        persist(safe);
    }

    public List<Booking> load() {
        String raw = preferences.getString(KEY_BOOKINGS, "[]");
        List<Booking> bookings = parse(raw);
        if (bookings != null) {
            return bookings;
        }

        String backup = preferences.getString(KEY_BOOKINGS_BACKUP, "[]");
        bookings = parse(backup);
        if (bookings != null) {
            preferences.putString(KEY_BOOKINGS, backup);
            return bookings;
        }
        return new ArrayList<>();
    }

    private List<Booking> parse(String raw) {
        List<Booking> bookings = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                try {
                    bookings.add(Booking.fromJson(array.getJSONObject(index)));
                } catch (JSONException ignored) {
                    // Skip only this corrupted item. The rest of the booking history is still valid.
                }
            }
        } catch (JSONException ignored) {
            return null;
        }
        return bookings;
    }

    private void persist(List<Booking> bookings) {
        String payload = toJson(bookings == null ? new ArrayList<>() : bookings).toString();
        preferences.putString(KEY_BOOKINGS, payload);
        preferences.putString(KEY_BOOKINGS_BACKUP, payload);
    }

    private JSONArray toJson(List<Booking> bookings) {
        JSONArray array = new JSONArray();
        for (Booking booking : bookings) {
            try {
                array.put(booking.toJson());
            } catch (JSONException ignored) {
                // Skip only the malformed booking instead of losing the full history.
            }
        }
        return array;
    }
}
