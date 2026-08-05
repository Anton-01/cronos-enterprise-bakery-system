package com.ninsky.cronos.presentation.controller.security;

import com.ninsky.cronos.infrastructure.security.jwe.JweKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public key discovery for JWE. Returns the bare JWK JSON object (not wrapped in the app's
 * {@code ApiResponseEnvelope} — a generic JOSE client library expects a plain JWK shape here,
 * and {@code EnvelopeResponseBodyAdvice} only wraps {@code ApiResponse<?>} return values, so a
 * plain {@code Map} return naturally passes through unwrapped).
 */
@RestController
@RequestMapping("/security")
@RequiredArgsConstructor
public class SecurityKeysController {

    private final JweKeyService jweKeyService;

    @GetMapping("/jwe-public-key")
    public Map<String, Object> jwePublicKey() {
        return jweKeyService.getPublicJwk().toJSONObject();
    }
}
