package com.ninsky.cronos.kitchen.allergen;

import com.ninsky.cronos.kitchen.allergen.AllergenDetector.Keyword;
import com.ninsky.cronos.kitchen.allergen.AllergenDetector.Match;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AllergenDetectorTest {

    private static final long MILK = 1;
    private static final long GLUTEN = 2;
    private static final long NUTS = 3;

    private final AllergenDetector detector = AllergenDetector.of(List.of(
            new Keyword(MILK, "MILK", "Leche", "leche"),
            new Keyword(MILK, "MILK", "Leche", "Leche condensada"),
            new Keyword(MILK, "MILK", "Leche", "milk"),
            new Keyword(MILK, "MILK", "Leche", "mantequilla"),
            new Keyword(GLUTEN, "GLUTEN", "Cereales con gluten", "harina de trigo"),
            new Keyword(GLUTEN, "GLUTEN", "Cereales con gluten", "wheat flour"),
            new Keyword(NUTS, "NUTS", "Frutos de cáscara", "nuez"),
            new Keyword(NUTS, "NUTS", "Frutos de cáscara", "  ")));

    private List<Long> ids(String text) {
        return detector.detect(text, Set.of()).stream().map(Match::allergenId).toList();
    }

    @Test
    void matchesWholeWordsOnly() {
        assertThat(ids("Ensalada de lechuga")).isEmpty();
        assertThat(ids("Pastel con leche")).containsExactly(MILK);
        assertThat(ids("nueces y lechería")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"HARINA DE TRÍGO", "harina-de-trigo", "Harina   de\ttrigo!", "harína de trigo"})
    void ignoresCaseAccentsAndPunctuation(String text) {
        assertThat(ids(text)).containsExactly(GLUTEN);
    }

    @Test
    void reportsTheLongestKeyword() {
        List<Match> matches = detector.detect("Pay de limón con leche condensada", Set.of());

        assertThat(matches).singleElement().extracting(Match::keyword).isEqualTo("leche condensada");
    }

    @Test
    void detectsBothLocales() {
        assertThat(ids("Cookies with wheat flour and milk")).containsExactly(GLUTEN, MILK);
    }

    @Test
    void sortsByAllergenName() {
        assertThat(detector.detect("nuez, mantequilla y harina de trigo", Set.of())).extracting(Match::name)
                .containsExactly("Cereales con gluten", "Frutos de cáscara", "Leche");
    }

    @Test
    void skipsExcludedAllergens() {
        assertThat(detector.detect("leche y nuez", Set.of(MILK))).extracting(Match::allergenId).containsExactly(NUTS);
    }

    @Test
    void combinesSeveralTextsWithoutJoiningWords() {
        // "le" + "che" must not merge into "leche"
        assertThat(detector.detect(List.of("pan de le", "che"), null)).isEmpty();
        assertThat(detector.detect(Arrays.asList("Pan", null, "con nuez"), null)).extracting(Match::allergenId)
                .containsExactly(NUTS);
    }

    @Test
    void blankInputAndEmptyDetectorFindNothing() {
        assertThat(ids("   ")).isEmpty();
        assertThat(AllergenDetector.empty().detect("leche", Set.of())).isEmpty();
        assertThat(detector.allergenIds()).containsExactlyInAnyOrder(MILK, GLUTEN, NUTS);
    }
}
