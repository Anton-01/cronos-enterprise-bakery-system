package com.ninsky.cronos.infrastructure.security;

import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.infrastructure.config.security.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    public static final String ACCESS_VERSION_CLAIM = "pv";

    private final JwtConfig jwtConfig;

    /**
     * Role/permission names are passed in rather than resolved here — the caller ({@code AuthenticationService})
     * already has them from {@code RoleRepositoryPort}/{@code PermissionRepositoryPort}, so this class stays a
     * pure token builder with no persistence dependency of its own.
     * {@code sessionId} is nullable (e.g. OAuth2 flows that don't always resolve one) — the blacklist
     * check in {@code JwtAuthenticationFilter} treats an absent session claim as "not blacklisted",
     * falling back to the user-level cutoff check only.
     * {@code dpopJkt} is nullable: null issues an ordinary unbound (Bearer) token, exactly today's
     * behavior; non-null binds the token to that key via a {@code cnf.jkt} claim (RFC 9449 §4.2) —
     * only presentable thereafter via the {@code DPoP} auth scheme with a matching proof.
     */
    public String generateAccessToken(User user, UUID sessionId, List<String> roleNames, List<String> permissionNames, String dpopJkt) {
        return generateAccessToken(user, sessionId, roleNames, permissionNames, dpopJkt, null);
    }

    /** {@code accessVersion} becomes the {@code pv} claim; tokens below the user's current version are rejected. */
    public String generateAccessToken(User user, UUID sessionId, List<String> roleNames, List<String> permissionNames, String dpopJkt,
                                      Long accessVersion) {
        Map<String, Object> claims = new HashMap<>();
        if (accessVersion != null) {
            claims.put(ACCESS_VERSION_CLAIM, accessVersion);
        }
        claims.put("jti", UUID.randomUUID().toString());
        claims.put("userId", user.getId().toString());
        claims.put("sessionId", sessionId != null ? sessionId.toString() : null);
        claims.put("email", user.getEmail());
        claims.put("roles", roleNames);
        // Nota: Solo agregar permisos al JWT si no son cientos, para no exceder el tamaño ideal del header HTTP
        claims.put("permissions", permissionNames);
        claims.put("2faEnabled", user.isTwoFactorEnabled());
        if (dpopJkt != null) {
            claims.put("cnf", Map.of("jkt", dpopJkt));
        }

        return buildToken(claims, user.getUsername(), jwtConfig.getAccessTokenExpiration());
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .issuer(jwtConfig.getIssuer())
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    public boolean isTokenValid(String token, String username) {
        try {
            final String tokenUsername = extractUsername(token);
            return (tokenUsername.equals(username) && !isTokenExpired(token));
        } catch (Exception e) {
            log.error("Token validation error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Signature + expiry check for a token whose holder was resolved by its {@code userId} claim.
     * Deliberately does NOT compare {@code sub} (the username at issue time): usernames are
     * user-editable, and a rename must not log the user out.
     */
    public boolean isTokenValidForUserId(String token, UUID userId) {
        try {
            return userId != null && userId.equals(extractUserId(token)) && !isTokenExpired(token);
        } catch (Exception e) {
            log.error("Token validation error: {}", e.getMessage());
            return false;
        }
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // AJUSTADO: Convierte el String del JWT de vuelta a UUID
    public UUID extractUserId(String token) {
        String userIdStr = extractClaim(token, claims -> claims.get("userId", String.class));
        return userIdStr != null ? UUID.fromString(userIdStr) : null;
    }

    public UUID extractSessionId(String token) {
        String sessionIdStr = extractClaim(token, claims -> claims.get("sessionId", String.class));
        return sessionIdStr != null ? UUID.fromString(sessionIdStr) : null;
    }

    /** Null for tokens minted before access versions existed. */
    public Long extractAccessVersion(String token) {
        Number pv = extractClaim(token, claims -> claims.get(ACCESS_VERSION_CLAIM, Number.class));
        return pv == null ? null : pv.longValue();
    }

    public Date extractIssuedAt(String token) {
        return extractClaim(token, Claims::getIssuedAt);
    }

    /** Null if the token was issued unbound (no {@code cnf} claim) — mirrors {@link #extractSessionId}'s null-safe shape. */
    @SuppressWarnings("unchecked")
    public String extractDpopJkt(String token) {
        Map<String, Object> cnf = extractClaim(token, claims -> claims.get("cnf", Map.class));
        return cnf != null ? (String) cnf.get("jkt") : null;
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
