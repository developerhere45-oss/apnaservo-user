package com.fixmate.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.socket.client.IO;
import io.socket.client.Socket;

final class BackendSocketClient {
    private static final long MAX_RECONNECT_DELAY_MS = 60_000L;
    interface BookingListener {
        void onBookingEvent(String event, JSONObject booking);
    }

    private final BackendClient backendClient;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Socket socket;
    private boolean connected;
    private boolean connecting;
    private long connectionGeneration;
    private int reconnectAttempt;
    private Runnable pendingReconnect;
    private BookingListener activeListener;

    BackendSocketClient(BackendClient backendClient) {
        this.backendClient = backendClient;
    }

    void connect(BookingListener listener) {
        if (isConnected() || connecting) {
            return;
        }
        cancelPendingReconnect();
        activeListener = listener;
        connectionGeneration += 1;
        connecting = true;
        final long generation = connectionGeneration;
        acquireTokenAndConnect(listener, generation, false);
    }

    private void acquireTokenAndConnect(BookingListener listener, long generation, boolean forceRefresh) {
        BackendClient.TokenCallback callback = new BackendClient.TokenCallback() {
            @Override
            public void onToken(String token) {
                if (generation != connectionGeneration || listener != activeListener) return;
                List<String> urls = orderedSocketUrls();
                connectAt(urls, 0, token, listener, generation);
            }

            @Override
            public void onError(String message) {
                if (generation != connectionGeneration || listener != activeListener) return;
                connecting = false;
                scheduleReconnect(listener, generation);
            }
        };
        if (forceRefresh) backendClient.refreshAuthentication(callback);
        else backendClient.getIdToken(callback);
    }

    private void connectAt(List<String> urls, int index, String token, BookingListener listener, long generation) {
        if (generation != connectionGeneration) return;
        if (index >= urls.size()) {
            connecting = false;
            scheduleReconnect(listener, generation);
            return;
        }
        try {
            IO.Options options = IO.Options.builder()
                    .setForceNew(true)
                    .setReconnection(true)
                    .build();
            options.transports = new String[]{"websocket", "polling"};
            options.timeout = 20000;
            // Reconnect ourselves so every new handshake uses a freshly issued
            // Firebase ID token instead of reusing the login-time token forever.
            options.reconnection = false;
            Map<String, String> auth = new HashMap<>();
            auth.put("token", token);
            auth.put("role", "user");
            if (backendClient.isLocalDevelopmentBackend()
                    || "local-device-auth".equals(token)
                    || "local-development".equals(token)) {
                auth.put("devUid", backendClient.getDevUid());
            }
            options.auth = auth;
            Socket candidate = IO.socket(urls.get(index), options);
            socket = candidate;
            attach(candidate, "booking:accepted", listener, generation);
            attach(candidate, "booking:status_update", listener, generation);
            attach(candidate, "booking:rejected", listener, generation);
            attach(candidate, "booking:chat_message", listener, generation);
            attach(candidate, "booking:chat_seen", listener, generation);
            attach(candidate, "partner:location_update", listener, generation);
            attach(candidate, "app_control:published", listener, generation);
            candidate.on(Socket.EVENT_CONNECT, args -> main.post(() -> {
                if (!isCurrent(candidate, generation)) return;
                connecting = false;
                connected = true;
                reconnectAttempt = 0;
                cancelPendingReconnect();
                listener.onBookingEvent("socket:connected", new JSONObject());
            }));
            candidate.on(Socket.EVENT_DISCONNECT, args -> main.post(() -> {
                if (!isCurrent(candidate, generation)) return;
                connecting = false;
                connected = false;
                socket = null;
                candidate.off();
                listener.onBookingEvent("socket:disconnected", new JSONObject());
                scheduleReconnect(listener, generation);
            }));
            candidate.on(Socket.EVENT_CONNECT_ERROR, args -> main.post(() -> {
                if (!isCurrent(candidate, generation)) return;
                connected = false;
                socket = null;
                candidate.off();
                candidate.disconnect();
                connectAt(urls, index + 1, token, listener, generation);
            }));
            candidate.connect();
        } catch (URISyntaxException ignored) {
            connectAt(urls, index + 1, token, listener, generation);
        }
    }

    private void scheduleReconnect(BookingListener listener, long generation) {
        if (generation != connectionGeneration || listener != activeListener || pendingReconnect != null) return;
        long exponent = Math.min(reconnectAttempt++, 6);
        long delay = Math.min(MAX_RECONNECT_DELAY_MS, 1_000L << exponent);
        pendingReconnect = () -> {
            pendingReconnect = null;
            if (generation != connectionGeneration || listener != activeListener || isConnected()) return;
            connecting = true;
            acquireTokenAndConnect(listener, generation, true);
        };
        main.postDelayed(pendingReconnect, delay);
    }

    private void cancelPendingReconnect() {
        if (pendingReconnect != null) {
            main.removeCallbacks(pendingReconnect);
            pendingReconnect = null;
        }
    }

    void disconnect() {
        connectionGeneration += 1;
        activeListener = null;
        reconnectAttempt = 0;
        cancelPendingReconnect();
        connecting = false;
        if (socket != null) {
            socket.disconnect();
            socket.off();
            socket = null;
        }
        connected = false;
    }

    boolean isConnected() {
        return connected && socket != null && socket.connected();
    }

    private boolean isCurrent(Socket candidate, long generation) {
        return generation == connectionGeneration && socket == candidate;
    }

    private void attach(Socket candidate, String event, BookingListener listener, long generation) {
        candidate.on(event, args -> {
            if (args.length == 0 || !(args[0] instanceof JSONObject)) {
                return;
            }
            JSONObject booking = (JSONObject) args[0];
            main.post(() -> {
                if (isCurrent(candidate, generation)) listener.onBookingEvent(event, booking);
            });
        });
    }

    private List<String> orderedSocketUrls() {
        List<String> urls = new ArrayList<>();
        urls.add(backendClient.getSocketUrl());
        for (String url : backendClient.getSocketUrls()) {
            if (!urls.contains(url)) {
                urls.add(url);
            }
        }
        if (isSocketUrl(BuildConfig.APNA_SERVO_SOCKET_URL)
                && !urls.contains(BuildConfig.APNA_SERVO_SOCKET_URL)) {
            urls.add(BuildConfig.APNA_SERVO_SOCKET_URL);
        }
        if (isSocketUrl(BuildConfig.APNA_SERVO_SOCKET_BACKUP_URL)
                && !urls.contains(BuildConfig.APNA_SERVO_SOCKET_BACKUP_URL)) {
            urls.add(BuildConfig.APNA_SERVO_SOCKET_BACKUP_URL);
        }
        return urls;
    }

    private boolean isSocketUrl(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://") || url.startsWith("wss://") || url.startsWith("ws://"));
    }
}
