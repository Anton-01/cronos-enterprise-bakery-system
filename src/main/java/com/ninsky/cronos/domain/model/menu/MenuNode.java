package com.ninsky.cronos.domain.model.menu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuNode {

    private Long id;
    private Long parentId;
    private String code;
    private String labelEn;
    private String labelEs;
    private String icon;
    private String path;
    private int displayOrder;
    private String requiredPermission;
    @Builder.Default
    private List<MenuNode> children = new ArrayList<>();

    public String label(java.util.Locale locale) {
        return locale != null && "en".equalsIgnoreCase(locale.getLanguage()) ? labelEn : labelEs;
    }
}
