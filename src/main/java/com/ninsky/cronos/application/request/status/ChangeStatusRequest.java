package com.ninsky.cronos.application.request.status;

import com.ninsky.cronos.domain.entity.enums.RecordStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(
        @NotNull(message = "El estatus no puede estar vacío. Valores permitidos: ACTIVE, INACTIVE, ARCHIVED")
        RecordStatus status
) {}
