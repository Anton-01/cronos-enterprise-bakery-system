package com.ninsky.cronos.application.response.menu;

import lombok.Builder;

import java.util.List;

@Builder
public record MenuItemResponse(
        String code,
        String label,
        String icon,
        String path,
        List<MenuItemResponse> children
) { }
