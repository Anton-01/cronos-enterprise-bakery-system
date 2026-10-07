package com.ninsky.cronos.iam.permission;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the {@code permissions} table in step with the code registry on every start (spec §5.1). */
@Slf4j
@Component
@Order(0)
public class PermissionCatalogSynchronizer implements ApplicationRunner {

    private final PermissionCatalogCustomRepository writer;

    public PermissionCatalogSynchronizer(JdbcTemplate jdbc) {
        this.writer = new PermissionCatalogCustomRepository(jdbc);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var result = writer.synchronize("PERMISSION_SYNC");
        log.info("Permission catalog synchronised: {} codes upserted, {} deprecated", result.upserted(), result.deprecated());
    }
}
