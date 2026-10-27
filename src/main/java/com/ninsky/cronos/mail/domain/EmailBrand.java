package com.ninsky.cronos.mail.domain;

/**
 * Look and footer shared by every email.
 *
 * @param primaryColor   {@code #rrggbb}: top border, buttons, links
 * @param accentColor    {@code #rrggbb}: header band
 * @param logoUrl        public {@code https://} image shown in the header
 * @param headerImageUrl optional 600 px banner above the body; replaces the top border
 * @param preferencesUrl where recipients manage their email preferences ({@code List-Unsubscribe})
 */
public record EmailBrand(String name, String primaryColor, String accentColor, String logoUrl, String headerImageUrl,
                         String supportEmail, String address, String preferencesUrl) {
}
