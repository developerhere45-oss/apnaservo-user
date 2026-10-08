package com.fixmate.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.LinkedHashSet;
import java.util.Set;

final class NetworkStateMonitor {
    private static final String TAG = "ApnaServoNetwork";
    private static final long ONLINE_STABILIZE_MS = 250L;
    private static final long OFFLINE_STABILIZE_MS = 900L;
    enum State {
        ONLINE,
        OFFLINE,
        CONNECTING,
        SLOW,
        RECONNECTING
    }

    interface Listener {
        void onNetworkStateChanged(State state, State previous);
    }

    private static NetworkStateMonitor instance;

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new LinkedHashSet<>();
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback callback;
    private Runnable pendingTransportUpdate;
    private volatile State state = State.CONNECTING;

    static synchronized NetworkStateMonitor get(Context context) {
        if (instance == null) {
            instance = new NetworkStateMonitor(context.getApplicationContext());
        }
        return instance;
    }

    private NetworkStateMonitor(Context context) {
        this.context = context;
    }

    void start() {
        if (callback != null) {
            updateFromActiveNetwork();
            return;
        }
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        updateFromActiveNetwork();
        if (connectivityManager == null) {
            setState(State.ONLINE);
            return;
        }
        callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                scheduleTransportUpdate(ONLINE_STABILIZE_MS);
            }

            @Override
            public void onLost(Network network) {
                // Android commonly reports the old Wi-Fi network as lost while
                // mobile data is already taking over. Debounce that hand-over so
                // one switch does not become OFFLINE -> ONLINE event spam.
                scheduleTransportUpdate(OFFLINE_STABILIZE_MS);
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                scheduleTransportUpdate(ONLINE_STABILIZE_MS);
            }
        };
        try {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            connectivityManager.registerNetworkCallback(request, callback);
        } catch (Exception ignored) {
        }
    }

    void stop() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(this::stop);
            return;
        }
        if (pendingTransportUpdate != null) main.removeCallbacks(pendingTransportUpdate);
        pendingTransportUpdate = null;
        if (connectivityManager != null && callback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(callback);
            } catch (Exception ignored) {
            }
        }
        callback = null;
        listeners.clear();
    }

    void addListener(Listener listener) {
        if (listener == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> addListener(listener));
            return;
        }
        listeners.add(listener);
        listener.onNetworkStateChanged(state, state);
    }

    void removeListener(Listener listener) {
        if (listener == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> removeListener(listener));
            return;
        }
        listeners.remove(listener);
    }

    State getState() {
        return state;
    }

    boolean isOnline() {
        return state == State.ONLINE || state == State.SLOW || state == State.RECONNECTING;
    }

    boolean hasInternetTransport() {
        if (connectivityManager == null) return false;
        try {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
            return caps != null
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } catch (Exception ignored) {
            return false;
        }
    }

    void refresh() {
        updateFromActiveNetwork();
    }

    void markRequestSlow() {
        if (isOnline()) {
            setState(State.SLOW);
        }
    }

    void markRequestSuccess() {
        if (state == State.SLOW || state == State.RECONNECTING || state == State.CONNECTING) {
            setState(State.ONLINE);
        }
    }

    void markConnectionProblem() {
        // A server/socket timeout is not a device connectivity transition.
        // Keep transport connectivity separate from request quality; otherwise
        // every failed API followed by any successful API generates a fake
        // "Connection restored" transition and a full resync feedback loop.
        if (hasInternetTransport()) markRequestSlow();
        else scheduleTransportUpdate(OFFLINE_STABILIZE_MS);
    }

    private void scheduleTransportUpdate(long delayMs) {
        main.post(() -> {
            if (pendingTransportUpdate != null) main.removeCallbacks(pendingTransportUpdate);
            pendingTransportUpdate = () -> {
                pendingTransportUpdate = null;
                updateFromActiveNetwork();
            };
            main.postDelayed(pendingTransportUpdate, delayMs);
        });
    }

    private void updateFromActiveNetwork() {
        if (connectivityManager == null) {
            setState(State.ONLINE);
            return;
        }
        try {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) {
                setState(State.OFFLINE);
                return;
            }
            NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
            if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                setState(State.OFFLINE);
                return;
            }
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                setState(State.ONLINE);
                return;
            }
            // While reconnecting, do not erase a confirmed OFFLINE state until
            // Android validates actual internet access. This preserves exactly
            // one meaningful OFFLINE -> ONLINE recovery notification.
            if (state != State.OFFLINE) setState(State.CONNECTING);
        } catch (Exception ignored) {
            if (state == State.CONNECTING) setState(State.OFFLINE);
        }
    }

    private void setState(State next) {
        // OkHttp completion callbacks call markRequestSuccess/markConnectionProblem
        // from worker threads. Every listener ultimately updates Activity views, so
        // state mutation and delivery must always be serialized on Android's UI looper.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> setState(next));
            return;
        }
        State previous = state;
        if (previous == next) {
            Log.d(TAG, "Connectivity callback ignored: state unchanged " + next);
            return;
        }
        state = next;
        Log.i(TAG, "Network state changed: " + previous + " -> " + next);
        for (Listener listener : new LinkedHashSet<>(listeners)) {
            try {
                listener.onNetworkStateChanged(next, previous);
            } catch (RuntimeException error) {
                Log.e(TAG, "Network listener failed", error);
            }
        }
    }
}
