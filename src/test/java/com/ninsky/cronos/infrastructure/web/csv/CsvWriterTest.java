package com.ninsky.cronos.infrastructure.web.csv;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    @Test
    void neutralisesFormulaPrefixes() {
        List.of("=SUM(A1)", "+1", "-1", "@cmd", "\tx", "\rx").forEach(value ->
                assertThat(CsvWriter.neutralise(value)).isEqualTo("'" + value));
        assertThat(CsvWriter.neutralise("Harina")).isEqualTo("Harina");
        assertThat(CsvWriter.neutralise("")).isEmpty();
    }

    @Test
    void quotesOnlyWhenNeeded() {
        assertThat(CsvWriter.escape("plain")).isEqualTo("plain");
        assertThat(CsvWriter.escape("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvWriter.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvWriter.escape("line\nbreak")).isEqualTo("\"line\nbreak\"");
    }

    @Test
    void writesBomCrlfRowsAndEmptyNulls() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvWriter.open(out).row(List.of("id", "name")).row(Arrays.asList(1, null)).row(List.of("=2+2", "Pan, dulce")).flush();
        byte[] bytes = out.toByteArray();
        assertThat(Arrays.copyOf(bytes, 3)).containsExactly(0xEF, 0xBB, 0xBF);
        assertThat(new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8))
                .isEqualTo("id,name\r\n1,\r\n'=2+2,\"Pan, dulce\"\r\n");
    }
}
