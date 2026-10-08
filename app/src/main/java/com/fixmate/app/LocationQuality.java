package com.fixmate.app;

import android.location.Location;
import android.os.Build;
import android.os.SystemClock;

/** Never treat an old, approximate or mock fix as a current service location. */
final class LocationQuality {
    private LocationQuality() {}

    static boolean usable(Location fix) {
        if (fix == null || !fix.hasAccuracy()) return false;
        if ((Build.VERSION.SDK_INT >= 31 ? fix.isMock() : fix.isFromMockProvider()) && !BuildConfig.DEBUG) return false;
        return valid(fix.getLatitude(), fix.getLongitude(), fix.getAccuracy(),
                fix.getElapsedRealtimeNanos(), SystemClock.elapsedRealtimeNanos());
    }

    /** A wider accuracy window is safe for the header label only. It must not
     * be used for booking dispatch or service-area authorization. */
    static boolean usableForHeader(Location fix) {
        if (fix == null || !fix.hasAccuracy()) return false;
        if ((Build.VERSION.SDK_INT >= 31 ? fix.isMock() : fix.isFromMockProvider()) && !BuildConfig.DEBUG) return false;
        long age = SystemClock.elapsedRealtimeNanos() - fix.getElapsedRealtimeNanos();
        double lat = fix.getLatitude(), lng = fix.getLongitude();
        return !Double.isNaN(lat) && !Double.isInfinite(lat) && !Double.isNaN(lng) && !Double.isInfinite(lng)
                && Math.abs(lat) <= 90 && Math.abs(lng) <= 180 && !(lat == 0 && lng == 0)
                && fix.getAccuracy() > 0 && fix.getAccuracy() <= 500
                && fix.getElapsedRealtimeNanos() > 0 && age >= 0 && age <= 300_000_000_000L;
    }

    static boolean valid(double lat, double lng, float accuracy, long fixNanos, long nowNanos) {
        long age = nowNanos - fixNanos;
        return !Double.isNaN(lat) && !Double.isInfinite(lat) && !Double.isNaN(lng) && !Double.isInfinite(lng)
                && Math.abs(lat) <= 90 && Math.abs(lng) <= 180 && !(lat == 0 && lng == 0)
                && !Float.isNaN(accuracy) && !Float.isInfinite(accuracy) && accuracy > 0 && accuracy <= 50
                && fixNanos > 0 && age >= 0 && age <= 60_000_000_000L;
    }

    static boolean better(Location candidate, Location current) {
        if (!usable(candidate)) return false;
        if (!usable(current)) return true;
        return betterFix(candidate.getAccuracy(), candidate.getElapsedRealtimeNanos(),
                current.getAccuracy(), current.getElapsedRealtimeNanos());
    }

    static boolean betterFix(float candidateAccuracy, long candidateTime,
            float currentAccuracy, long currentTime) {
        long delta = candidateTime - currentTime;
        if (delta < -5_000_000_000L) return false;
        // A newer network estimate must not displace a much more precise fresh GPS fix.
        return candidateAccuracy <= currentAccuracy
                || (delta > 15_000_000_000L && candidateAccuracy <= currentAccuracy + 10f);
    }
}
