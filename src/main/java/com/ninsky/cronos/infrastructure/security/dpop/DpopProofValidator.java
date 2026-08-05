package com.ninsky.cronos.infrastructure.security.dpop;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ninsky.cronos.infrastructure.config.security.DpopConfig;
import com.ninsky.cronos.infrastructure.exception.InvalidTokenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;

/**
 * Validates a DPoP proof JWT (RFC 9449 §4.3) against the request it accompanies. A proof is a
 * JWT the client signs with an ephemeral key it embeds in its own header ({@code jwk}) — the
 * server verifies the signature against that embedded key, then checks {@code htm}/{@code htu}
 * match the actual request, {@code iat} is fresh, and {@code jti} hasn't been seen before.
 * Returns the RFC 7638 JWK thumbprint ({@code jkt}) of the proving key on success.
 */
@Component
@RequiredArgsConstructor
public class DpopProofValidator {

    private static final String DPOP_TYPE = "dpop+jwt";

    private final DpopConfig dpopConfig;
    private final DpopReplayGuard replayGuard;

    /**
     * @param rawProof           the raw {@code DPoP} header value
     * @param expectedHttpMethod the actual HTTP method of the request the proof accompanies
     * @param expectedHttpUri    the actual absolute URI of the request (e.g. {@code request.getRequestURL()}), no query/fragment
     * @return the JWK thumbprint ({@code jkt}) of the key that signed the proof
     * @throws InvalidTokenException if the proof is missing, malformed, expired, replayed, or doesn't match the request
     */
    public String validate(String rawProof, String expectedHttpMethod, String expectedHttpUri) {
        if (!dpopConfig.isEnabled()) {
            throw new InvalidTokenException("DPoP support is disabled");
        }
        if (rawProof == null || rawProof.isBlank()) {
            throw new InvalidTokenException("Missing DPoP proof");
        }

        SignedJWT proof = parse(rawProof);
        JWSHeader header = proof.getHeader();

        JOSEObjectType type = header.getType();
        if (type == null || !DPOP_TYPE.equals(type.getType())) {
            throw new InvalidTokenException("DPoP proof has an invalid or missing typ header");
        }

        JWK jwk = header.getJWK();
        if (jwk == null) {
            throw new InvalidTokenException("DPoP proof is missing its embedded jwk header");
        }
        verifySignature(proof, jwk);

        JWTClaimsSet claims = claims(proof);
        String jti = requireClaim(claims, "jti");
        String htm = requireClaim(claims, "htm");
        String htu = requireClaim(claims, "htu");
        Date iat = claims.getIssueTime();
        if (iat == null) {
            throw new InvalidTokenException("DPoP proof is missing iat");
        }

        Duration maxAge = Duration.ofSeconds(dpopConfig.getProofMaxAgeSeconds());
        Instant now = Instant.now();
        Instant issuedAt = iat.toInstant();
        if (issuedAt.isBefore(now.minus(maxAge)) || issuedAt.isAfter(now.plusSeconds(30))) {
            throw new InvalidTokenException("DPoP proof is stale or issued in the future");
        }

        if (!expectedHttpMethod.equalsIgnoreCase(htm)) {
            throw new InvalidTokenException("DPoP proof htm does not match the request");
        }
        if (!urisMatch(expectedHttpUri, htu)) {
            throw new InvalidTokenException("DPoP proof htu does not match the request");
        }

        if (!replayGuard.markAndCheckNotReplayed(jti, maxAge)) {
            throw new InvalidTokenException("DPoP proof has already been used");
        }

        return thumbprint(jwk);
    }

    private String thumbprint(JWK jwk) {
        try {
            return jwk.computeThumbprint().toString();
        } catch (JOSEException e) {
            throw new InvalidTokenException("DPoP proof jwk could not be thumbprinted");
        }
    }

    private SignedJWT parse(String rawProof) {
        try {
            return SignedJWT.parse(rawProof);
        } catch (java.text.ParseException e) {
            throw new InvalidTokenException("DPoP proof is not a valid JWS");
        }
    }

    private JWTClaimsSet claims(SignedJWT proof) {
        try {
            return proof.getJWTClaimsSet();
        } catch (java.text.ParseException e) {
            throw new InvalidTokenException("DPoP proof has malformed claims");
        }
    }

    private String requireClaim(JWTClaimsSet claims, String name) {
        try {
            String value = claims.getStringClaim(name);
            if (value == null || value.isBlank()) {
                throw new InvalidTokenException("DPoP proof is missing claim: " + name);
            }
            return value;
        } catch (java.text.ParseException e) {
            throw new InvalidTokenException("DPoP proof has a malformed claim: " + name);
        }
    }

    private void verifySignature(SignedJWT proof, JWK jwk) {
        try {
            JWSVerifier verifier;
            if (jwk instanceof ECKey ecKey) {
                verifier = new ECDSAVerifier(ecKey.toECPublicKey());
            } else if (jwk instanceof RSAKey rsaKey) {
                verifier = new RSASSAVerifier(rsaKey.toRSAPublicKey());
            } else {
                throw new InvalidTokenException("DPoP proof uses an unsupported key type");
            }
            if (!proof.verify(verifier)) {
                throw new InvalidTokenException("DPoP proof signature is invalid");
            }
        } catch (JOSEException e) {
            throw new InvalidTokenException("DPoP proof signature could not be verified");
        }
    }

    /** Structural comparison (scheme/host case-insensitive, default ports stripped, exact path) — never query/fragment. */
    private boolean urisMatch(String actual, String claimed) {
        try {
            return normalize(new URI(actual)).equals(normalize(new URI(claimed)));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private URI normalize(URI uri) throws URISyntaxException {
        String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
            port = -1;
        }
        return new URI(scheme, null, host, port, uri.getPath(), null, null);
    }
}
