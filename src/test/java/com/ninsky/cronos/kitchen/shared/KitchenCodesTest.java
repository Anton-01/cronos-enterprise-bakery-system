package com.ninsky.cronos.kitchen.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KitchenCodesTest {

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource(value = {
            "Pan dulce|PAN_DULCE",
            "  Piña colada! |PINA_COLADA",
            "Crème brûlée|CREME_BRULEE",
            "3 leches|ING_3_LECHES",
            "a|A_X",
            "¡¡¡|ING",
            "|ING"
    }, delimiter = '|')
    void buildsStableUpperSnakeCodes(String text, String expected) {
        assertThat(KitchenCodes.of(text, "ING", 50)).isEqualTo(expected);
    }

    @Test
    void truncatesToMaxLength() {
        assertThat(KitchenCodes.of("Harina de trigo integral", "ING", 10)).isEqualTo("HARINA_DE_");
    }

    @Test
    void suffixesUntilFreeWithinMaxLength() {
        Set<String> taken = Set.of("HARINA", "HARINA_2", "ABCDEFGHIJ");

        assertThat(KitchenCodes.unique("HARINA", 10, taken::contains)).isEqualTo("HARINA_3");
        assertThat(KitchenCodes.unique("ABCDEFGHIJ", 10, taken::contains)).isEqualTo("ABCDEFGH_2");
        assertThat(KitchenCodes.unique("LECHE", 10, taken::contains)).isEqualTo("LECHE");
    }

    @Test
    void normalizesForDetection() {
        assertThat(TextNormalizer.normalize("  Leche  CONDENSADA, año!! ")).isEqualTo("leche condensada año");
        assertThat(TextNormalizer.normalize(null)).isEmpty();
    }
}
