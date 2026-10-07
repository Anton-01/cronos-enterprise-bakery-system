package com.ninsky.cronos.application.response.envelope;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A non-blocking warning of a successful response: stable code, request field path (or null), localised message. */
public record ApiWarning(String code, @JsonInclude(JsonInclude.Include.ALWAYS) String field, String message) {
}
