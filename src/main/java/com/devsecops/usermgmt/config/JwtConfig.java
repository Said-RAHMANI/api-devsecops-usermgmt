package com.devsecops.usermgmt.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Externalised JWT settings.
 *
 * <p>The secret is loaded strictly from the {@code JWT_SECRET}
 * environment variable so it never lives in source control. The
 * expiration defaults to 24h.</p>
 */
@Configuration
@Getter
public class JwtConfig {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:900000}")
    private long expiration;

    /** Refresh token validity in milliseconds. Default: 7 days. */
    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;
}
