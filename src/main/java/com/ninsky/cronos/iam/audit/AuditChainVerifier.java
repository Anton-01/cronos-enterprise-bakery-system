package com.ninsky.cronos.iam.audit;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditOutcome;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.iam.shared.TenantTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Nightly hash-chain verification of the ledger (spec §7.4). */
@Slf4j
@Component
public class AuditChainVerifier {

    private static final int FETCH_SIZE = 1000;
    private static final String ROWS = """
            SELECT id, created_at, actor_user_id, action, category, outcome, severity, target_type, target_id,
                   target_label, params::text AS params, changes::text AS changes, reason, prev_hash, hash
            FROM audit_log WHERE hash IS NOT NULL ORDER BY id""";

    /** Outcome of one pass; {@code brokenAtId} is null when the chain is intact. */
    public record Result(long rowsChecked, Long brokenAtId) {
        public boolean intact() {
            return brokenAtId == null;
        }
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate readOnly;
    private final AuditRecorder recorder;
    private final Clock clock;

    public AuditChainVerifier(DataSource dataSource, PlatformTransactionManager transactionManager, AuditRecorder recorder,
                              Clock clock) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setFetchSize(FETCH_SIZE);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.recorder = recorder;
        this.clock = clock;
    }

    /** Runs once per tenant day, even with several instances scheduled. */
    @Scheduled(cron = "${app.security.audit-chain.cron:0 30 3 * * *}", zone = "America/Mexico_City")
    public void nightly() {
        if (verifiedOn(TenantTime.today(clock))) {
            return;
        }
        Result result = verify();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("detail", result.rowsChecked());
        if (!result.intact()) {
            params.put("brokenAtId", result.brokenAtId());
            log.error("Audit hash chain broken at id {}", result.brokenAtId());
        }
        recorder.recordIndependently(AuditEvent.of(AuditAction.AUDIT_CHAIN_VERIFIED, AuditTargets.AUDIT_LOG, null, null)
                .outcome(result.intact() ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE)
                .severity(result.intact() ? AuditSeverity.INFO : AuditSeverity.CRITICAL)
                .params(params)
                .build());
    }

    /** Recomputes every hashed row in id order and checks its link to the previous one. */
    public Result verify() {
        Chain chain = new Chain();
        readOnly.executeWithoutResult(status -> jdbc.query(ROWS, (RowCallbackHandler) rs -> {
            if (chain.brokenAtId != null) {
                return;
            }
            long id = rs.getLong("id");
            String prevHash = rs.getString("prev_hash");
            AuditHasher.Material material = new AuditHasher.Material(rs.getObject("created_at", LocalDateTime.class),
                    rs.getObject("actor_user_id", UUID.class), rs.getString("action"), rs.getString("category"),
                    rs.getString("outcome"), rs.getString("severity"), rs.getString("target_type"), rs.getString("target_id"),
                    rs.getString("target_label"), rs.getString("params"), rs.getString("changes"), rs.getString("reason"));
            chain.accept(id, prevHash, rs.getString("hash"), material);
        }));
        return new Result(chain.rows, chain.brokenAtId);
    }

    private boolean verifiedOn(LocalDate day) {
        Integer found = jdbc.queryForObject("""
                SELECT count(*) FROM audit_log
                WHERE action = ? AND created_at >= ? AND created_at < ?""", Integer.class,
                AuditAction.AUDIT_CHAIN_VERIFIED.name(), day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        return found != null && found > 0;
    }

    /** Walks the chain; the first row may have any (or no) predecessor hash. */
    static final class Chain {
        long rows;
        Long brokenAtId;
        private String lastHash;

        void accept(long id, String prevHash, String hash, AuditHasher.Material material) {
            rows++;
            boolean linked = rows == 1 || Objects.equals(prevHash, lastHash);
            if (!linked || !Objects.equals(hash, safeHash(prevHash, material))) {
                brokenAtId = id;
            }
            lastHash = hash;
        }

        private static String safeHash(String prevHash, AuditHasher.Material material) {
            try {
                return AuditHasher.hash(prevHash, material);
            } catch (IllegalStateException e) {
                return null;
            }
        }
    }
}
