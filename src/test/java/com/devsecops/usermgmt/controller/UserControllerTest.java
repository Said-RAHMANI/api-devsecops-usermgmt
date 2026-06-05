package com.devsecops.usermgmt.controller;

import com.devsecops.usermgmt.config.JwtConfig;
import com.devsecops.usermgmt.dto.UpdateUserRequest;
import com.devsecops.usermgmt.dto.UserResponse;
import com.devsecops.usermgmt.security.JwtTokenProvider;
import com.devsecops.usermgmt.security.UserDetailsServiceImpl;
import com.devsecops.usermgmt.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link UserController}.
 *
 * <p>Uses {@code SecurityMockMvcRequestPostProcessors.user()} instead of
 * {@code @WithMockUser} for compatibility with Spring Security 6 stateless
 * session policy.</p>
 */
@WebMvcTest(controllers = UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private JwtConfig jwtConfig;

    private UserResponse sampleUser() {
        LocalDateTime now = LocalDateTime.now();
        return UserResponse.builder()
                .id(1L)
                .username("alice")
                .email("alice@example.com")
                .role("ROLE_USER")
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Test
    void getCurrentUserReturnsOk() throws Exception {
        when(userService.getCurrentUser("alice")).thenReturn(sampleUser());

        mockMvc.perform(get("/api/users/me").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void getUserByIdAuthorizedReturnsOk() throws Exception {
        when(userService.getUserById(eq(1L), eq("alice"))).thenReturn(sampleUser());

        mockMvc.perform(get("/api/users/1").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void updateCurrentUserReturnsOk() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .username("alice2")
                .email("alice2@example.com")
                .build();

        UserResponse updated = UserResponse.builder()
                .id(1L)
                .username("alice2")
                .email("alice2@example.com")
                .role("ROLE_USER")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userService.updateCurrentUser(eq("alice"), any(UpdateUserRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/users/me")
                        .with(user("alice").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice2"))
                .andExpect(jsonPath("$.email").value("alice2@example.com"));
    }

    @Test
    void unauthenticatedAccessIsRejected() throws Exception {
        // No security post-processor → no authentication → request is rejected
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().is4xxClientError());
    }
}
