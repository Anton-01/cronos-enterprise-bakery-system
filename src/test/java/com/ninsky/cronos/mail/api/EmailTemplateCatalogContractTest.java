package com.ninsky.cronos.mail.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.infrastructure.config.CacheConfig;
import com.ninsky.cronos.mail.MailFixtures;
import com.ninsky.cronos.mail.application.EmailAssetService;
import com.ninsky.cronos.mail.application.EmailContentProvider;
import com.ninsky.cronos.mail.application.EmailSettingsService;
import com.ninsky.cronos.mail.application.EmailTemplateCommandService;
import com.ninsky.cronos.mail.application.EmailTemplateQueryService;
import com.ninsky.cronos.mail.application.EmailTemplateService;
import com.ninsky.cronos.mail.application.EtagAccess;
import com.ninsky.cronos.mail.domain.EffectiveContent;
import com.ninsky.cronos.mail.domain.EmailBlock;
import com.ninsky.cronos.mail.domain.EmailLocale;
import com.ninsky.cronos.mail.domain.LocalizedEmail;
import com.ninsky.cronos.mail.infrastructure.EmailOverrideCustomRepository;
import com.ninsky.cronos.mail.infrastructure.EmailOverrideCustomRepository.OverrideRow;
import com.ninsky.cronos.mail.infrastructure.EmailSettingsCustomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /admin/email-templates} in the shape of email-template-customization §3, read strictly (unknown properties
 * fail) into the UI's model below: string version, brand and sender, enum categories, {@code es-MX}/{@code en} keys,
 * localized descriptions, and blocks carrying only the fields of their type. Plus the ETag / 304 behaviour of §5.3.
 */
class EmailTemplateCatalogContractTest {

    private static final ObjectMapper JSON = MailFixtures.JSON;
    private static final ObjectMapper STRICT = JSON.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final Set<String> CATEGORIES = Set.of("ACCOUNT", "SECURITY", "ADMIN", "QUOTES", "ORDERS", "RECIPES");
    private static final Map<String, Set<String>> BLOCK_FIELDS = Map.of(
            "heading", Set.of("type", "text"), "paragraph", Set.of("type", "text"), "button", Set.of("type", "label", "url"),
            "code", Set.of("type", "text", "caption"), "details", Set.of("type", "rows"), "callout", Set.of("type", "tone", "text"),
            "list", Set.of("type", "items"), "image", Set.of("type", "url", "alt", "width", "href"), "divider", Set.of("type"),
            "signature", Set.of("type", "text"));

    /** The UI's catalog model ({@code email-template.models.ts}). */
    record Catalog(String version, Brand brand, Sender sender, Long settingsRevision, List<Template> templates) {
    }

    record Brand(String name, String primaryColor, String accentColor, String logoUrl, String headerImageUrl, String supportEmail,
                 String address, String preferencesUrl) {
    }

    record Sender(String fromName, String fromAddress, String replyTo) {
    }

    record Template(String key, String category, String audience, String trigger, boolean transactional, Text description,
                    List<Variable> variables, Map<String, Content> content, List<String> customizedLocales, String updatedAt,
                    String updatedBy) {
    }

    record Variable(String name, Text description, String sample, String kind) {
    }

    record Text(@JsonProperty("es-MX") String esMx, @JsonProperty("en") String en) {
    }

    record Content(Long revision, String subject, String preheader, List<JsonNode> blocks) {
    }

    private final EmailOverrideCustomRepository overrides = mock(EmailOverrideCustomRepository.class);
    private final EmailSettingsCustomRepository settings = mock(EmailSettingsCustomRepository.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        EmailContentProvider provider = new EmailContentProvider(MailFixtures.DEFAULTS, overrides, settings, MailFixtures.properties(),
                new CacheConfig(60, 500).cacheManager(), "https://app.cronos.mx");
        EmailTemplateQueryService queries = new EmailTemplateQueryService(provider, overrides);
        mvc = MockMvcBuilders.standaloneSetup(new EmailTemplateController(queries, mock(EmailTemplateCommandService.class),
                        mock(EmailSettingsService.class), mock(EmailAssetService.class), mock(EmailTemplateService.class), provider,
                        mock(ActorProvider.class)))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(JSON))
                .build();
        when(settings.find(any())).thenReturn(Optional.empty());
        when(overrides.displayNames(any())).thenReturn(Map.of());
    }

    @Test
    void theCatalogResponseHasTheShapeTheUiReads() throws Exception {
        UUID author = UUID.randomUUID();
        when(overrides.findAll(MailFixtures.TENANT)).thenReturn(List.of(new OverrideRow(UUID.randomUUID(), "auth.password-reset",
                EmailLocale.ES_MX, new LocalizedEmail("Restablece tu contraseña", "El enlace vence pronto.", List.of(
                        new EmailBlock.Heading("Hola"), new EmailBlock.Image("https://assets.cronos.mx/mail/a.png", "", null, null),
                        new EmailBlock.Button("Elegir", "{{ resetUrl }}"))), 3, Instant.parse("2026-10-24T19:30:00Z"), author)));
        when(overrides.displayNames(Set.of(author))).thenReturn(Map.of(author, "Antón Admin"));

        String body = mvc.perform(get("/admin/email-templates")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Catalog catalog = STRICT.treeToValue(JSON.readTree(body).path("data"), Catalog.class);

        assertThat(catalog.version()).isEqualTo("2026-10-24");
        assertThat(catalog.brand().name()).isEqualTo("Cronos");
        assertThat(catalog.brand().preferencesUrl()).isEqualTo("https://app.cronos.mx/cronos/notificaciones?tab=preferences");
        assertThat(catalog.sender()).isEqualTo(new Sender("Cronos Bakery", "no-reply@cronos.mx", null));
        assertThat(catalog.settingsRevision()).isNull();
        assertThat(catalog.templates()).hasSize(MailFixtures.DEFAULTS.templates().size()).isNotEmpty();
        for (Template template : catalog.templates()) {
            assertThat(template.category()).as(template.key()).isIn(CATEGORIES);
            assertThat(template.description().esMx()).as(template.key()).isNotBlank();
            assertThat(template.description().en()).as(template.key()).isNotBlank();
            assertThat(template.content()).as(template.key()).containsOnlyKeys("es-MX", "en");
            template.content().forEach((locale, content) -> {
                assertThat(content.subject()).as(template.key() + "/" + locale).isNotBlank();
                assertThat(content.blocks()).as(template.key() + "/" + locale).isNotEmpty().allSatisfy(block -> {
                    Set<String> allowed = BLOCK_FIELDS.get(block.path("type").asText());
                    assertThat(allowed).as(template.key() + " block " + block).isNotNull();
                    block.fieldNames().forEachRemaining(field -> assertThat(allowed).as(template.key() + " " + block).contains(field));
                });
            });
            for (Variable variable : template.variables()) {
                assertThat(variable.description().esMx()).as(template.key() + "." + variable.name()).isNotBlank();
                assertThat(variable.description().en()).as(template.key() + "." + variable.name()).isNotBlank();
            }
        }
        assertThat(catalog.templates()).filteredOn(t -> t.key().equals("auth.registration-received")).singleElement()
                .satisfies(t -> {
                    assertThat(t.category()).isEqualTo("ACCOUNT");
                    assertThat(t.content().get("es-MX").preheader()).isEqualTo("Un administrador la revisará pronto.");
                    assertThat(t.content().get("es-MX").revision()).isNull();
                    assertThat(t.customizedLocales()).isEmpty();
                    assertThat(t.updatedBy()).isNull();
                    assertThat(t.variables()).filteredOn(v -> v.name().equals("reference")).singleElement()
                            .extracting(Variable::description).isEqualTo(new Text("Referencia del registro", "Registration reference"));
                });
        assertThat(catalog.templates()).filteredOn(t -> t.key().equals("auth.password-reset")).singleElement()
                .satisfies(t -> {
                    assertThat(t.category()).isEqualTo("SECURITY");
                    assertThat(t.customizedLocales()).containsExactly("es-MX");
                    assertThat(t.content().get("es-MX").revision()).isEqualTo(3);
                    assertThat(t.content().get("en").revision()).isNull();
                    assertThat(t.content().get("es-MX").blocks().get(1).toString())
                            .isEqualTo("{\"type\":\"image\",\"url\":\"https://assets.cronos.mx/mail/a.png\",\"alt\":\"\"}");
                    assertThat(t.updatedAt()).isEqualTo("2026-10-24T19:30:00Z");
                    assertThat(t.updatedBy()).isEqualTo("Antón Admin");
                });
    }

    @Test
    void theCatalogCarriesAWeakEtagAndAnswers304WhenItMatches() throws Exception {
        when(overrides.findAll(any())).thenReturn(List.of());
        MvcResult first = mvc.perform(get("/admin/email-templates").header(HttpHeaders.ACCEPT_LANGUAGE, "es-MX"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-cache"))
                .andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).matches("W/\"[0-9a-f]{64}\"");

        MvcResult notModified = mvc.perform(get("/admin/email-templates").header(HttpHeaders.ACCEPT_LANGUAGE, "es-MX")
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified()).andExpect(header().string(HttpHeaders.ETAG, etag)).andReturn();
        assertThat(notModified.getResponse().getContentAsString()).isEmpty();

        String english = mvc.perform(get("/admin/email-templates").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                .header(HttpHeaders.IF_NONE_MATCH, etag)).andExpect(status().isOk()).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(english).isNotEqualTo(etag);
    }

    @Test
    void theEtagChangesWithEveryContentOrSettingsChange() {
        var empty = new EmailTemplateCatalogResponse("2026-10-24", null, null, null, List.of());
        var rev7 = new EmailTemplateCatalogResponse("2026-10-24", null, null, 7L, List.of());
        var a1 = new EffectiveContent("a", EmailLocale.ES_MX, 1L, new LocalizedEmail("s", "", List.of()), null, null);
        var a2 = new EffectiveContent("a", EmailLocale.ES_MX, 2L, new LocalizedEmail("s", "", List.of()), null, null);
        var b5 = new EffectiveContent("b", EmailLocale.EN, 5L, new LocalizedEmail("s", "", List.of()), null, null);
        Set<String> tags = Set.of(
                EtagAccess.etag(empty, List.of()), EtagAccess.etag(rev7, List.of()), EtagAccess.etag(empty, List.of(a1)),
                EtagAccess.etag(empty, List.of(a2)), EtagAccess.etag(empty, List.of(a1, b5)), EtagAccess.etag(empty, List.of(a2, b5)));
        assertThat(tags).hasSize(6);
    }
}
