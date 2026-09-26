package com.ninsky.cronos.account.shared.domain;

import com.ninsky.cronos.account.shared.domain.audit.AuditDiff;
import com.ninsky.cronos.account.shared.domain.audit.FieldDiff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerAndAuditDiffTest {

    private final PiiMasker masker = new PiiMasker();

    @ParameterizedTest(name = "{0}={1} -> {2}")
    @CsvSource({
            "taxId, GODE561231GR8, GOD*********8",
            "taxId, CRO200101AB2, CRO********2",
            "phoneNumber, +525512345678, +52******5678",
            "phoneNumber, +14155552671, +14*****2671",
            "email, admin@cronos.com, a****@cronos.com",
            "legalName, PASTELERIA CRONOS, PASTELERIA CRONOS"})
    void masksPiiByField(String field, String value, String expected) {
        assertThat(masker.mask(field, value)).isEqualTo(expected);
    }

    @Test
    void nullsAndVeryShortValuesNeverLeak() {
        assertThat(masker.mask("taxId", null)).isNull();
        assertThat(masker.maskPhone("+52")).isEqualTo("***");
    }

    @Test
    void diffKeepsOnlyChangedKeysInOrderAndMasksThem() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("legalName", "PASTELERIA CRONOS");
        before.put("taxId", "GODE561231GR8");
        before.put("address.interiorNumber", "4B");
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("legalName", "PASTELERIA CRONOS");
        after.put("taxId", "CRO200101AB2");
        after.put("address.interiorNumber", null);

        var diff = AuditDiff.between(before, after, masker);

        assertThat(diff).containsExactly(
                Map.entry("taxId", new FieldDiff("GOD*********8", "CRO********2")),
                Map.entry("address.interiorNumber", new FieldDiff("4B", null)));
    }

    @Test
    void creationDiffsAgainstAnEmptySnapshot() {
        var diff = AuditDiff.between(Map.of(), Map.of("taxId", "GODE561231GR8"), masker);
        assertThat(diff.get("taxId")).isEqualTo(new FieldDiff(null, "GOD*********8"));
    }
}
