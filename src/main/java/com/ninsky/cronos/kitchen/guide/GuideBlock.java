package com.ninsky.cronos.kitchen.guide;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * Structured guide content (baking-studio §6.1, B5): never HTML. The {@code type} discriminator matches
 * {@code GuideBlock} in {@code baking-guide.models.ts}; an unknown type is rejected when the request is read.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = GuideBlock.Paragraph.class, name = "paragraph"),
        @JsonSubTypes.Type(value = GuideBlock.ListBlock.class, name = "list"),
        @JsonSubTypes.Type(value = GuideBlock.Table.class, name = "table"),
        @JsonSubTypes.Type(value = GuideBlock.Callout.class, name = "callout"),
        @JsonSubTypes.Type(value = GuideBlock.Formula.class, name = "formula")
})
public sealed interface GuideBlock {

    record Paragraph(String text) implements GuideBlock {
    }

    record ListBlock(boolean ordered, List<String> items) implements GuideBlock {
        public ListBlock {
            items = items == null ? List.of() : items;
        }
    }

    record Table(List<String> columns, List<List<String>> rows) implements GuideBlock {
        public Table {
            columns = columns == null ? List.of() : columns;
            rows = rows == null ? List.of() : rows;
        }
    }

    /** {@code tone}: {@code info}, {@code tip} or {@code warn}. */
    record Callout(String tone, String text) implements GuideBlock {
    }

    record Formula(String expression, String description) implements GuideBlock {
    }
}
