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
    interface BookingListener {
        void onBookingEvent(String event, JSONObject booking);
    }

    private final BackendClient backendClient;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Socket socket;
    private boolean connected;
    private boolean connecting;

    BackendSocketClient(BackendClient backendClient) {
        this.backendClient = backendClient;
    }

    void connect(BookingListener listener) {
        if (isConnected() || connecting) {
            return;
        }
        connecting = true;
        disconnect();
        connecting = true;
        backendClient.getIdToken(new BackendClient.TokenCallback() {
            @Override
            public void onToken(String token) {
                List<String> urls = orderedSocketUrls();
                connectAt(urls, 0, token, listener);
            }

            @Override
            public void onError(String message) {
                connecting = false;
            }
        });
    }

    private void connectAt(List<String> urls, int index, String token, BookingListener listener) {
        if (index >= urls.size()) {
            connecting = false;
            return;
        }
        try {
            IO.Options options = IO.Options.builder()
                    .setForceNew(true)
                    .setReconnection(true)
                    .build();
            options.transports = new String[]{"websocket", "polling"};
            options.timeout = 20000;
            options.reconnectionAttempts = Integer.MAX_VALUE;
            options.reconnectionDelay = 1000;
            options.reconnectionDelayMax = 8000;
            Map<String, String> auth = new HashMap<>();
            auth.put("token", token);
            auth.put("role", "user");
            if (backendClient.isLocalDevelopmentBackend()
                    || "local-device-auth".equals(token)
                    || "local-development".equals(token)) {
                auth.put("devUid", backendClient.getDevUid());
            }
            options.auth = auth;
            socket = IO.socket(urls.get(index), options);
            attach("booking:accepted", listener);
            attach("booking:status_update", listener);
            attach("booking:rejected", listener);
            attach("booking:chat_message", listener);
            attach("booking:chat_seen", listener);
            attach("partner:location_update", listener);
            attach("app_control:published", listener);
            socket.on(Socket.EVENT_CONNECT, args -> main.post(() -> {
                connecting = false;
                connected = true;
                listener.onBookingEvent("socket:connected", new JSONObject());
            }));
            socket.on(Socket.EVENT_DISCONNECT, args -> main.post(() -> {
                connecting = false;
                connected = false;
                listener.onBookingEvent("socket:disconnected", new JSONObject());
            }));
            socket.on(Socket.EVENT_CONNECT_ERROR, args -> main.post(() -> {
                connected = false;
                if (socket != null) {
                    socket.disconnect();
                    socket.off();
                    socket = null;
                }
                connectAt(urls, index + 1, token, listener);
            }));
            socket.connect();
        } catch (URISyntaxException ignored) {
            connectAt(urls, index + 1, token, listener);
        }
    }

    void disconnect() {
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

    private void attach(String event, BookingListener listener) {
        socket.on(event, args -> {
            if (args.length == 0 || !(args[0] instanceof JSONObject)) {
                return;
            }
            JSONObject booking = (JSONObject) args[0];
            main.post(() -> listener.onBookingEvent(event, booking));
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
        return urls;
    }

    private boolean isSocketUrl(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://") || url.startsWith("wss://") || url.startsWith("ws://"));
    }
}
