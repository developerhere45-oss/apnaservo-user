package com.fixmate.app.util;

public final class MoneyFormatter {
    private MoneyFormatter() {
    }

    public static String format(int amount) {
        return "Rs." + amount;
    }
}
