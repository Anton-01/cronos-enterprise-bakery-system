package com.ninsky.cronos.application.imports.csv;

import com.ninsky.cronos.domain.entity.enums.CategoryType;
import com.ninsky.cronos.infrastructure.exception.CsvImportRejectedException;
import com.ninsky.cronos.infrastructure.exception.CsvImportRejectedException.RowError;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;

class CsvCatalogFileTest {

    private static final Set<String> HEADERS = Set.of("name", "description", "type");

    private static CsvCatalogFile parse(String csv) {
        return parse(csv.getBytes(StandardCharsets.UTF_8));
    }

    private static CsvCatalogFile parse(byte[] content) {
        return CsvCatalogFile.parse(new ByteArrayInputStream(content), HEADERS, 3);
    }

    private static String onlyErrorKey(Runnable action) {
        CsvImportRejectedException rejected = catchThrowableOfType(CsvImportRejectedException.class, action::run);
        assertThat(rejected.errors()).hasSize(1);
        return rejected.errors().getFirst().messageKey();
    }

    @Test
    void readsAUtf8FileWithBomAndCaseInsensitiveHeaders() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "Name,DESCRIPTION,type\nPiñas,Frutas,ingredient\n".getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(body, 0, content, bom.length, body.length);

        CsvCatalogFile csv = parse(content);
        CSVRecord record = csv.records().getFirst();

        assertThat(csv.requiredText(record, "name", 100)).contains("Piñas");
        assertThat(csv.requiredEnum(record, "type", CategoryType.class)).contains(CategoryType.INGREDIENT);
        assertThat(CsvCatalogFile.lineOf(record)).isEqualTo(2);
        csv.rejectIfInvalid();
    }

    @Test
    void rejectsFileLevelProblems() {
        assertThat(onlyErrorKey(() -> parse("name,type\nA,PRODUCT\n"))).isEqualTo("import.header.missing");
        assertThat(onlyErrorKey(() -> parse("name,description,type\n"))).isEqualTo("import.csv.noRows");
        assertThat(onlyErrorKey(() -> parse("name,description,type\na,b,PRODUCT\nc,d,PRODUCT\ne,f,PRODUCT\ng,h,PRODUCT\n")))
                .isEqualTo("import.csv.tooManyRows");
        byte[] latin1 = "name,description,type\nPiñas,Frutas,PRODUCT\n".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(onlyErrorKey(() -> parse(latin1))).isEqualTo("import.csv.notUtf8");
    }

    @Test
    void collectsEveryRowProblemOrderedByLineBeforeAnythingIsWritten() {
        CsvCatalogFile csv = parse("name,description,type\n=HYPERLINK(1),ok,BREAD\nHarinas,,PRODUCT\nharinas,dup,PRODUCT\n");
        Map<String, Integer> firstLine = new HashMap<>();
        for (CSVRecord record : csv.records()) {
            var name = csv.requiredText(record, "name", 100);
            csv.requiredText(record, "description", 500);
            csv.requiredEnum(record, "type", CategoryType.class);
            name.ifPresent(n -> csv.isDuplicate(record, "name", n, n.toLowerCase(), firstLine));
        }

        assertThatThrownBy(csv::rejectIfInvalid)
                .isInstanceOf(CsvImportRejectedException.class)
                .extracting(e -> ((CsvImportRejectedException) e).errors())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(RowError.class))
                .extracting(RowError::line, RowError::column, RowError::messageKey)
                .containsExactly(
                        tuple(2, "name", "catalog.text.formulaInjection"),
                        tuple(2, "type", "import.cell.notAllowed"),
                        tuple(3, "description", "import.cell.required"),
                        tuple(4, "name", "import.csv.duplicateValue"));
    }
}
