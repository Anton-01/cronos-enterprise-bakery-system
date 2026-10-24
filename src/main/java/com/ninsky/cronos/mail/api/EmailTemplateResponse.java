package com.ninsky.cronos.mail.api;

import com.ninsky.cronos.mail.domain.EmailCategory;
import com.ninsky.cronos.mail.domain.EmailLocale;
import com.ninsky.cronos.mail.domain.LocalizedText;
import com.ninsky.cronos.mail.domain.MailAudience;
import com.ninsky.cronos.mail.domain.TemplateVariable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One catalog entry with its effective content per locale (§3.1).
 *
 * @param variables         not editable: the contract between the domain event and the template
 * @param customizedLocales the locales served from an override ("Customized" badge, "Restore default")
 * @param updatedAt         most recent override of any locale, null when none
 * @param updatedBy         display name of that override's author, null when none
 */
public record EmailTemplateResponse(String key, EmailCategory category, MailAudience audience, String trigger, boolean transactional,
                                    LocalizedText description, List<TemplateVariable> variables,
                                    Map<EmailLocale, EmailTemplateContent> content, List<EmailLocale> customizedLocales,
                                    Instant updatedAt, String updatedBy) {
}
