package com.devsecops.usermgmt.integration;

import com.devsecops.usermgmt.dto.JwtResponse;
import com.devsecops.usermgmt.dto.LoginRequest;
import com.devsecops.usermgmt.dto.RegisterRequest;
import com.devsecops.usermgmt.repository.RefreshTokenRepository;
import com.devsecops.usermgmt.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests.
 *
 * <p>Provides a shared PostgreSQL Testcontainer, a {@link TestRestTemplate}
 * wired to the running server, and utility methods for registering and
 * authenticating users. The database is wiped before each test to guarantee
 * isolation.</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "jwt.secret=integration-test-secret-key-must-be-at-least-32-chars!",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // Small rate-limit buckets for fast rate-limiting tests
        "rate-limit.login.capacity=3",
        "rate-limit.login.refill-tokens=3",
        "rate-limit.login.refill-period-seconds=60",
        "rate-limit.register.capacity=2",
        "rate-limit.register.refill-tokens=2",
        "rate-limit.register.refill-period-seconds=60",
        // Disable scheduled cleanup to avoid noise during tests
        "refresh-token.cleanup-interval-ms=86400000"
})
public abstract class AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    protected RegisterRequest registerRequest(String username) {
        return RegisterRequest.builder()
                .username(username)
                .email(username + "@test.com")
                .password("Password1!")
                .build();
    }

    protected ResponseEntity<JwtResponse> doRegister(String username) {
        HttpHeaders headers = jsonHeaders();
        return restTemplate.postForEntity(
                "/api/auth/register",
                new HttpEntity<>(registerRequest(username), headers),
                JwtResponse.class);
    }

    protected ResponseEntity<JwtResponse> doLogin(String username) {
        HttpHeaders headers = jsonHeaders();
        LoginRequest req = LoginRequest.builder()
                .username(username)
                .password("Password1!")
                .build();
        return restTemplate.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(req, headers),
                JwtResponse.class);
    }

    protected String registerAndGetToken(String username) {
        doRegister(username);
        ResponseEntity<JwtResponse> login = doLogin(username);
        return login.getBody().getToken();
    }

    protected HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    protected HttpHeaders bearerHeaders(String token) {
        HttpHeaders h = jsonHeaders();
        h.setBearerAuth(token);
        return h;
    }
}
