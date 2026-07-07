package com.example.starter.common;

/**
 * Masks the local part of an email address for logging. Emails are PII, so
 * log lines keep only the first character and the domain, enough to correlate
 * events without writing the full address to the logs.
 */
public final class EmailMasker {

    private EmailMasker() {
    }

    public static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "<none>";
        }
        int at = email.indexOf('@');
        if (at < 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
