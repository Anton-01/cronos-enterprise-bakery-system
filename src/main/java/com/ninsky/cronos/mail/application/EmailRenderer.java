package com.ninsky.cronos.mail.application;

import com.ninsky.cronos.mail.domain.ComposedEmail;

/** Turns a composed email into HTML (the same markup the UI preview produces). */
public interface EmailRenderer {

    /** Renders {@code email}; never fails on content (all values are escaped). */
    RenderedEmail render(ComposedEmail email);
}
