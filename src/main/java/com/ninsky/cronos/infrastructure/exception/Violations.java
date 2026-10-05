package com.ninsky.cronos.infrastructure.exception;

import java.util.ArrayList;
import java.util.List;

/** Collects every violation of a request so all of them are returned at once (spec N2). */
public final class Violations {

    private final List<ApiException.Violation> collected = new ArrayList<>();

    public Violations add(ApiErrorCode code, String field, String messageKey, Object... args) {
        collected.add(new ApiException.Violation(code, field, messageKey, List.of(args)));
        return this;
    }

    public Violations invalid(String field, String messageKey, Object... args) {
        return add(ApiErrorCode.VALIDATION_ERROR, field, messageKey, args);
    }

    /** Adds an invalid-field violation when {@code condition} holds. */
    public Violations invalidIf(boolean condition, String field, String messageKey, Object... args) {
        return condition ? invalid(field, messageKey, args) : this;
    }

    public boolean isEmpty() {
        return collected.isEmpty();
    }

    public boolean hasField(String field) {
        return collected.stream().anyMatch(v -> field.equals(v.field()));
    }

    /** Throws when anything was collected; 400s are listed before conflicts so the status stays 400. */
    public void throwIfAny() {
        if (!collected.isEmpty()) {
            List<ApiException.Violation> ordered = collected.stream()
                    .sorted(java.util.Comparator.comparingInt(v -> v.code() == ApiErrorCode.VALIDATION_ERROR ? 0 : 1))
                    .toList();
            throw new ApiException(ordered, null);
        }
    }
}
