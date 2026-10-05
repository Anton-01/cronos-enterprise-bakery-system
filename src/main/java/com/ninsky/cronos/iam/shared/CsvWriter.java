package com.ninsky.cronos.iam.shared;

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

/** UTF-8 CSV with BOM, RFC 4180 quoting and formula-injection neutralisation (spec §3.10). */
public final class CsvWriter implements AutoCloseable {

    private static final Set<Character> FORMULA_START = Set.of('=', '+', '-', '@', '\t', '\r');

    private final Writer out;

    public CsvWriter(OutputStream stream) {
        this.out = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
        write("﻿");
    }

    public void row(List<?> cells) {
        write(cells.stream().map(CsvWriter::cell).collect(Collectors.joining(",")) + "\r\n");
    }

    public static String cell(Object value) {
        String text = Objects.toString(value, "");
        if (!text.isEmpty() && FORMULA_START.contains(text.charAt(0))) {
            text = "'" + text;
        }
        boolean quote = text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
        return quote ? '"' + text.replace("\"", "\"\"") + '"' : text;
    }

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

    @Override
    public void close() {
        flush();
    }
}
