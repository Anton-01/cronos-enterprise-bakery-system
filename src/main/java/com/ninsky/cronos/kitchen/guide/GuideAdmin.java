package com.ninsky.cronos.kitchen.guide;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Staff shapes of {@code /baking-guide/admin/**} (§6.3, §6.5): base content is es-MX; {@code translations} maps a
 * language tag ({@code en}, {@code en-US}) to the fields it overrides (absent fields fall back to the base).
 */
public final class GuideAdmin {

    private GuideAdmin() {
    }

    public record ArticleTranslation(String title, String summary, List<String> tags, List<GuideBlock> blocks) {
    }

    public record ArticleRequest(String code, GuideCategory category, String title, String summary, String icon, List<String> tags,
                                 List<GuideBlock> blocks, List<String> sources, Integer displayOrder,
                                 @JsonProperty("isActive") Boolean isActive, Map<String, ArticleTranslation> translations) {
        public ArticleRequest {
            tags = tags == null ? List.of() : tags;
            blocks = blocks == null ? List.of() : blocks;
            sources = sources == null ? List.of() : sources;
            translations = translations == null ? Map.of() : translations;
        }
    }

    public record Article(UUID id, String code, GuideCategory category, String title, String summary, String icon, List<String> tags,
                          List<GuideBlock> blocks, List<String> sources, int displayOrder, @JsonProperty("isActive") boolean isActive,
                          Map<String, ArticleTranslation> translations, Instant updatedAt) {
    }

    public record PanTranslation(String name, String notes) {
    }

    public record PanRequest(String code, PanShape shape, String name, BigDecimal diameterCm, BigDecimal lengthCm, BigDecimal widthCm,
                             BigDecimal heightCm, BigDecimal volumeMl, Integer servings, String notes, Integer displayOrder,
                             Map<String, PanTranslation> translations) {
        public PanRequest {
            translations = translations == null ? Map.of() : translations;
        }

        PanSizeRequest dimensions() {
            return new PanSizeRequest(shape, name, diameterCm, lengthCm, widthCm, heightCm, volumeMl, servings, notes);
        }
    }

    public record Pan(@JsonUnwrapped PanSize pan, int displayOrder, Map<String, PanTranslation> translations, Instant updatedAt) {
    }

    public record ConversionTranslation(String name) {
    }

    public record ConversionRequest(String code, String name, BigDecimal gramsPerCup, BigDecimal gramsPerTablespoon, BigDecimal gramsPerTeaspoon,
                                    Integer displayOrder, Map<String, ConversionTranslation> translations) {
        public ConversionRequest {
            translations = translations == null ? Map.of() : translations;
        }
    }

    public record Conversion(@JsonUnwrapped IngredientConversion conversion, int displayOrder, Map<String, ConversionTranslation> translations,
                             Instant updatedAt) {
    }
}
