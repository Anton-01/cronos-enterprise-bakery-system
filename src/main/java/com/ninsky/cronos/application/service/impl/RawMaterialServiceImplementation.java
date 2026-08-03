package com.ninsky.cronos.application.service.impl;

import com.ninsky.cronos.application.request.core.CreateRawMaterialRequest;
import com.ninsky.cronos.application.request.core.DensityConversionRequest;
import com.ninsky.cronos.application.request.core.RawMaterialResponse;
import com.ninsky.cronos.application.request.core.UpdateRawMaterialRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.core.DensityConversionDto;
import com.ninsky.cronos.application.response.core.RawMaterialListResponse;
import com.ninsky.cronos.application.service.RawMaterialService;
import com.ninsky.cronos.domain.entity.auth.User;
import com.ninsky.cronos.domain.entity.core.IngredientConversion;
import com.ninsky.cronos.domain.entity.core.MeasurementUnit;
import com.ninsky.cronos.domain.entity.core.RawMaterial;
import com.ninsky.cronos.domain.entity.recipes.Recipe;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import com.ninsky.cronos.infrastructure.persistence.auth.UserRepository;
import com.ninsky.cronos.infrastructure.persistence.core.IngredientConversionRepository;
import com.ninsky.cronos.infrastructure.persistence.core.MeasurementUnitRepository;
import com.ninsky.cronos.infrastructure.persistence.core.RawMaterialRepository;
import com.ninsky.cronos.infrastructure.persistence.recipe.RecipeRepository;
import jakarta.xml.bind.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RawMaterialServiceImplementation implements RawMaterialService {

    private final RawMaterialRepository rawMaterialRepository;
    private final MeasurementUnitRepository measurementUnitRepository;
    private final IngredientConversionRepository ingredientConversionRepository;
    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;

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
            return mapToDetailedResponse(entity, 1L);
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
        BigDecimal baseUnitCost = calculateBaseUnitCost(
                request.unitCost(),
                request.purchaseQuantity(),
                purchaseUnit,
                request.yieldPercentage()
        );

        RawMaterial rawMaterial = RawMaterial.builder().name(request.name())
                .description(request.description()).brand(request.brand())
                .supplier(request.supplier()).categoryId(request.categoryId())
                .userId(user.getId()).purchaseUnit(purchaseUnit)
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
    public RawMaterialResponse updateRawMaterial(UUID rawMaterialId, UpdateRawMaterialRequest request, String username) throws ValidationException {
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

        BigDecimal newBaseUnitCost = calculateBaseUnitCost(request.unitCost(), request.purchaseQuantity(), purchaseUnit, request.yieldPercentage());

        // Actualizar datos de la entidad principal
        existingMaterial.setName(request.name());
        existingMaterial.setDescription(request.description());
        existingMaterial.setBrand(request.brand());
        existingMaterial.setSupplier(request.supplier());
        existingMaterial.setCategoryId(request.categoryId());
        existingMaterial.setPurchaseUnit(purchaseUnit);
        existingMaterial.setPurchaseQuantity(request.purchaseQuantity());
        existingMaterial.setUnitCost(request.unitCost());
        existingMaterial.setCurrency(request.currency());
        existingMaterial.setYieldPercentage(request.yieldPercentage());
        existingMaterial.setMinimumStock(request.minimumStock());
        existingMaterial.setBaseUnitCost(newBaseUnitCost);

        if (financialImpactDetected) {
            BigDecimal recalculatedBaseUnitCost = calculateBaseUnitCost(
                    existingMaterial.getUnitCost(),
                    existingMaterial.getPurchaseQuantity(),
                    existingMaterial.getPurchaseUnit(),
                    existingMaterial.getYieldPercentage()
            );
            existingMaterial.setBaseUnitCost(recalculatedBaseUnitCost);
        }

        rawMaterialRepository.save(existingMaterial);

        // NUEVO: Procesar las conversiones de densidad en el Update
        if (request.densityConversion() != null) {
            updateDensityConversions(existingMaterial.getId(), request.densityConversion(), 1L);
        }

        if (financialImpactDetected) {
            log.warn("Cambio financiero detectado en {}. Marcando recetas dependientes para recálculo.", existingMaterial.getName());
            recipeRepository.markRecipesAsNeedingRecalculationByRawMaterialId(existingMaterial.getId());
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
            createAndSaveConversion(rawMaterialId, cupUnit, gramsUnit, densityReq.gramsPerCup(), 1L);
        }

        // 3. Guardar conversión para Cucharadas (tbsp)
        if (densityReq.gramsPerTablespoon() != null) {
            MeasurementUnit tbspUnit = measurementUnitRepository.findByCodeIdentity("tbsp").orElseThrow(() -> new IllegalStateException("Unidad 'tbsp' (Cucharadas) no encontrada."));
            createAndSaveConversion(rawMaterialId, tbspUnit, gramsUnit, densityReq.gramsPerTablespoon(), 1L);
        }

        // 4. Guardar conversión para Cucharaditas (tsp)
        if (densityReq.gramsPerTeaspoon() != null) {
            MeasurementUnit tspUnit = measurementUnitRepository.findByCodeIdentity("tsp").orElseThrow(() -> new IllegalStateException("Unidad 'tsp' (Cucharaditas) no encontrada."));
            createAndSaveConversion(rawMaterialId, tspUnit, gramsUnit, densityReq.gramsPerTeaspoon(), 1L);
        }
    }

    private void createAndSaveConversion(UUID materialId, MeasurementUnit volumeUnit, MeasurementUnit massUnit, BigDecimal factor, Long userId) {
        IngredientConversion conversion = new IngredientConversion();
        conversion.setIngredientId(materialId);
        conversion.setVolumeUnit(volumeUnit);
        conversion.setMassUnit(massUnit);
        conversion.setFactor(factor);
        conversion.setUserId(userId);

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


    /**
     * CÁLCULO DE COSTO BASE EXACTO
     * Fórmula: Costo Total / (Cantidad Compra * Multiplicador * (Rendimiento / 100))
     */
    private BigDecimal calculateBaseUnitCost(BigDecimal unitCost, BigDecimal purchaseQuantity, MeasurementUnit purchaseUnit, BigDecimal yieldPercentage) {

        // Calcular la cantidad total en la unidad base del sistema (Ej. 5 Kg -> 5000 Gramos)
        BigDecimal quantityInBaseUnit = purchaseQuantity.multiply(purchaseUnit.getMultiplierToBase());

        // Aplicar el porcentaje de merma (Ej. 5000g * (85% / 100) = 4250g reales utilizables)
        BigDecimal yieldFactor = yieldPercentage.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
        BigDecimal usableQuantityInBaseUnit = quantityInBaseUnit.multiply(yieldFactor);

        // Calcular el costo final por cada unidad base real (Ej. $150 / 4250g = $0.035294 / g)
        return unitCost.divide(usableQuantityInBaseUnit, 6, RoundingMode.HALF_UP);
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
                .purchaseUnitId(material.getPurchaseUnit().getId()).purchaseQuantity(material.getPurchaseQuantity())
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
            String unitCode = conv.getVolumeUnit().getCodeIdentity();
            switch (unitCode) {
                case "cup" -> cup = conv.getFactor();
                case "tbsp" -> tbsp = conv.getFactor();
                case "tsp" -> tsp = conv.getFactor();
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
                material.getPurchaseUnit().getId(),
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
