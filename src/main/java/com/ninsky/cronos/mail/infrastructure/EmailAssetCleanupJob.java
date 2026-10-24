package com.ninsky.cronos.mail.infrastructure;

import com.ninsky.cronos.mail.application.EmailAssetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nightly removal of email images that no saved template or settings ever referenced, 30 days after upload (§4.6). */
@Slf4j
@Component
public class EmailAssetCleanupJob {

    static final int BATCH = 200;

    private final EmailAssetService assets;

    public EmailAssetCleanupJob(EmailAssetService assets) {
        this.assets = assets;
    }

    @Scheduled(cron = "${app.mail.assets.cleanup-cron:0 40 4 * * *}", zone = "America/Mexico_City")
    public void cleanup() {
        int removed = assets.cleanup(BATCH);
        if (removed > 0) {
            log.info("Removed {} unreferenced email assets", removed);
        }
    }
}
