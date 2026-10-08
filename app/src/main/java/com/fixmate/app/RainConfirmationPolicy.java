package com.fixmate.app;

final class RainConfirmationPolicy {
    private RainConfirmationPolicy() { }

    static boolean isMeasuredRain(double precipitationMm, int weatherCode) {
        return precipitationMm >= 0.10d && isRainCode(weatherCode);
    }

    /**
     * A weather condition code alone is not enough for a customer-facing rain advisory.
     * Require a meaningful current precipitation reading and a non-zero liquid-rain or
     * shower reading from the provider. This keeps forecast-only or stale wet codes from
     * triggering the visual effect.
     */
    static boolean isMeasuredRain(double precipitationMm, double rainMm, double showersMm, int weatherCode) {
        double liquidRain = Math.max(0d, rainMm) + Math.max(0d, showersMm);
        return precipitationMm >= 0.20d && liquidRain >= 0.10d && isRainCode(weatherCode);
    }

    static Result observe(boolean wetSample, long now, long candidateSince, long lastSeenAt,
            boolean sameArea, long confirmationMillis, long maximumGapMillis) {
        if (!wetSample) return new Result(0L, 0L, false, true);
        boolean continuous = candidateSince > 0L && lastSeenAt > 0L && now >= candidateSince
                && now - lastSeenAt <= maximumGapMillis && sameArea;
        long effectiveSince = continuous ? candidateSince : now;
        boolean confirmed = now - effectiveSince >= confirmationMillis;
        return new Result(effectiveSince, now, confirmed, false);
    }

    private static boolean isRainCode(int code) {
        return code >= 51 && code <= 67 || code >= 80 && code <= 82 || code >= 95 && code <= 99;
    }

    static final class Result {
        final long candidateSince;
        final long lastSeenAt;
        final boolean confirmedRain;
        final boolean reset;

        Result(long candidateSince, long lastSeenAt, boolean confirmedRain, boolean reset) {
            this.candidateSince = candidateSince;
            this.lastSeenAt = lastSeenAt;
            this.confirmedRain = confirmedRain;
            this.reset = reset;
        }
    }
}
