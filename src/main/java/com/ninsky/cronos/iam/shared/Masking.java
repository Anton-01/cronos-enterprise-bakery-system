package com.ninsky.cronos.iam.shared;

/** Partial disclosure of contact data in responses and audit entries. */
public final class Masking {

    private Masking() {
    }

    /** First char of the local part + {@code ***} + {@code @domain} (spec §3.5). */
    public static String email(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }

    /** Last four digits only. */
    public static String phone(String phone) {
        return phone == null ? null : "***" + phone.substring(Math.max(0, phone.length() - 4));
    }
}
