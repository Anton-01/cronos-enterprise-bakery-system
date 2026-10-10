package com.ninsky.cronos.mail.application;

/** A composed email turned into its MIME parts. */
public record RenderedEmail(String subject, String preheader, String html, String text) {
}
