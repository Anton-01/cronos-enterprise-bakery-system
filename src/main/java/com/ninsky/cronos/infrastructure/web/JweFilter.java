package com.ninsky.cronos.infrastructure.web;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.jwk.ECKey;
import com.ninsky.cronos.infrastructure.config.security.JweConfig;
import com.ninsky.cronos.infrastructure.security.jwe.JweKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * Opt-in JWE payload encryption, negotiated purely by headers — a request/response that doesn't
 * use them is untouched, zero overhead. Request side: {@code Content-Type: application/jwe} gets
 * decrypted and re-exposed as plain JSON to everything downstream. Response side: header
 * {@code X-JWE-Response-Key} (a client's ephemeral EC public JWK, base64url-encoded) gets the
 * response buffered, then encrypted back to that key. The two directions are independent signals.
 * Runs right after {@link TraceIdFilter}, wrapping Spring Security and MVC entirely — same
 * precedent, since {@code ErrorInterceptorAspect} explicitly can't advise {@code Filter}s.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class JweFilter extends OncePerRequestFilter {

    private static final String JWE_MEDIA_TYPE = "application/jwe";
    private static final String RESPONSE_KEY_HEADER = "X-JWE-Response-Key";

    private final JweKeyService jweKeyService;
    private final JweConfig jweConfig;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        HttpServletRequest effectiveRequest = request;
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith(JWE_MEDIA_TYPE)) {
            try {
                effectiveRequest = decryptRequest(request);
            } catch (Exception e) {
                log.warn("Failed to decrypt JWE request body: {}", e.getMessage());
                writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid encrypted request body");
                return;
            }
        }

        String responseKeyHeader = request.getHeader(RESPONSE_KEY_HEADER);
        if (responseKeyHeader == null || isExcludedPath(request)) {
            filterChain.doFilter(effectiveRequest, response);
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(effectiveRequest, wrappedResponse);
        finalizeEncryptedResponse(wrappedResponse, response, responseKeyHeader);
    }

    private HttpServletRequest decryptRequest(HttpServletRequest request) throws Exception {
        String rawBody = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        JWEObject jwe = JWEObject.parse(rawBody);
        assertExpectedAlgorithm(jwe);
        jwe.decrypt(new ECDHDecrypter(jweKeyService.getKeyPair().toECPrivateKey()));
        byte[] decryptedJson = jwe.getPayload().toBytes();
        return new DecryptedRequestWrapper(request, decryptedJson);
    }

    private void finalizeEncryptedResponse(ContentCachingResponseWrapper wrappedResponse, HttpServletResponse realResponse, String responseKeyHeader) throws IOException {
        byte[] buffered = wrappedResponse.getContentAsByteArray();

        if (buffered.length > jweConfig.getResponse().getMaxBufferedBytes()) {
            // Buffering-before-commit is exactly what makes this safe to abort — never fall back to
            // writing the real (unencrypted) payload just because it didn't fit.
            log.error("Response body ({} bytes) exceeds jwe.response.max-buffered-bytes ({}); refusing to encrypt or leak plaintext", buffered.length, jweConfig.getResponse().getMaxBufferedBytes());
            writeJsonError(realResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Response too large to encrypt");
            return;
        }

        String contentType = wrappedResponse.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).contains("json")) {
            log.warn("X-JWE-Response-Key was sent but the response content type ({}) isn't JSON-family; writing through unencrypted", contentType);
            wrappedResponse.copyBodyToResponse();
            return;
        }

        try {
            ECKey clientKey = ECKey.parse(new String(Base64.getUrlDecoder().decode(responseKeyHeader), StandardCharsets.UTF_8));
            JWEObject jwe = new JWEObject(new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A256GCM).build(), new Payload(buffered));
            jwe.encrypt(new ECDHEncrypter(clientKey.toECPublicKey()));

            realResponse.setContentType(JWE_MEDIA_TYPE);
            byte[] compact = jwe.serialize().getBytes(StandardCharsets.UTF_8);
            realResponse.setContentLength(compact.length);
            realResponse.getOutputStream().write(compact);
        } catch (Exception e) {
            log.error("Failed to encrypt response for X-JWE-Response-Key: {}", e.getMessage());
            writeJsonError(realResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to encrypt response");
        }
    }

    private void assertExpectedAlgorithm(JWEObject jwe) throws IOException {
        // Nimbus's ECDHDecrypter transparently accepts ECDH-ES and its key-wrapping variants — without
        // this explicit check, an attacker could pick a different alg/enc off the wire than intended
        // (classic alg-confusion territory), not just a style preference.
        JWEHeader header = jwe.getHeader();
        if (header.getAlgorithm() != JWEAlgorithm.ECDH_ES || header.getEncryptionMethod() != EncryptionMethod.A256GCM) {
            throw new IOException("Unsupported JWE alg/enc: " + header.getAlgorithm() + "/" + header.getEncryptionMethod());
        }
    }

    private boolean isExcludedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return jweConfig.getResponse().getExcludedPaths().stream().anyMatch(path::startsWith);
    }

    private void writeJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    /** Exposes a decrypted byte payload as the request body, with Content-Type rewritten so downstream Jackson binding is none the wiser. */
    private static class DecryptedRequestWrapper extends HttpServletRequestWrapper {
        private final byte[] body;

        DecryptedRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public String getContentType() {
            return MediaType.APPLICATION_JSON_VALUE;
        }

        @Override
        public String getHeader(String name) {
            if ("Content-Type".equalsIgnoreCase(name)) {
                return MediaType.APPLICATION_JSON_VALUE;
            }
            return super.getHeader(name);
        }

        @Override
        public java.util.Enumeration<String> getHeaders(String name) {
            if ("Content-Type".equalsIgnoreCase(name)) {
                return java.util.Collections.enumeration(java.util.List.of(MediaType.APPLICATION_JSON_VALUE));
            }
            return super.getHeaders(name);
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream byteStream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return byteStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // no-op: this wrapper serves an already-fully-buffered in-memory body, never async
                }

                @Override
                public int read() {
                    return byteStream.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
