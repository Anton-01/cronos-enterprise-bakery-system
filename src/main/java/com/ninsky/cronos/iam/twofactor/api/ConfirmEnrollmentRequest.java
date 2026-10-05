package com.ninsky.cronos.iam.twofactor.api;

import java.util.UUID;

public record ConfirmEnrollmentRequest(UUID enrollmentId, String code) {

    @Override
    public String toString() {
        return "ConfirmEnrollmentRequest[enrollmentId=" + enrollmentId + "]";
    }
}
