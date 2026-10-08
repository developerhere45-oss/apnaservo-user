package com.fixmate.app;

final class ServiceAreaPolicy {
    enum State { AVAILABLE, UNAVAILABLE, CHECKING, UNKNOWN }

    // Same production boundary as the backend. This is deliberately centralized here;
    // screens must not compare city-name strings or define their own coordinates.
    static final double GUWAHATI_CENTER_LAT = 26.1445d;
    static final double GUWAHATI_CENTER_LNG = 91.7362d;
    static final double GUWAHATI_RADIUS_KM = 35d;

    private ServiceAreaPolicy() { }

    static State evaluate(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90d || latitude > 90d
                || longitude < -180d || longitude > 180d
                || (latitude == 0d && longitude == 0d)) {
            return State.UNKNOWN;
        }
        return distanceKm(latitude, longitude, GUWAHATI_CENTER_LAT, GUWAHATI_CENTER_LNG)
                <= GUWAHATI_RADIUS_KM ? State.AVAILABLE : State.UNAVAILABLE;
    }

    static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2d) * Math.sin(dLat / 2d)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2d) * Math.sin(dLng / 2d);
        return 6371.0088d * 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
    }
}
