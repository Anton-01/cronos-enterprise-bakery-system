package com.ninsky.cronos.presentation.controller.core;

import com.ninsky.cronos.account.web.AccountWebMvcTest;
import com.ninsky.cronos.application.response.imports.core.CsvImportResponse;
import com.ninsky.cronos.application.service.AllergenService;
import com.ninsky.cronos.application.service.CategoryService;
import com.ninsky.cronos.domain.model.audit.Actor;
import com.ninsky.cronos.domain.model.auth.AuthUserProjection;
import com.ninsky.cronos.infrastructure.exception.CsvImportRejectedException;
import com.ninsky.cronos.infrastructure.exception.CsvImportRejectedException.RowError;
import com.ninsky.cronos.infrastructure.security.CronosUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SYSTEM categories and allergens are shared by every user: their bulk imports (and every allergen
 * write — allergens have no owner) are catalog maintenance, limited to SUPER_ADMIN / MANAGE_CATALOGS.
 */
@AccountWebMvcTest({CategoryController.class, AllergenController.class})
class CatalogImportAuthorizationWebTest {

    private static final UUID USER_ID = UUID.fromString("6d1f6f0e-2d55-4c1a-9a0c-3b8f4f6f2a11");
    private static final MockMultipartFile CSV = new MockMultipartFile("file", "catalog.csv", "text/csv",
            "name,description,type\nHarinas,Harinas de trigo,INGREDIENT\n".getBytes());
    private static final CsvImportResponse IMPORTED = CsvImportResponse.builder()
            .createdCategories(List.of("Harinas")).updatedCategories(List.of()).totalProcessed(1).build();

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CategoryService categoryService;
    @MockitoBean
    private AllergenService allergenService;

    private static RequestPostProcessor as(Set<String> roles, Set<String> permissions) {
        CronosUserPrincipal principal = new CronosUserPrincipal(new AuthUserProjection(USER_ID, "someone", "a@b.c", "x",
                true, true, true, true, false, null, roles, permissions));
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static RequestPostProcessor baker() {
        return as(Set.of("USER"), Set.of("VIEW_DASHBOARD"));
    }

    @Test
    void regularUsersCannotImportSystemCategoriesOrAllergens() throws Exception {
        mvc.perform(multipart("/category/import").file(CSV).with(baker())).andExpect(status().isForbidden());
        mvc.perform(multipart("/allergen/import").file(CSV).with(baker())).andExpect(status().isForbidden());
        verifyNoInteractions(categoryService, allergenService);
    }

    @Test
    void regularUsersCannotWriteSharedAllergens() throws Exception {
        mvc.perform(post("/allergen").with(baker()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Gluten\",\"alternativeName\":\"Trigo\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/allergen/{id}/status", UUID.randomUUID()).with(baker()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(allergenService);
    }

    @Test
    void readsStayOpenToEveryAuthenticatedUser() throws Exception {
        when(allergenService.getSystemAllergens(any())).thenReturn(Page.empty());
        mvc.perform(get("/allergen/system").with(baker())).andExpect(status().isOk());
    }

    @Test
    void superAdminsCanImportAndAreRecordedAsTheActor() throws Exception {
        when(categoryService.importCategoriesFromCsv(any(), any())).thenReturn(IMPORTED);

        mvc.perform(multipart("/category/import").file(CSV).with(as(Set.of("SUPER_ADMIN"), Set.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalProcessed").value(1));

        verify(categoryService).importCategoriesFromCsv(any(), eq(new Actor(USER_ID, "someone")));
    }

    @Test
    void theManageCatalogsPermissionIsEnough() throws Exception {
        when(allergenService.importAllergensFromCsv(any(), any())).thenReturn(IMPORTED);

        mvc.perform(multipart("/allergen/import").file(CSV).with(as(Set.of("CATALOG_MANAGER"), Set.of("MANAGE_CATALOGS"))))
                .andExpect(status().isOk());
    }

    @Test
    void anInvalidFileIsA400WithOneLocalizedErrorPerProblem() throws Exception {
        when(categoryService.importCategoriesFromCsv(any(), any())).thenThrow(new CsvImportRejectedException(List.of(
                RowError.of(3, "type", "import.cell.notAllowed", "BREAD", "[PRODUCT, INGREDIENT]"),
                RowError.of(null, null, "import.csv.notUtf8"))));

        mvc.perform(multipart("/category/import").file(CSV).with(as(Set.of("SUPER_ADMIN"), Set.of()))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("type"))
                .andExpect(jsonPath("$.errors[0].message").value("Line 3: Value \"BREAD\" is not allowed; expected one of [PRODUCT, INGREDIENT]"))
                .andExpect(jsonPath("$.errors[1].message").value("The file is not UTF-8 encoded: save it as \"CSV UTF-8\""));
    }
}
