package com.ninsky.cronos.account.shared.domain;

import java.util.List;

/**
 * Thrown by self-validating value objects when their input is invalid — a value object never
 * returns half-built. Carries an i18n message key (resolved against the app's MessageSource at the
 * API edge), never a user-facing string, so the domain stays locale-agnostic.
 */
public class DomainValidationException extends RuntimeException {

    private final String messageKey;
    private final List<Object> args;

    public DomainValidationException(String messageKey, Object... args) {
        super(messageKey);
        this.messageKey = messageKey;
        this.args = List.of(args);
    }

    public String messageKey() {
        return messageKey;
    }

    public List<Object> args() {
        return args;
    }
}
