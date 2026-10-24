package com.ninsky.cronos.mail.domain;

import java.util.List;

/**
 * A template with its variables applied: ready to be turned into HTML (by the renderer) and already turned into
 * its plain-text alternative.
 *
 * @param locale {@code es-MX} or {@code en}
 */
public record ComposedEmail(String templateKey, String locale, String subject, String preheader, List<EmailBlock> blocks,
                            String text, boolean transactional) {

    public ComposedEmail {
        blocks = List.copyOf(blocks);
    }

    /** Same email with {@code prefix} before the subject (test sends). */
    public ComposedEmail withSubjectPrefix(String prefix) {
        return new ComposedEmail(templateKey, locale, prefix + subject, preheader, blocks, text, transactional);
    }
}
