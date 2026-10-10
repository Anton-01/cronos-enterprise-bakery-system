package com.ninsky.cronos.mail.domain;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * One block of a localised email. Only the fields of its {@link BlockType} are used:
 * heading/paragraph/code/signature → {@code text}; button → {@code label} + {@code url}; callout → {@code tone} +
 * {@code text}; list → {@code items}; details → {@code rows}; divider → nothing.
 *
 * @param tone {@code info}, {@code success}, {@code warning} or {@code danger} (callouts)
 */
public record EmailBlock(BlockType type, String text, String label, String url, String tone, List<String> items,
                         List<DetailRow> rows) {

    public EmailBlock {
        Objects.requireNonNull(type, "type");
        items = items == null ? List.of() : List.copyOf(items);
        rows = rows == null ? List.of() : List.copyOf(rows);
        tone = tone == null ? "info" : tone;
    }

    /** The same block with every text run through {@code text} and the URL through {@code link}. */
    public EmailBlock map(UnaryOperator<String> text, UnaryOperator<String> link) {
        return new EmailBlock(type, apply(text, this.text), apply(text, label), apply(link, url), tone,
                items.stream().map(text).toList(),
                rows.stream().map(row -> new DetailRow(apply(text, row.label()), apply(text, row.value()))).toList());
    }

    private static String apply(UnaryOperator<String> operator, String value) {
        return value == null ? null : operator.apply(value);
    }
}
