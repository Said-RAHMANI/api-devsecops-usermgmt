package com.devsecops.usermgmt.integration;

import com.devsecops.usermgmt.dto.JwtResponse;
import com.devsecops.usermgmt.dto.RefreshTokenRequest;
import com.devsecops.usermgmt.dto.TokenRefreshResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for JWT refresh and logout:
 * POST /api/auth/refresh and POST /api/auth/logout.
 */
class RefreshTokenIntegrationTest extends AbstractIntegrationTest {

    @Test
    void refreshTokenReturnsNewAccessAndRefreshTokens() {
        ResponseEntity<JwtResponse> reg = doRegister("frank");
        String oldRefreshToken = reg.getBody().getRefreshToken();

        RefreshTokenRequest req = new RefreshTokenRequest(oldRefreshToken);
        ResponseEntity<TokenRefreshResponse> response = restTemplate.postForEntity(
                "/api/auth/refresh",
                new HttpEntity<>(req, jsonHeaders()),
                TokenRefreshResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isNotBlank();
        assertThat(response.getBody().getRefreshToken()).isNotBlank();
        // Rotation: new refresh token must differ from the old one
        assertThat(response.getBody().getRefreshToken()).isNotEqualTo(oldRefreshToken);
    }

    @Test
    void refreshTokenWithInvalidTokenReturns400() {
        RefreshTokenRequest req = new RefreshTokenRequest("invalid-token-xyz");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/refresh",
                new HttpEntity<>(req, jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void refreshTokenCannotBeReusedAfterRotation() {
        ResponseEntity<JwtResponse> reg = doRegister("grace");
        String firstRefreshToken = reg.getBody().getRefreshToken();

        // First refresh — consumes the token
        restTemplate.postForEntity(
                "/api/auth/refresh",
                new HttpEntity<>(new RefreshTokenRequest(firstRefreshToken), jsonHeaders()),
                TokenRefreshResponse.class);

        // Second use of the same token — must be rejected
        ResponseEntity<String> second = restTemplate.postForEntity(
                "/api/auth/refresh",
                new HttpEntity<>(new RefreshTokenRequest(firstRefreshToken), jsonHeaders()),
                String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void logoutRevokesRefreshToken() {
        doRegister("henry");
        ResponseEntity<JwtResponse> login = doLogin("henry");
        String accessToken   = login.getBody().getToken();
        String refreshToken  = login.getBody().getRefreshToken();

        // Logout
        ResponseEntity<Void> logout = restTemplate.exchange(
                "/api/auth/logout",
                HttpMethod.POST,
                new HttpEntity<>(bearerHeaders(accessToken)),
                Void.class);

        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Refresh token must no longer be valid after logout
        ResponseEntity<String> refreshAfterLogout = restTemplate.postForEntity(
                "/api/auth/refresh",
                new HttpEntity<>(new RefreshTokenRequest(refreshToken), jsonHeaders()),
                String.class);

        assertThat(refreshAfterLogout.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
