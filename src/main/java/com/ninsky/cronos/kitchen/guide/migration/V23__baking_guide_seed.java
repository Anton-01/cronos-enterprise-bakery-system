package com.ninsky.cronos.kitchen.guide.migration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.kitchen.guide.GuideAdmin;
import com.ninsky.cronos.kitchen.guide.GuideBlock;
import com.ninsky.cronos.kitchen.guide.GuideCategory;
import com.ninsky.cronos.kitchen.guide.GuideCustomRepository;
import com.ninsky.cronos.kitchen.guide.GuideValidator;
import com.ninsky.cronos.kitchen.guide.IngredientConversion;
import com.ninsky.cronos.kitchen.guide.PanShape;
import com.ninsky.cronos.kitchen.guide.PanSizeRequest;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loads the baker's guide seed (baking-studio §6.4) from {@code db/seed/baking-guide.es-MX.json}, a verbatim copy of
 * the UI's bundled {@code src/assets/baking-guide/seed.es-MX.json}: same stable UUIDv5 ids, so the offline copy and
 * the API agree. Upserts by code and never touches user pans. Every item passes the same rules as the staff API.
 */
@Slf4j
@Component
@SuppressWarnings("java:S101") // Flyway derives the version from this class name.
public class V23__baking_guide_seed extends BaseJavaMigration {

    static final String SEED = "db/seed/baking-guide.es-MX.json";
    private static final TypeReference<List<GuideBlock>> BLOCKS = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    @Override
    public void migrate(Context context) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode seed;
        try (InputStream in = new ClassPathResource(SEED).getInputStream()) {
            seed = mapper.readTree(in);
        }
        GuideCustomRepository store = new GuideCustomRepository(
                new NamedParameterJdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true)), mapper);

        int order = 0;
        for (JsonNode a : seed.get("articles")) {
            GuideAdmin.ArticleRequest request = new GuideAdmin.ArticleRequest(a.get("code").asText(), GuideCategory.valueOf(a.get("category").asText()),
                    a.get("title").asText(), a.get("summary").asText(), a.get("icon").asText(), mapper.convertValue(a.get("tags"), STRINGS),
                    mapper.convertValue(a.get("blocks"), BLOCKS), mapper.convertValue(a.get("sources"), STRINGS),
                    a.path("displayOrder").asInt(order), true, Map.of());
            check("article " + request.code(), v -> GuideValidator.article(v, request));
            store.upsertArticle(new GuideCustomRepository.ArticleRow(UUID.fromString(a.get("id").asText()), request.code(), request.category(),
                    request.title(), request.summary(), request.icon(), request.tags(), request.blocks(), request.sources(), Map.of(),
                    request.displayOrder(), true, null));
            order++;
        }

        order = 0;
        for (JsonNode p : seed.get("panSizes")) {
            PanSizeRequest pan = new PanSizeRequest(PanShape.valueOf(p.get("shape").asText()), p.get("name").asText(), decimal(p, "diameterCm"),
                    decimal(p, "lengthCm"), decimal(p, "widthCm"), decimal(p, "heightCm"), decimal(p, "volumeMl"),
                    p.hasNonNull("servings") ? p.get("servings").asInt() : null, p.hasNonNull("notes") ? p.get("notes").asText() : null);
            String code = p.get("code").asText();
            check("pan " + code, v -> {
                GuideValidator.panCode(v, code);
                GuideValidator.pan(v, pan);
            });
            store.upsertPan(new GuideCustomRepository.PanRow(UUID.fromString(p.get("id").asText()), code, null, GuideValidator.normalized(pan),
                    Map.of(), order++, null));
        }

        order = 0;
        for (JsonNode c : seed.get("conversions")) {
            GuideAdmin.ConversionRequest request = new GuideAdmin.ConversionRequest(c.get("code").asText(), c.get("name").asText(),
                    decimal(c, "gramsPerCup"), decimal(c, "gramsPerTablespoon"), decimal(c, "gramsPerTeaspoon"), order, Map.of());
            check("conversion " + request.code(), v -> GuideValidator.conversion(v, request));
            store.upsertConversion(new GuideCustomRepository.ConversionRow(new IngredientConversion(request.code(), request.name(),
                    request.gramsPerCup(), request.gramsPerTablespoon(), request.gramsPerTeaspoon()), Map.of(), order++, null));
        }

        store.setRevision(LocalDate.parse(seed.get("revision").asText()));
        log.info("V23: baker's guide seeded ({} articles, {} pan sizes, {} conversions)", seed.get("articles").size(),
                seed.get("panSizes").size(), seed.get("conversions").size());
    }

    private static void check(String item, java.util.function.Consumer<Violations> rules) {
        Violations violations = new Violations();
        rules.accept(violations);
        try {
            violations.throwIfAny();
        } catch (ApiException invalid) {
            throw new IllegalStateException("V23: invalid seed " + item + ": " + invalid.violations(), invalid);
        }
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).decimalValue() : null;
    }
}
