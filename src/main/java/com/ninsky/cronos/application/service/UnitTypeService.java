package com.ninsky.cronos.application.service;

import com.ninsky.cronos.application.request.core.UnitTypeRequest;
import com.ninsky.cronos.application.response.core.UnitTypeResponse;
import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import com.ninsky.cronos.domain.model.audit.Actor;
import com.ninsky.cronos.domain.port.core.UnitTypeSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UnitTypeService {

    UnitTypeResponse createUnitType(UnitTypeRequest request, Actor actor);

    UnitTypeResponse updateUnitType(Long id, UnitTypeRequest request, Actor actor);

    UnitTypeResponse getUnitType(Long id);

    Page<UnitTypeResponse> searchUnitTypes(UnitTypeSearchCriteria criteria, Pageable pageable);

    /** Active unit types, alphabetical — the options of the "Unit type" picker. Cached. */
    List<UnitTypeResponse> getActiveCatalog();

    void deleteUnitType(Long id, Actor actor);

    void changeStatus(Long id, RecordStatus status, Actor actor);
}
