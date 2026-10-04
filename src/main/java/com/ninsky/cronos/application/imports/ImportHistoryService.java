package com.ninsky.cronos.application.imports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninsky.cronos.domain.model.imports.ImportResource;
import com.ninsky.cronos.domain.model.imports.ImportStatus;
import com.ninsky.cronos.domain.port.imports.ImportBatchRepositoryPort;
import com.ninsky.cronos.infrastructure.exception.CatalogException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Read side of the import ledger: history and the exact report a user was shown at the time. */
@Service
@RequiredArgsConstructor
public class ImportHistoryService {

    private final ImportBatchRepositoryPort batchRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<ImportBatchSummary> history(ImportResource resource, ImportStatus status, Pageable pageable) {
        return batchRepository.search(resource, status, pageable).map(ImportBatchSummary::of);
    }

    @Transactional(readOnly = true)
    public ImportReport report(UUID batchId) {
        String json = batchRepository.findById(batchId)
                .orElseThrow(() -> CatalogException.notFound("import.batch.notFound", batchId))
                .reportJson();
        try {
            return objectMapper.readValue(json, ImportReport.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored report of import batch " + batchId + " is unreadable", e);
        }
    }
}
