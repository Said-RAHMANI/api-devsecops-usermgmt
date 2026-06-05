package com.devsecops.usermgmt.integration;

import com.devsecops.usermgmt.dto.JwtResponse;
import com.devsecops.usermgmt.dto.LoginRequest;
import com.devsecops.usermgmt.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for authentication endpoints:
 * POST /api/auth/register and POST /api/auth/login.
 */
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void registerWithValidDataReturns201WithTokens() {
        ResponseEntity<JwtResponse> response = doRegister("alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isNotBlank();
        assertThat(response.getBody().getRefreshToken()).isNotBlank();
        assertThat(response.getBody().getUsername()).isEqualTo("alice");
        assertThat(response.getBody().getRole()).isEqualTo("ROLE_USER");
    }

    @Test
    void registerDuplicateUsernameReturns400() {
        doRegister("bob");

        ResponseEntity<String> duplicate = restTemplate.postForEntity(
                "/api/auth/register",
                new HttpEntity<>(registerRequest("bob"), jsonHeaders()),
                String.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(duplicate.getBody()).contains("Username is already taken");
    }

    @Test
    void registerWithInvalidEmailReturns400() {
        RegisterRequest req = RegisterRequest.builder()
                .username("charlie")
                .email("not-an-email")
                .password("Password1!")
                .build();

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/register",
                new HttpEntity<>(req, jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Email");
    }

    @Test
    void loginWithValidCredentialsReturns200WithTokens() {
        doRegister("dave");

        ResponseEntity<JwtResponse> response = doLogin("dave");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isNotBlank();
        assertThat(response.getBody().getRefreshToken()).isNotBlank();
        assertThat(response.getBody().getType()).isEqualTo("Bearer");
    }

    @Test
    void loginWithWrongPasswordReturns401() {
        doRegister("eve");

        LoginRequest badLogin = LoginRequest.builder()
                .username("eve")
                .password("wrong-password")
                .build();

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(badLogin, jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Authentication Failed");
    }

    @Test
    void loginWithUnknownUserReturns401() {
        LoginRequest unknownLogin = LoginRequest.builder()
                .username("ghost")
                .password("Password1!")
                .build();

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(unknownLogin, jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
