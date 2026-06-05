package com.devsecops.usermgmt.service;

import com.devsecops.usermgmt.config.JwtConfig;
import com.devsecops.usermgmt.entity.RefreshToken;
import com.devsecops.usermgmt.entity.User;
import com.devsecops.usermgmt.exception.ResourceNotFoundException;
import com.devsecops.usermgmt.repository.RefreshTokenRepository;
import com.devsecops.usermgmt.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Manages the lifecycle of refresh tokens: creation, rotation, revocation
 * and scheduled cleanup of expired entries.
 *
 * <p>Rotation strategy: every successful refresh invalidates the old token
 * and issues a brand-new one, limiting the damage of a leaked token.</p>
 */
@Slf4j
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtConfig jwtConfig;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               JwtConfig jwtConfig) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtConfig = jwtConfig;
    }

    /**
     * Creates a new refresh token for the given username.
     * Any pre-existing token for that user is deleted first (single active token per user).
     */
    @Transactional
    public RefreshToken createRefreshToken(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + username));

        // Revoke any existing token before issuing a new one
        refreshTokenRepository.deleteByUser(user);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plusMillis(jwtConfig.getRefreshTokenExpiration()))
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Validates the token string: existence and expiration.
     *
     * @throws IllegalArgumentException if the token is unknown or expired
     */
    @Transactional(readOnly = true)
    public RefreshToken validateRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid refresh token"));

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new IllegalArgumentException(
                    "Refresh token has expired. Please log in again.");
        }
        return refreshToken;
    }

    /**
     * Rotates the token: deletes the old one and creates a fresh one.
     */
    @Transactional
    public RefreshToken rotateRefreshToken(RefreshToken oldToken) {
        String username = oldToken.getUser().getUsername();
        refreshTokenRepository.delete(oldToken);
        return createRefreshToken(username);
    }

    /**
     * Revokes all refresh tokens for the given username (logout).
     */
    @Transactional
    public void revokeByUsername(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            refreshTokenRepository.deleteByUser(user);
            log.info("Revoked refresh tokens for user '{}'", username);
        });
    }

    /**
     * Removes expired tokens from the database. Runs every 24 hours.
     */
    @Scheduled(fixedRateString = "${refresh-token.cleanup-interval-ms:86400000}")
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteAllExpiredBefore(Instant.now());
        log.debug("Expired refresh tokens cleaned up");
    }
}
