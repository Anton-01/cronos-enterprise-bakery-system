package com.ninsky.cronos.mail.domain;

import java.util.List;
import java.util.Objects;

/** Subject, preheader (inbox preview line) and body blocks of one template in one locale. */
public record LocalizedEmail(String subject, String preheader, List<EmailBlock> blocks) {

    public LocalizedEmail {
        Objects.requireNonNull(subject, "subject");
        preheader = preheader == null ? "" : preheader;
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }
}
