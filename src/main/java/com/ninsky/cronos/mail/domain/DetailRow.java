package com.ninsky.cronos.mail.domain;

/** One label/value line of a {@code details} block; both may contain placeholders. */
public record DetailRow(String label, String value) {
}
