package com.fixmate.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

import java.util.LinkedHashSet;
import java.util.Set;

final class NetworkStateMonitor {
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
    private State state = State.CONNECTING;

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
                main.post(() -> setState(State.RECONNECTING));
            }

            @Override
            public void onLost(Network network) {
                main.post(NetworkStateMonitor.this::updateFromActiveNetwork);
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                main.post(NetworkStateMonitor.this::updateFromActiveNetwork);
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
        listeners.add(listener);
        listener.onNetworkStateChanged(state, state);
    }

    void removeListener(Listener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
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
        updateFromActiveNetwork();
        if (isOnline()) {
            setState(State.RECONNECTING);
        }
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
            setState(State.CONNECTING);
        } catch (Exception ignored) {
            setState(State.ONLINE);
        }
    }

    private void setState(State next) {
        State previous = state;
        state = next;
        if (previous == next) {
            return;
        }
        for (Listener listener : new LinkedHashSet<>(listeners)) {
            listener.onNetworkStateChanged(next, previous);
        }
    }
}
