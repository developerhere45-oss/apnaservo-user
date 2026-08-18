package com.fixmate.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.net.URI;

final class AppControlConfig {
    enum AppMode { LIVE, PARTIALLY_UNAVAILABLE, MAINTENANCE, TEMPORARILY_UNAVAILABLE }
    enum ServiceStatus { AVAILABLE, PREPARING, HIGH_DEMAND, TEMPORARILY_UNAVAILABLE, DISABLED }

    static final AppControlConfig DEFAULT = new AppControlConfig("", "", AppMode.LIVE,
            Launch.none(), Update.none(), Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptyList(), Collections.emptyList(), "", "", Collections.emptyList());

    final String version;
    final String updatedAt;
    final AppMode mode;
    final Launch launch;
    final Update update;
    final Map<String, Feature> features;
    final Map<String, ServiceRule> services;
    final List<ContentItem> announcements;
    final List<ContentItem> banners;
    final String uiHomeTitle;
    final String uiHomeSubtitle;
    final List<String> uiHiddenSections;

    private AppControlConfig(String version, String updatedAt, AppMode mode,
            Launch launch, Update update, Map<String, Feature> features,
            Map<String, ServiceRule> services, List<ContentItem> announcements, List<ContentItem> banners, String uiHomeTitle, String uiHomeSubtitle, List<String> uiHiddenSections) {
        this.version = version;
        this.updatedAt = updatedAt;
        this.mode = mode;
        this.launch = launch;
        this.update = update;
        this.features = Collections.unmodifiableMap(features);
        this.services = Collections.unmodifiableMap(services);
        this.announcements = Collections.unmodifiableList(announcements);
        this.banners = Collections.unmodifiableList(banners);
        this.uiHomeTitle = uiHomeTitle;
        this.uiHomeSubtitle = uiHomeSubtitle;
        this.uiHiddenSections = Collections.unmodifiableList(uiHiddenSections);
    }

    static AppControlConfig parse(JSONObject root) {
        if (root == null) return DEFAULT;
        JSONObject config = root.optJSONObject("config");
        if (config == null) config = new JSONObject();
        JSONObject status = config.optJSONObject("appStatus");
        if (status == null) status = new JSONObject();
        AppMode mode = enumValue(AppMode.class, status.optString("mode"), AppMode.LIVE);

        Map<String, Feature> features = new LinkedHashMap<>();
        JSONObject featureJson = config.optJSONObject("features");
        if (featureJson != null) {
            for (String key : keys(featureJson)) {
                JSONObject value = featureJson.optJSONObject(key);
                if (value == null) continue;
                features.put(normalizeKey(key), new Feature(value.optBoolean("enabled", true),
                        safe(value, "audience"), safe(value, "startsAt"), safe(value, "endsAt"),
                        safe(value, "description")));
            }
        }

        Map<String, ServiceRule> services = new LinkedHashMap<>();
        JSONObject serviceJson = config.optJSONObject("services");
        if (serviceJson != null) {
            for (String key : keys(serviceJson)) {
                JSONObject value = serviceJson.optJSONObject(key);
                if (value == null) continue;
                services.put(normalizeServiceKey(key), new ServiceRule(
                        enumValue(ServiceStatus.class, value.optString("status"), ServiceStatus.AVAILABLE),
                        safe(value, "message"), safe(value, "startsAt"), safe(value, "endsAt")));
            }
        }

        JSONObject ui = config.optJSONObject("ui");
        return new AppControlConfig(safe(root, "version"), safe(root, "updatedAt"), mode,
                Launch.parse(config.optJSONObject("launch")),
                Update.parse(config.optJSONObject("update")), features, services,
                parseContent(root.optJSONArray("announcements")), parseContent(root.optJSONArray("banners")),
                ui == null ? "" : safe(ui, "homeTitle"), ui == null ? "" : safe(ui, "homeSubtitle"),
                ui == null ? Collections.emptyList() : strings(ui.optJSONArray("hiddenSections")));
    }

    boolean blocksBooking() {
        return mode == AppMode.MAINTENANCE || mode == AppMode.TEMPORARILY_UNAVAILABLE;
    }

    static AppControlConfig withMode(AppControlConfig source, AppMode mode) {
        if (source == null) source = DEFAULT;
        return new AppControlConfig(source.version, source.updatedAt, mode, source.launch, source.update,
                new LinkedHashMap<>(source.features), new LinkedHashMap<>(source.services),
                new ArrayList<>(source.announcements), new ArrayList<>(source.banners), source.uiHomeTitle,
                source.uiHomeSubtitle, new ArrayList<>(source.uiHiddenSections));
    }

    boolean uiSectionVisible(String id) { return !uiHiddenSections.contains(id); }

    private static List<String> strings(JSONArray values) { List<String> result = new ArrayList<>(); if (values != null) for (int i = 0; i < values.length(); i++) { String value = values.optString(i, "").trim(); if (!value.isEmpty()) result.add(value); } return result; }

    boolean featureEnabled(String name) {
        Feature feature = features.get(normalizeKey(name));
        return feature == null || (feature.enabled && feature.activeNow());
    }

    ServiceRule serviceRule(String id, String category, String name) {
        String[] candidates = {normalizeServiceKey(id), normalizeServiceKey(category), normalizeServiceKey(name)};
        for (String candidate : candidates) {
            ServiceRule rule = services.get(candidate);
            if (rule != null && rule.activeNow()) return rule;
        }
        return ServiceRule.AVAILABLE;
    }

    static int compareVersions(String left, String right) {
        String[] a = cleanVersion(left).split("\\.");
        String[] b = cleanVersion(right).split("\\.");
        int count = Math.max(a.length, b.length);
        for (int i = 0; i < count; i++) {
            int av = i < a.length ? numberPrefix(a[i]) : 0;
            int bv = i < b.length ? numberPrefix(b[i]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    static boolean isAllowedPlayStoreUrl(String rawUrl, String expectedPackage) {
        if (rawUrl == null || expectedPackage == null || expectedPackage.trim().isEmpty()) return false;
        try {
            URI uri = new URI(rawUrl.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.US);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.US);
            boolean officialWeb = "https".equals(scheme) && "play.google.com".equals(host)
                    && "/store/apps/details".equals(uri.getPath());
            boolean marketApp = "market".equals(scheme) && "details".equals(host);
            if (!officialWeb && !marketApp) return false;
            String query = uri.getRawQuery();
            if (query == null) return false;
            for (String pair : query.split("&")) {
                int separator = pair.indexOf('=');
                if (separator > 0 && "id".equals(pair.substring(0, separator))
                        && expectedPackage.equals(pair.substring(separator + 1))) return true;
            }
            return false;
        } catch (Exception ignored) { return false; }
    }

    static final class Update {
        final boolean enabled;
        final boolean force;
        final String latestVersion;
        final String minimumVersion;
        final String title;
        final String message;
        final String buttonText;
        final String storeUrl;

        Update(boolean enabled, boolean force, String latestVersion, String minimumVersion,
                String title, String message, String buttonText, String storeUrl) {
            this.enabled = enabled;
            this.force = force;
            this.latestVersion = latestVersion;
            this.minimumVersion = minimumVersion;
            this.title = title;
            this.message = message;
            this.buttonText = buttonText;
            this.storeUrl = storeUrl;
        }

        static Update parse(JSONObject value) {
            if (value == null) return none();
            return new Update(value.optBoolean("enabled", false),
                    "force".equalsIgnoreCase(value.optString("type")), safe(value, "latestVersion"),
                    safe(value, "minimumVersion"), safe(value, "title"), safe(value, "message"),
                    safe(value, "buttonText"), safe(value, "storeUrl"));
        }

        static Update none() { return new Update(false, false, "", "", "", "", "", ""); }
    }

    static final class Launch {
        final boolean enabled;
        final String launchAt, timezone, title, dateText, description, ctaText, imageUrl;
        Launch(boolean enabled, String launchAt, String timezone, String title, String dateText,
                String description, String ctaText, String imageUrl) {
            this.enabled = enabled; this.launchAt = launchAt; this.timezone = timezone; this.title = title;
            this.dateText = dateText; this.description = description; this.ctaText = ctaText; this.imageUrl = imageUrl;
        }
        static Launch parse(JSONObject value) {
            if (value == null) return none();
            return new Launch(value.optBoolean("enabled", false), safe(value, "launchAt"), safe(value, "timezone"),
                    safe(value, "title"), safe(value, "dateText"), safe(value, "description"),
                    safe(value, "ctaText"), safe(value, "imageUrl"));
        }
        static Launch none() { return new Launch(false, "", "", "", "", "", "", ""); }
    }

    static final class Feature {
        final boolean enabled;
        final String audience, startsAt, endsAt, description;
        Feature(boolean enabled, String audience, String startsAt, String endsAt, String description) {
            this.enabled = enabled;
            this.audience = audience;
            this.startsAt = startsAt;
            this.endsAt = endsAt;
            this.description = description;
        }
        boolean activeNow() {
            String target = audience == null ? "" : audience.trim().toLowerCase(Locale.US);
            boolean userAudience = target.isEmpty() || "all".equals(target) || "user".equals(target)
                    || "users".equals(target) || "customer".equals(target) || "customers".equals(target);
            return userAudience && TimeWindow.active(startsAt, endsAt);
        }
    }

    static final class ServiceRule {
        static final ServiceRule AVAILABLE = new ServiceRule(ServiceStatus.AVAILABLE, "", "", "");
        final ServiceStatus status;
        final String message, startsAt, endsAt;
        ServiceRule(ServiceStatus status, String message, String startsAt, String endsAt) {
            this.status = status;
            this.message = message;
            this.startsAt = startsAt;
            this.endsAt = endsAt;
        }
        boolean activeNow() { return TimeWindow.active(startsAt, endsAt); }
        boolean bookable() { return status == ServiceStatus.AVAILABLE || status == ServiceStatus.HIGH_DEMAND; }
    }

    static final class ContentItem {
        final String id, title, message, imageUrl, ctaText, ctaAction, serviceCategory, placement;
        final int priority;
        ContentItem(String id, String title, String message, String imageUrl, String ctaText,
                String ctaAction, String serviceCategory, String placement, int priority) {
            this.id = id; this.title = title; this.message = message; this.imageUrl = imageUrl;
            this.ctaText = ctaText; this.ctaAction = ctaAction; this.serviceCategory = serviceCategory;
            this.placement = placement; this.priority = priority;
        }
    }

    private static List<ContentItem> parseContent(JSONArray array) {
        List<ContentItem> result = new ArrayList<>();
        if (array == null) return result;
        for (int i = 0; i < Math.min(array.length(), 20); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String id = safe(item, "id");
            String title = safe(item, "title");
            String message = safe(item, "message");
            if (id.isEmpty() || (title.isEmpty() && message.isEmpty())) continue;
            result.add(new ContentItem(id, title, message, safe(item, "imageUrl"), safe(item, "ctaText"),
                    safe(item, "ctaAction"), safe(item, "serviceCategory"), safe(item, "placement"),
                    Math.max(-1000, Math.min(1000, item.optInt("priority", 0)))));
        }
        Collections.sort(result, (a, b) -> Integer.compare(b.priority, a.priority));
        return result;
    }

    private static final class TimeWindow {
        static boolean active(String start, String end) {
            long now = System.currentTimeMillis();
            long from = parseInstant(start);
            long until = parseInstant(end);
            return (from <= 0 || now >= from) && (until <= 0 || now <= until);
        }
        private static long parseInstant(String value) {
            if (value == null || value.trim().isEmpty()) return 0;
            try {
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    return java.time.Instant.parse(value.trim()).toEpochMilli();
                }
                java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                java.util.Date date = format.parse(value.substring(0, Math.min(19, value.length())));
                return date == null ? 0 : date.getTime();
            } catch (Exception ignored) { return 0; }
        }
    }

    private static List<String> keys(JSONObject object) {
        List<String> result = new ArrayList<>();
        java.util.Iterator<String> iterator = object.keys();
        while (iterator.hasNext()) result.add(iterator.next());
        return result;
    }

    private static String safe(JSONObject object, String key) {
        String value = object == null ? "" : object.optString(key, "");
        if ("null".equalsIgnoreCase(value)) return "";
        return value.length() > 1000 ? value.substring(0, 1000) : value.trim();
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US).replace('-', '_').replace(' ', '_');
    }

    private static String normalizeServiceKey(String value) {
        String key = normalizeKey(value).replace("&", "and");
        if (key.equals("ac_repair") || key.equals("ac_service") || key.equals("ac_repair_and_service")) return "ac";
        if (key.equals("electrical") || key.equals("electrical_service")) return "electrician";
        if (key.equals("plumber") || key.equals("plumbing_service")) return "plumbing";
        if (key.equals("carpentry") || key.equals("carpenter_service")) return "carpenter";
        if (key.equals("cleaning_services") || key.equals("home_cleaning")) return "cleaning";
        if (key.equals("appliance") || key.equals("appliance_repair")) return "appliances";
        if (key.equals("ro_service") || key.equals("water_purifier")) return "ro";
        if (key.equals("pest_control")) return "pest";
        if (key.equals("roadside_assistance")) return "roadside";
        if (key.equals("interior_design")) return "interior";
        return key;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try { return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase(Locale.US)); }
        catch (Exception ignored) { return fallback; }
    }

    private static String cleanVersion(String value) {
        if (value == null) return "0";
        String clean = value.trim().toLowerCase(Locale.US);
        return clean.startsWith("v") ? clean.substring(1) : clean;
    }

    private static int numberPrefix(String value) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length() && Character.isDigit(value.charAt(i)); i++) digits.append(value.charAt(i));
        try { return digits.length() == 0 ? 0 : Integer.parseInt(digits.toString()); }
        catch (Exception ignored) { return 0; }
    }
}
