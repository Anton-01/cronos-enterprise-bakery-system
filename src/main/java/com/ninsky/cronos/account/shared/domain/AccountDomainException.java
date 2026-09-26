package com.ninsky.cronos.account.shared.domain;

import java.util.List;
import java.util.Objects;

/**
 * Transport for one or more {@link AccountDomainError}s up to the web layer. Several errors are
 * carried at once so rule-based validation can report every failing field in a single 400, never
 * fail-fast on the first one.
 */
public class AccountDomainException extends RuntimeException {

    private final List<AccountDomainError> errors;

    public AccountDomainException(AccountDomainError error) {
        this(List.of(error));
    }

    public AccountDomainException(List<? extends AccountDomainError> errors) {
        super(Objects.requireNonNull(errors).isEmpty() ? "account error" : errors.getFirst().messageKey());
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("At least one error is required");
        }
        this.errors = List.copyOf(errors);
    }

    public List<AccountDomainError> errors() {
        return errors;
    }

    /** Status is decided by the first (primary) error; callers never mix kinds in one exception. */
    public AccountDomainError primary() {
        return errors.getFirst();
    }
}
