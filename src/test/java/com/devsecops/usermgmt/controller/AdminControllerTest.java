package com.devsecops.usermgmt.controller;

import com.devsecops.usermgmt.config.JwtConfig;
import com.devsecops.usermgmt.config.SecurityConfig;
import com.devsecops.usermgmt.dto.PageResponse;
import com.devsecops.usermgmt.dto.UserResponse;
import com.devsecops.usermgmt.security.JwtTokenProvider;
import com.devsecops.usermgmt.security.UserDetailsServiceImpl;
import com.devsecops.usermgmt.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link AdminController}.
 *
 * <p>{@code @Import(SecurityConfig.class)} forces the application's custom
 * {@link SecurityConfig} to be loaded in the slice context, which ensures
 * CSRF is disabled and the role-based URL rules are applied. Without it,
 * Spring Boot's default security (CSRF on, no URL rules) would be active.</p>
 */
@WebMvcTest(controllers = AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private JwtConfig jwtConfig;

    private UserResponse sampleUser(long id, String username) {
        LocalDateTime now = LocalDateTime.now();
        return UserResponse.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .role("ROLE_USER")
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Test
    void getAllUsersAsAdminReturnsOk() throws Exception {
        PageResponse<UserResponse> page = PageResponse.<UserResponse>builder()
                .content(List.of(sampleUser(1L, "alice"), sampleUser(2L, "bob")))
                .currentPage(0).pageSize(20).totalPages(1).totalElements(2)
                .build();
        when(userService.getAllUsersPaged(anyInt(), anyInt(), anyString())).thenReturn(page);

        mockMvc.perform(get("/api/admin/users")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].username").value("alice"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.currentPage").value(0));
    }

    @Test
    void getAllUsersAsRegularUserIsForbidden() throws Exception {
        // BFLA defense: non-admin caller must be denied at the URL-level matcher in SecurityConfig.
        mockMvc.perform(get("/api/admin/users")
                        .with(user("alice").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteUserAsAdminReturnsNoContent() throws Exception {
        doNothing().when(userService).adminDeleteUser(1L);

        mockMvc.perform(delete("/api/admin/users/1")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isNoContent());

        verify(userService).adminDeleteUser(1L);
    }
}
