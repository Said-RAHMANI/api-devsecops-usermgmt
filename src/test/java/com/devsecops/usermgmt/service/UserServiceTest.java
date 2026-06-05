package com.devsecops.usermgmt.service;

import com.devsecops.usermgmt.dto.UserResponse;
import com.devsecops.usermgmt.entity.Role;
import com.devsecops.usermgmt.entity.User;
import com.devsecops.usermgmt.exception.AccessDeniedException;
import com.devsecops.usermgmt.exception.ResourceNotFoundException;
import com.devsecops.usermgmt.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserService}.
 *
 * <p>The repository is mocked so behaviour can be asserted in isolation.
 * The fourth test exercises the BOLA defense in
 * {@code verifyOwnershipOrAdmin} — a non-admin caller must not be able
 * to read a user that does not belong to them.</p>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User userFixture(Long id, String username, Role role) {
        LocalDateTime now = LocalDateTime.now();
        return User.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .password("hashed")
                .role(role)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Test
    void getCurrentUserReturnsResponseWhenUserExists() {
        User alice = userFixture(1L, "alice", Role.ROLE_USER);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        UserResponse response = userService.getCurrentUser("alice");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getRole()).isEqualTo("ROLE_USER");
    }

    @Test
    void getCurrentUserThrowsResourceNotFoundWhenAbsent() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUser("ghost"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void getUserByIdAsOwnerReturnsResponse() {
        User alice = userFixture(1L, "alice", Role.ROLE_USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        // Ownership check loads the caller by username; same user as target.
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        UserResponse response = userService.getUserById(1L, "alice");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("alice");
    }

    @Test
    void getUserByIdAsDifferentNonAdminThrowsAccessDenied() {
        // BOLA defense: a regular user must not access another user's record.
        User alice = userFixture(1L, "alice", Role.ROLE_USER);
        User bob = userFixture(2L, "bob", Role.ROLE_USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));

        assertThatThrownBy(() -> userService.getUserById(1L, "bob"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("permission");
    }
}
