package com.ninsky.cronos.account.shared.domain.audit;

/** One changed field: already PII-masked values, {@code null} meaning "absent / cleared". */
public record FieldDiff(Object from, Object to) {
}
