package com.ninsky.cronos.mail.api;

import com.ninsky.cronos.mail.domain.EmailBlock;

import java.time.Instant;
import java.util.List;

/**
 * One entry of {@code GET /{key}/content/{locale}/history}.
 *
 * @param action    {@code SAVED} (with the saved content) or {@code RESET} (content fields null)
 * @param changedBy display name of the author
 */
public record EmailTemplateHistoryEntry(long revision, String subject, String preheader, List<EmailBlock> blocks, Instant changedAt,
                                        String changedBy, String action) {
}
