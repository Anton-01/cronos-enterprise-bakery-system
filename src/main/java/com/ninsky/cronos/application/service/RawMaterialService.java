package com.ninsky.cronos.application.service;

import com.ninsky.cronos.application.request.core.CreateRawMaterialRequest;
import com.ninsky.cronos.application.request.core.RawMaterialResponse;
import com.ninsky.cronos.application.request.core.UpdateRawMaterialRequest;
import com.ninsky.cronos.application.request.status.ChangeStatusRequest;
import com.ninsky.cronos.application.response.core.RawMaterialListResponse;
import jakarta.xml.bind.ValidationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface RawMaterialService {
    Page<RawMaterialListResponse> getUserRawMaterials(Pageable pageable, String username);
    RawMaterialResponse createRawMaterial(CreateRawMaterialRequest request, String username);
    RawMaterialResponse updateRawMaterial(UUID id, UpdateRawMaterialRequest request, String userName) throws ValidationException;
    RawMaterialResponse getRawMaterialById(UUID id);
    void changeStatus(UUID id, ChangeStatusRequest request);
}
