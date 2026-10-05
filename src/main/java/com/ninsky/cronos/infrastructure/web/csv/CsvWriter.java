package com.ninsky.cronos.infrastructure.web.csv;

import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RFC 4180 CSV for downloads (spec §3.10): UTF-8 with BOM, CRLF rows, every cell quoted when needed
 * and spreadsheet formula injection neutralised.
 */
public final class CsvWriter implements Flushable {

    private static final char BOM = '﻿';
    private static final Set<Character> FORMULA_START = Set.of('=', '+', '-', '@', '\t', '\r');

    private final Writer out;

    private CsvWriter(Writer out) {
        this.out = out;
    }

    /** Writes the BOM immediately. */
    public static CsvWriter open(OutputStream stream) {
        CsvWriter writer = new CsvWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8));
        writer.write(String.valueOf(BOM));
        return writer;
    }

    public CsvWriter row(List<?> cells) {
        write(cells.stream().map(cell -> cell == null ? "" : escape(neutralise(Objects.toString(cell))))
                .collect(Collectors.joining(",", "", "\r\n")));
        return this;
    }

    /** Cells a spreadsheet would evaluate get a leading apostrophe. */
    public static String neutralise(String value) {
        return !value.isEmpty() && FORMULA_START.contains(value.charAt(0)) ? "'" + value : value;
    }

    public static String escape(String value) {
        boolean quote = value.chars().anyMatch(c -> c == ',' || c == '"' || c == '\n' || c == '\r');
        return quote ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }

    @Override
    public void flush() {
        try {
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void write(String text) {
        try {
            out.write(text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
