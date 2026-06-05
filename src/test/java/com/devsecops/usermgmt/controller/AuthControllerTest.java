package com.devsecops.usermgmt.controller;

import com.devsecops.usermgmt.config.JwtConfig;
import com.devsecops.usermgmt.dto.JwtResponse;
import com.devsecops.usermgmt.dto.LoginRequest;
import com.devsecops.usermgmt.dto.RegisterRequest;
import com.devsecops.usermgmt.security.JwtTokenProvider;
import com.devsecops.usermgmt.security.UserDetailsServiceImpl;
import com.devsecops.usermgmt.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link AuthController}.
 *
 * <p>The Spring Security filter chain is disabled so the test focuses on
 * controller routing, validation and JSON binding. The {@link AuthService}
 * is mocked to assert the controller's contract independently of any
 * persistence or cryptographic concerns.</p>
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    // The following beans are mocked to satisfy SecurityConfig wiring under @WebMvcTest.
    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private JwtConfig jwtConfig;

    @Test
    void registerReturnsCreatedWithJwtResponse() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("alice")
                .email("alice@example.com")
                .password("password123")
                .build();

        JwtResponse response = JwtResponse.builder()
                .token("jwt-token-value")
                .type("Bearer")
                .username("alice")
                .role("ROLE_USER")
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token-value"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
    }

    @Test
    void registerDuplicateUsernameReturnsBadRequest() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("alice")
                .email("alice@example.com")
                .password("password123")
                .build();

        // AuthService throws IllegalArgumentException for duplicate username,
        // mapped to HTTP 400 by GlobalExceptionHandler.
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new IllegalArgumentException("Username is already taken"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username is already taken"));
    }

    @Test
    void loginReturnsOkWithJwtResponse() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("alice")
                .password("password123")
                .build();

        JwtResponse response = JwtResponse.builder()
                .token("jwt-token-value")
                .type("Bearer")
                .username("alice")
                .role("ROLE_USER")
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token-value"))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void loginBadCredentialsReturnsUnauthorized() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("alice")
                .password("wrong-password")
                .build();

        doThrow(new BadCredentialsException("Invalid username or password"))
                .when(authService).login(any(LoginRequest.class));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication Failed"));
    }
}
