package com.ninsky.cronos.mail.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Subject, preheader (inbox preview line) and body blocks of one template in one locale. An edited draft may carry a
 * null block until the content validator rejects it with its path; stored and rendered content never does.
 */
public record LocalizedEmail(String subject, String preheader, List<EmailBlock> blocks) {

    public LocalizedEmail {
        Objects.requireNonNull(subject, "subject");
        preheader = preheader == null ? "" : preheader;
        blocks = blocks == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(blocks));
    }
}
