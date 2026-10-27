package com.ninsky.cronos.mail.application;

import com.ninsky.cronos.mail.domain.ComposedEmail;
import com.ninsky.cronos.mail.domain.EmailSettings;

/** Turns a composed email into HTML (the same markup the UI preview produces), with the tenant's brand. */
public interface EmailRenderer {

    /** Renders {@code email}; never fails on content (all values are escaped). */
    RenderedEmail render(ComposedEmail email, EmailSettings settings);
}
