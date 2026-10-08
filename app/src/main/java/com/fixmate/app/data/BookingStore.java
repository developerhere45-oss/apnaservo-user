package com.fixmate.app.data;

import android.content.Context;

import com.fixmate.app.model.Booking;
import com.fixmate.app.util.SecurePrefs;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class BookingStore {
    private static final int MAX_BOOKINGS = 100;
    private static final int MAX_STORED_CHARS = 256 * 1024;
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

    /**
     * Applies a burst of remote snapshots with one encrypted read/write cycle.
     * Realtime callbacks must not serialise the whole history once per event.
     */
    public void saveAll(Collection<Booking> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        List<Booking> bookings = load();
        for (Booking snapshot : snapshots) {
            if (snapshot == null || snapshot.getId() == null || snapshot.getId().trim().isEmpty()) {
                continue;
            }
            for (int index = bookings.size() - 1; index >= 0; index--) {
                Booking existing = bookings.get(index);
                if (existing != null && snapshot.getId().equals(existing.getId())) {
                    bookings.remove(index);
                }
            }
            bookings.add(0, snapshot);
        }
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
        if (raw == null || raw.length() > MAX_STORED_CHARS) {
            return null;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < Math.min(array.length(), MAX_BOOKINGS); index++) {
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
        List<Booking> bounded = bookings == null ? new ArrayList<>() : new ArrayList<>(bookings);
        if (bounded.size() > MAX_BOOKINGS) {
            bounded = new ArrayList<>(bounded.subList(0, MAX_BOOKINGS));
        }
        String payload = toJson(bounded).toString();
        if (payload.length() > MAX_STORED_CHARS) {
            // Keep the newest valid history rather than persisting an
            // oversized payload that could block a later app launch.
            bounded = new ArrayList<>(bounded.subList(0, Math.min(20, bounded.size())));
            payload = toJson(bounded).toString();
        }
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
