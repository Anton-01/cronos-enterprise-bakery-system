package com.ninsky.cronos.application.service;

import com.ninsky.cronos.application.request.core.MeasurementUnitRequest;
import com.ninsky.cronos.application.request.core.UnitConversionRequest;
import com.ninsky.cronos.application.response.core.MeasurementUnitOptionResponse;
import com.ninsky.cronos.application.response.core.MeasurementUnitResponse;
import com.ninsky.cronos.application.response.core.UnitConversionResponse;
import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.model.audit.Actor;
import com.ninsky.cronos.domain.port.core.MeasurementUnitSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MeasurementUnitService {

    MeasurementUnitResponse createMeasurementUnit(MeasurementUnitRequest request, Actor actor);

    MeasurementUnitResponse updateMeasurementUnit(Long id, MeasurementUnitRequest request, Actor actor);

    MeasurementUnitResponse getMeasurementUnit(Long id);

    Page<MeasurementUnitResponse> searchMeasurementUnits(MeasurementUnitSearchCriteria criteria, Pageable pageable);

    /** Active units of active types, grouped by type then size — the recipe unit picker. Cached. */
    List<MeasurementUnitOptionResponse> getSelectableCatalog();

    void deleteMeasurementUnit(Long id, Actor actor);

    void changeStatus(Long id, RecordStatus status, Actor actor);

    /** On-the-fly conversion for recipe screens; never persists anything. */
    UnitConversionResponse convert(UnitConversionRequest request, Actor actor);
}
