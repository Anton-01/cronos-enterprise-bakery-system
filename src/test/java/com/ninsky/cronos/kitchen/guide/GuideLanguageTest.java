package com.ninsky.cronos.kitchen.guide;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GuideLanguageTest {

    private static final Map<String, String> TRANSLATIONS = Map.of("en", "English", "en-GB", "British", "fr", "Français");

    @Test
    void exactTagWinsOverItsLanguage() {
        assertThat(GuideLanguage.of("en-GB,en;q=0.8").pick(TRANSLATIONS)).contains("British");
    }

    @Test
    void fallsBackToTheLanguage() {
        assertThat(GuideLanguage.of("en-US").pick(TRANSLATIONS)).contains("English");
    }

    @Test
    void weightsDecideTheOrder() {
        assertThat(GuideLanguage.of("en;q=0.5, fr;q=0.9").pick(TRANSLATIONS)).contains("Français");
    }

    @Test
    void spanishOrMissingTranslationsMeanTheBaseContent() {
        assertThat(GuideLanguage.of("es-MX,en;q=0.9").pick(TRANSLATIONS)).isEmpty();
        assertThat(GuideLanguage.of("de").pick(TRANSLATIONS)).isEmpty();
        assertThat(GuideLanguage.of(null).pick(TRANSLATIONS)).isEmpty();
        assertThat(GuideLanguage.of("en").pick(Map.of())).isEmpty();
    }

    @Test
    void malformedHeaderMeansBaseContent() {
        assertThat(GuideLanguage.of(";;;q=x").key()).isEqualTo(GuideLanguage.BASE);
    }

    @Test
    void keyDistinguishesLanguagesForTheEtag() {
        assertThat(GuideLanguage.of("en-US").key()).isEqualTo("en-US,en");
        assertThat(GuideLanguage.of("es").key()).isEqualTo(GuideLanguage.BASE);
    }
}
