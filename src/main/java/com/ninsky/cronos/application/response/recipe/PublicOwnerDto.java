package com.ninsky.cronos.application.response.recipe;

import lombok.Builder;

@Builder
public record PublicOwnerDto(
        String fullName,
        String brandName // Por si en el futuro quieres agregar "Cronos Nisnky Bakery"
) {}
