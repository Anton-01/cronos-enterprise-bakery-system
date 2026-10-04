package com.ninsky.cronos.application.imports.spreadsheet;

import java.util.List;

/** The header row (row 1, as text) and every following non-blank row of one worksheet. */
public record RawSheet(List<String> headers, List<SheetRow> rows) {

    public RawSheet {
        headers = List.copyOf(headers);
        rows = List.copyOf(rows);
    }
}
