package com.devsecops.usermgmt.integration;

import com.devsecops.usermgmt.dto.LoginRequest;
import com.devsecops.usermgmt.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for rate limiting (OWASP API4).
 *
 * <p>Each test uses a unique X-Forwarded-For IP so buckets are independent
 * across test methods (the ConcurrentHashMap is shared within the Spring
 * context). The capacity is set to 3 (login) and 2 (register) via
 * {@link AbstractIntegrationTest} test properties.</p>
 */
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    // ── Login rate limit ─────────────────────────────────────────────────────

    @Test
    void loginRequestsWithinLimitAreAllowed() {
        doRegister("rita");

        // First request (capacity = 3) — must succeed or fail with 401 (bad creds),
        // but never 429
        ResponseEntity<String> response = loginWithIp("rita", "Password1!", "10.1.0.1");

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void loginExceedingLimitReturns429() {
        // Use a unique IP to avoid interference from other tests
        String ip = "10.2.0.1";

        // Make capacity+1 requests (capacity = 3, so 4 total)
        ResponseEntity<String> last = null;
        for (int i = 0; i <= 3; i++) {
            last = loginWithIp("nobody", "Password1!", ip);
        }

        assertThat(last.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(last.getBody()).contains("Too Many Requests");
        assertThat(last.getHeaders().getFirst("Retry-After")).isEqualTo("60");
    }

    // ── Register rate limit ──────────────────────────────────────────────────

    @Test
    void registerRequestsWithinLimitAreAllowed() {
        ResponseEntity<String> response = registerWithIp("user_ok", "20.1.0.1");

        // 201 Created or 400 (duplicate) — never 429 on first request
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void registerExceedingLimitReturns429() {
        String ip = "20.2.0.1";

        // Make capacity+1 requests (capacity = 2, so 3 total)
        ResponseEntity<String> last = null;
        for (int i = 0; i <= 2; i++) {
            last = registerWithIp("reg_user_" + i, ip);
        }

        assertThat(last.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(last.getBody()).contains("Too Many Requests");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ResponseEntity<String> loginWithIp(String username, String password, String ip) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", ip);

        LoginRequest req = LoginRequest.builder()
                .username(username).password(password).build();

        return restTemplate.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(req, headers),
                String.class);
    }

    private ResponseEntity<String> registerWithIp(String username, String ip) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", ip);

        RegisterRequest req = RegisterRequest.builder()
                .username(username)
                .email(username + "@test.com")
                .password("Password1!")
                .build();

        return restTemplate.postForEntity(
                "/api/auth/register",
                new HttpEntity<>(req, headers),
                String.class);
    }
}
