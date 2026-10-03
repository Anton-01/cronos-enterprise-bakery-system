package com.ninsky.cronos.account.shared.domain;

import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * The single place PII is masked before it reaches an audit diff or a log line:
 * RFC {@code GODE561231GR8 -> GOD*********8}, phone {@code +525512345678 -> +52******5678}.
 * Registered as a Spring bean by {@code AccountSharedConfig}; framework-free so the domain can use it.
 */
public class PiiMasker {

    private static final char MASK = '*';

    /** Audit-diff keys (request JSON paths) whose values are PII. */
    private final Map<String, UnaryOperator<String>> maskersByField = Map.of(
            "taxId", this::maskRfc,
            "phoneNumber", this::maskPhone,
            "email", this::maskEmail
    );

    public Object mask(String field, Object value) {
        if (value == null) {
            return null;
        }
        UnaryOperator<String> masker = maskersByField.get(field);
        return masker == null ? value : masker.apply(value.toString());
    }

    public String maskRfc(String rfc) {
        return keepEnds(rfc, 3, 1);
    }

    public String maskPhone(String phone) {
        return keepEnds(phone, 3, 4);
    }

    public String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return keepEnds(email, 1, 0);
        }
        return email.charAt(0) + String.valueOf(MASK).repeat(Math.max(1, at - 1)) + email.substring(at);
    }

    private static String keepEnds(String value, int head, int tail) {
        if (value == null) {
            return null;
        }
        if (value.length() <= head + tail) {
            return String.valueOf(MASK).repeat(value.length());
        }
        return value.substring(0, head)
                + String.valueOf(MASK).repeat(value.length() - head - tail)
                + value.substring(value.length() - tail);
    }
}
