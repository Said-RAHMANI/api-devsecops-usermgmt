package com.devsecops.usermgmt.security;

import com.devsecops.usermgmt.config.JwtConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtTokenProvider}.
 *
 * <p>The provider is constructed with a mocked {@link JwtConfig} that
 * supplies a 32+ character HS256 secret and a one-hour expiration. The
 * {@code @PostConstruct} initialiser is invoked manually because Spring
 * is not bootstrapped in this slice.</p>
 */
class JwtTokenProviderTest {

    private static final String TEST_SECRET = "test-secret-please-do-not-use-in-prod-32chars!";
    private static final long ONE_HOUR_MS = 60L * 60L * 1000L;

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        JwtConfig jwtConfig = mock(JwtConfig.class);
        when(jwtConfig.getSecret()).thenReturn(TEST_SECRET);
        when(jwtConfig.getExpiration()).thenReturn(ONE_HOUR_MS);

        provider = new JwtTokenProvider(jwtConfig);
        provider.init(); // Manually trigger @PostConstruct.
    }

    private UserDetails userDetails(String username) {
        return new User(username, "ignored",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void generateTokenProducesNonNullThreePartJwt() {
        String token = provider.generateToken(userDetails("alice"));

        assertThat(token).isNotNull().isNotBlank();
        // A compact JWS has exactly two dots (header.payload.signature).
        assertThat(token.chars().filter(c -> c == '.').count()).isEqualTo(2L);
    }

    @Test
    void validateTokenReturnsTrueForFreshlyIssuedToken() {
        String token = provider.generateToken(userDetails("alice"));

        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void validateTokenReturnsFalseForGarbledToken() {
        assertThat(provider.validateToken("not-a-valid-token")).isFalse();
    }

    @Test
    void getUsernameFromTokenExtractsSubject() {
        String token = provider.generateToken(userDetails("alice"));

        assertThat(provider.getUsernameFromToken(token)).isEqualTo("alice");
    }
}
