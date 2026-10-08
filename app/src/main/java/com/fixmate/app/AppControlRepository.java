package com.fixmate.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

final class AppControlRepository {
    interface Listener { void onConfig(AppControlConfig config, boolean fromNetwork); }

    private static final String PREFS = "apnaservo_app_control";
    private static final String KEY_JSON = "last_valid_json";
    private static final String KEY_FETCHED_AT = "last_fetched_at";
    private static final String KEY_ATTEMPTED_AT = "last_attempted_at";
    // Socket publish events are the fast path. This short, bounded poll is a
    // fallback for devices that missed a socket event while backgrounded.
    private static final long REFRESH_INTERVAL_MS = 2L * 60L * 1000L;
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private final SharedPreferences prefs;
    private final BackendClient backend;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean requestInFlight;
    private boolean forcedRefreshQueued;
    private boolean retryScheduled;
    private boolean disposed;
    private int retryAttempt;

    AppControlRepository(Context context, BackendClient backend) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.backend = backend;
    }

    AppControlConfig cached() {
        String raw = prefs.getString(KEY_JSON, "");
        if (raw == null || raw.trim().isEmpty()) return AppControlConfig.DEFAULT;
        if (raw.length() > AppControlConfig.MAX_REMOTE_CONFIG_CHARS) {
            prefs.edit().remove(KEY_JSON).apply();
            return AppControlConfig.DEFAULT;
        }
        try {
            JSONObject value = new JSONObject(raw);
            if (!AppControlConfig.isSafeRemotePayload(value)) throw new IllegalArgumentException("Invalid cached config");
            return AppControlConfig.parse(value);
        } catch (Exception ignored) {
            prefs.edit().remove(KEY_JSON).apply();
            return AppControlConfig.DEFAULT;
        }
    }

    void refreshIfDue(boolean force, Listener listener) {
        if (disposed) return;
        // A publish event can arrive while an older response is in flight.
        // Queue one forced retry so the customer never remains on that stale
        // configuration (for example HIGH_DEMAND after MAINTENANCE is published).
        if (requestInFlight) {
            if (force) forcedRefreshQueued = true;
            return;
        }
        long now = System.currentTimeMillis();
        long age = now - prefs.getLong(KEY_ATTEMPTED_AT, 0L);
        if (!force && age >= 0 && age < REFRESH_INTERVAL_MS) return;
        requestInFlight = true;
        prefs.edit().putLong(KEY_ATTEMPTED_AT, now).apply();
        backend.fetchAppControlConfig(new BackendClient.JsonCallback() {
            @Override public void onSuccess(JSONObject object) {
                requestInFlight = false;
                try {
                    if (disposed) return;
                    if (!AppControlConfig.isSafeRemotePayload(object)) {
                        if (!refreshQueuedIfNeeded(listener)) scheduleRetry(listener);
                        return;
                    }
                    AppControlConfig parsed = AppControlConfig.parse(object);
                    prefs.edit().putString(KEY_JSON, object.toString())
                            .putLong(KEY_FETCHED_AT, System.currentTimeMillis()).apply();
                    retryAttempt = 0;
                    if (listener != null) listener.onConfig(parsed, true);
                } catch (Exception ignored) {
                    // Keep the last valid cache; malformed remote data must never alter app flow.
                    scheduleRetry(listener);
                }
                refreshQueuedIfNeeded(listener);
            }

            @Override public void onError(String message) {
                requestInFlight = false;
                if (disposed) return;
                // Keep the last valid cache, then use a capped, jittered retry
                // instead of creating a retry storm during a backend outage.
                if (!refreshQueuedIfNeeded(listener)) scheduleRetry(listener);
            }
        });
    }

    private boolean refreshQueuedIfNeeded(Listener listener) {
        if (!forcedRefreshQueued) return false;
        forcedRefreshQueued = false;
        refreshIfDue(true, listener);
        return true;
    }

    private void scheduleRetry(Listener listener) {
        if (disposed || retryScheduled || retryAttempt >= MAX_RETRY_ATTEMPTS) return;
        retryScheduled = true;
        long exponentialDelay = Math.min(30_000L, 1_000L << retryAttempt);
        long delay = exponentialDelay + (long) (Math.random() * 1_000L);
        retryAttempt += 1;
        main.postDelayed(() -> {
            retryScheduled = false;
            refreshIfDue(true, listener);
        }, delay);
    }

    void dispose() {
        disposed = true;
        forcedRefreshQueued = false;
        retryScheduled = false;
        main.removeCallbacksAndMessages(null);
    }
}
