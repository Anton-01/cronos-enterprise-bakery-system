package com.ninsky.cronos.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ninsky.cronos.mail.application.MailProperties;
import com.ninsky.cronos.mail.domain.EmailSettings;
import com.ninsky.cronos.mail.domain.TemplateDefaults;
import com.ninsky.cronos.mail.infrastructure.ClasspathTemplateDefaults;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.List;
import java.util.UUID;

/** Shared test data of the mail module. */
public final class MailFixtures {

    /** Like Spring Boot's mapper: ISO-8601 instants. */
    public static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    public static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000000");
    public static final String ASSET_BASE = "https://assets.cronos.mx";
    public static final TemplateDefaults DEFAULTS = ClasspathTemplateDefaults.load(JSON);

    private MailFixtures() {
    }

    /** {@code app.mail.*} with the asset host enforced or not. */
    public static MailProperties properties(boolean enforceAssetHost) {
        return new MailProperties("no-reply@cronos.mx", "Cronos Bakery", null, "Cronos", "soporte@cronos.mx", "cronos.mx", 20, TENANT,
                List.of("mail.cronos.mx"),
                new MailProperties.Brand("#10b981", "#f9a94c", "", "Ciudad de México, México", ""),
                new MailProperties.Assets("local", ASSET_BASE, "./target/mail-assets", null, enforceAssetHost));
    }

    public static MailProperties properties() {
        return properties(false);
    }

    /** The defaults served before the first settings save. */
    public static EmailSettings settings() {
        return properties().defaultSettings("https://app.cronos.mx");
    }

    /** Thymeleaf engine over {@code templates/}. */
    public static SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
