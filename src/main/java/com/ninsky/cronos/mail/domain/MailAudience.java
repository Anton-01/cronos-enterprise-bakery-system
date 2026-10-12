package com.ninsky.cronos.mail.domain;

/** Who receives a template: an account holder, administrators, or a baker's client (no account). */
public enum MailAudience {
    USER,
    ADMIN,
    CLIENT
}
