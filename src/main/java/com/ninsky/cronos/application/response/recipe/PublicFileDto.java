package com.ninsky.cronos.application.response.recipe;

import lombok.Builder;

@Builder
public record PublicFileDto(
        String url,
        String fileType,
        String description
) {}
