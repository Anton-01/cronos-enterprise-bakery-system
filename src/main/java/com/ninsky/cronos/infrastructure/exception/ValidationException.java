package com.ninsky.cronos.infrastructure.exception;

import java.util.List;

public class ValidationException extends RuntimeException {
    private List<String> details;

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, List<String> details) {
        super(message);
        this.details = details;
    }

    public List<String> getDetails() {
        return details;
    }
}
