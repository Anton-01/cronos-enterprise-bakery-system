package com.ninsky.cronos.iam.shared;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    @Test
    void neutralisesFormulasAndQuotes() {
        assertThat(CsvWriter.cell("=SUM(A1)")).isEqualTo("'=SUM(A1)");
        assertThat(CsvWriter.cell("-1")).isEqualTo("'-1");
        assertThat(CsvWriter.cell("a,\"b\"")).isEqualTo("\"a,\"\"b\"\"\"");
        assertThat(CsvWriter.cell(null)).isEmpty();
    }

    @Test
    void writesBomAndCrlfRows() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CsvWriter csv = new CsvWriter(out)) {
            csv.row(List.of("id", "name"));
        }
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("﻿id,name\r\n");
    }

    @Test
    void masksContactData() {
        assertThat(Masking.email("carla@cronos.mx")).isEqualTo("c***@cronos.mx");
        assertThat(Masking.phone("+525511223344")).isEqualTo("***3344");
    }
}
