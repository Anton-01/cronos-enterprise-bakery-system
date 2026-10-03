package com.ninsky.cronos.application.imports;

import com.ninsky.cronos.domain.model.imports.ImportBatch;
import com.ninsky.cronos.domain.port.imports.ImportBatchRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records batches that wrote nothing (validated / rejected / failed) in their own transaction, so
 * the ledger entry survives the rollback of the import itself. A committed batch is instead
 * appended inside the import transaction — the data and its ledger row commit or vanish together.
 */
@Component
@RequiredArgsConstructor
public class ImportBatchRecorder {

    private final ImportBatchRepositoryPort batchRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendIsolated(ImportBatch batch) {
        batchRepository.append(batch);
    }
}
