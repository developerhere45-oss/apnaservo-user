package com.fixmate.app;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;

/** Main-thread and lifecycle-safe facade for every user-app Toast. */
final class SafeToast {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long DUPLICATE_WINDOW_MS = 1_200L;
    private static String lastMessage = "";
    private static long lastShownAt;

    private SafeToast() { }

    static PendingToast makeText(Context context, CharSequence text, int duration) {
        return new PendingToast(context, text, duration);
    }

    static final class PendingToast {
        private final Context appContext;
        private final WeakReference<Activity> activityRef;
        private final String message;
        private final int duration;

        PendingToast(Context context, CharSequence text, int duration) {
            Context safeContext = context == null ? null : context.getApplicationContext();
            this.appContext = safeContext == null ? context : safeContext;
            this.activityRef = new WeakReference<>(context instanceof Activity ? (Activity) context : null);
            this.message = text == null ? "" : text.toString();
            this.duration = duration;
        }

        void show() {
            Runnable display = () -> {
                if (appContext == null || message.trim().isEmpty()) return;
                Activity activity = activityRef.get();
                if (activity != null && (activity.isFinishing()
                        || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()))) {
                    return;
                }
                long now = android.os.SystemClock.elapsedRealtime();
                if (message.equals(lastMessage) && now - lastShownAt < DUPLICATE_WINDOW_MS) return;
                lastMessage = message;
                lastShownAt = now;
                android.widget.Toast.makeText(appContext, message, duration).show();
            };
            if (Looper.myLooper() == Looper.getMainLooper()) display.run();
            else MAIN.post(display);
        }
    }
}
