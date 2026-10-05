package com.ninsky.cronos.iam.user.api;

import java.util.List;
import java.util.UUID;

/** Partial-success report of a bulk operation. */
public record BulkResult(List<UUID> succeeded, List<Failure> failed) {

    public BulkResult {
        succeeded = List.copyOf(succeeded);
        failed = List.copyOf(failed);
    }

    public record Failure(UUID id, String code, String message) {
    }
}
