package com.ninsky.cronos.application.response.imports.core;

import lombok.Builder;

import java.util.List;

@Builder
public record CsvImportResponse(
        List<String> createdCategories,
        List<String> updatedCategories,
        int totalProcessed
) {}
