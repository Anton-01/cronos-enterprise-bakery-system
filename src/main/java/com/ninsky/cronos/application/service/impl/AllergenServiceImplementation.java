package com.ninsky.cronos.application.service.impl;

import com.ninsky.cronos.application.request.core.AllergenRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.core.AllergenResponse;
import com.ninsky.cronos.application.response.imports.core.CsvImportResponse;
import com.ninsky.cronos.application.service.AllergenService;
import com.ninsky.cronos.domain.model.core.Allergen;
import com.ninsky.cronos.domain.port.core.AllergenRepositoryPort;
import com.ninsky.cronos.infrastructure.exception.DuplicateResourceException;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import com.ninsky.cronos.infrastructure.exception.SystemResourceException;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service @Slf4j
@RequiredArgsConstructor
public class AllergenServiceImplementation implements AllergenService {

    private final AllergenRepositoryPort allergenRepository;
    private final UserRepositoryPort userRepository;

    /**
     * Creates a new Allergen
     */
    @Transactional
    @Override
    public AllergenResponse createAllergen(AllergenRequest request, String username) {
        if (allergenRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Allergen already exists");
        }

        Allergen allergen = Allergen.builder().name(request.name().trim())
                .alternativeName(request.alternativeName().trim())
                .description(request.description().trim())
                .build();

        allergen = allergenRepository.save(allergen);

        log.info("Allergen created: {} ", allergen.getName());

        return mapToResponse(allergen);
    }

    /**
     * Gets all allergens available for a user (system + user's own) - paginated
     */
    @Transactional(readOnly = true)
    @Override
    public Page<AllergenResponse> getUserAllergens(Pageable pageable) {
        log.debug("Fetching paginated Allergens By User. Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return allergenRepository.findAllByOrderByIdAsc(pageable).map(this::mapToResponse);
    }

    /**
     * Gets system allergens paginated
     */
    @Transactional(readOnly = true)
    @Override
    public Page<AllergenResponse> getSystemAllergens(Pageable pageable) {
        log.debug("Fetching paginated Allergens System. Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return allergenRepository.findSystemAllergens(pageable).map(this::mapToResponse);
    }

    /**
     * Update a new allergen
     */
    @Transactional
    @Override
    public AllergenResponse updateAllergen(UUID allergenId, AllergenRequest request, String username) {
        userRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado o token inválido"));

        Allergen allergen = allergenRepository.findById(allergenId)
                .orElseThrow(() -> new ResourceNotFoundException("Allergen not found with id: " + allergenId));

        if (Boolean.TRUE.equals(allergen.getIsSystemDefault())) {
            throw new SystemResourceException("System default allergen cannot be edited");
        }

        if (!allergen.getName().equalsIgnoreCase(request.name().trim()) && allergenRepository.existsByName(request.name().trim())) {
            throw new DuplicateResourceException("Allergen name already exists");
        }

        allergen.setName(request.name().trim());
        allergen.setAlternativeName(request.alternativeName().trim());
        allergen.setDescription(request.description().trim());

        allergen = allergenRepository.save(allergen);
        log.info("Allergen updated: ID {}, Name: {}", allergen.getId(), allergen.getName());

        return mapToResponse(allergen);
    }

    /**
     * Imports System Allergens
     */
    @Transactional
    @Override
    public CsvImportResponse importAllergensFromCsv(MultipartFile file) {
        List<String> created = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        int total = 0;

        CSVFormat csvFormat = CSVFormat.Builder.create().setHeader().setSkipHeaderRecord(true).setIgnoreSurroundingSpaces(true).get();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)); CSVParser csvParser = csvFormat.parse(reader)) {

            for (CSVRecord record : csvParser) {
                String name = record.get("name").trim();
                String alternativeName = record.get("alternativeName").trim();
                String description = record.get("description").trim();

                Optional<Allergen> existingAllergen = allergenRepository.findByName(name);

                if (existingAllergen.isPresent()) {
                    Allergen allergen = existingAllergen.get();
                    allergen.setAlternativeName(alternativeName);
                    allergen.setDescription(description);
                    allergenRepository.save(allergen);
                    updated.add(name);
                } else {
                    Allergen allergen = Allergen.builder().name(name).alternativeName(alternativeName).description(description)
                            .isSystemDefault(true).build();

                    allergenRepository.save(allergen);
                    created.add(name);
                }
                total++;
            }
            log.info("CSV Processed: {} categories ({} created, {} updated)", total, created.size(), updated.size());

        } catch (Exception e) {
            log.error("Error processing Allergen CSV file", e);
            throw new RuntimeException("Error processing CSV file: " + e.getMessage());
        }

        return CsvImportResponse.builder().createdCategories(created).updatedCategories(updated).totalProcessed(total).build();
    }

    @Transactional
    public void changeStatus(UUID id, ChangeStatusRequest request) {
        log.info("Updating the status of Allergen with ID {} to {}", id, request.status());

        int updatedRows = allergenRepository.updateStatus(id, request.status());

        if (updatedRows == 0) {
            log.warn("Attempt to change the status of a non-existent Allergen: {}", id);
            throw new ResourceNotFoundException("El Alérgeno con ID " + id + " no existe.");
        }
    }

    private AllergenResponse mapToResponse(Allergen allergen) {
        return AllergenResponse.builder().id(allergen.getId())
                .name(allergen.getName()).alternativeName(allergen.getAlternativeName())
                .description(allergen.getDescription()).isSystemDefault(allergen.getIsSystemDefault())
                .status(allergen.getStatus().name()).build();
    }
}
