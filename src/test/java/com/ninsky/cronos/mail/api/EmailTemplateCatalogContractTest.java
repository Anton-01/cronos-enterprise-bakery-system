package com.ninsky.cronos.mail.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.mail.application.EmailTemplateService;
import com.ninsky.cronos.mail.domain.EmailTemplateCatalog;
import com.ninsky.cronos.mail.infrastructure.MailConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /admin/email-templates} in the shape the UI reads without an adapter: every template has a
 * {@code category}, {@code content} in {@code es} and {@code en}, and variable descriptions in both languages. The
 * response is read strictly (unknown properties fail) into the UI's model below.
 */
class EmailTemplateCatalogContractTest {

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final ObjectMapper STRICT = JSON.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /** The UI's catalog model. */
    record Catalog(int version, List<Template> templates) {
    }

    record Template(String key, String category, String audience, boolean transactional, String trigger, String description,
                    List<Variable> variables, Map<String, Content> content) {
    }

    record Variable(String name, Text description, String sample, String kind) {
    }

    record Text(String es, String en) {
    }

    record Content(String subject, String preheader, List<JsonNode> blocks) {
    }

    @Test
    void theCatalogResponseHasTheShapeTheUiReads() throws Exception {
        EmailTemplateCatalog shipped = new MailConfig().emailTemplateCatalog(JSON);
        EmailTemplateService service = mock(EmailTemplateService.class);
        when(service.catalog()).thenReturn(shipped);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new EmailTemplateController(service, mock(ActorProvider.class)))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(JSON))
                .build();

        String body = mvc.perform(get("/admin/email-templates")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Catalog catalog = STRICT.treeToValue(JSON.readTree(body).path("data"), Catalog.class);

        assertThat(catalog.version()).isEqualTo(1);
        assertThat(catalog.templates()).hasSize(shipped.templates().size()).isNotEmpty();
        assertThat(catalog.templates()).extracting(Template::key).contains("auth.invitation", "auth.temporary-password",
                "auth.email-changed", "auth.two-factor-disabled", "auth.recovery-code-used", "auth.registration-attempt", "recipe.shared");
        for (Template template : catalog.templates()) {
            assertThat(template.category()).as(template.key()).isNotBlank();
            assertThat(template.key()).startsWith(template.category() + ".");
            assertThat(template.content()).as(template.key()).containsOnlyKeys("es", "en");
            template.content().forEach((locale, content) -> {
                assertThat(content.subject()).as(template.key() + "/" + locale).isNotBlank();
                assertThat(content.blocks()).as(template.key() + "/" + locale).isNotEmpty();
            });
            for (Variable variable : template.variables()) {
                assertThat(variable.description()).as(template.key() + "." + variable.name()).isNotNull();
                assertThat(variable.description().es()).as(template.key() + "." + variable.name() + " es").isNotBlank();
                assertThat(variable.description().en()).as(template.key() + "." + variable.name() + " en").isNotBlank();
            }
        }
        assertThat(catalog.templates()).filteredOn(t -> t.key().equals("auth.registration-received")).singleElement()
                .satisfies(t -> {
                    assertThat(t.category()).isEqualTo("auth");
                    assertThat(t.content().get("es").preheader()).isEqualTo("Un administrador la revisará pronto.");
                    assertThat(t.variables()).filteredOn(v -> v.name().equals("reference")).singleElement()
                            .extracting(Variable::description).isEqualTo(new Text("Referencia del registro", "Registration reference"));
                });
    }
}
