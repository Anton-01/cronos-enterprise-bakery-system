package com.ninsky.cronos.domain.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Permission {

    private Long id;
    private String name;
    private String description;
    private String resource;
    private String action;

    /** URN-style policy string derived from (resource, action) — no separate policy table needed. */
    public String toUrn() {
        String res = resource != null ? resource.toLowerCase() : "unknown";
        String act = action != null ? action.toLowerCase() : "unknown";
        return "urn:cronos:" + res + ":" + act;
    }
}
