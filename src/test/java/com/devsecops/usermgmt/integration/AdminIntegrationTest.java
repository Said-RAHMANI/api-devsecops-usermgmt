package com.devsecops.usermgmt.integration;

import com.devsecops.usermgmt.dto.JwtResponse;
import com.devsecops.usermgmt.entity.Role;
import com.devsecops.usermgmt.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Admin API: paginated user list and authorization.
 */
class AdminIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ── Pagination ───────────────────────────────────────────────────────────

    @Test
    void getAllUsersWithDefaultPaginationReturnsPageResponse() {
        String adminToken = createAdminAndGetToken();

        // Create 5 regular users
        for (int i = 1; i <= 5; i++) {
            doRegister("user" + i);
        }

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/admin/users",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)),
                JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.has("content")).isTrue();
        assertThat(body.has("currentPage")).isTrue();
        assertThat(body.has("pageSize")).isTrue();
        assertThat(body.has("totalPages")).isTrue();
        assertThat(body.has("totalElements")).isTrue();
        // admin + 5 users = 6 total
        assertThat(body.get("totalElements").asLong()).isEqualTo(6L);
        assertThat(body.get("currentPage").asInt()).isEqualTo(0);
    }

    @Test
    void getAllUsersWithCustomPageSizeRespectsBoundary() {
        String adminToken = createAdminAndGetToken();
        for (int i = 1; i <= 5; i++) {
            doRegister("puser" + i);
        }

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/admin/users?page=0&size=3",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)),
                JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.get("pageSize").asInt()).isEqualTo(3);
        assertThat(body.get("content").size()).isEqualTo(3);
        assertThat(body.get("totalPages").asInt()).isGreaterThan(1);
    }

    @Test
    void getAllUsersWithNegativePageReturns400() {
        String adminToken = createAdminAndGetToken();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/admin/users?page=-1",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Authorization (BFLA defense) ─────────────────────────────────────────

    @Test
    void getAllUsersAsRegularUserReturns403() {
        doRegister("normaluser");
        ResponseEntity<JwtResponse> login = doLogin("normaluser");
        String userToken = login.getBody().getToken();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/admin/users",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getAllUsersWithoutTokenReturns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/admin/users",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private String createAdminAndGetToken() {
        User admin = User.builder()
                .username("admin")
                .email("admin@test.com")
                .password(passwordEncoder.encode("Password1!"))
                .role(Role.ROLE_ADMIN)
                .build();
        userRepository.save(admin);

        ResponseEntity<JwtResponse> login = doLogin("admin");
        return login.getBody().getToken();
    }
}
