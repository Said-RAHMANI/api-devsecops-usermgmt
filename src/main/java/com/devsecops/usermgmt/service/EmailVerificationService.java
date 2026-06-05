package com.devsecops.usermgmt.service;

import com.devsecops.usermgmt.entity.EmailVerificationToken;
import com.devsecops.usermgmt.entity.User;
import com.devsecops.usermgmt.exception.ResourceNotFoundException;
import com.devsecops.usermgmt.repository.EmailVerificationTokenRepository;
import com.devsecops.usermgmt.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Manages email verification token lifecycle.
 *
 * <p>The verification flow is architecturally complete but not enforced:
 * accounts are usable immediately after registration. To enforce it, set
 * {@code user.setEmailVerified(false)} at registration and reject login
 * when the flag is still false.</p>
 *
 * <p>In a full implementation, {@link #createToken} would trigger an email
 * via a {@code JavaMailSender} or an external email service.</p>
 */
@Slf4j
@Service
public class EmailVerificationService {

    /** Token validity window. */
    private static final long TOKEN_VALIDITY_HOURS = 24;

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepository,
                                    UserRepository userRepository) {
        this.tokenRepository = tokenRepository;
        this.userRepository  = userRepository;
    }

    /**
     * Generates a verification token for {@code username} and persists it.
     * Any pre-existing token for that user is replaced.
     *
     * @return the raw token string (should be sent by email — not returned in API)
     */
    @Transactional
    public String createToken(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        // Delete any existing token for the user
        tokenRepository.findByUser(user).ifPresent(tokenRepository::delete);

        String rawToken = UUID.randomUUID().toString();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .token(rawToken)
                .expiresAt(Instant.now().plus(TOKEN_VALIDITY_HOURS, ChronoUnit.HOURS))
                .build();

        tokenRepository.save(token);
        log.info("Email verification token created for user '{}'", username);
        return rawToken;
    }

    /**
     * Validates the token and marks the user's email as verified.
     *
     * @throws IllegalArgumentException if the token is unknown, expired, or already used
     */
    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        if (token.isUsed()) {
            throw new IllegalArgumentException("Verification token has already been used");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            tokenRepository.delete(token);
            throw new IllegalArgumentException(
                    "Verification token has expired. Please request a new one.");
        }

        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);

        log.info("Email verified for user '{}'", user.getUsername());
    }

    /** Scheduled cleanup of expired tokens. Runs every 24 hours. */
    @Scheduled(fixedRateString = "${email-verification.cleanup-interval-ms:86400000}")
    @Transactional
    public void cleanupExpiredTokens() {
        tokenRepository.deleteAllExpiredBefore(Instant.now());
        log.debug("Expired email verification tokens cleaned up");
    }
}
