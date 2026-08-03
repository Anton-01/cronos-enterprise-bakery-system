package com.ninsky.cronos.application.service;

import com.ninsky.cronos.application.request.core.UnitTypeRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.core.UnitTypeResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UnitTypeService {
    UnitTypeResponse createUnitType(UnitTypeRequest request);
    Page<UnitTypeResponse> getUnitTypes(Pageable pageable);
    UnitTypeResponse updateUnitType(String username, Long unitTypeId, UnitTypeRequest request);
    void deleteUnitType(Long id);
    void changeStatus(Long id, ChangeStatusRequest request);
}
