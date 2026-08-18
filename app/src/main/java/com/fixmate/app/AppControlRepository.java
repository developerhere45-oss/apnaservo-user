package com.fixmate.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

final class AppControlRepository {
    interface Listener { void onConfig(AppControlConfig config, boolean fromNetwork); }

    private static final String PREFS = "apnaservo_app_control";
    private static final String KEY_JSON = "last_valid_json";
    private static final String KEY_FETCHED_AT = "last_fetched_at";
    private static final String KEY_ATTEMPTED_AT = "last_attempted_at";
    private static final long REFRESH_INTERVAL_MS = 10L * 60L * 1000L;
    private final SharedPreferences prefs;
    private final BackendClient backend;
    private boolean requestInFlight;

    AppControlRepository(Context context, BackendClient backend) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.backend = backend;
    }

    AppControlConfig cached() {
        String raw = prefs.getString(KEY_JSON, "");
        if (raw == null || raw.trim().isEmpty()) return AppControlConfig.DEFAULT;
        try { return AppControlConfig.parse(new JSONObject(raw)); }
        catch (Exception ignored) { return AppControlConfig.DEFAULT; }
    }

    void refreshIfDue(boolean force, Listener listener) {
        if (requestInFlight) return;
        long now = System.currentTimeMillis();
        long age = now - prefs.getLong(KEY_ATTEMPTED_AT, 0L);
        if (!force && age >= 0 && age < REFRESH_INTERVAL_MS) return;
        requestInFlight = true;
        prefs.edit().putLong(KEY_ATTEMPTED_AT, now).apply();
        backend.fetchAppControlConfig(new BackendClient.JsonCallback() {
            @Override public void onSuccess(JSONObject object) {
                requestInFlight = false;
                try {
                    if (object == null || object.optJSONObject("config") == null) return;
                    AppControlConfig parsed = AppControlConfig.parse(object);
                    prefs.edit().putString(KEY_JSON, object.toString())
                            .putLong(KEY_FETCHED_AT, System.currentTimeMillis()).apply();
                    if (listener != null) listener.onConfig(parsed, true);
                } catch (Exception ignored) {
                    // Keep the last valid cache; malformed remote data must never alter app flow.
                }
            }

            @Override public void onError(String message) {
                requestInFlight = false;
                // Offline/server failure intentionally keeps cached or default LIVE behavior.
            }
        });
    }
}
