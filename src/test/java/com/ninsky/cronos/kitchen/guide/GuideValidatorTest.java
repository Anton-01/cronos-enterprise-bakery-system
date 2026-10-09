package com.ninsky.cronos.kitchen.guide;

import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class GuideValidatorTest {

    private static List<String> fields(Consumer<Violations> rules) {
        Violations violations = new Violations();
        rules.accept(violations);
        try {
            violations.throwIfAny();
            return List.of();
        } catch (ApiException e) {
            return e.violations().stream().map(ApiException.Violation::field).toList();
        }
    }

    private static GuideAdmin.ArticleRequest article(List<GuideBlock> blocks) {
        return new GuideAdmin.ArticleRequest("FS_TEST", GuideCategory.FOOD_SAFETY, "Título", "Resumen", "pi pi-shield", List.of("tag"), blocks,
                List.of("NOM-251-SSA1-2009"), 10, true, Map.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"<b>negrita</b>", "Texto </p>", "<script>x</script>", "<img src=x>", "<Ñandú>"})
    void rejectsHtmlLookingText(String text) {
        assertThat(fields(v -> GuideValidator.blocks(v, "blocks", List.of(new GuideBlock.Paragraph(text)), true)))
                .containsExactly("blocks[0].text");
    }

    @ParameterizedTest
    @ValueSource(strings = {"3 < 4", "Temperatura <= 5 °C", "a<1", "Usa «comillas»"})
    void acceptsPlainTextWithComparisons(String text) {
        assertThat(fields(v -> GuideValidator.blocks(v, "blocks", List.of(new GuideBlock.Paragraph(text)), true))).isEmpty();
    }

    @Test
    void tableRowsMustMatchColumns() {
        GuideBlock.Table table = new GuideBlock.Table(List.of("a", "b"), List.of(List.of("1", "2"), List.of("3")));

        assertThat(fields(v -> GuideValidator.blocks(v, "blocks", List.of(table), true))).containsExactly("blocks[0].rows[1]");
    }

    @Test
    void tableLimitsColumnsAndRows() {
        GuideBlock.Table wide = new GuideBlock.Table(Collections.nCopies(9, "c"), List.of());
        GuideBlock.Table tall = new GuideBlock.Table(List.of("c"), Collections.nCopies(61, List.of("x")));

        assertThat(fields(v -> GuideValidator.blocks(v, "blocks", List.of(wide, tall), true)))
                .contains("blocks[0].columns", "blocks[1].rows");
    }

    @Test
    void listNeedsOneToFortyItemsAndCalloutAKnownTone() {
        assertThat(fields(v -> GuideValidator.blocks(v, "blocks",
                List.of(new GuideBlock.ListBlock(true, List.of()), new GuideBlock.Callout("danger", "Ojo")), true)))
                .containsExactly("blocks[0].items", "blocks[1].tone");
    }

    @Test
    void articleRules() {
        GuideAdmin.ArticleRequest bad = new GuideAdmin.ArticleRequest("fs", null, "", "x".repeat(301), "fa fa-star", List.of(), List.of(),
                List.of(), -1, true, Map.of("EN", new GuideAdmin.ArticleTranslation(null, null, null, null)));

        assertThat(fields(v -> GuideValidator.article(v, bad)))
                .contains("code", "category", "title", "summary", "icon", "blocks", "displayOrder", "translations.EN");
    }

    @Test
    void validArticleWithTranslationPasses() {
        GuideAdmin.ArticleRequest ok = new GuideAdmin.ArticleRequest("FS_TEST", GuideCategory.FOOD_SAFETY, "Título", "Resumen", "pi pi-shield",
                List.of("tag"), List.of(new GuideBlock.Formula("a = b × c", "Descripción")), List.of(), 10, true,
                Map.of("en-US", new GuideAdmin.ArticleTranslation("Title", null, null, null)));

        assertThat(fields(v -> GuideValidator.article(v, ok))).isEmpty();
    }

    @Test
    void roundPansNeedADiameterAndRectangularOnesBothSides() {
        PanSizeRequest round = new PanSizeRequest(PanShape.ROUND, "R", null, BigDecimal.TEN, null, new BigDecimal("7"), null, null, null);
        PanSizeRequest rect = new PanSizeRequest(PanShape.RECTANGULAR, "Q", null, BigDecimal.TEN, null, new BigDecimal("7"), null, null, null);

        assertThat(fields(v -> GuideValidator.pan(v, round))).containsExactly("diameterCm");
        assertThat(fields(v -> GuideValidator.pan(v, rect))).containsExactly("widthCm");
    }

    @Test
    void panRanges() {
        PanSizeRequest pan = new PanSizeRequest(PanShape.ROUND, "x".repeat(81), new BigDecimal("121"), null, null, new BigDecimal("0.4"),
                BigDecimal.ZERO, 0, "n".repeat(201));

        assertThat(fields(v -> GuideValidator.pan(v, pan))).containsExactly("name", "diameterCm", "heightCm", "volumeMl", "servings", "notes");
    }

    @Test
    void normalizedDropsUnusedDimensionsAndSquaresTheSquare() {
        PanSizeRequest square = GuideValidator.normalized(new PanSizeRequest(PanShape.SQUARE, " Cuadrado ", BigDecimal.ONE, new BigDecimal("20"),
                new BigDecimal("30"), new BigDecimal("5"), null, null, " "));
        PanSizeRequest round = GuideValidator.normalized(new PanSizeRequest(PanShape.BUNDT, "Bundt", new BigDecimal("22"), BigDecimal.ONE,
                BigDecimal.ONE, new BigDecimal("9"), new BigDecimal("2370"), 12, null));

        assertThat(square.name()).isEqualTo("Cuadrado");
        assertThat(square.diameterCm()).isNull();
        assertThat(square.widthCm()).isEqualByComparingTo("20");
        assertThat(square.notes()).isNull();
        assertThat(round.lengthCm()).isNull();
        assertThat(round.widthCm()).isNull();
        assertThat(round.diameterCm()).isEqualByComparingTo("22");
    }

    @Test
    void conversionRules() {
        GuideAdmin.ConversionRequest bad = new GuideAdmin.ConversionRequest("flour", "", BigDecimal.ZERO, new BigDecimal("-1"), null, 0,
                Map.of());

        assertThat(fields(v -> GuideValidator.conversion(v, bad))).containsExactly("code", "name", "gramsPerCup", "gramsPerTablespoon");
    }

    @Test
    void validArticlePassesWithEveryBlockType() {
        List<GuideBlock> blocks = List.of(new GuideBlock.Paragraph("Texto"), new GuideBlock.ListBlock(false, List.of("uno")),
                new GuideBlock.Table(List.of("a"), List.of(List.of(""))), new GuideBlock.Callout("warn", "Cuidado"),
                new GuideBlock.Formula("x = y", "d"));

        assertThat(fields(v -> GuideValidator.article(v, article(blocks)))).isEmpty();
    }
}
