package com.fixmate.app.util;

import com.fixmate.app.model.CustomerDetails;

public final class BookingValidator {
    private BookingValidator() {
    }

    public static ValidationResult validate(CustomerDetails details) {
        if (details.getName().isEmpty()) {
            return ValidationResult.error(Field.NAME, "Required");
        }
        if (details.getPhone().length() != 10) {
            return ValidationResult.error(Field.PHONE, "Enter 10 digits");
        }
        if (details.getAddress().isEmpty()) {
            return ValidationResult.error(Field.ADDRESS, "Required");
        }
        return ValidationResult.ok();
    }

    public enum Field {
        NONE,
        NAME,
        PHONE,
        ADDRESS
    }

    public static final class ValidationResult {
        private final boolean valid;
        private final Field field;
        private final String message;

        private ValidationResult(boolean valid, Field field, String message) {
            this.valid = valid;
            this.field = field;
            this.message = message;
        }

        public static ValidationResult ok() {
            return new ValidationResult(true, Field.NONE, "");
        }

        public static ValidationResult error(Field field, String message) {
            return new ValidationResult(false, field, message);
        }

        public boolean isValid() {
            return valid;
        }

        public Field getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }
    }
}
