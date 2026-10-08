package com.fixmate.app;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.fixmate.app.util.SecurePrefs;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

public class ApnaServoMessagingService extends FirebaseMessagingService {
    private static final String PREFS = "fixmate_prefs_v2";
    private static final String SECURE_PREFS = PREFS + "_secure";
    private static final String KEY_FCM_TOKEN = "fcm_token";
    private static final String KEY_PUSH_BOOKING_ID = "push_booking_id";
    private static final String KEY_PUSH_BOOKING_STATUS = "push_booking_status";
    private static final String KEY_PUSH_ACTION_TYPE = "push_action_type";
    private static final String KEY_PUSH_ACTION_ID = "push_action_id";
    private static final String KEY_PUSH_NOTIFICATION_ID = "push_notification_id";
    private static final String KEY_SEEN_NOTIFICATION_IDS = "seen_admin_notification_ids";
    private static final String CHANNEL_ADMIN = "admin_announcements";
    private static final String CHANNEL_OFFERS = "offers_promotions";
    private static final String CHANNEL_BOOKINGS = "booking_updates";

    @Override
    public void onNewToken(String token) {
        securePrefs().putString(KEY_FCM_TOKEN, token == null ? "" : token);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_FCM_TOKEN).apply();
        new BackendClient(this).saveFcmToken(token == null ? "" : token);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        Map<String, String> data = message.getData();
        String targetApp = valueAny(data, "", "targetApp", "target_app", "app", "recipientRole", "recipient_role");
        if (!isUserTarget(targetApp)) {
            return;
        }

        String notificationId = valueAny(data, "", "notificationId", "notification_id", "push_notification_id", "id");
        if (!notificationId.isEmpty() && alreadyShown(notificationId)) {
            return;
        }

        String bookingId = valueAny(data, "", "bookingId", "booking_id", "bookingCode", "booking_code");
        String status = valueAny(data, "", "status", "bookingStatus", "booking_status");
        String type = valueAny(data, "", "type", "event", "notificationType", "notification_type");
        String category = valueAny(data, "", "category", "notificationCategory", "notification_category");
        String actionType = valueAny(data, "", "actionType", "action_type", "push_action_type", "click_action", "screen", "route");
        String actionId = valueAny(data, "", "actionId", "action_id", "push_action_id", "targetId", "target_id");
        actionType = normalizeActionType(actionType, type, category, status, bookingId, actionId);
        if (actionId.isEmpty() && isBookingAction(actionType)) {
            actionId = bookingId;
        }
        if (bookingId.isEmpty() && isBookingAction(actionType)) {
            bookingId = actionId;
        }
        if (!bookingId.isEmpty() || !status.isEmpty()) {
            securePrefs().putString(KEY_PUSH_BOOKING_ID, bookingId);
            securePrefs().putString(KEY_PUSH_BOOKING_STATUS, status);
        }

        String title = message.getNotification() == null
                ? valueAny(data, "", "title", "notificationTitle", "notification_title")
                : message.getNotification().getTitle();
        String body = message.getNotification() == null
                ? valueAny(data, "", "body", "message", "notificationBody", "notification_body")
                : message.getNotification().getBody();
        title = compact(readableTitle(title, actionType, status), 120);
        body = compact(readableBody(body, actionType, status, bookingId, type), 500);
        String arrivalLabel = valueAny(data, "", "partnerArrivalEstimateLabel", "arrivalEstimateLabel", "etaLabel", "eta");
        boolean partnerAccepted = "accepted".equalsIgnoreCase(status)
                || safe(type).toLowerCase(Locale.ROOT).contains("accepted")
                || "booking_assigned".equalsIgnoreCase(category);
        if (partnerAccepted && !arrivalLabel.isEmpty()) {
            if (title.isEmpty() || "Partner assigned".equalsIgnoreCase(title) || "Booking update".equalsIgnoreCase(title)) {
                title = compact("Partner arriving in " + arrivalLabel, 120);
            }
            if (body.isEmpty() || !body.toLowerCase(Locale.ROOT).contains(arrivalLabel.toLowerCase(Locale.ROOT))) {
                body = compact("Partner arriving in " + arrivalLabel + ". Tap to track your booking.", 500);
            }
        }
        String imageUrl = valueAny(data, "", "imageUrl", "image_url", "bannerImageUrl", "banner_image_url");
        showNotification(
                title,
                body,
                bookingId,
                status,
                actionType,
                actionId,
                notificationId,
                imageUrl
        );
    }

    private void showNotification(
            String title,
            String body,
            String bookingId,
            String status,
            String actionType,
            String actionId,
            String notificationId,
            String imageUrl
    ) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        String channelId = channelFor(actionType, bookingId);
        ensureChannels(manager);

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(KEY_PUSH_BOOKING_ID, bookingId);
        intent.putExtra(KEY_PUSH_BOOKING_STATUS, status);
        intent.putExtra(KEY_PUSH_ACTION_TYPE, actionType);
        intent.putExtra(KEY_PUSH_ACTION_ID, actionId);
        intent.putExtra(KEY_PUSH_NOTIFICATION_ID, notificationId);
        int requestCode = notificationId.isEmpty() ? (int) System.currentTimeMillis() : Math.abs(notificationId.hashCode());
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(Notification.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
        manager.notify(requestCode, builder.build());
        loadRichNotificationImage(manager, builder, requestCode, imageUrl, body);
    }

    private void loadRichNotificationImage(
            NotificationManager manager,
            Notification.Builder builder,
            int requestCode,
            String imageUrl,
            String body
    ) {
        String safeUrl = imageUrl == null ? "" : imageUrl.trim();
        if (!safeUrl.startsWith("https://") || safeUrl.length() > 2000) {
            return;
        }
        RequestOptions options = new RequestOptions()
                .override(1024, 512)
                .format(DecodeFormat.PREFER_RGB_565)
                .dontAnimate();
        Glide.with(getApplicationContext())
                .asBitmap()
                .load(safeUrl)
                .apply(options)
                .into(new CustomTarget<Bitmap>(1024, 512) {
                    @Override
                    public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                        builder.setStyle(new Notification.BigPictureStyle()
                                .bigPicture(resource)
                                .setSummaryText(body));
                        manager.notify(requestCode, builder.build());
                    }

                    @Override
                    public void onLoadCleared(Drawable placeholder) {
                        // The text notification remains visible when the optional image is unavailable.
                    }
                });
    }

    private void ensureChannels(NotificationManager manager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        manager.createNotificationChannel(channel(CHANNEL_ADMIN, "Admin announcements", "Important ApnaServo announcements"));
        manager.createNotificationChannel(channel(CHANNEL_OFFERS, "Offers and promotions", "Offers, discounts and promotional updates"));
        manager.createNotificationChannel(channel(CHANNEL_BOOKINGS, "Booking updates", "Booking status and service updates"));
    }

    @TargetApi(Build.VERSION_CODES.O)
    private NotificationChannel channel(String id, String name, String description) {
        NotificationChannel channel = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(description);
        channel.enableVibration(true);
        return channel;
    }

    private String channelFor(String actionType, String bookingId) {
        if ("OPEN_OFFERS".equalsIgnoreCase(actionType)) {
            return CHANNEL_OFFERS;
        }
        if (!bookingId.isEmpty() || "OPEN_BOOKING".equalsIgnoreCase(actionType)) {
            return CHANNEL_BOOKINGS;
        }
        return CHANNEL_ADMIN;
    }

    private boolean isUserTarget(String targetApp) {
        if (targetApp == null || targetApp.trim().isEmpty()) return true;
        String normalized = targetApp.trim().toUpperCase(Locale.ROOT);
        return normalized.contains("USER") || normalized.contains("CUSTOMER") || normalized.equals("ALL");
    }

    private String normalizeActionType(String actionType, String type, String category, String status, String bookingId, String actionId) {
        String normalized = actionType == null ? "" : actionType.trim().toUpperCase(Locale.ROOT);
        String typeValue = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        String categoryValue = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        String statusValue = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || "NONE".equals(normalized)) {
            if (typeValue.contains("chat") || categoryValue.equals("chat")) return "OPEN_BOOKING_CHAT";
            if (categoryValue.contains("support") || typeValue.contains("support")) return "OPEN_SUPPORT";
            if (categoryValue.contains("payment") || statusValue.equals("amount_pending") || typeValue.contains("payment")) return "OPEN_PAYMENT";
            if (!safe(bookingId).isEmpty() || !safe(actionId).isEmpty() || typeValue.startsWith("booking:") || categoryValue.startsWith("booking")) {
                return "OPEN_BOOKING";
            }
            return "OPEN_HOME";
        }
        if ("BOOKING_CHAT".equals(normalized) || "CHAT".equals(normalized)) return "OPEN_BOOKING_CHAT";
        if ("BOOKING".equals(normalized) || "BOOKING_DETAIL".equals(normalized) || "CURRENT_BOOKING".equals(normalized)) return "OPEN_BOOKING";
        if ("PAYMENT".equals(normalized)) return "OPEN_PAYMENT";
        return normalized;
    }

    private boolean isBookingAction(String actionType) {
        String normalized = actionType == null ? "" : actionType.trim().toUpperCase(Locale.ROOT);
        return "OPEN_BOOKING".equals(normalized)
                || "OPEN_BOOKING_CHAT".equals(normalized)
                || "OPEN_PAYMENT".equals(normalized)
                || "OPEN_BOOKING_HISTORY".equals(normalized);
    }

    private String readableTitle(String title, String actionType, String status) {
        String clean = title == null ? "" : title.trim();
        if (!clean.isEmpty() && !"ApnaServo".equalsIgnoreCase(clean) && !"Notification".equalsIgnoreCase(clean)) {
            return clean;
        }
        String action = safe(actionType).toUpperCase(Locale.ROOT);
        String state = safe(status).toLowerCase(Locale.ROOT);
        if ("OPEN_BOOKING_CHAT".equals(action)) return "New chat message";
        if ("OPEN_PAYMENT".equals(action) || "amount_pending".equals(state)) return "Payment update";
        if ("accepted".equals(state)) return "Partner assigned";
        if ("on_the_way".equals(state)) return "Partner is on the way";
        if ("arrived".equals(state)) return "Partner has arrived";
        if ("started".equals(state)) return "Service started";
        if ("completed".equals(state)) return "Service completed";
        if ("cancelled".equals(state)) return "Booking cancelled";
        if ("OPEN_BOOKING".equals(action)) return "Booking update";
        return "ApnaServo update";
    }

    private String readableBody(String body, String actionType, String status, String bookingId, String type) {
        String clean = body == null ? "" : body.trim();
        if (!clean.isEmpty() && !"New update received".equalsIgnoreCase(clean) && !"Open the app to view the latest update".equalsIgnoreCase(clean)) {
            return clean;
        }
        String action = safe(actionType).toUpperCase(Locale.ROOT);
        String state = safe(status).toLowerCase(Locale.ROOT);
        String code = safe(bookingId).isEmpty() ? "your booking" : "booking " + bookingId;
        if ("OPEN_BOOKING_CHAT".equals(action) || safe(type).toLowerCase(Locale.ROOT).contains("chat")) return "Tap to open the booking chat.";
        if ("OPEN_PAYMENT".equals(action) || "amount_pending".equals(state)) return "Tap to review payment for " + code + ".";
        if ("accepted".equals(state)) return "A verified partner accepted " + code + ". Tap to view status.";
        if ("on_the_way".equals(state)) return "Your partner is on the way. Tap to track live status.";
        if ("arrived".equals(state)) return "Your partner has reached your location.";
        if ("started".equals(state)) return "Your service is now in progress.";
        if ("completed".equals(state)) return "Your service has been completed.";
        if ("cancelled".equals(state)) return "This booking has been cancelled.";
        return "Tap to open " + code + ".";
    }

    private String valueAny(Map<String, String> data, String fallback, String... keys) {
        if (data == null || keys == null) return fallback;
        for (String key : keys) {
            String result = data.get(key);
            if (result != null && !result.trim().isEmpty()) return compact(result, 500);
        }
        return fallback;
    }

    private String safe(String value) {
        return compact(value, 500);
    }

    private String compact(String value, int maximumLength) {
        String clean = value == null ? "" : value.trim();
        return clean.length() <= maximumLength ? clean : clean.substring(0, maximumLength);
    }

    private boolean alreadyShown(String notificationId) {
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_SEEN_NOTIFICATION_IDS, "");
        Set<String> ids = new LinkedHashSet<>();
        if (raw != null && !raw.trim().isEmpty()) {
            for (String id : raw.split(",")) {
                if (!id.trim().isEmpty()) ids.add(id.trim());
            }
        }
        if (ids.contains(notificationId)) {
            return true;
        }
        ids.add(notificationId);
        while (ids.size() > 100) {
            ids.remove(ids.iterator().next());
        }
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_SEEN_NOTIFICATION_IDS, String.join(",", ids))
                .apply();
        return false;
    }

    private String value(Map<String, String> data, String key, String fallback) {
        if (data == null) return fallback;
        String result = data.get(key);
        return result == null || result.trim().isEmpty() ? fallback : compact(result, 500);
    }

    private SecurePrefs securePrefs() {
        return new SecurePrefs(this, SECURE_PREFS);
    }
}
