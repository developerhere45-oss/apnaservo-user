package com.fixmate.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Dispatcher;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class BackendClient {
    interface JsonCallback {
        void onSuccess(JSONObject object);
        void onError(String message);
    }

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_TRANSIENT_RETRIES = 2;
    private static final long SLOW_REQUEST_MS = 9000L;
    private static final String LOCAL_DEVICE_TOKEN = "local-device-auth";
    private static final String INSTALLATION_PREFS = "apnaservo_user_installation";
    private static final String INSTALLATION_ID_KEY = "installation_id";
    private static final OkHttpClient SHARED_HTTP = new OkHttpClient.Builder()
            .dispatcher(createDispatcher())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final OkHttpClient http = SHARED_HTTP;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final NetworkStateMonitor networkMonitor;
    private final List<String> baseUrls;
    private final String devUid;
    private final String deviceId;
    private String activeBaseUrl;
    private String cachedToken = "";

    private static Dispatcher createDispatcher() {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(32);
        dispatcher.setMaxRequestsPerHost(8);
        return dispatcher;
    }

    private String installationId(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(INSTALLATION_PREFS, Context.MODE_PRIVATE);
        String existing = sanitizeDeviceId(prefs.getString(INSTALLATION_ID_KEY, ""));
        if (!existing.isEmpty()) return existing;
        String created = sanitizeDeviceId(UUID.randomUUID().toString());
        prefs.edit().putString(INSTALLATION_ID_KEY, created).apply();
        return created;
    }

    BackendClient(Context context) {
        this.networkMonitor = NetworkStateMonitor.get(context);
        this.networkMonitor.start();
        this.baseUrls = buildBaseUrls();
        this.activeBaseUrl = baseUrls.get(0);
        this.deviceId = installationId(context);
        this.devUid = "local-user-" + deviceId;
    }

    void warmUp() {
        Request request = new Request.Builder()
                .url(getSocketUrl() + "/health")
                .get()
                .build();
        http.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException error) {
                // Booking requests retain their own idempotent retry handling.
            }

            @Override
            public void onResponse(Call call, Response response) {
                response.close();
            }
        });
    }

    void fetchAppControlConfig(JsonCallback callback) {
        publicGet("/app-control/config?audience=users", callback == null ? silent() : callback);
    }

    void sendOtp(String phone, String role, JsonCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("phone", phone == null ? "" : phone);
            body.put("role", role == null ? "user" : role);
            body.put("purpose", "login");
        } catch (Exception ignored) {
        }
        publicPost("/otp/send", body, callback == null ? silent() : callback);
    }

    void verifyOtp(String phone, String role, String requestId, String otp, JsonCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("phone", phone == null ? "" : phone);
            body.put("role", role == null ? "user" : role);
            body.put("purpose", "login");
            body.put("requestId", requestId == null ? "" : requestId);
            body.put("otp", otp == null ? "" : otp);
        } catch (Exception ignored) {
        }
        publicPost("/otp/verify", body, callback == null ? silent() : callback);
    }

    String getSocketUrl() {
        return activeBaseUrl.replace("/api", "");
    }

    List<String> getSocketUrls() {
        List<String> socketUrls = new ArrayList<>();
        for (String url : baseUrls) {
            socketUrls.add(url.replace("/api", ""));
        }
        return socketUrls;
    }

    void upsertUserProfile(String name, String phone, String email, String address, double lat, double lng, String fcmToken, JsonCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("name", name);
            body.put("phone", phone);
            body.put("email", email == null ? "" : email);
            body.put("address", address == null ? "" : address);
            body.put("city", "Guwahati");
            body.put("lat", lat);
            body.put("lng", lng);
            body.put("fcmToken", fcmToken == null ? "" : fcmToken);
            body.put("profilePhotoUrl", auth.getCurrentUser() != null && auth.getCurrentUser().getPhotoUrl() != null
                    ? auth.getCurrentUser().getPhotoUrl().toString()
                    : "");
            body.put("deviceInfo", deviceInfo());
        } catch (Exception ignored) {
        }
        post("/users/profile", body, callback);
    }

    void syncSupportTicket(
            String ticketId,
            String clientMessageId,
            String senderRole,
            String senderName,
            String message,
            String category,
            String bookingId,
            String attachmentUrl
    ) {
        syncSupportTicket(ticketId, clientMessageId, senderRole, senderName, message, category, bookingId, attachmentUrl, silent());
    }

    void syncSupportTicket(
            String ticketId, String clientMessageId, String senderRole, String senderName,
            String message, String category, String bookingId, String attachmentUrl, JsonCallback callback
    ) {
        if (ticketId == null || ticketId.trim().isEmpty() || message == null || message.trim().isEmpty()) {
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("ticketId", ticketId.trim());
            body.put("clientMessageId", clientMessageId == null ? "" : clientMessageId.trim());
            body.put("senderRole", senderRole == null ? "user" : senderRole);
            body.put("senderName", senderName == null ? "" : senderName);
            body.put("message", message.trim());
            body.put("category", category == null ? "general" : category);
            body.put("bookingId", bookingId == null ? "" : bookingId);
            body.put("attachmentUrl", attachmentUrl == null ? "" : attachmentUrl);
            if (attachmentUrl != null && !attachmentUrl.trim().isEmpty()) {
                body.put("attachmentName", "Customer screenshot");
                body.put("attachmentMimeType", "image/jpeg");
            }
        } catch (Exception ignored) {
        }
        post("/users/support-tickets/sync", body, callback == null ? silent() : callback);
    }

    void saveFcmToken(String token) {
        JSONObject body = new JSONObject();
        try {
            body.put("token", token == null ? "" : token);
            body.put("platform", "android");
            body.put("deviceId", deviceId);
            body.put("appType", "user");
        } catch (Exception ignored) {
        }
        post("/notifications/device-token", body, silent());
    }

    void removeFcmToken(String token, JsonCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("token", token == null ? "" : token);
            body.put("deviceId", deviceId);
            body.put("appType", "user");
        } catch (Exception ignored) {
        }
        delete("/notifications/device-token", body, callback == null ? silent() : callback);
    }

    void requestAccountDeletion(String reason, JsonCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("reason", reason == null ? "" : reason.trim());
        } catch (Exception ignored) {
        }
        post("/users/delete-account-request", body, callback);
    }

    void fetchNotifications(JsonCallback callback) {
        fetchNotifications(1, 25, callback);
    }

    void fetchNotifications(int page, int limit, JsonCallback callback) {
        get("/notifications/my-notifications?role=user&page=" + Math.max(1, page) + "&limit=" + Math.max(1, Math.min(100, limit)), callback);
    }

    void markNotificationRead(String notificationId) {
        if (notificationId == null || notificationId.trim().isEmpty()) {
            return;
        }
        patch("/notifications/" + notificationId.trim() + "/read?role=user", new JSONObject(), silent());
    }

    void markAllNotificationsRead(JsonCallback callback) {
        patch("/notifications/read-all?role=user", new JSONObject(), callback == null ? silent() : callback);
    }

    void createBooking(JSONObject payload, JsonCallback callback) {
        if (payload != null && payload.optString("clientRequestId", "").trim().isEmpty()) {
            try {
                payload.put("clientRequestId", payload.optString("bookingCode", ""));
            } catch (Exception ignored) {
            }
        }
        post("/bookings", payload, callback);
    }

    void fetchBookingLaunchStatus(JsonCallback callback) {
        publicGet("/bookings/launch-status", callback);
    }

    void requestLaunchNotification(JsonCallback callback) {
        post("/bookings/launch-notify", new JSONObject(), callback == null ? silent() : callback);
    }

    void getBooking(String bookingId, JsonCallback callback) {
        get("/bookings/" + bookingId, callback);
    }

    void fetchUserBookings(JsonCallback callback) {
        get("/bookings/user", callback);
    }

    void fetchUserProfile(JsonCallback callback) {
        get("/users/me", callback);
    }

    void updateBookingContacts(String bookingId, String primaryPhone, String alternatePhone, JsonCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("primaryPhone", primaryPhone == null ? "" : primaryPhone);
            body.put("alternatePhone", alternatePhone == null ? "" : alternatePhone);
        } catch (Exception ignored) {
        }
        patch("/bookings/" + bookingId + "/contact", body, callback);
    }

    void updateBookingLocation(String bookingId, JSONObject location, JsonCallback callback) {
        patch("/bookings/" + bookingId + "/location", location == null ? new JSONObject() : location, callback);
    }

    void createCallLog(String bookingId, String action, String reason, JsonCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("action", action == null ? "start" : action);
            body.put("reason", reason == null ? "" : reason);
        } catch (Exception ignored) {
        }
        post("/bookings/" + bookingId + "/calls", body, callback);
    }

    void updateBookingStatus(String bookingId, String status, int finalAmount, JsonCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("status", status);
            body.put("finalAmount", finalAmount);
        } catch (Exception ignored) {
        }
        patch("/bookings/" + bookingId + "/status", body, callback);
    }

    void counterOfferQuote(String bookingId, int amount, String message, JsonCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("amount", amount);
            body.put("message", message == null ? "" : message);
        } catch (Exception ignored) {
        }
        post("/bookings/" + bookingId + "/quote/counter", body, callback);
    }

    void submitDirectPayment(String bookingId, JsonCallback callback) {
        post("/bookings/" + bookingId + "/payment-submitted", new JSONObject(), callback);
    }

    void submitReview(String bookingId, int rating, String comment, JsonCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("rating", rating);
            body.put("comment", comment == null ? "" : comment);
        } catch (Exception ignored) {
        }
        post("/reviews/bookings/" + bookingId, body, callback);
    }

    void monitorBookingChat(String bookingId, String message, String clientMessageId) {
        if (bookingId == null || bookingId.trim().isEmpty() || message == null || message.trim().isEmpty()) {
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("message", message);
            body.put("clientMessageId", clientMessageId == null ? "" : clientMessageId);
            body.put("source", "customer_support_chat");
        } catch (Exception ignored) {
        }
        post("/bookings/" + bookingId + "/chat/monitor", body, silent());
    }

    void fetchBookingChatMessages(String bookingId, JsonCallback callback) {
        if (bookingId == null || bookingId.trim().isEmpty()) {
            callback.onError("Booking missing");
            return;
        }
        get("/bookings/" + bookingId.trim() + "/chat/messages", callback);
    }

    void sendBookingChatMessage(String bookingId, String message, String clientMessageId, JsonCallback callback) {
        if (bookingId == null || bookingId.trim().isEmpty() || message == null || message.trim().isEmpty()) {
            callback.onError("Message missing");
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("message", message.trim());
            body.put("clientMessageId", clientMessageId == null ? "" : clientMessageId);
        } catch (Exception ignored) {
        }
        post("/bookings/" + bookingId.trim() + "/chat/messages", body, callback);
    }

    void markBookingChatSeen(String bookingId) {
        if (bookingId == null || bookingId.trim().isEmpty()) {
            return;
        }
        patch("/bookings/" + bookingId.trim() + "/chat/seen", new JSONObject(), silent());
    }

    void connectFcmTokenToProfile(String name, String phone, String email, String address, double lat, double lng) {
        upsertUserProfile(name, phone, email == null ? "" : email, address, lat, lng, "", silent());
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && !task.getResult().trim().isEmpty()) {
                saveFcmToken(task.getResult());
            }
        });
    }

    private JSONObject deviceInfo() {
        JSONObject info = new JSONObject();
        try {
            info.put("platform", "Android");
            info.put("os", "Android");
            info.put("osVersion", Build.VERSION.RELEASE);
            info.put("sdk", Build.VERSION.SDK_INT);
            info.put("manufacturer", Build.MANUFACTURER);
            info.put("model", Build.MODEL);
            info.put("device", Build.DEVICE);
            info.put("appVersion", BuildConfig.VERSION_NAME);
            info.put("appVersionCode", BuildConfig.VERSION_CODE);
        } catch (Exception ignored) {
        }
        return info;
    }

    void getIdToken(TokenCallback callback) {
        if (isLocalDevelopmentBackend()) {
            callback.onToken("local-development");
            return;
        }
        if (auth.getCurrentUser() != null) {
            auth.getCurrentUser().getIdToken(false)
                    .addOnSuccessListener(result -> {
                        cachedToken = result.getToken();
                        callback.onToken(cachedToken);
                    })
                    .addOnFailureListener(error -> {
                        if (!cachedToken.isEmpty()) {
                            callback.onToken(cachedToken);
                            return;
                        }
                        callback.onToken(LOCAL_DEVICE_TOKEN);
                    });
            return;
        }
        callback.onToken(LOCAL_DEVICE_TOKEN);
    }

    private void retryTokenAfterFirebaseError(TokenCallback callback, String firstError) {
        if (auth.getCurrentUser() != null) {
            auth.getCurrentUser().getIdToken(true)
                    .addOnSuccessListener(result -> {
                        cachedToken = result.getToken();
                        callback.onToken(cachedToken);
                    })
                    .addOnFailureListener(forceError -> {
                        if (auth.getCurrentUser() != null && auth.getCurrentUser().isAnonymous()) {
                            auth.signOut();
                            signInAndFetchToken(callback, forceError.getMessage());
                            return;
                        }
                        if (!cachedToken.isEmpty()) {
                            callback.onToken(cachedToken);
                            return;
                        }
                        callback.onToken(LOCAL_DEVICE_TOKEN);
                    });
            return;
        }
        signInAndFetchToken(callback, firstError);
    }

    private void signInAndFetchToken(TokenCallback callback, String previousError) {
        auth.signInAnonymously()
                .addOnSuccessListener(result -> result.getUser().getIdToken(false)
                        .addOnSuccessListener(token -> {
                            cachedToken = token.getToken();
                            callback.onToken(cachedToken);
                        })
                        .addOnFailureListener(error -> {
                            if (!cachedToken.isEmpty()) {
                                callback.onToken(cachedToken);
                                return;
                            }
                            callback.onToken(LOCAL_DEVICE_TOKEN);
                        }))
                .addOnFailureListener(error -> {
                    if (!cachedToken.isEmpty()) {
                        callback.onToken(cachedToken);
                        return;
                    }
                    callback.onToken(LOCAL_DEVICE_TOKEN);
                });
    }

    private String nonEmpty(String primary, String fallback) {
        if (primary != null && !primary.trim().isEmpty()) {
            return primary;
        }
        if (fallback != null && !fallback.trim().isEmpty()) {
            return fallback;
        }
        return "Firebase Auth token unavailable";
    }

    private void post(String path, JSONObject body, JsonCallback callback) {
        requestWithBody("POST", path, body, callback);
    }

    private void publicPost(String path, JSONObject body, JsonCallback callback) {
        executePublicWithFallback(path, body, callback, 0, null, 0);
    }

    private void publicGet(String path, JsonCallback callback) {
        executePublicGetWithFallback(path, callback, 0, null, 0);
    }

    private void patch(String path, JSONObject body, JsonCallback callback) {
        requestWithBody("PATCH", path, body, callback);
    }

    private void delete(String path, JSONObject body, JsonCallback callback) {
        requestWithBody("DELETE", path, body, callback);
    }

    private void get(String path, JsonCallback callback) {
        getIdToken(new TokenCallback() {
            @Override
            public void onToken(String token) {
                executeGetWithFallback(path, token, callback, 0, null);
            }

            @Override
            public void onError(String message) {
                main.post(() -> callback.onError(message));
            }
        });
    }

    private void executeGetWithFallback(String path, String token, JsonCallback callback, int index, String lastError) {
        executeGetWithFallback(path, token, callback, index, lastError, 0);
    }

    private void executeGetWithFallback(String path, String token, JsonCallback callback, int index, String lastError, int attempt) {
        markCrashOperation("GET", path);
        if (!networkMonitor.isOnline()) {
            main.post(() -> callback.onError(NetworkError.NO_INTERNET));
            return;
        }
        if (index >= baseUrls.size()) {
            networkMonitor.markConnectionProblem();
            String message = lastError == null || lastError.trim().isEmpty() ? NetworkError.SERVER : NetworkError.safe(lastError);
            main.post(() -> callback.onError(message));
            return;
        }
        String candidateBaseUrl = index == 0 ? activeBaseUrl : baseUrls.get(index);
        Request.Builder builder = new Request.Builder()
                .url(candidateBaseUrl + path)
                .get();
        addAuthenticationHeaders(builder, token);
        Request request = builder.build();
        long startedAt = System.currentTimeMillis();
        http.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                networkMonitor.markConnectionProblem();
                retryOrFallbackGet(path, token, callback, index, NetworkError.forIOException(e, networkMonitor.isOnline()), attempt);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                markRequestTiming(startedAt);
                String text = response.body() == null ? "{}" : response.body().string();
                if (!response.isSuccessful()) {
                    if (isTransientHttp(response.code()) && attempt < MAX_TRANSIENT_RETRIES) {
                        main.postDelayed(() -> executeGetWithFallback(path, token, callback, index, friendlyHttpError(response.code(), text), attempt + 1), retryDelay(attempt));
                        return;
                    }
                    executeGetWithFallback(path, token, callback, index + 1, friendlyHttpError(response.code(), text));
                    return;
                }
                networkMonitor.markRequestSuccess();
                activeBaseUrl = candidateBaseUrl;
                try {
                    JSONObject object = new JSONObject(text);
                    main.post(() -> callback.onSuccess(object));
                } catch (Exception error) {
                    main.post(() -> callback.onError(error.getMessage()));
                }
            }
        });
    }

    private void retryOrFallbackGet(String path, String token, JsonCallback callback, int index, String lastError, int attempt) {
        if (attempt < MAX_TRANSIENT_RETRIES) {
            main.postDelayed(() -> executeGetWithFallback(path, token, callback, index, lastError, attempt + 1), retryDelay(attempt));
            return;
        }
        executeGetWithFallback(path, token, callback, index + 1, lastError);
    }

    private void requestWithBody(String method, String path, JSONObject body, JsonCallback callback) {
        getIdToken(new TokenCallback() {
            @Override
            public void onToken(String token) {
                executeWithFallback(method, path, body, token, callback, 0, null);
            }

            @Override
            public void onError(String message) {
                main.post(() -> callback.onError(message));
            }
        });
    }

    private void executePublicWithFallback(String path, JSONObject body, JsonCallback callback, int index, String lastError, int attempt) {
        markCrashOperation("POST", path);
        if (!networkMonitor.isOnline()) {
            main.post(() -> callback.onError(NetworkError.NO_INTERNET));
            return;
        }
        if (index >= baseUrls.size()) {
            networkMonitor.markConnectionProblem();
            String message = lastError == null || lastError.trim().isEmpty() ? NetworkError.SERVER : NetworkError.safe(lastError);
            main.post(() -> callback.onError(message));
            return;
        }
        String candidateBaseUrl = index == 0 ? activeBaseUrl : baseUrls.get(index);
        Request request = new Request.Builder()
                .url(candidateBaseUrl + path)
                .post(RequestBody.create((body == null ? new JSONObject() : body).toString(), JSON))
                .build();
        long startedAt = System.currentTimeMillis();
        http.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (attempt < MAX_TRANSIENT_RETRIES) {
                    main.postDelayed(() -> executePublicWithFallback(path, body, callback, index, NetworkError.forIOException(e, networkMonitor.isOnline()), attempt + 1), retryDelay(attempt));
                    return;
                }
                networkMonitor.markConnectionProblem();
                executePublicWithFallback(path, body, callback, index + 1, NetworkError.forIOException(e, networkMonitor.isOnline()), 0);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                markRequestTiming(startedAt);
                String text = response.body() == null ? "{}" : response.body().string();
                if (!response.isSuccessful()) {
                    if (isTransientHttp(response.code()) && attempt < MAX_TRANSIENT_RETRIES) {
                        main.postDelayed(() -> executePublicWithFallback(path, body, callback, index, friendlyHttpError(response.code(), text), attempt + 1), retryDelay(attempt));
                        return;
                    }
                    main.post(() -> callback.onError(friendlyHttpError(response.code(), text)));
                    return;
                }
                networkMonitor.markRequestSuccess();
                activeBaseUrl = candidateBaseUrl;
                try {
                    JSONObject object = new JSONObject(text);
                    main.post(() -> callback.onSuccess(object));
                } catch (Exception error) {
                    main.post(() -> callback.onError(error.getMessage()));
                }
            }
        });
    }

    private void executePublicGetWithFallback(String path, JsonCallback callback, int index, String lastError, int attempt) {
        markCrashOperation("GET", path);
        if (!networkMonitor.isOnline()) {
            main.post(() -> callback.onError(NetworkError.NO_INTERNET));
            return;
        }
        if (index >= baseUrls.size()) {
            networkMonitor.markConnectionProblem();
            String message = lastError == null || lastError.trim().isEmpty() ? NetworkError.SERVER : NetworkError.safe(lastError);
            main.post(() -> callback.onError(message));
            return;
        }
        String candidateBaseUrl = index == 0 ? activeBaseUrl : baseUrls.get(index);
        Request request = new Request.Builder().url(candidateBaseUrl + path).get().build();
        long startedAt = System.currentTimeMillis();
        http.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException error) {
                if (attempt < MAX_TRANSIENT_RETRIES) {
                    main.postDelayed(() -> executePublicGetWithFallback(path, callback, index, NetworkError.forIOException(error, networkMonitor.isOnline()), attempt + 1), retryDelay(attempt));
                    return;
                }
                executePublicGetWithFallback(path, callback, index + 1, NetworkError.forIOException(error, networkMonitor.isOnline()), 0);
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                markRequestTiming(startedAt);
                String text = response.body() == null ? "{}" : response.body().string();
                if (!response.isSuccessful()) {
                    if (isTransientHttp(response.code()) && attempt < MAX_TRANSIENT_RETRIES) {
                        main.postDelayed(() -> executePublicGetWithFallback(path, callback, index, friendlyHttpError(response.code(), text), attempt + 1), retryDelay(attempt));
                        return;
                    }
                    executePublicGetWithFallback(path, callback, index + 1, friendlyHttpError(response.code(), text), 0);
                    return;
                }
                networkMonitor.markRequestSuccess();
                activeBaseUrl = candidateBaseUrl;
                try {
                    JSONObject object = new JSONObject(text);
                    main.post(() -> callback.onSuccess(object));
                } catch (Exception error) {
                    main.post(() -> callback.onError(error.getMessage()));
                }
            }
        });
    }

    private void executeWithFallback(String method, String path, JSONObject body, String token, JsonCallback callback, int index, String lastError) {
        executeWithFallback(method, path, body, token, callback, index, lastError, 0);
    }

    private void executeWithFallback(String method, String path, JSONObject body, String token, JsonCallback callback, int index, String lastError, int attempt) {
        markCrashOperation(method, path);
        if (!networkMonitor.isOnline()) {
            main.post(() -> callback.onError(NetworkError.NO_INTERNET));
            return;
        }
        if (index >= baseUrls.size()) {
            networkMonitor.markConnectionProblem();
            String message = lastError == null || lastError.trim().isEmpty() ? NetworkError.SERVER : NetworkError.safe(lastError);
            main.post(() -> callback.onError(message));
            return;
        }
        String candidateBaseUrl = index == 0 ? activeBaseUrl : baseUrls.get(index);
        RequestBody requestBody = RequestBody.create((body == null ? new JSONObject() : body).toString(), JSON);
        Request.Builder builder = new Request.Builder()
                .url(candidateBaseUrl + path);
        addAuthenticationHeaders(builder, token);
        addIdempotencyHeader(builder, body);
        if ("PATCH".equals(method)) {
            builder.patch(requestBody);
        } else if ("DELETE".equals(method)) {
            builder.delete(requestBody);
        } else {
            builder.post(requestBody);
        }
        execute(builder.build(), method, path, body, token, callback, candidateBaseUrl, index, attempt);
    }

    private void execute(Request request, String method, String path, JSONObject body, String token, JsonCallback callback, String candidateBaseUrl, int index, int attempt) {
        long startedAt = System.currentTimeMillis();
        http.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                networkMonitor.markConnectionProblem();
                retryOrFallbackWithBody(method, path, body, token, callback, index, NetworkError.forIOException(e, networkMonitor.isOnline()), attempt, e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                markRequestTiming(startedAt);
                String text = response.body() == null ? "{}" : response.body().string();
                if (!response.isSuccessful()) {
                    if (canAutoRetryBody(method, path, body) && isTransientHttp(response.code()) && attempt < MAX_TRANSIENT_RETRIES) {
                        main.postDelayed(() -> executeWithFallback(method, path, body, token, callback, index, friendlyHttpError(response.code(), text), attempt + 1), retryDelay(attempt));
                        return;
                    }
                    main.post(() -> callback.onError(friendlyHttpError(response.code(), text)));
                    return;
                }
                networkMonitor.markRequestSuccess();
                activeBaseUrl = candidateBaseUrl;
                try {
                    JSONObject object = new JSONObject(text);
                    main.post(() -> callback.onSuccess(object));
                } catch (Exception error) {
                    main.post(() -> callback.onError(error.getMessage()));
                }
            }
        });
    }

    private void retryOrFallbackWithBody(String method, String path, JSONObject body, String token, JsonCallback callback, int index, String lastError, int attempt, IOException error) {
        if (canAutoRetryBody(method, path, body) && attempt < MAX_TRANSIENT_RETRIES) {
            main.postDelayed(() -> executeWithFallback(method, path, body, token, callback, index, lastError, attempt + 1), retryDelay(attempt));
            return;
        }
        if (canAutoRetryBody(method, path, body) && !(error instanceof InterruptedIOException)) {
            executeWithFallback(method, path, body, token, callback, index + 1, lastError);
            return;
        }
        main.post(() -> callback.onError(NetworkError.safe(lastError)));
    }

    private boolean isTransientHttp(int code) {
        return code == 408 || code == 425 || code == 429 || code >= 500;
    }

    private void markCrashOperation(String method, String path) {
        String safePath = path == null ? "unknown" : path.trim();
        int query = safePath.indexOf('?');
        if (query >= 0) safePath = safePath.substring(0, query);
        String[] pieces = safePath.split("/");
        String group = pieces.length > 1 && !pieces[1].isEmpty() ? pieces[1] : "root";
        FirebaseCrashlytics.getInstance().setCustomKey(
                "api_operation",
                (method == null ? "REQUEST" : method) + " /" + group
        );
    }

    private long retryDelay(int attempt) {
        return attempt == 0 ? 700L : 1600L;
    }

    private String friendlyHttpError(int code, String body) {
        String backendMessage = "";
        String backendCode = "";
        try {
            JSONObject error = new JSONObject(body == null ? "{}" : body);
            backendMessage = error.optString("message", "").trim();
            backendCode = error.optString("code", "").trim();
        } catch (Exception ignored) {
        }
        if ((code == 400 || code == 409 || code == 422 || code == 423 || code == 503) && !backendMessage.isEmpty()) {
            return (backendCode.startsWith("APP_") ? backendCode + "\n" : "")
                    + "Request Not Completed\n" + backendMessage;
        }
        return NetworkError.forHttp(code);
    }

    private String networkError(String message) {
        return NetworkError.safe(message);
    }

    private boolean canAutoRetryBody(String method, String path, JSONObject body) {
        if ("GET".equals(method)) {
            return true;
        }
        if ("/bookings".equals(path)
                || path.contains("/payment")
                || path.contains("/status")
                || "DELETE".equals(method)
                || "PATCH".equals(method)) {
            return false;
        }
        return body != null && (!body.optString("clientMessageId", "").trim().isEmpty()
                || !body.optString("ticketId", "").trim().isEmpty());
    }

    private void addIdempotencyHeader(Request.Builder builder, JSONObject body) {
        if (body == null) {
            return;
        }
        String requestId = body.optString("clientRequestId", "").trim();
        if (requestId.isEmpty()) requestId = body.optString("bookingCode", "").trim();
        if (requestId.isEmpty()) requestId = body.optString("clientMessageId", "").trim();
        if (requestId.isEmpty()) requestId = body.optString("ticketId", "").trim();
        if (!requestId.isEmpty()) {
            builder.addHeader("Idempotency-Key", requestId);
            builder.addHeader("X-Client-Request-Id", requestId);
        }
    }

    private void markRequestTiming(long startedAt) {
        long elapsed = System.currentTimeMillis() - startedAt;
        if (elapsed >= SLOW_REQUEST_MS) {
            networkMonitor.markRequestSlow();
        }
    }

    private JsonCallback silent() {
        return new JsonCallback() {
            @Override public void onSuccess(JSONObject object) {}
            @Override public void onError(String message) {}
        };
    }

    boolean isLocalDevelopmentBackend() {
        return activeBaseUrl.startsWith("http://127.0.0.1")
                || activeBaseUrl.startsWith("http://10.0.2.2")
                || activeBaseUrl.startsWith("http://localhost");
    }

    String getDevUid() {
        return devUid;
    }

    private void addAuthenticationHeaders(Request.Builder builder, String token) {
        builder.addHeader("Authorization", "Bearer " + token);
        if (isLocalDevelopmentBackend() || LOCAL_DEVICE_TOKEN.equals(token) || "local-development".equals(token)) {
            builder.addHeader("X-ApnaServo-Dev-Uid", devUid);
            builder.addHeader("X-ApnaServo-Dev-Role", "user");
        }
    }

    private String sanitizeDeviceId(String raw) {
        String cleaned = raw == null ? "" : raw.replaceAll("[^a-zA-Z0-9._:-]", "");
        return cleaned.length() >= 6 ? cleaned : "android-device";
    }

    private List<String> buildBaseUrls() {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        addSecureUrl(urls, BuildConfig.APNA_SERVO_API_BASE_URL);
        if (urls.isEmpty()) {
            urls.add("https://apnaservobk-1.onrender.com/api");
        }
        return new ArrayList<>(urls);
    }

    private void addSecureUrl(LinkedHashSet<String> urls, String rawUrl) {
        if (rawUrl == null) {
            return;
        }
        String url = rawUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.startsWith("https://") || url.startsWith("http://")) {
            urls.add(url);
        }
    }

    interface TokenCallback {
        void onToken(String token);
        void onError(String message);
    }
}
