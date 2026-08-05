package com.ninsky.cronos.application.service.impl;

import com.ninsky.cronos.application.request.core.CreateRawMaterialRequest;
import com.ninsky.cronos.application.request.core.DensityConversionRequest;
import com.ninsky.cronos.application.request.core.UpdateRawMaterialRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.core.DensityConversionDto;
import com.ninsky.cronos.application.response.core.RawMaterialListResponse;
import com.ninsky.cronos.application.response.core.RawMaterialResponse;
import com.ninsky.cronos.application.service.RawMaterialService;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.model.core.IngredientConversion;
import com.ninsky.cronos.domain.model.core.MeasurementUnit;
import com.ninsky.cronos.domain.model.core.RawMaterial;
import com.ninsky.cronos.domain.port.core.IngredientConversionRepositoryPort;
import com.ninsky.cronos.domain.port.core.MeasurementUnitRepositoryPort;
import com.ninsky.cronos.domain.port.core.RawMaterialRepositoryPort;
import com.ninsky.cronos.domain.port.core.RecipeRecalculationPort;
import com.ninsky.cronos.domain.service.core.RawMaterialCostingService;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import com.ninsky.cronos.infrastructure.exception.ValidationException;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RawMaterialServiceImplementation implements RawMaterialService {

    // Density conversions are recorded per-user (IngredientConversion.userId), but that column is
    // Long while User.id is UUID — a pre-existing schema mismatch this phase doesn't touch (it
    // needs a migration, not a mechanical refactor). Preserving the original hardcoded value rather
    // than introducing a lossy/incorrect conversion.
    private static final Long DENSITY_CONVERSION_USER_ID_PLACEHOLDER = 1L;

    private final RawMaterialRepositoryPort rawMaterialRepository;
    private final MeasurementUnitRepositoryPort measurementUnitRepository;
    private final IngredientConversionRepositoryPort ingredientConversionRepository;
    private final UserRepositoryPort userRepository;
    private final RecipeRecalculationPort recipeRecalculationPort;
    private final RawMaterialCostingService costingService;

    @Transactional(readOnly = true)
    @Override
    public Page<RawMaterialListResponse> getUserRawMaterials(Pageable pageable, String username) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado o token inválido"));

        log.info("Fetching paginated Raw Materials by User. Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return rawMaterialRepository.findAllForListByUserId(user.getId(), pageable);
    }

    @Transactional(readOnly = true)
    @Override
    public RawMaterialResponse getRawMaterialById(UUID id) {
        log.info("Iniciando búsqueda de materia prima con ID: {}", id);
        return rawMaterialRepository.findById(id).map(entity -> {
            log.debug("Materia prima encontrada: {}", entity.getName());
            return mapToDetailedResponse(entity, DENSITY_CONVERSION_USER_ID_PLACEHOLDER);
        }).orElseThrow(() -> {
            log.warn("No se encontró la materia prima con ID: {}", id);
            return new ResourceNotFoundException("Materia prima no encontrada con ID: " + id);
        });
    }

    @Transactional
    @Override
    public RawMaterialResponse createRawMaterial(CreateRawMaterialRequest request, String username) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado o token inválido"));

        // 1. Validaciones preventivas de negocio (Adicionales a las de @Valid del Controller)
        validateBusinessRules(request.purchaseQuantity(), request.unitCost(), request.yieldPercentage());

        // 2. Obtener catálogos
        MeasurementUnit purchaseUnit = measurementUnitRepository.findById(request.purchaseUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("Unidad de compra no encontrada."));

        // 3. El Motor de Costos: Calcular el costo real por unidad base (Ej. Costo por 1 Gramo descontando merma)
        BigDecimal baseUnitCost = costingService.calculateBaseUnitCost(
                request.unitCost(),
                request.purchaseQuantity(),
                purchaseUnit.getMultiplierToBase(),
                request.yieldPercentage()
        );

        RawMaterial rawMaterial = RawMaterial.builder().name(request.name())
                .description(request.description()).brand(request.brand())
                .supplier(request.supplier()).categoryId(request.categoryId())
                .userId(user.getId()).purchaseUnitId(purchaseUnit.getId())
                .purchaseQuantity(request.purchaseQuantity())
                .unitCost(request.unitCost()).currency(request.currency())
                .yieldPercentage(request.yieldPercentage()).baseUnitCost(baseUnitCost)
                .currentStock(BigDecimal.ZERO) // Inicializa en cero, el inventario se nutre en otro proceso
                .minimumStock(request.minimumStock())
                .build();

        rawMaterial = rawMaterialRepository.save(rawMaterial);

        // Procesar las conversiones de densidad si vienen en el Request
        if (request.densityConversion() != null) {
            saveDensityConversions(rawMaterial.getId(), request.densityConversion());
        }

        log.info("Insumo creado exitosamente: {} con ID: {}", rawMaterial.getName(), rawMaterial.getId());

        return mapToResponse(rawMaterial);
    }

    @Transactional
    @Override
    public RawMaterialResponse updateRawMaterial(UUID rawMaterialId, UpdateRawMaterialRequest request, String username) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado o token inválido"));

        RawMaterial existingMaterial = rawMaterialRepository.findById(rawMaterialId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo no encontrado."));

        if (!existingMaterial.getUserId().equals(user.getId())) {
            throw new ValidationException("No tienes permisos para modificar este insumo.");
        }

        validateBusinessRules(request.purchaseQuantity(), request.unitCost(), request.yieldPercentage());

        MeasurementUnit purchaseUnit = measurementUnitRepository.findById(request.purchaseUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("Unidad de compra no encontrada."));

        boolean financialImpactDetected = false;

        if (request.unitCost() != null && existingMaterial.getUnitCost().compareTo(request.unitCost()) != 0) {
            financialImpactDetected = true;
        }
        if (request.yieldPercentage() != null && existingMaterial.getYieldPercentage().compareTo(request.yieldPercentage()) != 0) {
            financialImpactDetected = true;
        }
        if (request.purchaseQuantity() != null && existingMaterial.getPurchaseQuantity().compareTo(request.purchaseQuantity()) != 0) {
            financialImpactDetected = true;
        }

        BigDecimal newBaseUnitCost = costingService.calculateBaseUnitCost(
                request.unitCost(), request.purchaseQuantity(), purchaseUnit.getMultiplierToBase(), request.yieldPercentage());

        // Actualizar datos de la entidad principal
        existingMaterial.setName(request.name());
        existingMaterial.setDescription(request.description());
        existingMaterial.setBrand(request.brand());
        existingMaterial.setSupplier(request.supplier());
        existingMaterial.setCategoryId(request.categoryId());
        existingMaterial.setPurchaseUnitId(purchaseUnit.getId());
        existingMaterial.setPurchaseQuantity(request.purchaseQuantity());
        existingMaterial.setUnitCost(request.unitCost());
        existingMaterial.setCurrency(request.currency());
        existingMaterial.setYieldPercentage(request.yieldPercentage());
        existingMaterial.setMinimumStock(request.minimumStock());
        existingMaterial.setBaseUnitCost(newBaseUnitCost);

        if (financialImpactDetected) {
            BigDecimal recalculatedBaseUnitCost = costingService.calculateBaseUnitCost(
                    existingMaterial.getUnitCost(),
                    existingMaterial.getPurchaseQuantity(),
                    purchaseUnit.getMultiplierToBase(),
                    existingMaterial.getYieldPercentage()
            );
            existingMaterial.setBaseUnitCost(recalculatedBaseUnitCost);
        }

        rawMaterialRepository.save(existingMaterial);

        // NUEVO: Procesar las conversiones de densidad en el Update
        if (request.densityConversion() != null) {
            updateDensityConversions(existingMaterial.getId(), request.densityConversion(), DENSITY_CONVERSION_USER_ID_PLACEHOLDER);
        }

        if (financialImpactDetected) {
            log.warn("Cambio financiero detectado en {}. Marcando recetas dependientes para recálculo.", existingMaterial.getName());
            recipeRecalculationPort.markRecipesAsNeedingRecalculation(existingMaterial.getId());
        }

        log.info("Insumo actualizado: {}", existingMaterial.getName());
        return mapToResponse(existingMaterial);
    }

    @Transactional
    public void changeStatus(UUID id, ChangeStatusRequest request) {
        log.info("Updating the status of RawMaterial with ID {} to {}", id, request.status());

        int updatedRows = rawMaterialRepository.updateStatus(id, request.status());

        if (updatedRows == 0) {
            log.warn("Attempt to change the status of a non-existent RawMaterial: {}", id);
            throw new ResourceNotFoundException("El Ingrediente con ID " + id + " no existe.");
        }
    }

    private void saveDensityConversions(UUID rawMaterialId, DensityConversionRequest densityReq) {
        // Idealmente búscala por su código 'g' para no hardcodear IDs.
        MeasurementUnit gramsUnit = measurementUnitRepository.findByCodeIdentity("g").orElseThrow(() -> new IllegalStateException("Unidad 'g' (Gramos) no encontrada en el sistema."));

        // 2. Guardar conversión para Tazas (cup)
        if (densityReq.gramsPerCup() != null) {
            MeasurementUnit cupUnit = measurementUnitRepository.findByCodeIdentity("cup").orElseThrow(() -> new IllegalStateException("Unidad 'cup' (Tazas) no encontrada."));
            createAndSaveConversion(rawMaterialId, cupUnit, gramsUnit, densityReq.gramsPerCup(), DENSITY_CONVERSION_USER_ID_PLACEHOLDER);
        }

        // 3. Guardar conversión para Cucharadas (tbsp)
        if (densityReq.gramsPerTablespoon() != null) {
            MeasurementUnit tbspUnit = measurementUnitRepository.findByCodeIdentity("tbsp").orElseThrow(() -> new IllegalStateException("Unidad 'tbsp' (Cucharadas) no encontrada."));
            createAndSaveConversion(rawMaterialId, tbspUnit, gramsUnit, densityReq.gramsPerTablespoon(), DENSITY_CONVERSION_USER_ID_PLACEHOLDER);
        }

        // 4. Guardar conversión para Cucharaditas (tsp)
        if (densityReq.gramsPerTeaspoon() != null) {
            MeasurementUnit tspUnit = measurementUnitRepository.findByCodeIdentity("tsp").orElseThrow(() -> new IllegalStateException("Unidad 'tsp' (Cucharaditas) no encontrada."));
            createAndSaveConversion(rawMaterialId, tspUnit, gramsUnit, densityReq.gramsPerTeaspoon(), DENSITY_CONVERSION_USER_ID_PLACEHOLDER);
        }
    }

    private void createAndSaveConversion(UUID materialId, MeasurementUnit volumeUnit, MeasurementUnit massUnit, BigDecimal factor, Long userId) {
        IngredientConversion conversion = IngredientConversion.builder()
                .ingredientId(materialId)
                .volumeUnitId(volumeUnit.getId())
                .massUnitId(massUnit.getId())
                .factor(factor)
                .userId(userId)
                .build();

        ingredientConversionRepository.save(conversion);

        log.info("Ingredient Conversion Saved for material ID {}", materialId);
    }

    // EL CONTROLADOR MAESTRO DE LAS ACTUALIZACIONES DE DENSIDAD
    private void updateDensityConversions(UUID rawMaterialId, DensityConversionRequest densityReq, Long userId) {
        upsertOrDeleteConversion(rawMaterialId, "cup", densityReq.gramsPerCup(), userId);
        upsertOrDeleteConversion(rawMaterialId, "tbsp", densityReq.gramsPerTablespoon(), userId);
        upsertOrDeleteConversion(rawMaterialId, "tsp", densityReq.gramsPerTeaspoon(), userId);
    }

    // LÓGICA DINÁMICA: Inserta, Actualiza o Elimina según lo que mande Angular
    private void upsertOrDeleteConversion(UUID materialId, String volumeCode, BigDecimal newFactor, Long userId) {

        MeasurementUnit volumeUnit = measurementUnitRepository.findByCodeIdentity(volumeCode)
                .orElseThrow(() -> new IllegalStateException("Unidad '" + volumeCode + "' no encontrada."));

        Optional<IngredientConversion> existingOpt = ingredientConversionRepository
                .findByIngredientIdAndVolumeUnitIdAndUserId(materialId, volumeUnit.getId(), userId);

        if (newFactor == null) {
            // Escenario 1: El usuario borró el valor en Angular. Eliminamos el registro.
            existingOpt.ifPresent(ingredientConversionRepository::delete);
        } else {
            if (existingOpt.isPresent()) {
                // Escenario 2: Ya existía. Actualizamos el valor (Update).
                IngredientConversion existing = existingOpt.get();
                existing.setFactor(newFactor);
                ingredientConversionRepository.save(existing);
            } else {
                // Escenario 3: No existía. Lo creamos (Insert).
                MeasurementUnit gramsUnit = measurementUnitRepository.findByCodeIdentity("g")
                        .orElseThrow(() -> new IllegalStateException("Unidad 'g' no encontrada."));
                createAndSaveConversion(materialId, volumeUnit, gramsUnit, newFactor, userId);
            }
        }
    }

    private void validateBusinessRules(BigDecimal quantity, BigDecimal cost, BigDecimal yield) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La cantidad de compra debe ser estrictamente mayor a cero para evitar división por cero en el costeo.");
        }
        if (yield == null || yield.compareTo(BigDecimal.ZERO) <= 0 || yield.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("El porcentaje de rendimiento debe estar entre 0.01 y 100.");
        }
        if (cost == null || cost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El costo no puede ser negativo.");
        }
    }

    private RawMaterialResponse mapToResponse(RawMaterial material) {
        return RawMaterialResponse.builder()
                .id(material.getId()).name(material.getName()).description(material.getDescription())
                .brand(material.getBrand()).supplier(material.getSupplier()).categoryId(material.getCategoryId())
                .purchaseUnitId(material.getPurchaseUnitId()).purchaseQuantity(material.getPurchaseQuantity())
                .unitCost(material.getUnitCost()).currency(material.getCurrency()).yieldPercentage(material.getYieldPercentage())
                .minimumStock(material.getMinimumStock())
                .status(material.getStatus().name())
                .build();
    }

    private RawMaterialResponse mapToDetailedResponse(RawMaterial material, Long userId) {

        // 1. Buscar todas las conversiones de este ingrediente y usuario
        List<IngredientConversion> conversions = ingredientConversionRepository.findAllByIngredientIdAndUserId(material.getId(), userId);

        // 2. Extraer los factores según el código de la unidad de volumen
        BigDecimal cup = null;
        BigDecimal tbsp = null;
        BigDecimal tsp = null;

        for (IngredientConversion conv : conversions) {
            String unitCode = measurementUnitRepository.findById(conv.getVolumeUnitId())
                    .map(MeasurementUnit::getCodeIdentity)
                    .orElse(null);
            switch (unitCode == null ? "" : unitCode) {
                case "cup" -> cup = conv.getFactor();
                case "tbsp" -> tbsp = conv.getFactor();
                case "tsp" -> tsp = conv.getFactor();
                default -> { /* not a known display unit, ignore */ }
            }
        }

        // 3. Crear el sub-objeto solo si existe al menos una conversión
        DensityConversionDto densityDto = (cup != null || tbsp != null || tsp != null)
                ? new DensityConversionDto(cup, tbsp, tsp)
                : null;

        // 4. Armar la respuesta completa
        return new RawMaterialResponse(
                material.getId(),
                material.getName(),
                material.getCategoryId(),
                material.getDescription(),
                material.getBrand(),
                material.getSupplier(),
                material.getPurchaseUnitId(),
                material.getPurchaseQuantity(),
                material.getUnitCost(),
                material.getCurrency(),
                material.getYieldPercentage(),
                material.getMinimumStock(),
                material.getBaseUnitCost(),
                material.getStatus().name(),
                densityDto
        );
    }
}
