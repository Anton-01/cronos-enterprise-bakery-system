package com.ninsky.cronos.mail.api;

import com.ninsky.cronos.mail.domain.EmailBlock;
import com.ninsky.cronos.mail.domain.LocalizedEmail;

import java.util.List;

/**
 * Content of one template locale, as served in the catalog and as sent by the editor
 * ({@code PUT /{key}/content/{locale}}, and drafts in preview/test).
 *
 * @param revision in a response: the override's revision, null when the default is served; in a request: the revision
 *                 the editor started from (null when it started from the default)
 */
public record EmailTemplateContent(Long revision, String subject, String preheader, List<EmailBlock> blocks) {

    public EmailTemplateContent {
        blocks = blocks == null ? List.of() : blocks.stream().toList();
    }

    /** Subject, preheader and blocks without the revision. */
    public LocalizedEmail toContent() {
        return new LocalizedEmail(subject == null ? "" : subject, preheader, blocks);
    }

    public static EmailTemplateContent of(Long revision, LocalizedEmail content) {
        return new EmailTemplateContent(revision, content.subject(), content.preheader(), content.blocks());
    }
}
