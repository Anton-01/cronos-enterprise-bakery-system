package com.ninsky.cronos.application.imports;

/** An expected worksheet column, matched against the header row by {@link SheetLayout#normalize}. */
public record ColumnSpec(String name, boolean required) {

    public static ColumnSpec required(String name) {
        return new ColumnSpec(name, true);
    }

    public static ColumnSpec optional(String name) {
        return new ColumnSpec(name, false);
    }
}
