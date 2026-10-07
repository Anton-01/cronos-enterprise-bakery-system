package com.ninsky.cronos.kitchen.shared;

import com.ninsky.cronos.application.response.envelope.ApiWarning;

import java.util.List;

/** A result plus the non-blocking warnings of the envelope (§8). */
public record Warned<T>(T data, List<ApiWarning> warnings) {

    public Warned {
        warnings = List.copyOf(warnings);
    }
}
