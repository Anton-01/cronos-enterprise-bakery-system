package com.ninsky.cronos.account.it;

import com.ninsky.cronos.account.fiscal.application.port.FiscalDataRepository;
import com.ninsky.cronos.account.fiscal.domain.FiscalAddress;
import com.ninsky.cronos.account.fiscal.domain.FiscalData;
import com.ninsky.cronos.account.fiscal.domain.LegalName;
import com.ninsky.cronos.account.fiscal.domain.MexicanState;
import com.ninsky.cronos.account.fiscal.domain.MxZipCode;
import com.ninsky.cronos.account.fiscal.domain.Rfc;
import com.ninsky.cronos.account.fiscal.domain.TaxRegime;
import com.ninsky.cronos.account.fiscal.domain.TaxpayerType;
import com.ninsky.cronos.account.shared.application.port.AuditTrail;
import com.ninsky.cronos.account.shared.domain.PiiMasker;
import com.ninsky.cronos.account.shared.domain.audit.AuditChange;
import com.ninsky.cronos.account.shared.domain.audit.AuditDiff;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FiscalDataPersistenceIT extends AccountPostgresIT {

    @Autowired
    private FiscalDataRepository repository;
    @Autowired
    private AuditTrail auditTrail;
    @Autowired
    private PiiMasker piiMasker;

    @Test
    void upsertCreatesThenUpdatesWithVersionAndNullClearing() {
        UUID userId = insertUser("fiscal_" + System.nanoTime(), null);

        FiscalData created = tx.execute(s -> repository.save(fiscal(userId, "4B", TaxRegime.REGIMEN_SIMPLIFICADO_CONFIANZA, null)));
        assertThat(created.version()).isZero();
        assertThat(created.updatedAt()).isNotNull();

        FiscalData updated = tx.execute(s -> repository.save(fiscal(userId, null, TaxRegime.ACTIVIDADES_EMPRESARIALES_PROFESIONALES, 0L)));
        assertThat(updated.version()).isEqualTo(1L);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM user_fiscal_data WHERE user_id = ?", userId);
        assertThat(row).containsEntry("tax_regime", "612").containsEntry("taxpayer_type", "INDIVIDUAL")
                .containsEntry("state", "CMX").containsEntry("country", "MEX");
        assertThat(row.get("interior_number")).isNull();
        assertThat(repository.findByUserId(userId)).get().extracting(FiscalData::taxpayerType).isEqualTo(TaxpayerType.INDIVIDUAL);
    }

    @Test
    void staleVersionIsAnOptimisticLockFailure() {
        UUID userId = insertUser("stale_" + System.nanoTime(), null);
        tx.execute(s -> repository.save(fiscal(userId, null, TaxRegime.REGIMEN_SIMPLIFICADO_CONFIANZA, null)));
        tx.execute(s -> repository.save(fiscal(userId, "1", TaxRegime.REGIMEN_SIMPLIFICADO_CONFIANZA, 0L)));

        assertThatThrownBy(() -> tx.execute(s -> repository.save(fiscal(userId, "2", TaxRegime.REGIMEN_SIMPLIFICADO_CONFIANZA, 0L))))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void auditRowIsWrittenOnlyAfterCommitWithMaskedDiff() {
        UUID userId = insertUser("audit_" + System.nanoTime(), null);
        FiscalData data = fiscal(userId, null, TaxRegime.REGIMEN_SIMPLIFICADO_CONFIANZA, null);

        tx.execute(s -> {
            repository.save(data);
            auditTrail.record(new AuditChange.FiscalDataCreated(userId, userId, AuditDiff.between(Map.of(), data.snapshot(), piiMasker)));
            assertThat(auditRows(userId)).as("nothing before commit").isZero();
            return null;
        });

        assertThat(auditRows(userId)).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT action, target_type, ip_address, changes::text AS changes FROM audit_log WHERE actor_user_id = ?", userId);
        assertThat(row).containsEntry("action", "FISCAL_DATA_CREATED").containsEntry("target_type", "FISCAL_DATA")
                .containsEntry("ip_address", "203.0.113.7");
        assertThat((String) row.get("changes")).contains("GOD*********8").doesNotContain("GODE561231GR8");
    }

    @Test
    void rolledBackWritesLeaveNoAuditRow() {
        UUID userId = insertUser("rollback_" + System.nanoTime(), null);

        tx.execute(s -> {
            auditTrail.record(new AuditChange.PasswordChanged(userId));
            s.setRollbackOnly();
            return null;
        });

        assertThat(auditRows(userId)).isZero();
    }

    private int auditRows(UUID userId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE actor_user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    private static FiscalData fiscal(UUID userId, String interiorNumber, TaxRegime regime, Long version) {
        return new FiscalData(userId, new LegalName("Pasteleria Cronos"), new Rfc("GODE561231GR8"), regime,
                new FiscalAddress("Av. Reforma", "222", interiorNumber, "Juárez", "Cuauhtémoc", MexicanState.CMX, new MxZipCode("06600"), "MEX"),
                version, null);
    }
}
