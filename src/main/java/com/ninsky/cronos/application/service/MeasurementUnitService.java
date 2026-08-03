package com.ninsky.cronos.application.service;


import com.ninsky.cronos.application.request.core.CreateMeasurementUnitRequest;
import com.ninsky.cronos.application.request.core.UpdateMeasurementUnitRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.core.MeasurementUnitResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MeasurementUnitService {
    MeasurementUnitResponse createMeasurementUnit(CreateMeasurementUnitRequest request, String authentication);
    Page<MeasurementUnitResponse> getUserMeasurementUnits(Pageable pageable, String authentication);
    Page<MeasurementUnitResponse> getSystemMeasurementUnits(Pageable pageable, String authentication);
    MeasurementUnitResponse updateMeasurementUnit(UpdateMeasurementUnitRequest request, String authentication);
    void changeStatus(Long id, ChangeStatusRequest request);
}
