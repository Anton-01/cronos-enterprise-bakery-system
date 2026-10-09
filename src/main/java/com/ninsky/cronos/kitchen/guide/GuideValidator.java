package com.ninsky.cronos.kitchen.guide;

import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.kitchen.shared.Numbers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Content rules of baking-studio §6.2, with exact field paths ({@code blocks[2].rows[0][1]}). Every text is plain
 * text (B5): anything that looks like markup ({@code <} followed by a letter or {@code /}) is rejected.
 */
public final class GuideValidator {

    static final int MAX_TEXT = 2_000;
    static final int MAX_BLOCKS = 40;
    static final int MAX_LIST_ITEMS = 40;
    static final int MAX_COLUMNS = 8;
    static final int MAX_ROWS = 60;
    static final int MAX_TAGS = 20;
    static final int MAX_TAG = 40;
    static final int MAX_SOURCES = 20;
    static final int MAX_SOURCE = 300;

    private static final Pattern MARKUP = Pattern.compile("<[\\p{L}/]");
    private static final Pattern ARTICLE_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{2,49}$");
    private static final Pattern CONVERSION_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,39}$");
    private static final Pattern PAN_CODE = Pattern.compile("^[A-Z][A-Za-z0-9_]{1,49}$"); // seed codes such as RECT_20x30
    private static final Pattern ICON = Pattern.compile("^pi pi-[a-z0-9-]+$");
    private static final Pattern LANGUAGE_TAG = Pattern.compile("^[a-z]{2,3}(-[A-Z]{2})?$");
    private static final Set<String> TONES = Set.of("info", "tip", "warn");

    private GuideValidator() {
    }

    public static void article(Violations violations, GuideAdmin.ArticleRequest request) {
        violations.invalidIf(request.code() == null || !ARTICLE_CODE.matcher(request.code()).matches(), "code", "api.validation.pattern");
        violations.invalidIf(request.category() == null, "category", "api.validation.required");
        text(violations, "title", request.title(), 120, true);
        text(violations, "summary", request.summary(), 300, true);
        violations.invalidIf(request.icon() == null || !ICON.matcher(request.icon()).matches(), "icon", "api.validation.pattern");
        tags(violations, "tags", request.tags());
        violations.invalidIf(request.sources().size() > MAX_SOURCES, "sources", "api.validation.listSize", 0, MAX_SOURCES);
        for (int i = 0; i < request.sources().size(); i++) {
            text(violations, "sources[" + i + "]", request.sources().get(i), MAX_SOURCE, true);
        }
        blocks(violations, "blocks", request.blocks(), true);
        violations.invalidIf(request.displayOrder() != null && (request.displayOrder() < 0 || request.displayOrder() > 10_000), "displayOrder",
                "api.validation.range", 0, 10_000);
        for (Map.Entry<String, GuideAdmin.ArticleTranslation> entry : request.translations().entrySet()) {
            String prefix = "translations." + entry.getKey();
            if (!language(violations, prefix, entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            GuideAdmin.ArticleTranslation translation = entry.getValue();
            text(violations, prefix + ".title", translation.title(), 120, false);
            text(violations, prefix + ".summary", translation.summary(), 300, false);
            if (translation.tags() != null) {
                tags(violations, prefix + ".tags", translation.tags());
            }
            if (translation.blocks() != null) {
                blocks(violations, prefix + ".blocks", translation.blocks(), true);
            }
        }
    }

    /** Block rules; {@code required} = at least one block. */
    public static void blocks(Violations violations, String path, List<GuideBlock> blocks, boolean required) {
        violations.invalidIf((required && blocks.isEmpty()) || blocks.size() > MAX_BLOCKS, path, "api.validation.listSize", required ? 1 : 0,
                MAX_BLOCKS);
        for (int i = 0; i < blocks.size(); i++) {
            block(violations, path + "[" + i + "]", blocks.get(i));
        }
    }

    private static void block(Violations violations, String path, GuideBlock block) {
        switch (block) {
            case null -> violations.invalid(path, "api.validation.required");
            case GuideBlock.Paragraph p -> text(violations, path + ".text", p.text(), MAX_TEXT, true);
            case GuideBlock.Callout c -> {
                violations.invalidIf(c.tone() == null || !TONES.contains(c.tone()), path + ".tone", "kitchen.guide.tone");
                text(violations, path + ".text", c.text(), MAX_TEXT, true);
            }
            case GuideBlock.Formula f -> {
                text(violations, path + ".expression", f.expression(), MAX_TEXT, true);
                text(violations, path + ".description", f.description(), MAX_TEXT, true);
            }
            case GuideBlock.ListBlock l -> {
                violations.invalidIf(l.items().isEmpty() || l.items().size() > MAX_LIST_ITEMS, path + ".items", "api.validation.listSize", 1,
                        MAX_LIST_ITEMS);
                for (int j = 0; j < l.items().size(); j++) {
                    text(violations, path + ".items[" + j + "]", l.items().get(j), MAX_TEXT, true);
                }
            }
            case GuideBlock.Table t -> table(violations, path, t);
        }
    }

    private static void table(Violations violations, String path, GuideBlock.Table table) {
        int columns = table.columns().size();
        violations.invalidIf(columns < 1 || columns > MAX_COLUMNS, path + ".columns", "api.validation.listSize", 1, MAX_COLUMNS);
        for (int c = 0; c < columns; c++) {
            text(violations, path + ".columns[" + c + "]", table.columns().get(c), MAX_TEXT, true);
        }
        violations.invalidIf(table.rows().size() > MAX_ROWS, path + ".rows", "api.validation.listSize", 0, MAX_ROWS);
        for (int r = 0; r < table.rows().size(); r++) {
            List<String> row = table.rows().get(r);
            String rowPath = path + ".rows[" + r + "]";
            if (row == null || row.size() != columns) {
                violations.invalid(rowPath, "kitchen.guide.rowCells", columns);
                continue;
            }
            for (int c = 0; c < columns; c++) {
                text(violations, rowPath + "[" + c + "]", row.get(c), MAX_TEXT, false);
            }
        }
    }

    /**
     * Pan rules (§6.2): round shapes need {@code diameterCm}, the rest {@code lengthCm} (+ {@code widthCm} unless SQUARE);
     * 1–120 cm; height 0.5–40 cm; volume 1–100 000 ml; servings 1–1000; notes ≤ 200.
     */
    public static void pan(Violations violations, PanSizeRequest request) {
        violations.invalidIf(request.shape() == null, "shape", "api.validation.required");
        text(violations, "name", request.name(), 80, true);
        if (request.shape() != null) {
            if (request.shape().round()) {
                size(violations, "diameterCm", request.diameterCm());
            } else {
                size(violations, "lengthCm", request.lengthCm());
                if (request.shape() != PanShape.SQUARE) {
                    size(violations, "widthCm", request.widthCm());
                }
            }
        }
        violations.invalidIf(!Numbers.within(request.heightCm(), "0.5", "40", 2), "heightCm", "api.validation.range", "0.5", 40);
        violations.invalidIf(request.volumeMl() != null && !Numbers.within(request.volumeMl(), "1", "100000", 2), "volumeMl",
                "api.validation.range", 1, "100,000");
        violations.invalidIf(!Numbers.within(request.servings(), 1, 1000), "servings", "api.validation.range", 1, 1000);
        text(violations, "notes", request.notes(), 200, false);
    }

    public static void panCode(Violations violations, String code) {
        violations.invalidIf(code == null || !PAN_CODE.matcher(code).matches(), "code", "api.validation.pattern");
    }

    public static void panTranslations(Violations violations, Map<String, GuideAdmin.PanTranslation> translations) {
        translations.forEach((tag, translation) -> {
            String prefix = "translations." + tag;
            if (language(violations, prefix, tag) && translation != null) {
                text(violations, prefix + ".name", translation.name(), 80, false);
                text(violations, prefix + ".notes", translation.notes(), 200, false);
            }
        });
    }

    public static void conversion(Violations violations, GuideAdmin.ConversionRequest request) {
        violations.invalidIf(request.code() == null || !CONVERSION_CODE.matcher(request.code()).matches(), "code", "api.validation.pattern");
        text(violations, "name", request.name(), 80, true);
        violations.invalidIf(!Numbers.positive(request.gramsPerCup(), "10000", 2), "gramsPerCup", "api.validation.range", "0.01", "10,000");
        violations.invalidIf(request.gramsPerTablespoon() != null && !Numbers.positive(request.gramsPerTablespoon(), "1000", 2),
                "gramsPerTablespoon", "api.validation.range", "0.01", "1,000");
        violations.invalidIf(request.gramsPerTeaspoon() != null && !Numbers.positive(request.gramsPerTeaspoon(), "1000", 2),
                "gramsPerTeaspoon", "api.validation.range", "0.01", "1,000");
        request.translations().forEach((tag, translation) -> {
            String prefix = "translations." + tag;
            if (language(violations, prefix, tag) && translation != null) {
                text(violations, prefix + ".name", translation.name(), 80, false);
            }
        });
    }

    /** Stored shape of a valid pan: dimensions the shape does not use are null; SQUARE width = length. */
    public static PanSizeRequest normalized(PanSizeRequest request) {
        boolean round = request.shape().round();
        BigDecimal length = round ? null : request.lengthCm();
        BigDecimal width = round ? null : request.shape() == PanShape.SQUARE ? length : request.widthCm();
        return new PanSizeRequest(request.shape(), request.name().strip(), round ? request.diameterCm() : null, length, width,
                request.heightCm(), request.volumeMl(), request.servings(), blankToNull(request.notes()));
    }

    static boolean looksLikeMarkup(String value) {
        return value != null && MARKUP.matcher(value).find();
    }

    private static void tags(Violations violations, String path, List<String> tags) {
        violations.invalidIf(tags.size() > MAX_TAGS, path, "api.validation.listSize", 0, MAX_TAGS);
        for (int i = 0; i < tags.size(); i++) {
            text(violations, path + "[" + i + "]", tags.get(i), MAX_TAG, true);
        }
    }

    private static boolean language(Violations violations, String path, String tag) {
        boolean valid = tag != null && LANGUAGE_TAG.matcher(tag).matches();
        violations.invalidIf(!valid, path, "kitchen.guide.language");
        return valid;
    }

    private static void size(Violations violations, String field, BigDecimal value) {
        violations.invalidIf(!Numbers.within(value, "1", "120", 2), field, "api.validation.range", 1, 120);
    }

    private static void text(Violations violations, String field, String value, int max, boolean required) {
        if (value == null || value.isBlank()) {
            violations.invalidIf(required, field, "api.validation.required");
            return;
        }
        if (value.length() > max) {
            violations.invalid(field, "api.validation.maxLength", max);
        } else if (looksLikeMarkup(value)) {
            violations.invalid(field, "kitchen.guide.plainText");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
