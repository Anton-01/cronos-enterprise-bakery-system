package com.ninsky.cronos.mail.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * One block of a localised email. Polymorphic on {@code type}: each record carries only the fields of its type, so the
 * JSON has no {@code null} / {@code []} noise. Limits are checked by the application's content validator, not here
 * (the defaults are trusted; edits are validated with field paths).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = EmailBlock.Heading.class, name = "heading"),
        @JsonSubTypes.Type(value = EmailBlock.Paragraph.class, name = "paragraph"),
        @JsonSubTypes.Type(value = EmailBlock.Button.class, name = "button"),
        @JsonSubTypes.Type(value = EmailBlock.Code.class, name = "code"),
        @JsonSubTypes.Type(value = EmailBlock.Details.class, name = "details"),
        @JsonSubTypes.Type(value = EmailBlock.Callout.class, name = "callout"),
        @JsonSubTypes.Type(value = EmailBlock.ListBlock.class, name = "list"),
        @JsonSubTypes.Type(value = EmailBlock.Image.class, name = "image"),
        @JsonSubTypes.Type(value = EmailBlock.Divider.class, name = "divider"),
        @JsonSubTypes.Type(value = EmailBlock.Signature.class, name = "signature")
})
public sealed interface EmailBlock permits EmailBlock.Heading, EmailBlock.Paragraph, EmailBlock.Button, EmailBlock.Code,
        EmailBlock.Details, EmailBlock.Callout, EmailBlock.ListBlock, EmailBlock.Image, EmailBlock.Divider, EmailBlock.Signature {

    /** The block type (drives the HTML fragment and the plain-text form). */
    @JsonIgnore
    BlockType type();

    /** The same block with every text run through {@code text} and every link through {@code link}. */
    EmailBlock map(UnaryOperator<String> text, UnaryOperator<String> link);

    /** Every text and link of the block (placeholder checks). */
    @JsonIgnore
    default Stream<String> texts() {
        return switch (this) {
            case Heading b -> Stream.of(b.text());
            case Paragraph b -> Stream.of(b.text());
            case Button b -> Stream.of(b.label(), b.url());
            case Code b -> Stream.of(b.text(), b.caption());
            case Details b -> b.rows().stream().flatMap(row -> Stream.of(row.label(), row.value()));
            case Callout b -> Stream.of(b.text());
            case ListBlock b -> b.items().stream();
            case Image b -> Stream.of(b.url(), b.alt(), b.href());
            case Divider b -> Stream.empty();
            case Signature b -> Stream.of(b.text());
        };
    }

    private static String apply(UnaryOperator<String> operator, String value) {
        return value == null ? null : operator.apply(value);
    }

    /** {@code <h1>}. */
    record Heading(String text) implements EmailBlock {
        public BlockType type() {
            return BlockType.HEADING;
        }

        public Heading map(UnaryOperator<String> text, UnaryOperator<String> link) {
            return new Heading(apply(text, this.text));
        }
    }

    /** A body paragraph. */
    record Paragraph(String text) implements EmailBlock {
        public BlockType type() {
            return BlockType.PARAGRAPH;
        }

        public Paragraph map(UnaryOperator<String> text, UnaryOperator<String> link) {
            return new Paragraph(apply(text, this.text));
        }
    }

    /** A call-to-action link styled as a button. */
    record Button(String label, String url) implements EmailBlock {
        public BlockType type() {
            return BlockType.BUTTON;
        }

        public Button map(UnaryOperator<String> text, UnaryOperator<String> link) {
            return new Button(apply(text, label), apply(link, url));
        }
    }

    /**
     * A short code (temporary password, one-time code).
     *
     * @param caption optional line under the code
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Code(String text, String caption) implements EmailBlock {
        public BlockType type() {
            return BlockType.CODE;
        }

        public Code map(UnaryOperator<String> text, UnaryOperator<String> link) {
            return new Code(apply(text, this.text), apply(text, caption));
        }
    }

    /** A label/value table; rows whose value renders empty are hidden. */
    record Details(List<DetailRow> rows) implements EmailBlock {
        public Details {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }

        public BlockType type() {
            return BlockType.DETAILS;
        }

        public Details map(UnaryOperator<String> text, UnaryOperator<String> link) {
            return new Details(rows.stream().map(row -> new DetailRow(apply(text, row.label()), apply(text, row.value()))).toList());
        }
    }

    /**
     * A highlighted note.
     *
     * @param tone {@code info} (default), {@code success}, {@code warning} or {@code danger}
     */
    record Callout(String tone, String text) implements EmailBlock {
        public Callout {
            tone = tone == null ? "info" : tone;
        }

        public BlockType type() {
            return BlockType.CALLOUT;
        }

        public Callout map(UnaryOperator<String> text, UnaryOperator<String> link) {
            return new Callout(tone, apply(text, this.text));
        }
    }

    /** A bulleted list. */
    record ListBlock(List<String> items) implements EmailBlock {
        public ListBlock {
            items = items == null ? List.of() : List.copyOf(items);
        }

        public BlockType type() {
            return BlockType.LIST;
        }

        public ListBlock map(UnaryOperator<String> text, UnaryOperator<String> link) {
            return new ListBlock(items.stream().map(item -> apply(text, item)).toList());
        }
    }

    /**
     * A centered image, linked when {@code href} is set.
     *
     * @param alt   alternative text; empty only for decorative images
     * @param width 40–536 px, null for the full content width
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Image(String url, String alt, Integer width, String href) implements EmailBlock {
        public BlockType type() {
            return BlockType.IMAGE;
        }

        public Image map(UnaryOperator<String> text, UnaryOperator<String> link) {
            return new Image(apply(link, url), apply(text, alt), width, apply(link, href));
        }
    }

    /** A horizontal rule. */
    record Divider() implements EmailBlock {
        public BlockType type() {
            return BlockType.DIVIDER;
        }

        public Divider map(UnaryOperator<String> text, UnaryOperator<String> link) {
            return this;
        }
    }

    /** The closing line. */
    record Signature(String text) implements EmailBlock {
        public BlockType type() {
            return BlockType.SIGNATURE;
        }

        public Signature map(UnaryOperator<String> text, UnaryOperator<String> link) {
            return new Signature(apply(text, this.text));
        }
    }
}
