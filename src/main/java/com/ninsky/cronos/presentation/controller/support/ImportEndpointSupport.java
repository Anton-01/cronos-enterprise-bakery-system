package com.ninsky.cronos.presentation.controller.support;

import com.ninsky.cronos.application.imports.ImportFile;
import com.ninsky.cronos.domain.model.imports.ImportResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Shared plumbing of the catalog import endpoints (multipart → {@link ImportFile}, template download). */
public final class ImportEndpointSupport {

    public static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private ImportEndpointSupport() {
    }

    public static ImportFile toImportFile(MultipartFile file) {
        try {
            return new ImportFile(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
    }

    public static ResponseEntity<Resource> template(ImportResource resource) {
        Resource template = new ClassPathResource(resource.templateFile());
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(template.getFilename()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(template);
    }
}
