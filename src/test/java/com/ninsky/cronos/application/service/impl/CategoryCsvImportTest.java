package com.ninsky.cronos.application.service.impl;

import com.ninsky.cronos.application.imports.ImportProperties;
import com.ninsky.cronos.application.response.imports.core.CsvImportResponse;
import com.ninsky.cronos.application.service.audit.CatalogAuditTrail;
import com.ninsky.cronos.domain.entity.enums.CategoryScope;
import com.ninsky.cronos.domain.entity.enums.CategoryType;
import com.ninsky.cronos.domain.model.audit.Actor;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.core.Category;
import com.ninsky.cronos.domain.port.core.CategoryRepositoryPort;
import com.ninsky.cronos.infrastructure.exception.CsvImportRejectedException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CategoryCsvImportTest {

    private static final Actor ADMIN = new Actor(UUID.randomUUID(), "admin");

    private final CategoryRepositoryPort repository = mock(CategoryRepositoryPort.class);
    private final CatalogAuditTrail auditTrail = mock(CatalogAuditTrail.class);
    private final CategoryServiceImplementation service = new CategoryServiceImplementation(repository, auditTrail,
            new ImportProperties(DataSize.ofMegabytes(2), 2000));

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "categories.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void oneInvalidRowRejectsTheWholeFileAndNothingIsWritten() {
        assertThatThrownBy(() -> service.importCategoriesFromCsv(
                csv("name,description,type\nHarinas,Trigo,INGREDIENT\nPan,Bollería,BREAD\n"), ADMIN))
                .isInstanceOf(CsvImportRejectedException.class);

        verifyNoInteractions(repository, auditTrail);
    }

    @Test
    void validFileUpsertsSystemCategoriesAndIsAudited() {
        Category existing = Category.builder().id(5L).name("Harinas").description("old").type(CategoryType.INGREDIENT)
                .scope(CategoryScope.SYSTEM).build();
        when(repository.findByNameForOwner("Harinas", CategoryType.INGREDIENT, null)).thenReturn(Optional.of(existing));
        when(repository.findByNameForOwner("Pasteles", CategoryType.PRODUCT, null)).thenReturn(Optional.empty());

        CsvImportResponse response = service.importCategoriesFromCsv(
                csv("name,description,type\nHarinas,Trigo y centeno,INGREDIENT\nPasteles,Pastelería fina,product\n"), ADMIN);

        assertThat(response.createdCategories()).containsExactly("Pasteles");
        assertThat(response.updatedCategories()).containsExactly("Harinas");
        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
        verify(repository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Category::scope).containsOnly(CategoryScope.SYSTEM);
        assertThat(saved.getAllValues().getFirst().description()).isEqualTo("Trigo y centeno");
        verify(auditTrail).record(eq(ADMIN), eq(AuditAction.DATA_IMPORT_COMMITTED), eq(CatalogAuditTrail.TARGET_CATEGORY),
                isNull(), isNull(), anyString());
        verify(repository, never()).delete(any());
    }
}
