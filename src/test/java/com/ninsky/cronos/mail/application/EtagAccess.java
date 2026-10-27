package com.ninsky.cronos.mail.application;

import com.ninsky.cronos.mail.api.EmailTemplateCatalogResponse;
import com.ninsky.cronos.mail.domain.EffectiveContent;
import com.ninsky.cronos.mail.domain.EmailLocale;

import java.util.List;

/** Test access to the package-private {@link EmailTemplateQueryService#etag}. */
public final class EtagAccess {

    private EtagAccess() {
    }

    public static String etag(EmailTemplateCatalogResponse catalog, List<EffectiveContent> overrides) {
        return EmailTemplateQueryService.etag(catalog, overrides, EmailLocale.ES_MX);
    }
}
