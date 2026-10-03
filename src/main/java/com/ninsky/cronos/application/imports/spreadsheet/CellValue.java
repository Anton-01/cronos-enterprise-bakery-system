package com.ninsky.cronos.application.imports.spreadsheet;

/**
 * A cell already reduced to text, independently of the spreadsheet library. Numbers come in their
 * shortest exact decimal form without exponent ({@code 0.0283495}, never {@code 2.83495E-2});
 * booleans as {@code TRUE}/{@code FALSE}; formula cells as their cached result; error cells
 * ({@code #DIV/0!}) as {@link Kind#ERROR} with the error text.
 */
public record CellValue(Kind kind, String text) {

    public enum Kind {
        BLANK, TEXT, NUMBER, BOOLEAN, ERROR
    }

    public static final CellValue BLANK = new CellValue(Kind.BLANK, "");

    public boolean isBlank() {
        return kind == Kind.BLANK || (kind == Kind.TEXT && text.isBlank());
    }
}
