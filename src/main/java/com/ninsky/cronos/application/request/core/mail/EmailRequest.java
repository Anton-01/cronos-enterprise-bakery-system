package com.ninsky.cronos.application.request.core.mail;

import lombok.Builder;
import java.util.Map;

@Builder
public record EmailRequest(
        String to,
        String subject,
        String templateName, // Ej: "auth/new-device", "sales/quote", "inventory/low-stock"
        Map<String, Object> variables // Variables dinámicas para inyectar en el HTML
) {}
