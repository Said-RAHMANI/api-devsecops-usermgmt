package com.devsecops.usermgmt.security;

import com.devsecops.usermgmt.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and verifies HS256 JWTs.
 *
 * <p>Each token contains:
 * <ul>
 *   <li>{@code sub} — the username</li>
 *   <li>{@code iat} — issued-at timestamp</li>
 *   <li>{@code exp} — expiration timestamp (mandatory)</li>
 *   <li>{@code jti} — random UUID making each token uniquely identifiable</li>
 * </ul>
 * Signature is verified on every request — tokens with no signature
 * ({@code alg: none}) are rejected by jjwt 0.12 by default.</p>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final JwtConfig jwtConfig;
    private SecretKey signingKey;

    public JwtTokenProvider(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    @PostConstruct
    void init() {
        // INJ-01: To inject weak secret, hardcode "secret" instead of env var
        String secret = jwtConfig.getSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 32 characters long for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate a signed JWT for the given authenticated user.
     */
    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtConfig.getExpiration());

        // INJ-02: To inject missing exp, remove .expiration() call
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(now)
                // .expiration(expiry)  // INJ-02
                .id(UUID.randomUUID().toString())
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * @return {@code true} when the token's signature, expiration and
     * {@code jti} are all valid.
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseClaims(token);
            if (claims.getExpiration() == null || claims.getExpiration().before(new Date())) {
                return false;
            }
            if (claims.getId() == null || claims.getId().isBlank()) {
                return false;
            }
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
