package com.fixmate.app;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

final class NetworkError {
    static final String NO_INTERNET = "No Internet Connection\nPlease make sure your internet connection is turned on and try again.";
    static final String SLOW = "Connection Is Slow\nYour internet connection seems unstable. Please check your network and try again.";
    static final String TIMEOUT = "Connection Is Taking Too Long\nWe're having trouble connecting to the server. Please check your internet connection and try again.";
    static final String SERVER = "Something Went Wrong\nWe couldn't connect to the server right now. Please try again.";
    static final String BOOKING_SAFE = "Connection Lost\nYour booking is safe. We're having trouble updating the latest information. We'll reconnect automatically.";
    static final String FINDING_SAFE = "Connection Lost\nYour search is still active. Please check your internet connection. We'll reconnect automatically.";
    static final String PARTNER_ASSIGNED_SAFE = "Connection Lost\nYour partner is assigned. We're temporarily unable to update the latest information.";

    private NetworkError() {
    }

    static String forIOException(IOException error, boolean online) {
        if (!online || error instanceof UnknownHostException || error instanceof ConnectException) {
            return NO_INTERNET;
        }
        if (error instanceof SocketTimeoutException || error instanceof InterruptedIOException) {
            return TIMEOUT;
        }
        return SERVER;
    }

    static String forHttp(int code) {
        if (code == 401) {
            return "Session Expired\nPlease login again to continue.";
        }
        if (code == 403) {
            return "Action Not Allowed\nYou are signed in, but this action is not allowed.";
        }
        if (code == 404) {
            return "Not Found\nWe couldn't find the requested information. Please refresh and try again.";
        }
        if (code == 408 || code == 425 || code == 429) {
            return SLOW;
        }
        if (code >= 500) {
            return SERVER;
        }
        return "Request Not Completed\nPlease check the details and try again.";
    }

    static boolean isConnectionProblem(String message) {
        String normalized = safe(message).toLowerCase(Locale.US);
        return normalized.contains("no internet")
                || normalized.contains("connection is slow")
                || normalized.contains("connection is taking too long")
                || normalized.contains("connection lost")
                || normalized.contains("couldn't connect")
                || normalized.contains("network");
    }

    static boolean isTimeout(String message) {
        return safe(message).toLowerCase(Locale.US).contains("taking too long");
    }

    static String title(String message) {
        String safe = safe(message);
        int split = safe.indexOf('\n');
        return split > 0 ? safe.substring(0, split) : "Something Went Wrong";
    }

    static String body(String message) {
        String safe = safe(message);
        int split = safe.indexOf('\n');
        if (split >= 0 && split + 1 < safe.length()) {
            return safe.substring(split + 1);
        }
        return "Please try again.";
    }

    static String safe(String message) {
        if (message == null || message.trim().isEmpty()) {
            return SERVER;
        }
        String value = message.trim();
        String lower = value.toLowerCase(Locale.US);
        if (lower.contains("exception")
                || lower.contains("failed to fetch")
                || lower.contains("network error")
                || lower.contains("err_")
                || lower.contains("econn")
                || lower.contains("timeout")
                || lower.contains("stack")
                || lower.contains("mongodb")
                || lower.contains("database")) {
            return SERVER;
        }
        return value;
    }
}
