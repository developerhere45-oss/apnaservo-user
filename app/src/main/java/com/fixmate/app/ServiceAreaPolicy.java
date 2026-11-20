package com.fixmate.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ServiceAreaPolicy {
    enum State { AVAILABLE, UNAVAILABLE, CHECKING, UNKNOWN }

    private static volatile List<Area> areas = Collections.singletonList(new Area("guwahati", 26.1445d, 91.7362d, 35d));

    private ServiceAreaPolicy() { }

    /** Uses the published admin configuration. Invalid remote data never removes the bundled fallback. */
    static void configure(JSONArray remoteAreas) {
        if (remoteAreas == null) return;
        List<Area> parsed = new ArrayList<>();
        for (int i = 0; i < Math.min(remoteAreas.length(), 50); i++) {
            JSONObject item = remoteAreas.optJSONObject(i);
            if (item == null || !item.optBoolean("enabled", true)) continue;
            double lat = item.optDouble("centerLat", Double.NaN);
            double lng = item.optDouble("centerLng", Double.NaN);
            double radius = item.optDouble("radiusKm", Double.NaN);
            if (!Double.isFinite(lat) || !Double.isFinite(lng) || !Double.isFinite(radius)
                    || lat < -90d || lat > 90d || lng < -180d || lng > 180d
                    || radius < 1d || radius > 250d || (lat == 0d && lng == 0d)) continue;
            parsed.add(new Area(item.optString("id", "area" + i), lat, lng, radius));
        }
        if (!parsed.isEmpty()) areas = Collections.unmodifiableList(parsed);
    }

    static State evaluate(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90d || latitude > 90d
                || longitude < -180d || longitude > 180d
                || (latitude == 0d && longitude == 0d)) {
            return State.UNKNOWN;
        }
        for (Area area : areas) {
            if (distanceKm(latitude, longitude, area.latitude, area.longitude) <= area.radiusKm) {
                return State.AVAILABLE;
            }
        }
        return State.UNAVAILABLE;
    }

    static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2d) * Math.sin(dLat / 2d)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2d) * Math.sin(dLng / 2d);
        return 6371.0088d * 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
    }

    private static final class Area {
        final String id;
        final double latitude;
        final double longitude;
        final double radiusKm;
        Area(String id, double latitude, double longitude, double radiusKm) {
            this.id = id;
            this.latitude = latitude;
            this.longitude = longitude;
            this.radiusKm = radiusKm;
        }
    }
}
